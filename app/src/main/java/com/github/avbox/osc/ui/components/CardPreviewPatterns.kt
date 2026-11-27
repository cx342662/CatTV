package com.github.avbox.osc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie

@Composable
internal fun CardPatternView(pattern: Int, videos: List<Movie.Video>, progress: (Movie.Video) -> Int?, onClick: (Movie.Video) -> Unit, onLongClick: (Movie.Video) -> Unit = {}, onActiveVideo: (Movie.Video) -> Unit = {}) {
    if (videos.isEmpty()) return

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            when (pattern) {
                0 -> {
                    val pager = rememberPagerState(pageCount = { videos.size.coerceAtMost(8) })
                    LaunchedEffect(pager.currentPage, videos) { onActiveVideo(videos[pager.currentPage]) }
                    HorizontalPager(state = pager, pageSpacing = 12.dp) { page ->
                        VodCard(videos[page], { onClick(videos[page]) }, { onLongClick(videos[page]) }, Modifier.fillMaxWidth(), VodCardStyle.Wide)
                    }
                    Text("${pager.currentPage + 1} / ${pager.pageCount}", style = MaterialTheme.typography.labelSmall)
                }
                1, 2 -> {
                    val columns = if (pattern == 1) 2 else 3
                    videos.take(6).chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { video -> VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.weight(1f), VodCardStyle.Portrait) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                3 -> videos.take(3).forEach { video -> PreviewTextCard(video, true, onClick, onLongClick = onLongClick) }
                4 -> videos.take(2).forEach { video -> PreviewTextCard(video, false, onClick, onLongClick = onLongClick) }
                5 -> videos.take(5).forEachIndexed { rank, video ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text((rank + 1).toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                        PreviewTextCard(video, true, onClick, Modifier.weight(1f), onLongClick)
                    }
                }
                6 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    videos.take(3).forEach { video -> VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.weight(1f), VodCardStyle.Portrait) }
                }
                7 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { column ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            videos.take(8).filterIndexed { index, _ -> index % 2 == column }.forEachIndexed { index, video ->
                                VodCard(video, { onClick(video) }, { onLongClick(video) }, style = if ((index + column) % 2 == 0) VodCardStyle.Portrait else VodCardStyle.Wide)
                            }
                        }
                    }
                }
                8 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VodCard(videos.first(), { onClick(videos.first()) }, { onLongClick(videos.first()) }, Modifier.weight(2f), VodCardStyle.Overlay)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        videos.drop(1).take(2).forEach { video -> VodCard(video, { onClick(video) }, { onLongClick(video) }, style = VodCardStyle.Overlay) }
                    }
                }
                9 -> videos.take(2).forEach { video -> VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.fillMaxWidth(), VodCardStyle.Wide) }
                10 -> {
                    val watched = videos.filter { progress(it) != null }.take(5)
                    if (watched.isEmpty()) Text(androidx.compose.ui.res.stringResource(R.string.card_preview_no_progress), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    watched.forEach { video ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewTextCard(video, true, onClick, onLongClick = onLongClick)
                            LinearProgressIndicator(progress = { (progress(video) ?: 0).coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
}

@Composable
internal fun PreviewTextCard(video: Movie.Video, horizontal: Boolean, onClick: (Movie.Video) -> Unit, modifier: Modifier = Modifier, onLongClick: (Movie.Video) -> Unit = {}, onActiveVideo: (Movie.Video) -> Unit = {}) {
    PressableCard(onClick = { onClick(video) }, onLongClick = { onLongClick(video) }, modifier = modifier.fillMaxWidth()) {
        if (horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(88.dp).aspectRatio(2f / 3f)) { PreviewPoster(video) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { PreviewCopy(video) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { PreviewPoster(video) }
                PreviewCopy(video)
            }
        }
}

}

@Composable
private fun PreviewPoster(video: Movie.Video) {
    VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
}

@Composable
private fun PreviewCopy(video: Movie.Video) {
    Text(video.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (video.year > 0) Text(video.year.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    video.des?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
}






