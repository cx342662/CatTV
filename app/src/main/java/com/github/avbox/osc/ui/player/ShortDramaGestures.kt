package com.github.avbox.osc.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal fun shortDramaModeActive(enabled: Boolean, portraitVideo: Boolean, fullScreen: Boolean, portraitWindow: Boolean): Boolean = enabled && portraitVideo && fullScreen && portraitWindow

internal fun shortDramaSwipeDirection(distance: Float, height: Int): Int {
    if (abs(distance) < maxOf(96f, height * .12f)) return 0
    return if (distance < 0) 1 else -1
}

@Composable
internal fun Modifier.shortDramaGestures(onTap: () -> Unit, onEpisode: (Int) -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val episode by rememberUpdatedState(onEpisode)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var dx = 0f
            var dy = 0f
            var moved = false
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.size > 1 || change.isConsumed) { cancelled = true; break }
                dx += change.position.x - change.previousPosition.x
                dy += change.position.y - change.previousPosition.y
                if (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop) moved = true
                if (moved) change.consume()
                if (!change.pressed) break
            }
            if (!cancelled) {
                if (!moved) tap()
                else if (abs(dy) > abs(dx)) shortDramaSwipeDirection(dy, size.height).takeIf { it != 0 }?.let(episode)
            }
        }
    }
}
