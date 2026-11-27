package com.github.avbox.osc.ui.components

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.testing.TestApplication
import com.github.avbox.osc.ui.ComposeTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w420dp-h933dp-xxhdpi", application = TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardPickerPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()

    @Test fun wholeViewSelectsLayoutAndUsesReferencePresentation() {
        var chosen = -1
        val samples = localCardPreviewVideos(listOf("时光代理人", "火柴盒大电影", "人性本兽", "魔女嘉莉"), "在照相馆中开启一段时光之旅。")
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xffff2d63))) {
                CompositionLocalProvider(LocalCardPreviewMode provides true) {
                    CardLayoutPreview(samples, { 36 }, {}, {}, { chosen = it }, selected = 0)
                }
            }
        }
        compose.waitForIdle()
        System.getenv("CATTV_UI_SCREENSHOT_DIR")?.let { directory ->
            val output = File(directory).also { it.mkdirs() }
            File(output, "card-picker-reference.png").outputStream().use {
                compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("横向卡片").performClick()
        compose.runOnIdle { assertEquals(1, chosen) }
    }

    @Test fun allLayoutsRenderIncludingBothStackedCarousels() {
        val selected = mutableIntStateOf(0)
        val samples = localCardPreviewVideos(listOf("时光代理人", "火柴盒大电影", "人性本兽", "魔女嘉莉"), "照片中的时光与故事。")
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCardPreviewMode provides true) {
                    Box(Modifier.size(420.dp, 800.dp).testTag("layout").verticalScroll(rememberScrollState())) {
                        HomeCardCollection(selected.intValue, samples, { 36 }, {})
                    }
                }
            }
        }
        for (layout in 0..14) {
            compose.runOnIdle { selected.intValue = layout }
            compose.waitForIdle()
            assertTrue(compose.onNodeWithTag("layout").fetchSemanticsNode().boundsInRoot.height > 0f)
            if (layout in listOf(5, 8, 14)) System.getenv("CATTV_UI_SCREENSHOT_DIR")?.let { directory ->
                File(directory, "card-layout-$layout.png").outputStream().use {
                    compose.onNodeWithTag("layout").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test fun cloverControlHasCircularButtonDimensions() {
        compose.setContent { MaterialTheme { CloverRoundControl({}, Modifier.testTag("clover"), Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) } }
        compose.onNodeWithTag("clover").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        System.getenv("CATTV_UI_SCREENSHOT_DIR")?.let { directory ->
            File(directory, "clover-round-button.png").outputStream().use {
                compose.onNodeWithTag("clover").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}


