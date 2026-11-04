package com.github.avbox.osc.ui

import com.github.avbox.osc.bean.LiveChannelGroup
import com.github.avbox.osc.bean.LiveChannelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayViewModelChannelListTest {

    private fun group(groupIndex: Int, password: String = "", channelNames: List<String>): LiveChannelGroup {
        val group = LiveChannelGroup()
        group.groupIndex = groupIndex
        group.groupName = "G$groupIndex"
        group.groupPassword = password
        val items = ArrayList<LiveChannelItem>()
        channelNames.forEachIndexed { index, name ->
            val item = LiveChannelItem()
            item.channelIndex = index
            item.channelName = name
            items.add(item)
        }
        group.liveChannels = items
        return group
    }

    @Test
    fun groupsChanged_buildsRowsAndLockedGroups() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(
            listOf(
                group(0, channelNames = listOf("CCTV1", "CCTV2")),
                group(1, password = "1234", channelNames = listOf("付费台")),
            ),
        )
        val list = vm.state.value.channelList
        assertEquals(listOf("g0", "g1"), list.rows.map { it.key })
        assertEquals(setOf(1), list.lockedGroups)
        assertEquals(0, list.playingGroupIndex)
        assertEquals(-1, list.playingChannelIndex)
    }

    @Test
    fun expandGroup_rebuildsRows() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(listOf(group(0, channelNames = listOf("CCTV1", "CCTV2"))))
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups + 0) }
        assertEquals(listOf("g0", "c0_0", "c0_1"), vm.state.value.channelList.rows.map { it.key })
    }

    @Test
    fun selectionUpdate_keepsRowsInstance() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(listOf(group(0, channelNames = listOf("CCTV1", "CCTV2"))))
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups + 0) }
        val rows = vm.state.value.channelList.rows
        vm.updateChannelList { it.copy(playingGroupIndex = 0, playingChannelIndex = 1) }
        assertSame(rows, vm.state.value.channelList.rows)
        assertEquals(1, vm.state.value.channelList.playingChannelIndex)
    }

    @Test
    fun confirmedPassword_unlocksGroupRows() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(listOf(group(0, password = "1234", channelNames = listOf("付费台"))))
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups + 0) }
        assertEquals(listOf("g0"), vm.state.value.channelList.rows.map { it.key })
        vm.updateChannelList { it.copy(confirmedPasswordGroups = it.confirmedPasswordGroups + 0) }
        assertEquals(setOf(0), vm.state.value.channelList.confirmedPasswordGroups)
        assertEquals(emptySet<Int>(), vm.state.value.channelList.lockedGroups)
        assertEquals(listOf("g0", "c0_0"), vm.state.value.channelList.rows.map { it.key })
    }

    @Test
    fun collapseGroup_rebuildsRowsToHeaderOnly() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(listOf(group(0, channelNames = listOf("CCTV1"))))
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups + 0) }
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups - 0) }
        assertEquals(listOf("g0"), vm.state.value.channelList.rows.map { it.key })
    }

    @Test
    fun scrollRequest_incrementsWithoutTouchingSelection() {
        val vm = LivePlayViewModel()
        vm.updateChannelList { it.copy(scrollRequestId = it.scrollRequestId + 1) }
        vm.updateChannelList { it.copy(scrollRequestId = it.scrollRequestId + 1) }
        assertEquals(2L, vm.state.value.channelList.scrollRequestId)
        assertEquals(0, vm.state.value.channelList.playingGroupIndex)
    }


    @Test
    fun browsingCategory_keepsPlaybackAndScrollStateIndependent() {
        val vm = LivePlayViewModel()
        vm.onChannelGroupsChanged(
            listOf(
                group(0, channelNames = listOf("A")),
                group(1, channelNames = listOf("B")),
                group(2, channelNames = listOf("C")),
                group(3, channelNames = listOf("D")),
            ),
        )
        vm.setPlayingGroupIndex(1)
        vm.setPlayingChannelIndex(7)
        vm.setBrowseGroupListPosition(2, 18)

        val rows = vm.state.value.channelList.rows
        vm.setBrowseGroupIndex(3)

        val list = vm.state.value.channelList
        assertEquals(3, list.browseGroupIndex)
        assertEquals(3, list.tappedGroupIndex)
        assertEquals(1, list.playingGroupIndex)
        assertEquals(7, list.playingChannelIndex)
        assertEquals(2, list.browseGroupFirstVisibleIndex)
        assertEquals(18, list.browseGroupScrollOffset)
        assertSame(rows, list.rows)
    }

    @Test
    fun refreshedGroups_preserveValidBrowseCategoryAndScrollPosition() {
        val vm = LivePlayViewModel()
        val groups = listOf(
            group(0, channelNames = listOf("A")),
            group(1, channelNames = listOf("B")),
            group(2, channelNames = listOf("C")),
        )
        vm.onChannelGroupsChanged(groups)
        vm.setBrowseGroupIndex(2)
        vm.setBrowseGroupListPosition(1, 24)

        vm.onChannelGroupsChanged(groups)

        val list = vm.state.value.channelList
        assertEquals(2, list.browseGroupIndex)
        assertEquals(1, list.browseGroupFirstVisibleIndex)
        assertEquals(24, list.browseGroupScrollOffset)
    }

    @Test
    fun playingChannel_isCarriedByState() {
        val vm = LivePlayViewModel()
        val item = LiveChannelItem()
        item.channelName = "CCTV1"
        vm.updateChannelList { it.copy(playingChannel = item) }
        assertTrue(vm.state.value.channelList.playingChannel === item)
    }
}
