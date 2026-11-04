package com.github.avbox.osc.ui.page

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.avbox.osc.R
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.bean.LiveChannelGroup
import com.github.avbox.osc.bean.LiveChannelItem
import com.github.avbox.osc.ui.LivePlayActivity

/**
 * Displays at most ten channels already loaded from the user's chosen live config.
 * Never creates a default channel list or fetches a live subscription on its own.
 */
internal fun livePreviewChannels(groups: List<LiveChannelGroup>): List<LiveChannelItem> =
    groups.asSequence()
        .filter { it.groupPassword.isNullOrEmpty() }
        .flatMap { it.liveChannels.orEmpty().asSequence() }
        .filter { !it.channelName.isNullOrBlank() && !it.channelUrls.isNullOrEmpty() }
        .distinctBy { it.channelName.orEmpty().trim().lowercase() }
        .take(10)
        .toList()

@Composable
internal fun HomeLiveChannels(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val channels = livePreviewChannels(ApiConfig.get().channelGroupList)
    if (channels.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "直播频道",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(channels, key = { it.channelName.orEmpty() }) { channel ->
                HomeLiveChannelCard(channel = channel) {
                    context.startActivity(
                        Intent(context, LivePlayActivity::class.java)
                            .putExtra(LivePlayActivity.EXTRA_INITIAL_CHANNEL_NAME, channel.channelName)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HomeLiveChannelCard(channel: LiveChannelItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .width(138.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val logo = channel.channelLogo.orEmpty()
            if (logo.startsWith("https://", ignoreCase = true)) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(38.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_config_live),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = channel.channelName.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
