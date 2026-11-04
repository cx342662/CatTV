package com.github.avbox.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
private val FocusedBackground = Color(0x553F5068)
private val MainText = Color(0xFFF4F6FA)
private val DimText = Color(0xFFD0D5DE)

@Composable
internal fun FullscreenLiveChannelPanel(
    channelList: LiveChannelListUi,
    onToggleGroup: (Int) -> Unit,
    onSelectChannel: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = channelList.rows.mapNotNull { row -> if (row.channel == null) row.group else null }
    // Browse selection is independent from the channel currently playing.
    // tappedGroupIndex is persisted in the ViewModel, so reopening the overlay
    // must restore the last browsed category instead of snapping to group zero.
    val selectedGroup = when {
        groups.any { it.groupIndex == channelList.tappedGroupIndex } -> channelList.tappedGroupIndex
        groups.any { it.groupIndex == channelList.playingGroupIndex } -> channelList.playingGroupIndex
        else -> groups.firstOrNull()?.groupIndex ?: 0
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
            modifier = Modifier.width(150.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(groups, key = { _, item -> "overlay_group_${item.groupIndex}" }) { _, item ->
                val selected = item.groupIndex == selectedGroup
                var focused by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clickable {
                            // Persist the selected category in the ViewModel as well as
                            // local UI state. Previously only locked groups invoked this
                            // callback, so normal category taps were lost when the
                            // overlay recomposed/reopened and the list fell back to group 0.
                            selectedGroup = item.groupIndex
                            onToggleGroup(item.groupIndex)
                        }
                        .focusable(),
                    shape = RoundedCornerShape(7.dp),
                    color = if (selected) SelectedBackground else if (focused) FocusedBackground else Color(0x33202020),
                ) {
                    Text(
                        text = item.groupName.orEmpty(),
                        color = if (selected) Color(0xFF171A22) else MainText,
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
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) SelectedBackground else if (focused) FocusedBackground else Color(0x33202020),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.channelNum.toString(),
                color = if (selected) Color(0xFF171A22) else DimText,
                fontSize = 14.sp,
                modifier = Modifier.width(48.dp),
            )
            Text(
                text = item.channelName.orEmpty(),
                color = if (selected) Color(0xFF171A22) else MainText,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
