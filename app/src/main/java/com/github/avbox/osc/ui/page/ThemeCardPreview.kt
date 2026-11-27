package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.*

@Composable
internal fun ThemeCardPreview(onDismiss: () -> Unit) {
    val vm: CardPreviewViewModel = viewModel(key = "local-card-preview")
    val categories by vm.categories.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Int?>(null) }
    var highlighted by remember { mutableStateOf<Int?>(null) }
    var section by remember { mutableStateOf("@recommendations") }
    var batch by remember { mutableStateOf(false) }
    var selectedSections by remember { mutableStateOf(setOf("@recommendations")) }
    val layoutConfig by vm.config.collectAsStateWithLifecycle()
    val titles = stringArrayResource(R.array.card_local_sample_titles).toList()
    val description = stringResource(R.string.card_sample_synopsis)
    val samples = remember(titles, description) { localCardPreviewVideos(titles, description) }
    CompositionLocalProvider(LocalCardPreviewMode provides true) {
        val layout = chosen
        if (layout == null) {
            CardLayoutPreview(samples, { 36 }, onDismiss, {}, { highlighted = it; chosen = it }, highlighted ?: vm.selected(section, layoutConfig))
        } else {
            val datasets = (listOf("@recommendations" to stringResource(R.string.home_recommend)) + categories.map { it.id.orEmpty() to it.name.orEmpty() }).distinctBy { it.first }
            CardPickerSheet(stringResource(R.string.card_select_data), { chosen = null }) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(stringResource(R.string.card_batch_add), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.card_batch_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = batch, onCheckedChange = { batch = it; selectedSections = setOf(section) })
                            }
                        }
                    }
                    if (categories.isEmpty()) item { Text(stringResource(R.string.card_categories_not_loaded)) }
                    items(datasets, key = { it.first }) { (id, name) ->
                        val picked = if (batch) id in selectedSections else section == id
                        Surface(onClick = {
                            if (batch) selectedSections = if (picked) selectedSections - id else selectedSections + id
                            else { section = id; selectedSections = setOf(id) }
                        }, shape = RoundedCornerShape(26.dp), color = if (picked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f)) {
                                    Icon(painterResource(R.drawable.ic_card_source), null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(name, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                if (batch) Checkbox(picked, onCheckedChange = null) else RadioButton(picked, onClick = null)
                            }
                        }
                    }
                }
                Button(enabled = vm.canApply() && selectedSections.isNotEmpty(), onClick = {
                    (if (batch) selectedSections else setOf(section)).forEach { vm.apply(it, layout) }
                    chosen = null
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(R.string.card_apply_home), fontSize = 17.sp)
                }
            }
        }
    }
}
