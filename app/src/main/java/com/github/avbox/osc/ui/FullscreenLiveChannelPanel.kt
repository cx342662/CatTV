package com.github.avbox.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.github.avbox.osc.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.github.avbox.osc.bean.LiveChannelItem

private val PanelGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xD9000000), Color(0xA6000000), Color(0x4D000000)),
)
private val SelectedBackground = Color(0xFFCCD0E6)
private val FocusedBackground = Color.White
private val MainText = Color(0xFFF4F6FA)
private val DimText = Color(0xFFD0D5DE)

@Composable
internal fun FullscreenLiveChannelPanel(
    channelList: LiveChannelListUi,
    onBrowseGroup: (Int) -> Unit,
    onBrowseGroupScrollChanged: (Int, Int) -> Unit,
    onSelectChannel: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    epg: LiveEpgUi? = null,
    onEpgClick: (Int) -> Boolean = { false },
) {
    val groups = channelList.rows.mapNotNull { row -> if (row.channel == null) row.group else null }
    // The ViewModel owns category selection and scroll position so the same
    // browse state survives the portrait/fullscreen composable swap.
    val selectedGroup = when {
        groups.any { it.groupIndex == channelList.browseGroupIndex } -> channelList.browseGroupIndex
        groups.any { it.groupIndex == channelList.playingGroupIndex } -> channelList.playingGroupIndex
        else -> groups.firstOrNull()?.groupIndex ?: 0
    }
    val groupListState = rememberLazyListState(
        initialFirstVisibleItemIndex = channelList.browseGroupFirstVisibleIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = channelList.browseGroupScrollOffset,
    )
    val entryFocus = remember { FocusRequester() }
    val entryIndex = remember { groupListState.firstVisibleItemIndex }
    LaunchedEffect(groups.isNotEmpty(), epg != null) {
        if (groups.isNotEmpty() && epg != null) {
            androidx.compose.runtime.withFrameNanos { }
            entryFocus.requestFocus()
        }
    }
    LaunchedEffect(groupListState) {
        snapshotFlow { groupListState.firstVisibleItemIndex to groupListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onBrowseGroupScrollChanged(index, offset) }
    }
    val group = groups.firstOrNull { it.groupIndex == selectedGroup }
    val locked = group != null && channelList.lockedGroups.contains(group.groupIndex)
    val channels = if (locked) emptyList() else group?.liveChannels.orEmpty()
    val channelState = rememberLazyListState()
    LaunchedEffect(selectedGroup, channelList.playingChannelIndex) {
        if (selectedGroup == channelList.playingGroupIndex) {
            val current = channels.indexOfFirst { it.channelIndex == channelList.playingChannelIndex }
            if (current >= 0) channelState.scrollToItem(current)
        } else {
            channelState.scrollToItem(0)
        }
    }

    Row(
        modifier = modifier
            .background(PanelGradient)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(
            state = groupListState,
            modifier = Modifier.width(150.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(groups, key = { _, item -> "overlay_group_${item.groupIndex}" }) { index, item ->
                val selected = item.groupIndex == selectedGroup
                var focused by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (epg != null && index == entryIndex) Modifier.focusRequester(entryFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable {
                            // Browsing a category must not expand/collapse the legacy
                            // channel rows. That structural rebuild was resetting the
                            // left category LazyColumn to its first item.
                            onBrowseGroup(item.groupIndex)
                        },
                    shape = RoundedCornerShape(7.dp),
                    color = if (focused) FocusedBackground else if (selected) SelectedBackground else Color(0x33202020),
                ) {
                    Text(
                        text = item.groupName.orEmpty(),
                        color = if (selected || focused) Color(0xFF171A22) else MainText,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        }
        LazyColumn(
            state = channelState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(channels, key = { index, channel -> "overlay_channel_${selectedGroup}_${index}_${channel.channelIndex}" }) { index, channel ->
                ChannelPanelRow(
                    item = channel,
                    selected = selectedGroup == channelList.playingGroupIndex &&
                        channel.channelIndex == channelList.playingChannelIndex,
                    onClick = { onSelectChannel(selectedGroup, index) },
                )
            }
        }
        if (epg != null) {
            val now = remember(epg.epgList) { java.util.Date() }
            val currentIndex = epg.epgList.indexOfFirst {
                val start = it.startdateTime
                val end = it.enddateTime
                start != null && end != null && !now.before(start) && now.before(end)
            }
            val programmeList = rememberLazyListState()
            LaunchedEffect(epg.channelName, currentIndex) {
                programmeList.scrollToItem(currentIndex.coerceAtLeast(0))
            }
            LazyColumn(
                state = programmeList,
                modifier = Modifier.weight(1.25f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (epg.epgList.isEmpty()) item {
                    Text(stringResource(R.string.live_epg_empty), color = DimText,
                        modifier = Modifier.padding(12.dp))
                }
                itemsIndexed(epg.epgList) { index, item ->
                    val started = item.startdateTime?.let { !now.before(it) } == true
                    val playable = index == currentIndex || (started && epg.canCatchup)
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(enabled = playable) { onEpgClick(index) },
                        shape = RoundedCornerShape(7.dp),
                        color = if (focused) FocusedBackground else if (index == currentIndex) SelectedBackground else Color(0x33202020),
                    ) {
                        Text(
                            text = "${item.start.orEmpty()}  ${item.title.orEmpty()}",
                            color = if (focused || index == currentIndex) Color(0xFF171A22) else MainText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPanelRow(
    item: LiveChannelItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (focused) FocusedBackground else if (selected) SelectedBackground else Color(0x33202020),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.channelNum.toString(),
                color = if (selected || focused) Color(0xFF171A22) else DimText,
                fontSize = 14.sp,
                modifier = Modifier.width(48.dp),
            )
            Text(
                text = item.channelName.orEmpty(),
                color = if (selected || focused) Color(0xFF171A22) else MainText,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
