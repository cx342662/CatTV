package com.github.catvod.crawler

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.ui.MainActivity
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps CatVod extensions bound to the currently visible host Activity.
 *
 * Some extensions keep an Activity in InitOrigin and otherwise fall back to
 * ActivityThread.mActivities reflection. That fallback can select a stale
 * Activity and can cause the host Activity to be finished while an async
 * dialog is still being shown.
 */
object PluginActivityBridge {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pluginLoaders = LinkedHashSet<ClassLoader>()

    @JvmStatic
    fun snapshotCallbacks(application: Application?): List<Any>? {
        if (application == null) return null
        return try {
            val field = Application::class.java.getDeclaredField("mActivityLifecycleCallbacks")
            field.isAccessible = true
            val value = field.get(application)
            when (value) {
                is List<*> -> synchronized(value) { value.filterNotNull().toList() }
                is CopyOnWriteArrayList<*> -> value.filterNotNull().toList()
                else -> null
            }
        } catch (error: Throwable) {
            AppLog.e("PluginActivityBridge", "snapshot lifecycle callbacks failed", error)
            null
        }
    }

    @JvmStatic
    fun onPluginInitialized(
        rootLoader: ClassLoader,
        application: Application?,
        callbacksBefore: List<Any>?
    ) {
        synchronized(pluginLoaders) {
            pluginLoaders.addAll(collectPluginLoaders(rootLoader))
        }
        val task = Runnable {
            val activity = MainActivity.activeActivity
            if (activity != null && isUsable(activity)) {
                syncAllPluginActivities(activity)
                replayNewCallbacks(application, callbacksBefore, activity)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) task.run() else mainHandler.post(task)
    }

    @JvmStatic
    fun syncAllPluginActivities(activity: Activity) {
        if (!isUsable(activity)) return
        val snapshot = synchronized(pluginLoaders) { pluginLoaders.toList() }
        snapshot.forEach { syncPluginActivity(it, activity) }
    }

    private fun isUsable(activity: Activity): Boolean =
        !activity.isFinishing && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed)

