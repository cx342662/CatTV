package com.github.avbox.osc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.bean.Movie

@Composable
internal fun CardDeck(videos: List<Movie.Video>, portrait: Boolean, onClick: (Movie.Video) -> Unit, onLongClick: (Movie.Video) -> Unit, onActiveVideo: (Movie.Video) -> Unit) {
    if (videos.isEmpty()) return
    val pager = rememberPagerState(pageCount = { videos.size.coerceAtMost(8) })
    LaunchedEffect(pager.currentPage, videos) { onActiveVideo(videos[pager.currentPage]) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (portrait) minOf(maxWidth * .62f, 220.dp) else maxWidth * .88f
        val imageHeight = if (portrait) width * 1.5f else width * 9f / 16f
        Box(Modifier.fillMaxWidth().height(imageHeight + 66.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxWidth().height(imageHeight).clipToBounds(), contentAlignment = Alignment.TopCenter) {
            for (depth in 2 downTo 1) {
                val video = videos[(pager.currentPage + depth) % videos.size]
                Box(Modifier.padding(top = (depth * 8).dp).width(width).height(imageHeight).graphicsLayer {
                    scaleX = 1f - depth * .045f
                    scaleY = 1f - depth * .045f
                    rotationZ = if (depth == 1) -5f else 5f
                }.clip(RoundedCornerShape(16.dp))) {
                    VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
                }
            }
            }
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 14.dp) { page ->
                val video = videos[page]
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.width(width), if (portrait) VodCardStyle.Portrait else VodCardStyle.Landscape)
                }
            }
        }
    }
}

