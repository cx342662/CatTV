package com.github.avbox.osc.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.avbox.osc.R
import com.github.avbox.osc.player.state.SelectDialogState

@Composable
internal fun PlayerAspectPanel(state: SelectDialogState, onDismiss: () -> Unit) {
    PlayerDialog(onDismiss) {
        val dismiss = LocalPlayerSheetDismiss.current
        Surface(Modifier.testTag("aspect-panel").widthIn(max = 560.dp).fillMaxWidth(.92f), shape = RoundedCornerShape(28.dp), color = Color(0xE61B1D21)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.tip, Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = dismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, stringResource(R.string.player_panel_close), tint = Color.White) }
                }
                listOf(0 until minOf(4, state.items.size), 4 until state.items.size).forEach { indices ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (index in indices) AspectOption(state.items[index], index == state.defaultIndex) { state.onSelected(index) }
                    }
                }
                Text(stringResource(R.string.player_scale_mode_hint), color = Color.White.copy(alpha = .6f), fontSize = 11.sp, lineHeight = 14.sp)
                Text(stringResource(R.string.player_direction_title), color = Color.White.copy(alpha = .75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                state.orientation?.let { choice ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choice.options.forEachIndexed { index, label -> AspectOption(label, choice.selected == index) { choice.onSelect(index) } }
                    }
                }
                Text(listOf(state.helperText, stringResource(R.string.player_direction_hint)).filter { it.isNotEmpty() }.joinToString(" · "), color = Color.White.copy(alpha = .6f), fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun AspectOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(13.dp), color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .09f)) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
