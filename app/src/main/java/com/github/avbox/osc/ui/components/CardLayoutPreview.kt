package com.github.avbox.osc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie

@Composable
internal fun CardLayoutPreview(videos: List<Movie.Video>, progress: (Movie.Video) -> Int?, onDismiss: () -> Unit, onClick: (Movie.Video) -> Unit, onChoose: (Int) -> Unit, selected: Int = -1) {
    val patterns = stringArrayResource(R.array.card_preview_patterns)
    val names = listOf(stringResource(R.string.card_preview_portrait), stringResource(R.string.card_preview_landscape)) + patterns.toList() + stringResource(R.string.card_preview_compact) + stringResource(R.string.card_portrait_deck)
    val descriptions = stringArrayResource(R.array.card_view_descriptions)
    val order = com.github.avbox.osc.util.referenceHomeCardLayouts
    CardPickerSheet(stringResource(R.string.card_select_view), onDismiss, backLabel = R.string.common_cancel) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(order.size, key = { order[it] }) { index ->
                val layout = order[index]
                Surface(shape = RoundedCornerShape(32.dp), color = Color.Transparent, border = if (selected == layout) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier.fillMaxWidth().clickable { onChoose(layout) }) {
                    Column(Modifier.padding(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(names[layout], fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(descriptions[layout], fontSize = 15.sp, lineHeight = 20.sp, color = Color(0xFF8E8E93))
                        }
                        HomeCardCollection(layout, videos, progress, { onChoose(layout) }, edgePadding = 24.dp)
                    }
                }
            }
        }
    }
}

