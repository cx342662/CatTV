package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.CardLayoutPreview
import com.github.avbox.osc.ui.components.HomeCardCollection
import com.github.avbox.osc.ui.components.ProvideTmdbPosterLookup

@Composable
internal fun ThemeCardPreview(onDismiss: () -> Unit) {
    val vm: HomeViewModel = viewModel(key = "theme-card-preview")
    val rec by vm.rec.collectAsStateWithLifecycle()
    val partitions by vm.partitions.collectAsStateWithLifecycle()
    val percents by vm.cardPlayedPercents.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Int?>(null) }
    var section by remember { mutableStateOf("@recommendations") }
    val layoutConfig by vm.cardLayoutConfig.collectAsStateWithLifecycle()
    val applied = vm.cardLayout(section, layoutConfig)
    val context = LocalContext.current
    val click: (com.github.avbox.osc.bean.Movie.Video) -> Unit = { video ->
        vm.rememberCardDouban(video)
        context.dispatchVodCardClick(video, onAction = { vm.handleAction(it) })
    }
    LaunchedEffect(vm) { vm.loadHome() }
    ProvideTmdbPosterLookup(vm) {
        val layout = chosen
        if (layout == null) {
            CardLayoutPreview(
                videos = (rec.videos + partitions.flatMap { it.videos }).distinctBy { "${it.sourceKey}:${it.id}:${it.name}" },
                progress = { vm.cardPlayedPercent(it, percents) },
                onDismiss = onDismiss,
                onClick = click,
                onChoose = { chosen = it },
                selected = applied,
            )
        } else {
            val datasets = listOf("@recommendations" to stringResource(R.string.home_recommend)) + partitions.map { it.sort.id.orEmpty() to it.sort.name.orEmpty() }
            val selectedVideos = if (section == "@recommendations") rec.videos else partitions.firstOrNull { it.sort.id == section }?.videos.orEmpty()
            Dialog(onDismissRequest = { chosen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Text(stringResource(R.string.card_select_data), style = MaterialTheme.typography.titleLarge)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { HomeCardCollection(layout, selectedVideos.take(8), { vm.cardPlayedPercent(it, percents) }, click) }
                            items(datasets, key = { it.first }) { (id, name) ->
                                TextButton(onClick = { section = id; partitions.firstOrNull { it.sort.id == id }?.let { vm.ensureLoaded(id) } }) {
                                    RadioButton(selected = section == id, onClick = null)
                                    Text(name, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.common_back)) }
                            Button(enabled = selectedVideos.isNotEmpty(), onClick = { vm.applyCardLayout(section, layout); chosen = null }) { Text(stringResource(R.string.card_apply_home)) }
                        }
                    }
                }
            }
        }
    }
}


