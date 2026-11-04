package com.github.avbox.osc.ui.page

import com.github.avbox.osc.bean.LiveChannelGroup
import com.github.avbox.osc.bean.LiveChannelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList

class HomeLiveChannelsTest {
    private fun group(password: String = "", vararg names: String): LiveChannelGroup {
        return LiveChannelGroup().apply {
            groupPassword = password
            liveChannels = ArrayList(names.mapIndexed { index, name ->
                LiveChannelItem().apply {
                    channelIndex = index
                    channelName = name
                    channelUrls = arrayListOf("https://example.invalid/live/$index.m3u8")
                }
            })
        }
    }

    @Test
    fun emptyConfigurationHasNoPreviewChannels() {
        assertTrue(livePreviewChannels(emptyList()).isEmpty())
    }

    @Test
    fun doesNotExposeLockedGroups() {
        val result = livePreviewChannels(listOf(group("secret", "Private"), group("", "Public")))
        assertEquals(listOf("Public"), result.map { it.channelName })
    }

    @Test
    fun skipsUnplayableChannelsAndDuplicateNames() {
        val first = group("", "One", "TWO")
        val second = group("", "two", "Three")
        first.liveChannels!![0].channelUrls = arrayListOf()
        assertEquals(
            listOf("TWO", "Three"),
            livePreviewChannels(listOf(first, second)).map { it.channelName },
        )
    }

    @Test
    fun limitsPreviewToTenChannels() {
        val names = (1..20).map { "Channel$it" }.toTypedArray()
        val result = livePreviewChannels(listOf(group("", *names)))
        assertEquals(10, result.size)
        assertEquals("Channel10", result.last().channelName)
    }
}
