package com.github.avbox.osc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.MovieSort

@Composable
internal fun CloverBrowseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CloverRoundControl(onClick, modifier, Modifier.glassTopBarSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
internal fun CloverRoundControl(onClick: () -> Unit, modifier: Modifier = Modifier, decoration: Modifier = Modifier) {
    Box(
        modifier = modifier.size(56.dp).then(decoration).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_clover), stringResource(R.string.home_clover_browse), Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun HomeBrowsePanel(sorts: List<MovieSort.SortData>, onDismiss: () -> Unit, onView: (MovieSort.SortData, Map<String, String>) -> Unit) {
    var selectedId by remember(sorts) { mutableStateOf(sorts.firstOrNull()?.id) }
    val selected = sorts.firstOrNull { it.id == selectedId }
    var filters by remember(selectedId) { mutableStateOf(selected?.filterSelect?.toMap().orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_browse_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.home_browse_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sorts.forEach { sort -> FilterChip(selected = selectedId == sort.id, onClick = { selectedId = sort.id }, label = { Text(sort.name.orEmpty()) }) }
                        }
                    }
                    items(selected?.filters.orEmpty()) { filter ->
                        val filterKey = filter.key
                        if (!filterKey.isNullOrBlank()) {
                            Column {
                                Text(filter.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filter.values.orEmpty().entries.toList(), key = { it.key }) { entry ->
                                        FilterChip(selected = filters[filterKey] == entry.key, onClick = { filters = filters + (filterKey to entry.key) }, label = { Text(entry.value) })
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
                    Button(enabled = selected != null, onClick = { selected?.let { onView(it, filters) } }) {
                        Text(stringResource(R.string.home_browse_view))
                    }
                }
            }
        }
    }
}


