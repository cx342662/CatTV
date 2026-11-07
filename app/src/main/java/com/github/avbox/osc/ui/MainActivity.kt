package com.github.avbox.osc.ui

import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Build
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.github.avbox.osc.R
import com.github.avbox.osc.BuildConfig
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.player.PlaybackService
import com.github.avbox.osc.ui.page.MainScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.AppThemeState
import com.github.avbox.osc.util.PermissionHelper

private const val PREWARM_ON_RESUME_DELAY_MS = 2000L

class MainActivity : BaseActivity() {

    // BaseActivity normally forces portrait on narrow Android windows. That policy
    // overrides the manifest placeholder, so the dedicated TV build must opt out.
    override fun orientationPolicyValue(): Int {
        return if (BuildConfig.TV_MODE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            super.orientationPolicyValue()
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    private fun applyStatusBarAppearance() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val light = !AppThemeState.isDark(night)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarAppearance()
        PlaybackService.prewarm(this, PREWARM_ON_RESUME_DELAY_MS)
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        applyStatusBarAppearance()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !PermissionHelper.isStorageGranted(this)) {
            PermissionHelper.requestStorage(this) { _, _ -> }
        }
        PermissionHelper.requestNotificationIfNeeded(this)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                MainScreen()
            }
        }
    }
}
