package com.github.avbox.osc.ui

import android.content.res.Configuration
import android.os.Build
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.player.PlaybackService
import com.github.avbox.osc.ui.page.MainScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.AppThemeState
import com.github.avbox.osc.util.PermissionHelper
import com.github.avbox.osc.util.AppLog
import com.github.catvod.crawler.PluginActivityBridge

private const val PREWARM_ON_RESUME_DELAY_MS = 2000L

class MainActivity : BaseActivity() {

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    private fun applyStatusBarAppearance() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val light = !AppThemeState.isDark(night)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    override fun onStart() {
        super.onStart()
        activeActivity = this
        PluginActivityBridge.syncAllPluginActivities(this)
    }

    override fun onResume() {
        super.onResume()
        activeActivity = this
        PluginActivityBridge.syncAllPluginActivities(this)
        applyStatusBarAppearance()
        PlaybackService.prewarm(this, PREWARM_ON_RESUME_DELAY_MS)
    }

    override fun onDestroy() {
        PluginActivityBridge.onActivityDestroyed(this)
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }

    private fun pluginFinishCaller(): StackTraceElement? =
        Throwable().stackTrace.firstOrNull {
            it.className.startsWith("com.github.catvod.spider.") ||
                it.className.startsWith("com.wexfnw.")
        }

    override fun finish() {
        val caller = pluginFinishCaller()
        if (caller != null) {
            AppLog.e(
                "PluginActivityBridge",
                "blocked extension finish() from ${caller.className}#${caller.methodName}",
                Throwable()
            )
            return
        }
        super.finish()
    }

    override fun finishAffinity() {
        val caller = pluginFinishCaller()
        if (caller != null) {
            AppLog.e(
                "PluginActivityBridge",
                "blocked extension finishAffinity() from ${caller.className}#${caller.methodName}",
                Throwable()
            )
            return
        }
        super.finishAffinity()
    }

    override fun finishAndRemoveTask() {
        val caller = pluginFinishCaller()
        if (caller != null) {
            AppLog.e(
                "PluginActivityBridge",
                "blocked extension finishAndRemoveTask() from ${caller.className}#${caller.methodName}",
                Throwable()
            )
            return
        }
        super.finishAndRemoveTask()
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        applyStatusBarAppearance()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !PermissionHelper.isStorageGranted(this)) {
            PermissionHelper.requestStorage(this) { _, _ -> }
        }
        PermissionHelper.requestNotificationIfNeeded(this)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                MainScreen()
            }
        }
    }

    companion object {
        @Volatile
        @JvmField
        var activeActivity: MainActivity? = null
    }
}

