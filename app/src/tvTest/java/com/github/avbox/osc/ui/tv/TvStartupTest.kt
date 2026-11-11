package com.github.avbox.osc.ui.tv

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import com.github.avbox.osc.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TvStartupTest {
    @Test
    fun phoneSubmissionRequiresActivePairingAndValidAddress() {
        val pairing = com.github.avbox.osc.server.SourcePairing
        val active = pairing.open()
        val path = pairing.PREFIX + active.token
        fun post(url: String): Int {
            val type = fi.iki.elonen.NanoHTTPD.IHTTPSession::class.java
            val session = java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                when (method.name) {
                    "getUri" -> path
                    "getMethod" -> fi.iki.elonen.NanoHTTPD.Method.POST
                    "getHeaders" -> mapOf("content-length" to "100", "content-type" to "application/x-www-form-urlencoded")
                    "parseBody" -> null
                    "getParameters" -> mapOf("name" to listOf("Example"), "url" to listOf(url), "kind" to listOf("live"))
                    else -> error(method.name)
                }
            } as fi.iki.elonen.NanoHTTPD.IHTTPSession
            return pairing.serve(session).status.requestStatus
        }
        try {
            assertEquals(400, post("file:///local"))
            assertTrue(active.incoming.tryReceive().isFailure)
            assertEquals(200, post("https://example.test/list"))
            assertEquals(false, active.incoming.tryReceive().getOrThrow().vod)
        } finally {
            pairing.close(active)
        }
        assertEquals(404, post("https://example.test/list"))
    }


    @Test
    fun titleArtworkClearsWhenTheNextSelectionHasNoLogo() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
        val bitmap = android.graphics.Bitmap.createBitmap(100, 30, android.graphics.Bitmap.Config.ARGB_8888)
        val hero = TvHeroView(RuntimeEnvironment.getApplication(), scope,
            loadPoster = { null },
            loadHero = { video -> TvArtworkRepository.Hero(null, "Overview", 9.0,
                logo = if (video.id == "first") bitmap else null) })
        val info = hero.getChildAt(hero.childCount - 1) as android.widget.LinearLayout
        hero.bind(com.github.avbox.osc.bean.Movie.Video().apply { id = "first"; name = "First" })
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(View.GONE, info.getChildAt(0).visibility)
        assertEquals(View.VISIBLE, info.getChildAt(1).visibility)
        hero.bind(com.github.avbox.osc.bean.Movie.Video().apply { id = "second"; name = "Second" })
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(View.VISIBLE, info.getChildAt(0).visibility)
        assertEquals(View.GONE, info.getChildAt(1).visibility)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun referenceImageSizesReplaceExistingSizeInsteadOfAppendingIt() {
        assertEquals("https://mirror.test/t/p/w500/poster.jpg",
            TvArtworkUrls.sized("https://mirror.test/t/p/w500/original/poster.jpg", "w500"))
        assertEquals("https://mirror.test/t/p/w1920/backdrop.jpg",
            TvArtworkUrls.sized("https://mirror.test/t/p/original/backdrop.jpg", "w1920"))
        assertEquals("https://mirror.test/t/p/original/backdrop.jpg",
            TvArtworkUrls.sized("https://mirror.test/t/p/w1920/backdrop.jpg", "original"))
    }

    @Test
    fun wholeScreenFollowsSelectionWhenTmdbHasNoImage() {
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi")
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
        val hero = TvHeroView(RuntimeEnvironment.getApplication(), scope,
            loadPoster = { video -> android.graphics.Bitmap.createBitmap(20, 30, android.graphics.Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (video.id == "first") android.graphics.Color.RED else android.graphics.Color.BLUE)
            } },
            loadHero = { TvArtworkRepository.Hero(null, "", 0.0) },
        )
        hero.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        hero.layout(0, 0, 1280, 720)
        fun select(id: String, color: Int) {
            hero.bind(com.github.avbox.osc.bean.Movie.Video().apply { this.id = id; sourceKey = "fixture" })
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val backdrop = hero.getChildAt(1) as android.widget.ImageView
            assertEquals(1280, backdrop.width)
            assertEquals(720, backdrop.height)
            assertEquals(color, (backdrop.drawable as android.graphics.drawable.BitmapDrawable).bitmap.getPixel(0, 0))
        }
        select("first", android.graphics.Color.RED)
        select("second", android.graphics.Color.BLUE)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun fullBackdropAndHeroInfoKeepTheirProportionsOnDensePhoneDisplays() {
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-xxhdpi")
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val hero = TvHeroView(context, scope)
        hero.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        hero.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        assertEquals(metrics.widthPixels, hero.getChildAt(0).width)
        assertEquals(metrics.heightPixels, hero.getChildAt(0).height)
        val info = hero.getChildAt(hero.childCount - 1)
        assertTrue(info.bottom > metrics.heightPixels / 3)
        assertTrue(info.bottom < metrics.heightPixels * 2 / 3)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun nativeRowsInflateAndBindUnderTvTheme() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.TvTheme)
        controller.create().start().resume().visible()
        val frame = FrameLayout(activity).apply { id = android.R.id.content }
        activity.setContentView(frame)
        val rows = RowsSupportFragment()
        val adapter = ArrayObjectAdapter(ListRowPresenter())
        rows.adapter = adapter
        activity.supportFragmentManager.beginTransaction().replace(frame.id, rows).commitNow()
        val cards = ArrayObjectAdapter(object : Presenter() {
            override fun onCreateViewHolder(parent: ViewGroup) = ViewHolder(TextView(parent.context))
            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
                (viewHolder.view as TextView).text = item.toString()
            }
            override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
        })
        cards.add("Movie")
        adapter.add(ListRow(HeaderItem("Category"), cards))
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
