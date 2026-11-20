package com.github.avbox.osc.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackStopTimerState {
    private val current = MutableStateFlow(0L)
    val deadline = current.asStateFlow()
    internal fun update(value: Long) { current.value = value }
}
