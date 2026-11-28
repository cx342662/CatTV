package com.github.avbox.osc.player.engine

internal data class AdaptiveBufferProfile(val minMs: Int, val maxMs: Int, val startMs: Int, val resumeMs: Int, val bytes: Int) {
    companion object {
        fun forDevice(memoryMb: Int, lowRam: Boolean): AdaptiveBufferProfile = when {
            lowRam || memoryMb in 1..128 -> AdaptiveBufferProfile(6000, 16000, 700, 2000, 16 * 1024 * 1024)
            memoryMb in 129..256 -> AdaptiveBufferProfile(10000, 30000, 500, 1800, 32 * 1024 * 1024)
            else -> AdaptiveBufferProfile(12000, 40000, 500, 1800, 64 * 1024 * 1024)
        }
    }
}
