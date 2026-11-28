package com.github.avbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.bean.Movie
import kotlin.math.abs

@Composable
internal fun ReferenceCardCarousel(videos: List<Movie.Video>, sideInfo: Boolean, onClick: (Movie.Video) -> Unit, onLongClick: (Movie.Video) -> Unit, onActiveVideo: (Movie.Video) -> Unit, edgePadding: Dp) {
    if (videos.isEmpty()) return
    val pager = rememberPagerState(pageCount = { videos.size })
    LaunchedEffect(pager.settledPage, videos) { videos.getOrNull(pager.settledPage)?.let(onActiveVideo) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)), contentPadding = PaddingValues(horizontal = edgePadding), pageSpacing = 14.dp, beyondViewportPageCount = 0) { page ->
            val video = videos.getOrNull(page) ?: return@HorizontalPager
            val transform = Modifier.graphicsLayer {
                val distance = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                scaleX = 1f - .025f * distance
                scaleY = 1f - .025f * distance
            }
            if (sideInfo) {
                PressableCard(onClick = { onClick(video) }, onLongClick = { onLongClick(video) }, modifier = transform.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().height(162.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.width(108.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
                            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
                        }
                        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(video.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (video.year > 0) Text(video.year.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            video.des?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            } else {
                VodCard(video, { onClick(video) }, { onLongClick(video) }, transform.fillMaxWidth(), VodCardStyle.Wide)
            }
        }
        if (videos.size > 1) Row(Modifier.align(Alignment.CenterHorizontally).semantics { stateDescription = "${pager.currentPage + 1}/${videos.size}" }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            val first = (pager.currentPage - 3).coerceIn(0, (videos.size - 7).coerceAtLeast(0))
            for (index in first until minOf(first + 7, videos.size)) {
                val selected = index == pager.currentPage
                Box(Modifier.width(if (selected) 16.dp else 5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .65f else .15f)))
            }
        }
    }
}
