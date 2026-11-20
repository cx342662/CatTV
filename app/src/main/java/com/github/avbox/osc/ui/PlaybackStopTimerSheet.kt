package com.github.avbox.osc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.player.PlaybackStopTimerState
import com.github.avbox.osc.ui.components.AVBoxBottomSheet
import kotlinx.coroutines.delay

@Composable
internal fun PlaybackStopTimerSheet(onDismiss: () -> Unit, onApply: (Long?) -> Unit) {
    val deadline by PlaybackStopTimerState.deadline.collectAsState()
    val remaining by produceState(0L, deadline) {
        while (true) {
            value = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(1000)
        }
    }
    var input by remember { mutableStateOf("30") }
    val minutes = input.toLongOrNull()
    val valid = minutes != null && minutes in 1..1440
    AVBoxBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.detail_stop_timer)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                val status = if (deadline == 0L) stringResource(R.string.stop_timer_off) else {
                    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(java.util.Date(System.currentTimeMillis() + remaining))
                    stringResource(R.string.stop_timer_status, remaining / 60000, remaining / 1000 % 60, time)
                }
                Text(status, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L, 90L).forEach { preset ->
                    FilterChip(selected = minutes == preset, onClick = { input = preset.toString() },
                        label = { Text(preset.toString()) }, modifier = Modifier.weight(1f))
                }
            }
            OutlinedTextField(value = input, onValueChange = { text ->
                if (text.length <= 4 && text.all(Char::isDigit)) input = text
            }, label = { Text(stringResource(R.string.stop_timer_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = input.isNotEmpty() && !valid, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
            Button(onClick = { onApply(minutes) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(stringResource(R.string.stop_timer_apply))
            }
            TextButton(onClick = { onApply(null) }, enabled = deadline > 0L, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stop_timer_cancel))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
