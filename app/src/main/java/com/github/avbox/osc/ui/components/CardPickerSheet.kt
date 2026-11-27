package com.github.avbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.avbox.osc.R

@Composable
internal fun CardPickerSheet(title: String, onBack: () -> Unit, backLabel: Int = R.string.common_back, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.90f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFF2F4F0) else Color(0xFF17191C)) {
                Column(Modifier.navigationBarsPadding()) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(44.dp, 6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .22f), RoundedCornerShape(50)))
                    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack, modifier = Modifier.width(76.dp)) { Text(stringResource(backLabel), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(title, Modifier.weight(1f), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.width(76.dp))
                    }
                    content()
                }
            }
        }
    }
}

