package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun MediaBadgeSettings(onDismiss: () -> Unit) {
    val display = MediaBadges.display
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun runPackAction(action: suspend () -> Unit) {
        loading = true
        error = false
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                com.github.avbox.osc.util.AppLog.e("MediaBadges", failure)
                error = true
            } finally {
                loading = false
            }
        }
    }
    AVBoxBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.media_badges_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsSwitchRow(stringResource(R.string.media_badges_enable), display.enabled, {
                MediaBadges.update(display.copy(enabled = it))
            })
            SettingsSwitchRow(stringResource(R.string.media_badges_player), display.player, {
                MediaBadges.update(display.copy(player = it))
            }, enabled = display.enabled)
            SettingsSwitchRow(stringResource(R.string.media_badges_follow), display.followControls, {
                MediaBadges.update(display.copy(followControls = it))
            }, enabled = display.enabled && display.player)
            SettingsSwitchRow(stringResource(R.string.media_badges_cards), display.cards, {
                MediaBadges.update(display.copy(cards = it))
            }, enabled = display.enabled)
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = false
                },
                label = { Text(stringResource(R.string.media_badges_url)) },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = error,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error) Text(stringResource(R.string.media_badges_error))
            TextButton(enabled = !loading && url.isNotBlank(), onClick = {
                runPackAction {
                    MediaBadges.importPack(url)
                    url = ""
                }
            }) { Text(stringResource(if (loading) R.string.media_badges_loading else R.string.media_badges_import)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            BadgePackOption(
                title = stringResource(R.string.media_badges_builtin),
                selected = MediaBadges.packUrl.isEmpty(),
                enabled = !loading,
                onSelect = { runPackAction { MediaBadges.selectPack("") } },
            )
            for (savedUrl in MediaBadges.packUrls) {
                androidx.compose.runtime.key(savedUrl) {
                    BadgePackOption(
                        title = savedUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull()?.takeIf { it.isNotBlank() }
                            ?: savedUrl,
                        url = savedUrl,
                        selected = MediaBadges.packUrl == savedUrl,
                        enabled = !loading,
                        onSelect = { runPackAction { MediaBadges.selectPack(savedUrl) } },
                        onDelete = { runPackAction { MediaBadges.deletePack(savedUrl) } },
                    )
                }
            }
            Text(stringResource(R.string.media_badges_preview), modifier = Modifier.padding(vertical = 8.dp))
            MediaBadgeRow("2160p WEB-DL Dolby Vision HDR10+ HEVC Atmos 10bit", preview = true)
            MediaBadgeRow("1080p BluRay REMUX HDR10 H.265 DTS", Modifier.padding(top = 8.dp), preview = true)
        }
    }
}

@Composable
private fun BadgePackOption(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    url: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            if (url != null) {
                SelectionContainer {
                    Text(url, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.common_delete))
            }
        }
    }
}
