package com.github.avbox.osc.data

import org.junit.Assert.*
import org.junit.Test

class LiveQualityLabelsTest {
    @Test fun qualityDigitsAreNeverEpisodeNumbers() {
        for (label in listOf("原画2K60", "蓝光4M", "1080P", "720P", "4K", "原画", "蓝光8M", "HDR")) {
            assertEquals(label, -1, EpisodeMatcher.extractEpisodeNumber(label))
        }
    }
    @Test fun ordinaryEpisodesStillMatch() {
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("第12集"))
        assertEquals(3, EpisodeMatcher.extractEpisodeNumber("EP03"))
        assertEquals(8, EpisodeMatcher.extractEpisodeNumber("08"))
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("第12集 1080p"))
    }
}
