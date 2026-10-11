package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import coil3.request.ErrorResult
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.asDrawable
import coil3.size.Scale
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.components.WatchItemIconModel
import io.baiyanwu.coinmonitor.ui.components.coinIconFailures
import io.baiyanwu.coinmonitor.ui.components.coinIconInitial
import io.baiyanwu.coinmonitor.ui.components.coinIconRequest
import io.baiyanwu.coinmonitor.ui.components.localChainIconResource
import io.baiyanwu.coinmonitor.ui.components.watchItemIconModel
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 原生悬浮窗的轻量宿主；图片获取、解码、内存/磁盘缓存全部交给 Coil 单例。 */
internal class OverlayCoinIconView(context: Context, private val scope: CoroutineScope) : FrameLayout(context) {
    private val initial = initialView()
    private val token = imageView(ImageView.ScaleType.CENTER_CROP)
    private val badge = FrameLayout(context)
    private val badgeInitial = initialView()
    private val badgeImage = imageView(ImageView.ScaleType.FIT_CENTER)
    private var binding: Binding? = null
    private var loadJob: Job? = null

    init {
        addView(initial, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(token, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        badge.addView(badgeInitial, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        badge.addView(badgeImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(badge)
    }

    fun bind(item: WatchItem, sizePx: Int, colors: CoinMonitorColors) {
        val next = Binding(watchItemIconModel(item), sizePx.coerceAtLeast(1),
            colors.heroBackground.toArgb(), colors.accent.toArgb(), colors.cardBackground.toArgb())
        if (binding == next) return
        loadJob?.cancel()
        binding = next
        initial.text = coinIconInitial(next.model.symbol)
        initial.setTextColor(next.accent)
        initial.setTextSize(TypedValue.COMPLEX_UNIT_PX, next.sizePx * 0.5f)
        initial.background = circle(next.background)
        initial.visibility = VISIBLE
        token.setImageDrawable(null)
        badge.visibility = if (next.model.chainIndex == null) GONE else VISIBLE
        val badgeSize = (next.sizePx * 0.5f).roundToInt().coerceAtLeast(1)
        badge.layoutParams = LayoutParams(badgeSize, badgeSize, Gravity.BOTTOM or Gravity.END)
        badge.background = circle(next.surface)
        val border = resources.displayMetrics.density.roundToInt().coerceAtMost(badgeSize / 4)
        badge.setPadding(border, border, border, border)
        badge.clipToOutline = true
        badge.outlineProvider = ViewOutlineProvider.BACKGROUND
        val name = next.model.chainIndex?.let { OnchainChainRegistry.resolve(it)?.displayName ?: it }.orEmpty()
        badge.contentDescription = name
        badgeInitial.text = coinIconInitial(name)
        badgeInitial.setTextColor(next.accent)
        badgeInitial.setTextSize(TypedValue.COMPLEX_UNIT_PX, badgeSize * 0.5f)
        badgeInitial.visibility = VISIBLE
        badgeImage.setImageDrawable(null)
        if (isAttachedToWindow) startLoading(next)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding?.let(::startLoading)
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        loadJob = null
        super.onDetachedFromWindow()
    }

    private fun startLoading(expected: Binding) {
        loadJob?.cancel()
        loadJob = scope.launch {
            coroutineScope {
                launch {
                    var image = loadUrls(listOfNotNull(expected.model.tokenIconUrl), expected.sizePx, Scale.FILL)
                    if (image == null && expected.model.allowSymbolLookup) {
                        val url = CoinIconService.get(context).resolveIconUrl(expected.model.symbol)
                        image = loadUrls(listOfNotNull(url), expected.sizePx, Scale.FILL)
                    }
                    ensureActive()
                    if (binding == expected && isAttachedToWindow && image != null) {
                        token.setImageDrawable(image)
                        initial.visibility = INVISIBLE
                    }
                }
                expected.model.chainIndex?.let { index -> launch {
                    val size = (expected.sizePx * 0.5f).roundToInt().coerceAtLeast(1)
                    val local = localChainIconResource(index)
                    val image = if (local != null) loadImage(local, size, Scale.FIT)
                        else loadUrls(OnchainChainIconRegistry.resolveIconUrls(index), size, Scale.FIT)
                    ensureActive()
                    if (binding == expected && isAttachedToWindow && image != null) {
                        badgeImage.setImageDrawable(image)
                        badgeInitial.visibility = INVISIBLE
                    }
                } }
            }
        }
    }

    private suspend fun loadUrls(urls: List<String>, size: Int, scale: Scale): Drawable? {
        for (url in urls.distinct()) loadImage(url, size, scale)?.let { return it }
        return null
    }

    private suspend fun loadImage(data: Any, size: Int, scale: Scale): Drawable? {
        if (data is String && !coinIconFailures.canRequest(data)) return null
        return when (val result = SingletonImageLoader.get(context).execute(coinIconRequest(context, data, size, scale))) {
            is SuccessResult -> {
                if (data is String) coinIconFailures.clear(data)
                result.image.asDrawable(resources)
            }
            is ErrorResult -> {
                if (result.throwable is CancellationException) throw result.throwable
                if (data is String) coinIconFailures.recordFailure(data, result.throwable)
                null
            }
        }
    }

    private fun initialView() = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun imageView(scale: ImageView.ScaleType) = ImageView(context).apply {
        scaleType = scale
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setOval(0, 0, view.width, view.height) }
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private data class Binding(val model: WatchItemIconModel, val sizePx: Int, val background: Int, val accent: Int, val surface: Int)
}
