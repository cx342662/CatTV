package com.github.avbox.osc.ui.page

import android.app.Activity
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.testing.TestApplication
import com.github.avbox.osc.ui.DetailActivity
import com.github.avbox.osc.ui.SearchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApplication::class)
class HomeSourcePickerNavigationTest {
    @Test fun homeSelectsSourcesAndResultOpensDetailWithoutSearchLoop() {
        Robolectric.buildActivity(Activity::class.java).setup().use { owner ->
            val activity = owner.get()
            val video = Movie.Video().apply { name = "同名影片"; id = "room-1"; sourceKey = "source-a" }
            activity.dispatchHomeCardClick(video)
            val picker = Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(SearchActivity::class.java.name, picker.component?.className)
            assertEquals("同名影片", picker.getStringExtra("title"))
            assertTrue(picker.getBooleanExtra("sourcePicker", false))
            activity.openVodCardOrDetail(video)
            val detail = Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(DetailActivity::class.java.name, detail.component?.className)
            assertFalse(detail.getBooleanExtra("sourcePicker", false))
        }
    }
}
