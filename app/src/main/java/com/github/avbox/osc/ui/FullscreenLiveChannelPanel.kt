package com.github.avbox.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.github.avbox.osc.bean.EpgInfo
import com.github.avbox.osc.bean.LiveChannelGroup
import com.github.avbox.osc.bean.LiveChannelItem

private val PanelGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xE6000000), Color(0x99000000), Color(0x12000000)),
)
private val SelectedBackground = Color(0xFF394C6B)
private val FocusedBackground = Color(0xFF354255)
private val MainText = Color(0xFFF4F6FA)
private val DimText = Color(0xFFAEB8C5)

@Composable
internal fun FullscreenLiveChannelPanel(
    channelList: LiveChannelListUi,
    epg: LiveEpgUi,
    onEpgRowClicked: (Int) -> Boolean,
    onToggleGroup: (Int) -> Unit,
    onSelectChannel: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group header rows always exist, even for collapsed or password-locked groups.
    // Channel data is taken only from the user's active live config.
    val groups = channelList.rows.mapNotNull { row ->
        if (row.channel == null) row.group else null
    }
    var selectedGroup by remember { mutableIntStateOf(channelList.playingGroupIndex) }
    LaunchedEffect(groups, channelList.playingGroupIndex) {
        if (groups.none { it.groupIndex == selectedGroup }) {
            selectedGroup = channelList.playingGroupIndex
        }
    }
    val group = groups.firstOrNull { it.groupIndex == selectedGroup }
    val groupLocked = group != null && channelList.lockedGroups.contains(group.groupIndex)
    val channels = if (groupLocked) emptyList() else group?.liveChannels.orEmpty()
    val channelState = rememberLazyListState()
    LaunchedEffect(selectedGroup, channelList.playingChannelIndex) {
        if (selectedGroup == channelList.playingGroupIndex) {
            val current = channels.indexOfFirst { it.channelIndex == channelList.playingChannelIndex }
            if (current >= 0) channelState.scrollToItem(current)
        } else {
            channelState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier.background(PanelGradient),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 18.dp, end = 6.dp),
        ) {
            Text(
                text = "直播频道",
                color = MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭频道列表", tint = DimText)
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.width(126.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(groups, key = { _, item -> "group_${item.groupIndex}" }) { _, item ->
                    val selected = item.groupIndex == selectedGroup
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) SelectedBackground else if (focused) FocusedBackground else Color.Transparent)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                selectedGroup = item.groupIndex
                                if (channelList.lockedGroups.contains(item.groupIndex)) {
                                    onToggleGroup(item.groupIndex)
                                }
                            }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.groupName.orEmpty(),
                            color = if (selected) MainText else DimText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (channelList.lockedGroups.contains(item.groupIndex)) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "加密分组",
                                tint = DimText,
                                modifier = Modifier.width(13.dp),
                            )
                        }
                    }
                }
            }
            LazyColumn(
                state = channelState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                itemsIndexed(channels, key = { index, channel -> "channel_${selectedGroup}_${index}_${channel.channelIndex}" }) { index, channel ->
                    ChannelPanelRow(
                        item = channel,
                        selected = selectedGroup == channelList.playingGroupIndex &&
                            channel.channelIndex == channelList.playingChannelIndex,
                        onClick = { onSelectChannel(selectedGroup, index) },
                    )
                }
            }
            // FongMi's third rail displays EPG alongside the selected channel
            // rather than opening a separate bright bottom sheet.
            if (selectedGroup == channelList.playingGroupIndex && epg.epgList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        Text(
                            text = "节目单",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MainText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    itemsIndexed(epg.epgList) { index, item ->
                        val now = Date()
                        val currentlyPlaying = item.startdateTime != null && item.enddateTime != null &&
                            !now.before(item.startdateTime) && !now.after(item.enddateTime)
                        val available = item.startdateTime != null && !now.before(item.startdateTime) &&
                            (epg.canCatchup || currentlyPlaying)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (currentlyPlaying) SelectedBackground else Color.Transparent)
                                .clickable(enabled = available) { onEpgRowClicked(index) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "${item.start.orEmpty()}-${item.end.orEmpty()}",
                                fontSize = 11.sp,
                                color = DimText,
                            )
                            Text(
                                text = item.title.orEmpty(),
                                fontSize = 13.sp,
                                color = MainText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) SelectedBackground else if (focused) FocusedBackground else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.channelNum.toString(),
            color = if (selected) MainText else DimText,
            fontSize = 12.sp,
            modifier = Modifier.width(32.dp),
        )
        val logo = item.channelLogo.orEmpty()
        if (logo.startsWith("http://", true) || logo.startsWith("https://", true)) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 36.dp, height = 28.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = item.channelName.orEmpty(),
            color = MainText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
