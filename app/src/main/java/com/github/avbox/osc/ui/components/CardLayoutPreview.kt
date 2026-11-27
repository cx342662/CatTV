package com.github.avbox.osc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie

@Composable
internal fun CardLayoutPreview(
    videos: List<Movie.Video>,
    progress: (Movie.Video) -> Int?,
    onDismiss: () -> Unit,
    onClick: (Movie.Video) -> Unit,
    onChoose: (Int) -> Unit,
    selected: Int = -1,
) {
    val patterns = stringArrayResource(R.array.card_preview_patterns)
    val names = listOf(stringResource(R.string.card_preview_portrait), stringResource(R.string.card_preview_landscape)) + patterns.toList() + stringResource(R.string.card_preview_compact)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.card_select_view), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
                }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(14, key = { it }) { layout ->
                        Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(if (selected == layout) 2.dp else 1.dp, if (selected == layout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp)) {
                                    Text(names[layout], Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { onChoose(layout) }) { Text(stringResource(R.string.card_select_view)) }
                                }
                                if (videos.isEmpty()) Text(stringResource(R.string.card_preview_empty), Modifier.padding(horizontal = 16.dp))
                                else HomeCardCollection(layout, videos.take(8), progress, onClick)
                            }
                        }
                    }
                }
            }
        }
    }
}
