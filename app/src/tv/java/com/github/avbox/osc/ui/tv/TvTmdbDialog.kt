package com.github.avbox.osc.ui.tv

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.page.ConfigManageViewModel

internal class TvTmdbDialog(private val context: Context, private val vm: ConfigManageViewModel) {
    fun show() {
        val state = vm.tmdbState().value
        val actions = vm.tmdbActions()
        val items = arrayOf(
            context.getString(R.string.tv_tmdb_enabled, context.getString(if (state.enabled) R.string.tv_setting_on else R.string.tv_setting_off)),
            context.getString(if (state.apiKey.isBlank()) R.string.tv_artwork_key_missing else R.string.tv_artwork_key_ready),
            context.getString(R.string.tv_tmdb_api_base),
            context.getString(R.string.tv_tmdb_image_base),
            context.getString(R.string.tv_tmdb_test_api),
            context.getString(R.string.tv_tmdb_test_image),
            context.getString(R.string.tv_artwork_clear),
        )
        AlertDialog.Builder(context).setTitle(R.string.tv_settings_artwork).setItems(items) { _, index ->
            when (index) {
                0 -> { actions.setEnabled(!state.enabled); show() }
                1 -> configure()
                2 -> edit(items[index], state.apiBase) { actions.setApiBase(it); actions.commitApiBase() }
                3 -> edit(items[index], state.imageBase) { actions.setImageBase(it); actions.commitImageBase() }
                4 -> actions.testApi()
                5 -> actions.testImage()
                6 -> {
                    com.github.avbox.osc.api.TmdbPoster.clearCachedPosters()
                    android.widget.Toast.makeText(context, R.string.tv_artwork_cleared, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton(R.string.common_back, null).show()
    }

    private fun configure() {
        val state = vm.tmdbState().value
        val actions = vm.tmdbActions()
        val builtin = state.apiKey == com.github.avbox.osc.BuildConfig.TV_TMDB_KEY && state.apiKey.isNotBlank()
        val column = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 12, 32, 12)
        }
        fun field(title: Int, value: String, secret: Boolean = false): EditText {
            column.addView(android.widget.TextView(context).apply { setText(title) })
            return EditText(context).apply {
                setSingleLine()
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    if (secret) android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_URI
                setText(value)
                if (secret && builtin) setHint(R.string.tv_artwork_builtin_hint)
                column.addView(this)
            }
        }
        val key = field(R.string.tv_tmdb_api_key, if (builtin) "" else state.apiKey, true)
        val api = field(R.string.tv_tmdb_api_base, state.apiBase)
        val image = field(R.string.tv_tmdb_image_base, state.imageBase)
        AlertDialog.Builder(context).setTitle(R.string.tv_settings_artwork).setView(column)
            .setPositiveButton(R.string.tv_ui_059) { _, _ ->
                if (!builtin || key.text.isNotBlank()) actions.setApiKey(key.text.toString().trim())
                actions.setApiBase(api.text.toString().trim())
                actions.commitApiBase()
                actions.setImageBase(image.text.toString().trim())
                actions.commitImageBase()
                show()
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun edit(title: String, value: String, save: (String) -> Unit) {
        val input = EditText(context).apply { setSingleLine(); setText(value) }
        AlertDialog.Builder(context).setTitle(title).setView(input)
            .setPositiveButton(R.string.tv_ui_059) { _, _ -> save(input.text.toString().trim()); show() }
            .setNegativeButton(R.string.common_cancel, null).show()
    }
}