    private fun collectPluginLoaders(root: ClassLoader): Set<ClassLoader> {
        val found = LinkedHashSet<ClassLoader>()
        val pending = ArrayDeque<ClassLoader>()
        pending.add(root)
        while (pending.isNotEmpty() && found.size < 16) {
            val current = pending.removeFirst()
            if (!found.add(current)) continue
            try {
                val initClass = Class.forName("com.github.catvod.spider.Init", false, current)
                for (methodName in arrayOf("loader", "classLoader", "getClassLoader")) {
                    try {
                        val method = initClass.getDeclaredMethod(methodName)
                        if (Modifier.isStatic(method.modifiers) &&
                            ClassLoader::class.java.isAssignableFrom(method.returnType)
                        ) {
                            method.isAccessible = true
                            (method.invoke(null) as? ClassLoader)?.let { if (!found.contains(it)) pending.add(it) }
                        }
                    } catch (_: Throwable) {
                    }
                }
                for (field in initClass.declaredFields) {
                    try {
                        if (!Modifier.isStatic(field.modifiers)) continue
                        field.isAccessible = true
                        val value = field.get(null)
                        if (value is ClassLoader && !found.contains(value)) pending.add(value)
                        if (value != null) {
                            for (nested in value.javaClass.declaredFields) {
                                try {
                                    nested.isAccessible = true
                                    val nestedValue = nested.get(value)
                                    if (nestedValue is ClassLoader && !found.contains(nestedValue)) pending.add(nestedValue)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
                for (inner in initClass.declaredClasses) {
                    for (field in inner.declaredFields) {
                        try {
                            if (!Modifier.isStatic(field.modifiers)) continue
                            field.isAccessible = true
                            val value = field.get(null)
                            if (value is ClassLoader && !found.contains(value)) pending.add(value)
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return found
    }

    private fun syncPluginActivity(rootLoader: ClassLoader, activity: Activity) {
        if (!isUsable(activity)) return
        for (loader in collectPluginLoaders(rootLoader)) {
            for (name in arrayOf(
                "com.github.catvod.spider.Init",
                "com.github.catvod.spider.InitOrigin",
                "com.github.catvod.spider.AwInit"
            )) {
                try {
                    val type = Class.forName(name, false, loader)
                    syncInitClass(type, activity)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun syncInitClass(type: Class<*>, activity: Activity) {
        var instance: Any? = null
        try {
            val getter = type.getDeclaredMethod("get")
            if (Modifier.isStatic(getter.modifiers)) {
                getter.isAccessible = true
                instance = getter.invoke(null)
            }
        } catch (_: Throwable) {
        }
        if (instance == null) {
            for (field in type.declaredFields) {
                try {
                    if (!Modifier.isStatic(field.modifiers) || !type.isAssignableFrom(field.type)) continue
                    field.isAccessible = true
                    instance = field.get(null)
                    if (instance != null) break
                } catch (_: Throwable) {
                }
            }
        }
        bindActivityFields(type, null, activity)
        if (instance != null) bindActivityFields(type, instance, activity)
        for (method in type.declaredMethods) {
            try {
                val params = method.parameterTypes
                if (params.size == 1 && params[0].isAssignableFrom(Activity::class.java) &&
                    method.name.contains("activity", ignoreCase = true)
                ) {
                    method.isAccessible = true
                    if (Modifier.isStatic(method.modifiers)) method.invoke(null, activity)
                    else if (instance != null) method.invoke(instance, activity)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun bindActivityFields(type: Class<*>, target: Any?, activity: Activity) {
        for (field in type.declaredFields) {
            try {
                val isStatic = Modifier.isStatic(field.modifiers)
                if ((target == null) != isStatic) continue
                field.isAccessible = true
                when {
                    Activity::class.java.isAssignableFrom(field.type) -> field.set(target, activity)
                    WeakReference::class.java.isAssignableFrom(field.type) -> {
                        val old = field.get(target) as? WeakReference<*>
                        val value = old?.get()
                        if (value == null || value is Activity ||
                            field.name.contains("activity", ignoreCase = true)
                        ) field.set(target, WeakReference(activity))
                    }
                }
            } catch (error: Throwable) {
                AppLog.e("PluginActivityBridge", "bind Activity field failed: ${field.name}", error)
            }
        }
    }

    private fun replayNewCallbacks(
        application: Application?,
        before: List<Any>?,
        activity: Activity
    ) {
        if (application == null || before == null || !isUsable(activity)) return
        val after = snapshotCallbacks(application) ?: return
        val added = after.filter { it !in before }
            .filterIsInstance<Application.ActivityLifecycleCallbacks>()
        if (added.isEmpty()) return

        val safeCallbacks = added.map { callback ->
            val safe = safeCallback(callback)
            try {
                application.unregisterActivityLifecycleCallbacks(callback)
                application.registerActivityLifecycleCallbacks(safe)
            } catch (error: Throwable) {
                AppLog.e("PluginActivityBridge", "wrap lifecycle callback failed", error)
            }
            safe
        }
        safeCallbacks.forEach { callback ->
            try {
                callback.onActivityCreated(activity, null)
                callback.onActivityStarted(activity)
                callback.onActivityResumed(activity)
            } catch (error: Throwable) {
                AppLog.e("PluginActivityBridge", "replay lifecycle callback failed", error)
            }
        }
    }

    private fun safeCallback(delegate: Application.ActivityLifecycleCallbacks) =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) =
                invoke("onActivityCreated") { delegate.onActivityCreated(activity, state) }

            override fun onActivityStarted(activity: Activity) =
                invoke("onActivityStarted") { delegate.onActivityStarted(activity) }

            override fun onActivityResumed(activity: Activity) =
                invoke("onActivityResumed") { delegate.onActivityResumed(activity) }

            override fun onActivityPaused(activity: Activity) {
                if (MainActivity.activeActivity != null && MainActivity.activeActivity !== activity) return
                invoke("onActivityPaused") { delegate.onActivityPaused(activity) }
            }

            override fun onActivityStopped(activity: Activity) {
                if (MainActivity.activeActivity != null && MainActivity.activeActivity !== activity) return
                invoke("onActivityStopped") { delegate.onActivityStopped(activity) }
            }

            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) =
                invoke("onActivitySaveInstanceState") { delegate.onActivitySaveInstanceState(activity, state) }

            override fun onActivityDestroyed(activity: Activity) {
                if (MainActivity.activeActivity != null && MainActivity.activeActivity !== activity) return
                invoke("onActivityDestroyed") { delegate.onActivityDestroyed(activity) }
            }

            private inline fun invoke(label: String, block: () -> Unit) {
                try {
                    block()
                } catch (error: Throwable) {
                    AppLog.e("PluginActivityBridge", "plugin $label callback failed", error)
                }
            }
        }
}
