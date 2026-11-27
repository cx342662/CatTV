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
internal fun HomeCardCollection(layout: Int, videos: List<Movie.Video>, progress: (Movie.Video) -> Int?, onClick: (Movie.Video) -> Unit, onLongClick: (Movie.Video) -> Unit = {}, onActiveVideo: (Movie.Video) -> Unit = {}) {
    if (videos.isEmpty()) return
    LaunchedEffect(layout, videos) { onActiveVideo(videos.first()) }
    when (layout) {
        0, 1 -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(videos) { _, video ->
                VodCard(video, { onClick(video) }, { onLongClick(video) }, Modifier.width(if (layout == 0) 120.dp else 260.dp), if (layout == 0) VodCardStyle.Portrait else VodCardStyle.Wide)
            }
        }
        13 -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            videos.take(8).forEach { PreviewTextCard(it, true, onClick, onLongClick = onLongClick) }
        }
        in 2..12 -> CardPatternView(layout - 2, videos, progress, onClick, onLongClick, onActiveVideo)
    }
}



