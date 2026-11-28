package com.github.avbox.osc.ui.player
import org.junit.Assert.*
import org.junit.Test
class ShortDramaSwipeTest {
 @Test fun onlyEnabledPortraitFullscreenCanActivate() {
  assertTrue(shortDramaModeActive(true, true, true, true))
  assertFalse(shortDramaModeActive(false, true, true, true))
  assertFalse(shortDramaModeActive(true, false, true, true))
  assertFalse(shortDramaModeActive(true, true, false, true))
  assertFalse(shortDramaModeActive(true, true, true, false))
 }

 @Test fun deliberateSwipeChangesOneEpisodeAndSmallMotionDoesNothing() {
  assertEquals(1, shortDramaSwipeDirection(-500f, 2800))
  assertEquals(-1, shortDramaSwipeDirection(500f, 2800))
  assertEquals(0, shortDramaSwipeDirection(150f, 2800))
  assertEquals(0, shortDramaSwipeDirection(0f, 2800))
 }
}
