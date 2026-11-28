package com.github.avbox.osc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.player.state.ParamsChoice
import com.github.avbox.osc.player.state.SelectDialogState
import com.github.avbox.osc.testing.TestApplication
import com.github.avbox.osc.ui.components.LocalCardPreviewMode
import com.github.avbox.osc.ui.components.localCardPreviewVideos
import com.github.avbox.osc.ui.player.PlayerAspectPanel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w420dp-h933dp-xxhdpi", application = TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceScreensPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()

    @Test fun sourceChipsFilterActualCardsAndPosterOpensSelectedSource() {
        val first = localCardPreviewVideos(listOf("时光代理人", "火柴盒大电影"), "本地预览")
        val second = localCardPreviewVideos(listOf("时光代理人", "火柴盒大电影"), "本地预览")
        first.forEach { it.sourceKey = "a" }
        second.forEach { it.sourceKey = "b" }
        val selected = mutableStateOf<String?>(null)
        var opened: String? = null
        val results = listOf(SearchViewModel.SourceResult("a", "片源 A", SearchViewModel.ResultState.Done, first), SearchViewModel.SourceResult("b", "片源 B", SearchViewModel.ResultState.Done, second))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCardPreviewMode provides true) {
                    Box(Modifier.fillMaxSize().testTag("sources")) {
                        SourcePickerGrid(results, false, selected.value, { selected.value = it }, 0.dp, { opened = it.sourceKey }, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        capture("source-picker-grid.png", false)
        compose.onNodeWithText("片源 B 2").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("b", selected.value) }
        compose.onNodeWithContentDescription("火柴盒大电影").performClick()
        compose.runOnIdle { assertEquals("b", opened) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w840dp-h420dp-xxhdpi")
    fun aspectAndOrientationOptionsAreIndependentAndClickable() {
        var ratio = -1
        var direction = -1
        val state = SelectDialogState("画面比例", listOf("自适应", "裁剪铺满", "拉伸全屏", "高度拉伸", "宽度拉伸", "16:9", "4:3", "1:1", "9:16"), 0, orientation = ParamsChoice(listOf("自动", "横屏", "竖屏", "跟随手机"), 1) { direction = it }, helperText = "1920 × 1080", onSelected = { ratio = it })
        compose.setContent { MaterialTheme { PlayerAspectPanel(state, {}) } }
        compose.waitForIdle()
        capture("player-aspect-panel.png", true)
        compose.onNodeWithText("9:16").performClick()
        compose.onNodeWithText("竖屏").performClick()
        compose.runOnIdle { assertEquals(8, ratio); assertEquals(2, direction) }
    }

    private fun capture(name: String, dialog: Boolean) {
        System.getenv("CATTV_UI_SCREENSHOT_DIR")?.let { directory ->
            File(File(directory).also { it.mkdirs() }, name).outputStream().use {
                val node = if (dialog) compose.onNodeWithTag("aspect-panel") else compose.onNodeWithTag("sources")
                node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
