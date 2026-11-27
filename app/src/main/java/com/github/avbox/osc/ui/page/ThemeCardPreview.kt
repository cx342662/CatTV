package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.*

@Composable
internal fun ThemeCardPreview(onDismiss: () -> Unit) {
    val vm: CardPreviewViewModel = viewModel(key = "local-card-preview")
    val categories by vm.categories.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Int?>(null) }
    var section by remember { mutableStateOf("@recommendations") }
    val layoutConfig by vm.config.collectAsStateWithLifecycle()
    val titles = stringArrayResource(R.array.card_local_sample_titles).toList()
    val description = stringResource(R.string.card_local_sample_hint)
    val samples = remember(titles, description) { localCardPreviewVideos(titles, description) }
    CompositionLocalProvider(LocalCardPreviewMode provides true) {
        val layout = chosen
        if (layout == null) {
            CardLayoutPreview(samples, { 36 }, onDismiss, {}, { chosen = it }, vm.selected(section, layoutConfig))
        } else {
            val datasets = listOf("@recommendations" to stringResource(R.string.home_recommend)) + categories.map { it.id.orEmpty() to it.name.orEmpty() }
            Dialog(onDismissRequest = { chosen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Text(stringResource(R.string.card_select_data), style = MaterialTheme.typography.titleLarge)
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { HomeCardCollection(layout, samples, { 36 }, {}) }
                            if (categories.isEmpty()) item { Text(stringResource(R.string.card_categories_not_loaded)) }
                            items(datasets.distinctBy { it.first }, key = { it.first }) { (id, name) ->
                                TextButton(onClick = { section = id }) {
                                    RadioButton(selected = section == id, onClick = null)
                                    Text(name, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.common_back)) }
                            Button(enabled = vm.canApply(), onClick = { vm.apply(section, layout); chosen = null }) { Text(stringResource(R.string.card_apply_home)) }
                        }
                    }
                }
            }
        }
    }
}
