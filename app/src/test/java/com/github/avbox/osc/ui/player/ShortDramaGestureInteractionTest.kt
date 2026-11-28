package com.github.avbox.osc.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.testing.TestApplication
import com.github.avbox.osc.ui.ComposeTestActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w420dp-h933dp", application = TestApplication::class)
class ShortDramaGestureInteractionTest {
 @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()
 @Test fun verticalSwipesChangeOneEpisodeAndTapOnlyRevealsControls() {
  var next = 0
  var previous = 0
  var taps = 0
  compose.setContent {
   Box(Modifier.fillMaxSize().testTag("drama").shortDramaGestures({ taps++ }, { if (it > 0) next++ else previous++ }))
  }
  compose.onNodeWithTag("drama").performTouchInput { swipeUp() }
  compose.runOnIdle { assertEquals(1, next); assertEquals(0, previous); assertEquals(0, taps) }
  compose.onNodeWithTag("drama").performTouchInput { swipeDown() }
  compose.runOnIdle { assertEquals(1, previous) }
  compose.onNodeWithTag("drama").performTouchInput { swipeLeft() }
  compose.runOnIdle { assertEquals(1, next); assertEquals(1, previous) }
  compose.onNodeWithTag("drama").performTouchInput { click() }
  compose.runOnIdle { assertEquals(1, taps) }
 }
}
