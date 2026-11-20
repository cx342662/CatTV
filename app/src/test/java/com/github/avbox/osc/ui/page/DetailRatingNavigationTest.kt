package com.github.avbox.osc.ui.page

import android.app.Activity
import android.app.Application
import com.github.avbox.osc.bean.Movie
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class DetailRatingNavigationTest {
    @Test fun actualNavigationCarriesTheScoreAndSubjectFromPosterCard() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val video = Movie.Video().apply {
                id = "playable-id"; sourceKey = "playable-source"; name = "可能的爱"
                doubanScore = 8.6; doubanId = "1306612"
            }
            controller.get().openVodCardOrDetail(video)
            val intent = shadowOf(controller.get()).nextStartedActivity
            assertEquals("playable-id", intent.getStringExtra("id"))
            assertEquals("1306612", intent.getStringExtra("doubanId"))
            assertEquals(8.6, intent.getDoubleExtra("doubanScore", -1.0), 0.0)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
