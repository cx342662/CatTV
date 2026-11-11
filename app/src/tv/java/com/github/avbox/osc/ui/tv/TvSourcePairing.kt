package com.github.avbox.osc.ui.tv

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.github.avbox.osc.R
import com.github.avbox.osc.server.ControlManager
import com.github.avbox.osc.server.SourcePairing
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun TvHomeActivity.showSourcePairing(defaultVod: Boolean = true, choose: (() -> Unit)? = null, save: (Boolean, String, String) -> Unit) {
    val address = runCatching { ControlManager.get().getAddress(false) }.getOrDefault("")
    val lanReady = address.startsWith("http://") && !address.contains("127.0.0.1") && !address.contains("0.0.0.0")
    val pairing = if (lanReady) SourcePairing.open() else null
    val image = ImageView(this).apply {
        if (pairing != null) {
            val url = address.trimEnd('/') + SourcePairing.PREFIX + pairing.token
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 400, 400)
            val pixels = IntArray(400 * 400) { if (matrix[it % 400, it / 400]) Color.BLACK else Color.WHITE }
            setImageBitmap(Bitmap.createBitmap(pixels, 400, 400, Bitmap.Config.ARGB_8888))
        } else visibility = android.view.View.GONE
    }
    var vod = defaultVod
    val name = android.widget.EditText(this).apply { setHint(R.string.tv_ui_055); setSingleLine() }
    val link = android.widget.EditText(this).apply {
        setHint(R.string.tv_ui_056)
        setSingleLine()
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
    }
    val kind = TextView(this).apply {
        setText(if (vod) R.string.tv_settings_sources else R.string.tv_settings_live)
    }
    val fields = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), 0, 0, 0)
        addView(TextView(context).apply { setText(if (lanReady) R.string.tv_pair_help else R.string.tv_pair_unavailable) })
        addView(kind)
        addView(name)
        addView(link)
    }
    val panel = LinearLayout(this).apply {
        setPadding(dp(24), dp(12), dp(24), dp(12))
        addView(image, LinearLayout.LayoutParams(dp(230), dp(230)))
        addView(fields, LinearLayout.LayoutParams(0, -2, 1f))
    }
    val builder = AlertDialog.Builder(this).setTitle(R.string.tv_pair_title).setView(panel)
        .setPositiveButton(R.string.tv_ui_059, null).setNegativeButton(R.string.tv_ui_014, null)
    if (choose != null) builder.setNeutralButton(R.string.tv_ui_043) { _, _ -> choose() }
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialog.show()
    dialog.window?.setLayout(dp(820), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val address = link.text.toString().trim().filterNot { it.isISOControl() }
        if (address.isBlank()) {
            link.setError(getString(R.string.tv_ui_060))
        } else {
            val title = name.text.toString().trim().filterNot { it.isISOControl() }.ifEmpty { address }
            save(vod, title, address)
            dialog.dismiss()
        }
    }
    val job = lifecycleScope.launch {
        if (pairing == null) return@launch
        val source = withTimeoutOrNull(300_000) { pairing.incoming.receive() }
        SourcePairing.close(pairing)
        image.alpha = .35f
        if (source != null) {
            vod = source.vod
            kind.setText(if (vod) R.string.tv_settings_sources else R.string.tv_settings_live)
            name.setText(source.name)
            link.setText(source.url)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        }
    }
    dialog.setOnDismissListener { pairing?.let(SourcePairing::close); job.cancel() }
}
