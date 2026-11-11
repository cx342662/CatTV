package com.github.avbox.osc.ui.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.Presenter
import com.github.avbox.osc.bean.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class TvCommand(val title: String, val run: () -> Unit)

internal object TvItemDiff : DiffCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean = when {
        oldItem is Movie.Video && newItem is Movie.Video ->
            oldItem.id == newItem.id && oldItem.sourceKey == newItem.sourceKey
        oldItem is TvCommand && newItem is TvCommand -> oldItem.title == newItem.title
        else -> false
    }
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean = oldItem === newItem
}

internal fun tvFocusBackground(radius: Int, stroke: Int = Color.WHITE) = StateListDrawable().apply {
    fun shape(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(Color.TRANSPARENT)
        setStroke(if (focused) 3 else 0, stroke)
    }
    addState(intArrayOf(android.R.attr.state_focused), shape(true))
    addState(intArrayOf(), shape(false))
}

internal class TvCardPresenter(
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val onLongClick: (Movie.Video) -> Unit,
) : Presenter() {
    private class Card(val root: LinearLayout, val image: ImageView, val title: TextView) : ViewHolder(root) {
        var job: Job? = null
    }
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = ViewGroup.LayoutParams(dp(196), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val image = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.TRANSPARENT) }
            clipToOutline = true
            isDuplicateParentStateEnabled = true
            foreground = tvFocusBackground(dp(10), 0xFFE50914.toInt())
        }
        val title = TextView(parent.context).apply {
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(18).toFloat())
            gravity = Gravity.CENTER
            maxLines = 1
            minLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(7), dp(4), dp(4))
        }
        root.addView(image, LinearLayout.LayoutParams(-1, dp(294)))
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        return Card(root, image, title)
    }
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder as Card
        card.job?.cancel()
        card.image.setImageDrawable(null)
        card.image.visibility = if (item is Movie.Video) android.view.View.VISIBLE else android.view.View.GONE
        card.title.text = when (item) {
            is Movie.Video -> item.name.orEmpty()
            is TvCommand -> item.title
            else -> ""
        }
        if (item is Movie.Video) card.job = scope.launch {
            TvArtworkRepository.poster(item)?.let(card.image::setImageBitmap)
        }
        card.root.setOnLongClickListener {
            if (item is Movie.Video) { onLongClick(item); true } else false
        }
    }
    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder as Card
        card.job?.cancel()
        card.image.setImageDrawable(null)
        card.root.setOnLongClickListener(null)
    }
}
