package com.github.avbox.osc.util

import android.app.Application
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class CatTvUpdaterTest {
    @Test fun tvLatestDoesNotHideMobileUpdate() {
        val releases = JSONArray("""[{"tag_name":"tv-arm32-v70"},{"tag_name":"v1.3.4"},{"tag_name":"v1.3.5","body":"评分修复"}]""")
        val selected = CatTvUpdater.selectMobileRelease(releases)
        assertEquals("v1.3.5", selected?.getString("tag_name"))
        assertEquals("评分修复", selected?.getString("body"))
    }
    @Test fun draftAndPrereleaseAreNotOffered() {
        val releases = JSONArray("""[{"tag_name":"v1.4.0","draft":true},{"tag_name":"v1.3.9","prerelease":true},{"tag_name":"v1.3.5"}]""")
        assertEquals("v1.3.5", CatTvUpdater.selectMobileRelease(releases)?.getString("tag_name"))
    }
}
