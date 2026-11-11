package com.github.avbox.osc.ui.tv

import com.github.avbox.osc.R

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.github.avbox.osc.bean.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TvHeroView(
    context: Context,
    private val scope: CoroutineScope,
    private val loadPoster: suspend (Movie.Video) -> android.graphics.Bitmap? = TvArtworkRepository::backgroundPoster,
    private val loadHero: suspend (Movie.Video) -> TvArtworkRepository.Hero = TvArtworkRepository::hero,
) : FrameLayout(context) {
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val nextImage = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val title = text(58f).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
    private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_START; visibility = GONE }
    private val metadata = text(18f)
    private val overview = text(16f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
    private var current: String? = null
    private var request: Job? = null
    val hasVideo: Boolean get() = current != null

    init {
        addView(image, LayoutParams(-1, -1))
        addView(nextImage, LayoutParams(-1, -1))
        addView(android.view.View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.BLACK, Color.TRANSPARENT, Color.TRANSPARENT))
        }, LayoutParams(-1, -1))
        addView(android.view.View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.BLACK, Color.TRANSPARENT, Color.TRANSPARENT))
        }, LayoutParams(-1, -1))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(32), 0, dp(24), dp(22))
            addView(title)
            addView(logo, LinearLayout.LayoutParams(-1, dp(125)))
            addView(metadata)
            addView(overview)
        }
        addView(column, LayoutParams((resources.displayMetrics.widthPixels * .56f).toInt(), -2,
            Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(410) })
        title.text = "CatTV"
        overview.text = context.getString(R.string.tv_ui_001)
    }

    fun refresh(video: Movie.Video) { current = null; bind(video) }

    fun bind(video: Movie.Video) {
        val key = "${video.sourceKey}:${video.id}"
        if (key == current) return
        current = key
        request?.cancel()
        title.text = video.name.orEmpty()
        title.visibility = VISIBLE
        logo.visibility = GONE
        logo.setImageDrawable(null)
        metadata.text = heroMetadata(video)
        overview.text = android.text.Html.fromHtml(video.des.orEmpty(), android.text.Html.FROM_HTML_MODE_LEGACY)
        request = scope.launch {
            delay(250)
            val result = loadHero(video)
            val backdrop = result.image ?: loadPoster(video)
            if (current == key) {
                if (backdrop != null) {
                    showBackdrop(backdrop)
                }
                overview.text = android.text.Html.fromHtml(result.overview, android.text.Html.FROM_HTML_MODE_LEGACY)
                metadata.text = heroMetadata(video, result.rating, result.genres)
                result.logo?.let {
                    logo.setImageBitmap(it)
                    logo.visibility = VISIBLE
                    title.visibility = GONE
                }
            }
        }
    }

    private fun heroMetadata(video: Movie.Video, tmdb: Double = 0.0, genres: List<String> = emptyList()): CharSequence {
        val note = video.note?.trim()?.takeUnless {
            Regex("""^(?:评分\s*[:：]?\s*)0(?:\.0+)?$""").matches(it)
        }
        val parts = listOfNotNull(
            video.year.takeIf { it > 0 }?.toString(),
            note,
            genres.filter { it.isNotBlank() }.take(2).joinToString(" / ").ifBlank { video.type.orEmpty() },
        ).filter { it.isNotBlank() }.distinct()
        val douban = video.doubanScore?.takeIf { it > 0 && it <= 10 }
        val rating = douban ?: tmdb.takeIf { it > 0 && it <= 10 } ?: return parts.joinToString("  ·  ")
        val badge = if (douban != null) context.getString(R.string.tv_douban_badge) else "TMDB"
        return android.text.SpannableStringBuilder(parts.joinToString("  ·  ")).apply {
            if (isNotEmpty()) append("  ·  ")
            val start = length
            append(badge)
            setSpan(android.text.style.ForegroundColorSpan(if (douban != null) 0xFFFFAFAF.toInt() else 0xFF7ADCC6.toInt()),
                start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" ").append(String.format(java.util.Locale.ROOT, "%.1f", rating))
        }
    }

    private fun showBackdrop(bitmap: android.graphics.Bitmap) {
        nextImage.animate().withEndAction(null).cancel()
        if (nextImage.alpha > 0f) image.setImageDrawable(nextImage.drawable)
        nextImage.setImageBitmap(bitmap)
        nextImage.alpha = 0f
        nextImage.animate().alpha(1f).setDuration(320).withEndAction {
            image.setImageBitmap(bitmap)
            nextImage.alpha = 0f
        }.start()
    }

    private fun text(size: Float) = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * tvLayoutScale(context))
        setPadding(0, dp(3), 0, dp(3))
        setShadowLayer(dp(6).toFloat(), 1f, 1f, Color.BLACK)
    }

    private fun dp(value: Int) = (value * tvLayoutScale(context)).toInt()
}

internal fun tvLayoutScale(context: Context): Float = context.resources.displayMetrics.let {
    minOf(it.widthPixels / 1280f, it.heightPixels / 720f)
}
