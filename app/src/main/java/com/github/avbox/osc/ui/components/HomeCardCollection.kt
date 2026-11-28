package com.github.avbox.osc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.bean.Movie

@Composable
internal fun HomeCardCollection(layout: Int, videos: List<Movie.Video>, progress: (Movie.Video) -> Int?, onClick: (Movie.Video) -> Unit, onLongClick: (Movie.Video) -> Unit = {}, onActiveVideo: (Movie.Video) -> Unit = {}, edgePadding: androidx.compose.ui.unit.Dp = 16.dp) {
    if (videos.isEmpty()) return
    LaunchedEffect(layout, videos) { onActiveVideo(videos.first()) }
    when (layout) {
        0, 1 -> LazyRow(contentPadding = PaddingValues(horizontal = edgePadding), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(videos) { _, video ->
                VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.width(if (layout == 0) 128.dp else 256.dp), if (layout == 0) VodCardStyle.Portrait else VodCardStyle.Landscape)
            }
        }
        2 -> ReferenceCardCarousel(videos, false, onClick, onLongClick, onActiveVideo, edgePadding)
        5 -> ReferenceCardCarousel(videos, true, onClick, onLongClick, onActiveVideo, edgePadding)
        8, 14 -> Box(Modifier.padding(horizontal = edgePadding)) { CardDeck(videos, layout == 14, onClick, onLongClick, onActiveVideo) }
    }
}





