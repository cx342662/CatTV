package com.github.avbox.osc.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.github.avbox.osc.util.AppLog

internal fun Activity.showHomeBehindPictureInPicture() {
    try {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (error: ActivityNotFoundException) {
        AppLog.e("PictureInPicture", error)
    }
}
