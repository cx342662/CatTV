package com.github.avbox.osc.ui

import androidx.activity.compose.setContent
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class SearchActivity : BaseActivity() {

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        if (com.github.avbox.osc.BuildConfig.TV_MODE) {
            startActivity(android.content.Intent().setClassName(this, "com.github.avbox.osc.ui.tv.TvHomeActivity")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("tv_search", intent.getStringExtra("title").orEmpty()))
            finish()
            return
        }
        enableTransparentEdgeToEdge()
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    SearchScreen()
                }
            }
        }
    }
}
