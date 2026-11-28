package com.github.avbox.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.ui.components.PressableCard
import com.github.avbox.osc.ui.components.VodPoster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcePickerGrid(done: List<SearchViewModel.SourceResult>, running: Boolean, selectedSource: String?, onSelectSource: (String?) -> Unit, topPad: Dp, onCardClick: (Movie.Video) -> Unit, onCardLongClick: (Movie.Video) -> Unit) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedSource) { gridState.scrollToItem(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val entries = remember(done, selectedSource) { done.filter { selectedSource == null || it.sourceKey == selectedSource }.flatMap { source -> source.videos.map { source.sourceName to it } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when { maxWidth >= 840.dp -> 6; maxWidth >= 600.dp -> 4; else -> 3 }
        LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = topPad + 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "sources") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = selectedSource == null, onClick = { onSelectSource(null) }, label = { Text("${stringResource(R.string.common_all)} ${done.sumOf { it.videos.size }}") }, shape = RoundedCornerShape(22.dp)) }
                            items(done, key = { it.sourceKey }) { source ->
                                FilterChip(selected = source.sourceKey == selectedSource, onClick = { onSelectSource(if (selectedSource == source.sourceKey) null else source.sourceKey) }, label = { Text("${source.sourceName} ${source.videos.size}", maxLines = 1) }, shape = RoundedCornerShape(22.dp))
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.search_source_filter)) }
                    }
                    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            items(entries.size, key = { index -> "${entries[index].second.sourceKey}:${entries[index].second.id}:$index" }) { index ->
                val (source, video) = entries[index]
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    PressableCard(onClick = { onCardClick(video) }, onLongClick = { onCardLongClick(video) }, shape = RoundedCornerShape(14.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true, altName = video.en)
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(horizontal = 7.dp, vertical = 5.dp)) {
                                Text(source, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val quality = Regex("(?i)(2160P|1080P|720P|4K|HDR)").find(video.note.orEmpty())?.value
                            if (quality != null) Text(quality, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .5f)).padding(horizontal = 5.dp, vertical = 3.dp))
                        }
                    }
                    Text(video.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (menuOpen) ModalBottomSheet(onDismissRequest = { menuOpen = false }) {
        TextButton(onClick = { onSelectSource(null); menuOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.common_all)} ${done.sumOf { it.videos.size }}") }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(done, key = { it.sourceKey }) { source ->
                TextButton(onClick = { onSelectSource(source.sourceKey); menuOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("${source.sourceName} ${source.videos.size}") }
            }
        }
    }
}
