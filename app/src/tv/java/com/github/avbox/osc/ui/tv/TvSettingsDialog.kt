package com.github.avbox.osc.ui.tv

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.avbox.osc.R
import com.github.avbox.osc.event.UiChange
import com.github.avbox.osc.event.UiEvents
import com.github.avbox.osc.player.thirdparty.PlayerCatalog
import com.github.avbox.osc.player.state.PlaybackSettingsState
import com.github.avbox.osc.ui.settings.UiSettingsState
import com.github.avbox.osc.util.DanmuSettingsState
import com.github.avbox.osc.util.SearchSettingsState

internal class TvSettingsDialog(
    context: Context,
    private val onSources: () -> Unit,
    private val onTmdb: () -> Unit,
    private val onLiveSources: () -> Unit,
    private val sourceAddress: (Boolean) -> String,
    private val editSource: (Boolean) -> Unit,
    private val page: Int = R.string.tv_settings_title,
) : Dialog(context, R.style.TvTheme) {
    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(26), dp(30), dp(24))
        }
        fun row(vararg cells: Cell) {
            cells.forEach { cell ->
                content.addView(setting(cell), LinearLayout.LayoutParams(-1, dp(64)).apply {
                    bottomMargin = dp(24)
                })
            }
        }
        fun sourceRow(vod: Boolean, label: Int, history: () -> Unit) {
            val line = LinearLayout(context)
            line.addView(setting(Cell(label, { sourceAddress(vod) }) { editSource(vod) }),
                LinearLayout.LayoutParams(0, dp(64), 1f))
            val historyTile = setting(Cell(R.string.tv_ui_004, { "" }, history))
            line.addView(historyTile, LinearLayout.LayoutParams(dp(112), dp(64)).apply { leftMargin = dp(16) })
            content.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
        }
        fun toggle(title: Int, read: () -> Boolean, write: (Boolean) -> Unit) = Cell(title,
            { context.getString(if (read()) R.string.tv_setting_on else R.string.tv_setting_off) },
            { write(!read()) })
        fun subpage(title: Int) = Cell(title, { "›" }) {
            TvSettingsDialog(context, onSources, onTmdb, onLiveSources, sourceAddress, editSource, title).show()
        }
        when (page) {
            R.string.tv_settings_title -> {
                sourceRow(true, R.string.tv_settings_sources, onSources)
                sourceRow(false, R.string.tv_settings_live, onLiveSources)
                row(subpage(R.string.settings_play), subpage(R.string.tv_settings_danmu))
                row(subpage(R.string.tv_settings_personal), subpage(R.string.tv_settings_cache))
                row(Cell(R.string.tv_settings_artwork, { "›" }, onTmdb))
            }
            R.string.settings_play -> {
                val players = PlayerCatalog.all().sortedDescending()
                val renders = listOf("SurfaceView", "TextureView")
                row(Cell(R.string.settings_play_kernel, { PlayerCatalog.name(PlaybackSettingsState.config.playType) }) {
                    chooseLabels(R.string.settings_play_kernel, players.map { PlayerCatalog.name(it) },
                        players.indexOf(PlaybackSettingsState.config.playType)) { PlaybackSettingsState.playType = players[it] }
                }, Cell(R.string.settings_play_render, { renders.getOrElse(1 - PlaybackSettingsState.config.playRender) { renders.first() } }) {
                    chooseLabels(R.string.settings_play_render, renders, 1 - PlaybackSettingsState.config.playRender) {
                        if (it == 1 && PlaybackSettingsState.config.playTunnel) PlaybackSettingsState.playTunnel = false
                        PlaybackSettingsState.playRender = 1 - it
                    }
                })
                row(toggle(R.string.settings_auto_switch_line, { PlaybackSettingsState.config.autoSwitchLine }) { PlaybackSettingsState.autoSwitchLine = it },
                    toggle(R.string.settings_m3u8_purify, { PlaybackSettingsState.config.m3u8Purify }) { PlaybackSettingsState.m3u8Purify = it })
                row(toggle(R.string.settings_play_tunnel, { PlaybackSettingsState.config.playTunnel }) { PlaybackSettingsState.playTunnel = it },
                    toggle(R.string.settings_play_prefer_aac, { PlaybackSettingsState.config.preferAac }) { PlaybackSettingsState.preferAac = it })
                row(Cell(R.string.settings_buffer_time, { PlaybackSettingsState.config.bufferTimes.toString() }) {
                    choose(R.string.settings_buffer_time, listOf(1, 2, 3, 5, 10), PlaybackSettingsState.config.bufferTimes) { PlaybackSettingsState.bufferTimes = it }
                })
            }
            R.string.tv_settings_danmu -> row(toggle(R.string.settings_danmu_switch, { DanmuSettingsState.config.open }) { DanmuSettingsState.open = it })
            R.string.tv_settings_personal -> {
                row(toggle(R.string.settings_incognito, { UiSettingsState.config.incognito }) {
                    UiSettingsState.incognito = it
                    UiEvents.post(UiChange.HistoryChanged)
                }, toggle(R.string.settings_history_merge, { SearchSettingsState.config.historyMerge }) {
                    SearchSettingsState.historyMerge = it
                    UiEvents.post(UiChange.HistoryChanged)
                })
                row(Cell(R.string.settings_search_threads, { SearchSettingsState.config.searchThreads.toString() }) {
                    choose(R.string.settings_search_threads, listOf(2, 4, 8, 16, 24, 32), SearchSettingsState.config.searchThreads) { SearchSettingsState.searchThreads = it }
                })
            }
            R.string.tv_settings_cache -> row(
                toggle(R.string.tv_settings_cache_play, { PlaybackSettingsState.config.playCache }) { PlaybackSettingsState.playCache = it },
                toggle(R.string.tv_settings_preload, { PlaybackSettingsState.config.preloadNextEpisode }) { PlaybackSettingsState.preloadNextEpisode = it })
        }
        row(Cell(R.string.common_back, { "‹" }) { dismiss() })
        val scroll = ScrollView(context).apply {
            isFocusable = false
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF020508.toInt(), 0xFF041C32.toInt()),
            )
            addView(content)
        }
        setContentView(scroll)
        content.getChildAt(0)?.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.decorView?.systemUiVisibility = 5894
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        }

    }

    private data class Cell(val title: Int, val value: () -> String, val action: () -> Unit)

    private fun setting(cell: Cell): LinearLayout {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                colors = intArrayOf(0xFF191D22.toInt(), 0xFF192E40.toInt())
            }
            setPadding(dp(20), 0, dp(20), 0)
        }
        row.addView(text(context.getString(cell.title), 24), LinearLayout.LayoutParams(0, -1, 1f))
        val value = text(cell.value(), 22).apply {
            maxWidth = dp(850)
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        row.addView(value)
        row.setOnClickListener { cell.action(); value.text = cell.value() }
        row.setOnFocusChangeListener { _, focused ->
            row.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                colors = if (focused) intArrayOf(Color.WHITE, Color.WHITE) else intArrayOf(0xFF191D22.toInt(), 0xFF192E40.toInt())
            }
            for (index in 0 until row.childCount) {
                (row.getChildAt(index) as? TextView)?.setTextColor(if (focused) Color.BLACK else Color.WHITE)
            }
            row.scaleX = if (focused) 1.01f else 1f
            row.scaleY = if (focused) 1.01f else 1f
            value.text = cell.value()
        }
        return row
    }

    private fun choose(title: Int, choices: List<Int>, current: Int, select: (Int) -> Unit) {
        chooseLabels(title, choices.map(Int::toString), choices.indexOf(current)) { select(choices[it]) }
    }

    private fun chooseLabels(title: Int, choices: List<String>, current: Int, select: (Int) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(context).setTitle(title)
            .setSingleChoiceItems(choices.toTypedArray(), current) { dialog, index ->
                select(index)
                dialog.dismiss()
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun text(value: String, size: Int) = TextView(context).apply {
        text = value
        setTextColor(Color.WHITE)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * tvLayoutScale(context))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun dp(value: Int) = (value * tvLayoutScale(context)).toInt()
}
