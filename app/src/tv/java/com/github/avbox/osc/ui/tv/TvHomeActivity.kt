package com.github.avbox.osc.ui.tv

import com.github.avbox.osc.R

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.github.avbox.osc.api.AppBootstrap
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.ui.LivePlayActivity
import com.github.avbox.osc.ui.PartitionListActivity
import com.github.avbox.osc.ui.SearchActivity
import com.github.avbox.osc.ui.SearchViewModel
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.*
import com.github.avbox.osc.util.PermissionHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TvHomeActivity : BaseActivity() {
    private val home by lazy { ViewModelProvider(this)[HomeViewModel::class.java] }
    private val history by lazy { ViewModelProvider(this)[HistoryViewModel::class.java] }
    private val collects by lazy { ViewModelProvider(this)[CollectViewModel::class.java] }
    private val search by lazy { ViewModelProvider(this)[SearchViewModel::class.java] }
    private val config by lazy {
        ViewModelProvider(this, ConfigManageViewModel.defaultFactory())[ConfigManageViewModel::class.java]
    }
    private val rows = RowsSupportFragment()
    private val rowAdapter = ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false).apply {
        headerPresenter = null
        setSelectEffectEnabled(false)
    })
    private val rails = linkedMapOf<String, Pair<ListRow, ArrayObjectAdapter>>()
    private lateinit var hero: TvHeroView
    private lateinit var status: TextView
    private lateinit var navigation: LinearLayout
    private lateinit var categoryNavigation: LinearLayout
    private var categoryKeys: List<String> = emptyList()
    private var section = 0
    private var selectedSort: String? = null

    override fun onResume() {
        super.onResume()
        cattv.update.TvUpdate.check(this)
    }

    override fun keepStatusBarHidden() = true

    override fun init() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(9, 13, 22))
        }
        hero = TvHeroView(this, lifecycleScope)
        root.addView(hero, FrameLayout.LayoutParams(-1, -1))
        navigation = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        navigation.addView(navAction("⌂") { showRecommended() }, LinearLayout.LayoutParams(dp(56), dp(64)))
        listOf(6 to R.string.tv_ui_008, 1 to R.string.tv_ui_003, 2 to R.string.tv_ui_004, 3 to R.string.tv_ui_005, 4 to R.string.tv_ui_006, 5 to R.string.tv_ui_007, 7 to R.string.tv_ui_009).forEach { (index, resource) ->
            val text = getString(resource)
            navigation.addView(navAction(text) {
                when (index) {
                    0 -> { section = 0; rails.clear(); rowAdapter.clear(); renderHome() }
                    1 -> showSearch()
                    2 -> { section = 2; showRecords() }
                    3 -> { section = 3; showRecords() }
                    4 -> showSources()
                    5 -> showSites()
                    6 -> startActivity(Intent(this, LivePlayActivity::class.java))
                    7 -> TvSettingsDialog(this,
                        onSources = { showSources() },
                        onTmdb = { TvTmdbDialog(this, config).show() },
                        onLiveSources = { showSources(false) },
                        sourceAddress = { vod -> if (vod) config.activeUrl.value else config.liveActiveUrl.value },
                        editSource = { vod -> showSourcePairing(vod, { showSources(vod) }, config::commitAdd) },
                    ).show()
                    8 -> { section = 0; home.reload() }
                }
            }, LinearLayout.LayoutParams(-2, dp(64)).apply { rightMargin = dp(30) })
        }
        root.addView(navigation, FrameLayout.LayoutParams(-1, dp(76), Gravity.TOP).apply {
            leftMargin = dp(32)
            rightMargin = dp(32)
        })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categoryNavigation = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(32), 0, dp(32), 0)
        }
        val categories = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(categoryNavigation)
        }
        bottom.addView(categories, LinearLayout.LayoutParams(-1, dp(48)))
        status = label(getString(R.string.tv_ui_011), 16f)
        status.setPadding(dp(32), 0, dp(32), 0)
        status.setTextColor(Color.argb(165, 255, 255, 255))
        status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(13).toFloat())
        bottom.addView(status, LinearLayout.LayoutParams(-1, 0))
        val frame = FrameLayout(this).apply { id = View.generateViewId() }
        bottom.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottom, FrameLayout.LayoutParams(-1, dp(400), Gravity.BOTTOM))
        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R &&
            !PermissionHelper.isStorageGranted(this)) {
            PermissionHelper.requestStorage(this) { _, _ -> }
        }
        rows.adapter = rowAdapter
        rows.setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Movie.Video -> dispatchVodCardClick(item, home::handleAction)
                is TvCommand -> item.run()
            }
        }
        rows.setOnItemViewSelectedListener { _, item, rowHolder, _ ->
            (rowHolder as? ListRowPresenter.ViewHolder)?.gridView?.apply {
                setPadding(dp(32), dp(14), dp(32), dp(12))
                clipToPadding = false
                horizontalSpacing = dp(16)
            }
            if (item is Movie.Video) hero.bind(item)
        }
        supportFragmentManager.beginTransaction().replace(frame.id, rows).commitNow()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(home.rec, home.partitions, home.pageLoading, AppBootstrap.state) { _, _, _, _ -> Unit }
                        .collect {
                            if (section == 0) renderHome()
                            else if (section == 4) renderCategory()
                        }
                }
                launch { history.items.collect { if (section == 2) showRecords() } }
                launch { collects.items.collect { if (section == 3) showRecords() } }
                launch { combine(search.results, search.running) { _, _ -> Unit }
                    .collect { if (section == 5) renderSearch() } }
                launch { home.pageErrorEvents.collect { status.text = it } }
                launch { home.actionMessages.collect { Toast.makeText(this@TvHomeActivity, it, Toast.LENGTH_LONG).show() } }
                launch { config.toastEvent.collect { message ->
                    if (message != null) {
                        Toast.makeText(this@TvHomeActivity, message, Toast.LENGTH_LONG).show()
                        config.clearToast()
                    }
                } }
            }
        }
        AppBootstrap.start()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (section != 0) {
                    section = 0
                    rails.clear()
                    rowAdapter.clear()
                    renderHome()
                    navigation.getChildAt(1).requestFocus()
                } else if (!navigation.hasFocus()) navigation.getChildAt(1).requestFocus()
                else AlertDialog.Builder(this@TvHomeActivity).setMessage(getString(R.string.tv_ui_012))
                    .setPositiveButton(getString(R.string.tv_ui_013)) { _, _ -> finish() }.setNegativeButton(getString(R.string.tv_ui_014), null).show()
            }
        })
        navigation.getChildAt(1).requestFocus()
        intent.getStringExtra("tv_search")?.let(::startSearch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("tv_search")?.let(::startSearch)
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = getString(R.string.tv_ui_015); setSingleLine() }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.tv_ui_016)).setView(input)
            .setPositiveButton(getString(R.string.tv_ui_003), null).setNegativeButton(getString(R.string.tv_ui_014), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isEmpty()) input.error = getString(R.string.tv_ui_017)
                else { startSearch(query); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun startSearch(query: String) {
        section = 5
        rails.clear()
        rowAdapter.clear()
        search.search(query)
        renderSearch()
    }

    private fun renderSearch() {
        val results = search.results.value
        status.text = "${search.searchedTitle.value} · " +
            if (search.running.value) getString(R.string.tv_ui_018) else getString(R.string.tv_ui_019, results.sumOf { it.videos.size })
        results.filter { it.videos.isNotEmpty() }.forEach { result ->
            updateRail("search:${result.sourceKey}", result.sourceName, result.videos)
        }
    }

    private fun renderHome() {
        val boot = AppBootstrap.state.value
        status.text = when (boot) {
            is AppBootstrap.Boot.Loading -> getString(R.string.tv_ui_020)
            is AppBootstrap.Boot.Error -> getString(R.string.tv_ui_021, boot.msg)
            is AppBootstrap.Boot.Ready -> {
                val name = home.currentSource.value?.name.orEmpty()
                if (home.pageLoading.value) getString(R.string.tv_ui_022, name)
                else if (home.rec.value.videos.isEmpty() && home.partitions.value.none { it.videos.isNotEmpty() })
                    getString(R.string.tv_ui_023, name)
                else getString(R.string.tv_ui_024, name)
            }
        }
        if (boot is AppBootstrap.Boot.Error) {
            updateRail("recovery", getString(R.string.tv_ui_025), listOf(
                TvCommand(getString(R.string.tv_ui_026)) { AppBootstrap.retry() },
                TvCommand(getString(R.string.tv_ui_027)) { showSources() },
                TvCommand(getString(R.string.tv_ui_028)) { AppBootstrap.continueOffline() },
            ))
            return
        }
        removeRail("recovery")
        val sorts = home.sorts.value
        val recommended = home.rec.value.videos
        val keys = listOf("recommended:${recommended.isNotEmpty()}") + sorts.map { "${it.id}:${it.name}" }
        if (categoryKeys != keys) {
            categoryKeys = keys
            categoryNavigation.removeAllViews()
            if (recommended.isNotEmpty()) {
                categoryNavigation.addView(action(getString(R.string.tv_ui_030), ::showRecommended),
                    LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(14) })
            }
            sorts.forEach { sort ->
                categoryNavigation.addView(action(sort.name.orEmpty()) {
                    openCategory(sort.id.orEmpty())
                }, LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(14) })
            }
        }
        val firstPartition = home.partitions.value.firstOrNull { it.videos.isNotEmpty() }
        val videos = recommended.ifEmpty { firstPartition?.videos.orEmpty() }
        selectCategoryChip(if (recommended.isNotEmpty()) null else firstPartition?.sort?.id)
        if (videos.isNotEmpty()) updateRail("recommended", getString(R.string.tv_ui_030), videos)
        else removeRail("recommended")
        val first = videos.firstOrNull()
        if (!hero.hasVideo && first != null) hero.bind(first)
    }

    private fun updateRail(key: String, title: String, items: List<Any>) {
        val existing = rails[key]
        if (existing != null) {
            existing.second.setItems(items, TvItemDiff)
        } else {
            val adapter = ArrayObjectAdapter(TvCardPresenter(lifecycleScope, ::dp, ::showVideoActions))
            adapter.addAll(0, items)
            val row = ListRow(HeaderItem(title), adapter)
            rails[key] = row to adapter
            rowAdapter.add(row)
        }
    }

    private fun showVideoActions(video: Movie.Video) {
        lifecycleScope.launch {
            val collected = home.isVideoCollected(video)
            AlertDialog.Builder(this@TvHomeActivity).setTitle(video.name)
                .setItems(arrayOf(getString(R.string.tv_ui_032), if (collected) getString(R.string.tv_ui_033) else getString(R.string.tv_ui_034), getString(R.string.tv_artwork_info), getString(R.string.tv_artwork_manual))) { _, index ->
                    when (index) {
                        0 -> dispatchVodCardClick(video, home::handleAction)
                        1 -> home.toggleVideoCollect(video, !collected)
                        3 -> manualMatch(video)
                        2 -> {
                            val info = TvArtworkRepository.displayedPoster(video)
                            val message = if (info == null) getString(R.string.tv_artwork_no_info) else
                                getString(R.string.tv_artwork_info_value,
                                    if (info.provider == "VOD") getString(R.string.tv_artwork_vod) else info.provider,
                                    info.width, info.height)
                            AlertDialog.Builder(this@TvHomeActivity).setTitle(R.string.tv_artwork_info)
                                .setMessage(message + "\n" + artworkStatus())
                                .setNeutralButton(R.string.tv_settings_artwork) { _, _ -> TvTmdbDialog(this@TvHomeActivity, config).show() }
                                .setPositiveButton(android.R.string.ok, null).show()
                        }
                    }
                }.show()
        }
    }

    private fun manualMatch(video: Movie.Video) {
        val input = EditText(this).apply { setSingleLine(); setText(video.name) }
        AlertDialog.Builder(this).setTitle(R.string.tv_artwork_manual).setView(input)
            .setPositiveButton(R.string.tv_ui_003) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val hits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.github.avbox.osc.api.TmdbPoster.manualCandidates(input.text.toString())
                        }
                        if (isFinishing || isDestroyed) return@launch
                        if (hits.isEmpty()) {
                            AlertDialog.Builder(this@TvHomeActivity).setMessage(R.string.tv_artwork_match_empty)
                                .setPositiveButton(R.string.tv_settings_artwork) { _, _ -> TvTmdbDialog(this@TvHomeActivity, config).show() }.show()
                        } else AlertDialog.Builder(this@TvHomeActivity).setTitle(R.string.tv_artwork_manual)
                            .setItems(hits.map { "${it.title} · ${it.year} · ${it.mediaType} · #${it.id}" }.toTypedArray()) { _, index ->
                                com.github.avbox.osc.api.TmdbPoster.selectManualMatch(video.name.orEmpty(), hits[index])
                                hero.refresh(video)
                                showRecommended()
                            }.setNegativeButton(R.string.common_cancel, null).show()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        AlertDialog.Builder(this@TvHomeActivity).setMessage(R.string.tv_artwork_match_failed)
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                }
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun artworkStatus(): String {
        val tmdb = config.tmdbState().value
        return getString(when {
            !tmdb.enabled -> R.string.tv_artwork_tmdb_disabled
            tmdb.apiKey.isBlank() -> R.string.tv_artwork_key_missing
            else -> R.string.tv_artwork_tmdb_enabled
        })
    }

    private fun showRecommended() {
        section = 0
        selectedSort = null
        rails.clear()
        rowAdapter.clear()
        renderHome()
    }

    private fun selectCategoryChip(id: String?) {
        val hasRecommended = categoryKeys.firstOrNull() == "recommended:true"
        val index = if (id == null) {
            if (hasRecommended) 0 else -1
        } else {
            val sortIndex = home.sorts.value.indexOfFirst { it.id == id }
            if (sortIndex < 0) -1 else sortIndex + if (hasRecommended) 1 else 0
        }
        for (i in 0 until categoryNavigation.childCount) {
            categoryNavigation.getChildAt(i).isSelected = i == index
        }
    }

    private fun openCategory(id: String) {
        section = 4
        selectedSort = id
        selectCategoryChip(id)
        rails.clear()
        rowAdapter.clear()
        home.ensureLoaded(id)
        renderCategory()
        rows.view?.requestFocus()
    }

    private fun renderCategory() {
        val partition = home.partitions.value.firstOrNull { it.sort.id == selectedSort } ?: return
        status.text = getString(R.string.tv_ui_035, partition.sort.name.orEmpty(), partition.videos.size)
        val items: List<Any> = partition.videos + when (partition.state) {
            HomeViewModel.PartitionState.Error -> listOf(TvCommand(getString(R.string.tv_ui_036)) { home.retryPartition(partition) })
            HomeViewModel.PartitionState.Loading -> listOf(TvCommand(getString(R.string.tv_ui_037)) {})
            else -> if (partition.hasMore) listOf(TvCommand(getString(R.string.tv_ui_038)) { home.loadMorePartition(partition) }) else emptyList()
        }
        updateRail("category", partition.sort.name.orEmpty(), items)
    }

    private fun removeRail(key: String) {
        rails.remove(key)?.let { rowAdapter.remove(it.first) }
    }

    private fun showRecords() {
        rails.clear()
        rowAdapter.clear()
        val title = if (section == 2) getString(R.string.tv_ui_039) else getString(R.string.tv_ui_040)
        val videos = if (section == 2) history.items.value.map { item ->
            Movie.Video().apply { id = item.id; name = item.name; pic = item.pic; sourceKey = item.sourceKey }
        } else collects.items.value.map { item ->
            Movie.Video().apply { id = item.vodId; name = item.name; pic = item.pic; sourceKey = item.sourceKey }
        }
        status.text = if (videos.isEmpty()) getString(R.string.tv_ui_041, title) else getString(R.string.tv_ui_035, title, videos.size)
        updateRail("records", title, videos)
    }

    private fun showSites() {
        val sites = home.sources.value
        if (sites.isEmpty()) { showSources(); return }
        AlertDialog.Builder(this).setTitle(getString(R.string.tv_ui_042))
            .setSingleChoiceItems(sites.map { it.name.orEmpty() }.toTypedArray(),
                sites.indexOfFirst { it.key == home.currentSource.value?.key }) { dialog, index ->
                section = 0
                rails.clear()
                rowAdapter.clear()
                home.switchSource(sites[index])
                dialog.dismiss()
            }.setNegativeButton(getString(R.string.tv_ui_014), null).show()
    }

    private fun showSources(vod: Boolean = true) {
        val sources = (if (vod) config.vodItems.value else config.liveItems.value).map(::parseSubscribe)
        AlertDialog.Builder(this).setTitle(if (vod) R.string.tv_ui_043 else R.string.tv_settings_live)
            .setItems((listOf(getString(R.string.tv_ui_044), getString(R.string.tv_ui_045), getString(if (vod) R.string.tv_settings_live else R.string.tv_settings_sources), getString(R.string.tv_pair_title)) + sources.map {
                (if (it.url == (if (vod) config.activeUrl.value else config.liveActiveUrl.value)) "✓ " else "") + it.name
            }).toTypedArray()) { _, index ->
                when (index) {
                    0 -> showSourcePairing(vod, { showSources(vod) }, config::commitAdd)
                    1 -> AppBootstrap.retry()
                    2 -> showSources(!vod)
                    3 -> showSourcePairing(vod, { showSources(vod) }, config::commitAdd)
                    else -> sourceActions(sources[index - 4], vod)
                }
            }.setNegativeButton(getString(R.string.tv_ui_047), null).show()
    }

    private fun sourceActions(source: SubscribeSource, vod: Boolean) {
        AlertDialog.Builder(this).setTitle(source.name)
            .setItems(arrayOf(getString(R.string.tv_ui_048), getString(R.string.tv_ui_049), getString(R.string.tv_ui_050))) { _, index ->
                when (index) {
                    0 -> {
                        if (vod) {
                            section = 0
                            rails.clear()
                            rowAdapter.clear()
                        }
                        config.requestSwitch(source, vod)
                        if (config.pendingSwitch.value != null) {
                            AlertDialog.Builder(this).setMessage(getString(R.string.tv_ui_051))
                                .setPositiveButton(getString(R.string.tv_ui_052)) { _, _ -> config.enableAndSwitch() }
                                .setNegativeButton(getString(R.string.tv_ui_014)) { _, _ -> config.cancelPendingSwitch() }.show()
                        }
                    }
                    1 -> editSource(source, vod)
                    2 -> AlertDialog.Builder(this).setMessage(getString(R.string.tv_ui_053, source.name))
                        .setPositiveButton(getString(R.string.tv_ui_054)) { _, _ ->
                            config.exitManageMode()
                            config.toggleSelected(source.name + SUBSCRIBE_SPLIT + source.url)
                            config.deleteSelected(if (vod) SubscribeMode.Vod else SubscribeMode.Live)
                            showSources(vod)
                        }.setNegativeButton(getString(R.string.tv_ui_014), null).show()
                }
            }.show()
    }

    private fun editSource(source: SubscribeSource?, vod: Boolean) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val name = EditText(this).apply { hint = getString(R.string.tv_ui_055); setText(source?.name.orEmpty()); setSingleLine() }
        val url = EditText(this).apply {
            hint = getString(R.string.tv_ui_056)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(source?.url.orEmpty())
            setSingleLine()
        }
        fields.addView(name)
        fields.addView(url)
        val dialog = AlertDialog.Builder(this).setTitle(if (source == null) getString(R.string.tv_ui_057) else getString(R.string.tv_ui_058))
            .setView(fields).setPositiveButton(getString(R.string.tv_ui_059), null).setNegativeButton(getString(R.string.tv_ui_014), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = sanitizeConfigText(url.text.toString())
                if (address.isEmpty()) { url.error = getString(R.string.tv_ui_060); return@setOnClickListener }
                val title = sanitizeConfigText(name.text.toString()).ifEmpty { address }
                if (source == null) config.commitAdd(vod, title, address)
                else config.commitEdit(vod, source, title, address)
                dialog.dismiss()
                showSources(vod)
            }
        }
        dialog.show()
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * tvLayoutScale(this@TvHomeActivity))
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun action(value: String, click: () -> Unit) = label(value, 16f).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        isFocusable = true
        isClickable = true
        background = android.graphics.drawable.StateListDrawable().apply {
            fun pill(focused: Boolean, selected: Boolean = false) = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.TRANSPARENT)
                if (focused || selected) setStroke(dp(2), if (focused) 0xFFE50914.toInt() else 0x66E50914)
            }
            addState(intArrayOf(android.R.attr.state_focused), pill(true))
            addState(intArrayOf(android.R.attr.state_selected), pill(false, true))
            addState(intArrayOf(), pill(false))
        }
        setOnClickListener { click() }
    }

    private fun navAction(value: String, click: () -> Unit): View {
        val root = FrameLayout(this).apply { isFocusable = true; isClickable = true }
        val text = label(value, 20f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            alpha = .72f
        }
        val indicator = View(this).apply { setBackgroundColor(Color.WHITE); visibility = View.INVISIBLE }
        root.addView(text, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))
        root.addView(indicator, FrameLayout.LayoutParams(dp(24), dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(8)
        })
        root.setOnFocusChangeListener { _, focused ->
            text.alpha = if (focused) 1f else .72f
            text.setTypeface(null, if (focused) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            indicator.visibility = if (focused) View.VISIBLE else View.INVISIBLE
        }
        root.setOnClickListener { click() }
        return root
    }

    internal fun dp(value: Int): Int = (value * tvLayoutScale(this)).toInt()
}

