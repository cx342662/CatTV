package com.github.avbox.osc.player.engine
import org.junit.Test
import org.junit.Assert.*
class AdaptiveBufferProfileTest {
 @Test fun budgetsScaleWithMemoryAndLowRamOverridesMemoryClass() {
  assertEquals(16 * 1024 * 1024, AdaptiveBufferProfile.forDevice(512, true).bytes)
  assertEquals(32 * 1024 * 1024, AdaptiveBufferProfile.forDevice(256, false).bytes)
  assertEquals(64 * 1024 * 1024, AdaptiveBufferProfile.forDevice(512, false).bytes)
  for (memory in listOf(64, 128, 256, 512)) {
   val p = AdaptiveBufferProfile.forDevice(memory, false)
   assertTrue(p.startMs <= p.resumeMs && p.resumeMs <= p.minMs && p.minMs <= p.maxMs)
  }
 }
}
