package io.baiyanwu.coinmonitor.overlay.marquee

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.overlay.CoinIconService
import io.baiyanwu.coinmonitor.overlay.MarqueeOverlayWindowHost
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.resolveCoinMonitorColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class MarqueeOverlayWindowController(
    private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val scope: CoroutineScope
) : MarqueeOverlayWindowHost {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coinIconService = CoinIconService.get(context)

    private var rootView: FrameLayout? = null
    private var trackView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var structureSignature: String? = null
    private var lastSettingsWindowY: Int? = null
    private var isDragging: Boolean = false
    private val holdersById = LinkedHashMap<String, MutableList<MarqueeItemHolder>>()
    private val priceWidthsById = LinkedHashMap<String, Int>()
    private val iconBitmapCache = LinkedHashMap<String, Bitmap>()
    private val pendingIconLoads = mutableSetOf<String>()
    private val completedIconLoads = mutableSetOf<String>()

    private val overlayColors
        get() = resolveCoinMonitorColors(context, appPreferencesRepository.getPreferences())

    private val localizedContext: Context
        get() = AppConfigurationApplier.wrapContext(
            context,
            appPreferencesRepository.getPreferences()
        )

    override fun showOrUpdate(
        items: List<WatchItem>,
        locked: Boolean,
        settings: MarqueeOverlaySettings
    ) {
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            hide()
            return
        }

        val metrics = MarqueeMetrics.from(context, settings)
        ensureWindow(settings, locked, metrics)
        val root = rootView ?: return
        val params = layoutParams ?: return
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = metrics.heightPx
        params.x = MarqueeWindowPositionPolicy.resolveX()
        if (!isDragging && settings.windowY != lastSettingsWindowY) {
            params.y = settings.windowY ?: DEFAULT_WINDOW_Y_DP.dp
            lastSettingsWindowY = settings.windowY
        }
        params.y = MarqueeWindowPositionPolicy.resolveY(
            requestedY = params.y,
            overlayHeightPx = metrics.heightPx,
            screenHeightPx = screenHeight
        )
        val flags = resolveWindowFlags(locked)
        if (params.flags != flags) params.flags = flags

        applyRootStyle(root, settings.opacity)
        val visibleItems = items.take(settings.maxItems)
        if (visibleItems.isEmpty()) {
            renderEmptyState(root, metrics)
        } else {
            renderMarquee(
                root = root,
                items = visibleItems,
                settings = settings,
                metrics = metrics,
                screenWidth = screenWidth
            )
        }
        // Keep the active gesture listener stable while quote updates arrive.
        if (!isDragging) {
            applyTouchHandler(root, locked, metrics, screenHeight)
        }

        if (root.parent == null) {
            windowManager.addView(root, params)
        } else {
            windowManager.updateViewLayout(root, params)
        }
    }

    override fun hide() {
        cancelAnimator()
        rootView?.let { view ->
            if (view.parent != null) windowManager.removeViewImmediate(view)
        }
        holdersById.clear()
        priceWidthsById.clear()
        iconBitmapCache.clear()
        pendingIconLoads.clear()
        completedIconLoads.clear()
        rootView = null
        trackView = null
        layoutParams = null
        structureSignature = null
        lastSettingsWindowY = null
        isDragging = false
    }

    private fun ensureWindow(
        settings: MarqueeOverlaySettings,
        locked: Boolean,
        metrics: MarqueeMetrics
    ) {
        if (rootView != null && layoutParams != null) return
        rootView = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
        }
        lastSettingsWindowY = settings.windowY
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            metrics.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            resolveWindowFlags(locked),
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = MarqueeWindowPositionPolicy.resolveX()
            y = settings.windowY ?: DEFAULT_WINDOW_Y_DP.dp
        }
    }

    private fun resolveWindowFlags(locked: Boolean): Int {
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        return if (locked) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
    }

    private fun applyRootStyle(root: FrameLayout, opacity: Float) {
        root.background = GradientDrawable().apply {
            setColor(
                overlayColors.overlayBackground.copy(
                    alpha = opacity.coerceIn(
                        MarqueeOverlaySettings.MIN_OPACITY,
                        MarqueeOverlaySettings.MAX_OPACITY
                    )
                ).toArgb()
            )
        }
    }

    private fun renderEmptyState(root: FrameLayout, metrics: MarqueeMetrics) {
        val emptySignature = "empty|${metrics.fontScale}|${metrics.heightPx}"
        if (structureSignature != emptySignature) {
            cancelAnimator()
            holdersById.clear()
            priceWidthsById.clear()
            trackView = null
            root.removeAllViews()
            root.addView(
                TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    text = localizedContext.getString(R.string.overlay_empty_state)
                    setTextColor(overlayColors.overlayText.copy(alpha = 0.92f).toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.textSizeSp)
                }
            )
            structureSignature = emptySignature
        }
    }

    private fun renderMarquee(
        root: FrameLayout,
        items: List<WatchItem>,
        settings: MarqueeOverlaySettings,
        metrics: MarqueeMetrics,
        screenWidth: Int
    ) {
        val signature = MarqueeTrackStructure.signature(
            itemIds = items.map(WatchItem::id),
            fontScale = metrics.fontScale,
            itemWidthPx = metrics.itemWidthPx,
            cycleSeparatorWidthPx = metrics.cycleSeparatorWidthPx,
            viewportWidthPx = screenWidth,
            speed = settings.speed
        )
        val resolvedPriceWidths = resolvePriceWidths(items, metrics)
        if (structureSignature != signature || priceWidthsById != resolvedPriceWidths) {
            priceWidthsById.clear()
            priceWidthsById.putAll(resolvedPriceWidths)
            rebuildTrack(root, items, settings, metrics, screenWidth, signature)
        } else {
            bindVisibleItems(items, metrics)
        }
    }

    private fun rebuildTrack(
        root: FrameLayout,
        items: List<WatchItem>,
        settings: MarqueeOverlaySettings,
        metrics: MarqueeMetrics,
        screenWidth: Int,
        signature: String
    ) {
        cancelAnimator()
        holdersById.clear()
        root.removeAllViews()
        val plan = MarqueeTrackPlanner.plan(
            itemWidthsPx = items.map { item ->
                metrics.itemWidthPx(priceWidthsById.getValue(item.id))
            },
            viewportWidthPx = screenWidth,
            density = context.resources.displayMetrics.density,
            speed = settings.speed,
            cycleSeparatorWidthPx = metrics.cycleSeparatorWidthPx
        ) ?: return
        val track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                plan.cycleWidthPx * plan.repetitionCount,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        repeat(plan.repetitionCount) {
            items.forEach { item ->
                val holder = createItemHolder(
                    metrics = metrics,
                    priceWidthPx = priceWidthsById.getValue(item.id)
                )
                holdersById.getOrPut(item.id) { mutableListOf() }.add(holder)
                track.addView(holder.root)
            }
            track.addView(createCycleSeparator(metrics))
        }
        bindVisibleItems(items, metrics)
        root.addView(track)
        trackView = track
        structureSignature = signature
        startAnimator(track, plan, signature)
    }

    private fun resolvePriceWidths(
        items: List<WatchItem>,
        metrics: MarqueeMetrics
    ): LinkedHashMap<String, Int> {
        val measurementView = marqueeTextView(metrics, Gravity.START).apply {
            setTypeface(typeface, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        return items.associateTo(LinkedHashMap()) { item ->
            val measuredWidthPx = ceil(
                measurementView.paint.measureText(QuoteFormatter.formatOverlayPrice(item)).toDouble()
            ).toInt()
            item.id to measuredWidthPx.coerceIn(1, metrics.priceWidthPx)
        }
    }

    private fun createItemHolder(
        metrics: MarqueeMetrics,
        priceWidthPx: Int
    ): MarqueeItemHolder {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                metrics.itemWidthPx(priceWidthPx),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                metrics.iconSlotWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(metrics.iconSizePx, metrics.iconSizePx).apply {
                gravity = Gravity.CENTER
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setImageDrawable(buildDefaultIconDrawable(metrics))
        }
        val price = marqueeTextView(metrics, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(
                priceWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTypeface(typeface, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        iconContainer.addView(icon)
        root.addView(iconContainer)
        root.addView(price)
        return MarqueeItemHolder(root, icon, price)
    }

    private fun createCycleSeparator(metrics: MarqueeMetrics): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                metrics.cycleSeparatorWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        metrics.cycleSeparatorLineWidthPx,
                        metrics.cycleSeparatorLineHeightPx
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundColor(
                        overlayColors.overlayMutedText.copy(alpha = 0.55f).toArgb()
                    )
                }
            )
        }
    }

    private fun marqueeTextView(metrics: MarqueeMetrics, horizontalGravity: Int): TextView {
        return TextView(context).apply {
            gravity = horizontalGravity or Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.textSizeSp)
        }
    }

    private fun bindVisibleItems(items: List<WatchItem>, metrics: MarqueeMetrics) {
        items.forEach { item -> bindItem(item, metrics) }
    }

    private fun bindItem(
        item: WatchItem,
        metrics: MarqueeMetrics
    ) {
        val holders = holdersById[item.id].orEmpty()
        holders.forEach { holder ->
            holder.price.text = QuoteFormatter.formatOverlayPrice(item)
            holder.price.setTextColor(
                item.resolveLivePriceColor(overlayColors, overlayColors.overlayText).toArgb()
            )
            holder.price.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.textSizeSp)
        }
        bindItemIcons(holders, item, metrics)
    }

    private fun bindItemIcons(
        holders: List<MarqueeItemHolder>,
        item: WatchItem,
        metrics: MarqueeMetrics
    ) {
        if (holders.isEmpty()) return
        val iconSignature = listOf(
            item.id,
            item.iconUrl,
            item.chainIndex,
            item.baseSymbol,
            metrics.iconSizePx,
            overlayColors.overlayFallbackBorder.toArgb()
        ).joinToString("|")
        holders.forEach { holder ->
            if (holder.iconSignature != iconSignature) {
                holder.iconSignature = iconSignature
                holder.icon.setImageDrawable(buildDefaultIconDrawable(metrics))
            }
        }
        iconBitmapCache[iconSignature]?.let { bitmap ->
            holders.forEach { holder -> holder.icon.setImageBitmap(bitmap) }
            return
        }
        if (iconSignature in completedIconLoads || !pendingIconLoads.add(iconSignature)) return

        scope.launch {
            val bitmap = coinIconService.loadBitmap(
                symbol = item.baseSymbol,
                preferredIconUrl = item.iconUrl,
                fallbackIconUrls = OnchainChainIconRegistry.resolveIconUrls(item.chainIndex),
                grayscaleFallback = item.chainIndex != null
            )
            pendingIconLoads.remove(iconSignature)
            completedIconLoads.add(iconSignature)
            if (bitmap != null) iconBitmapCache[iconSignature] = bitmap
            holdersById[item.id].orEmpty().forEach { holder ->
                if (holder.iconSignature == iconSignature) {
                    if (bitmap != null) {
                        holder.icon.setImageBitmap(bitmap)
                    } else {
                        holder.icon.setImageDrawable(buildDefaultIconDrawable(metrics))
                    }
                }
            }
        }
    }

    private fun buildDefaultIconDrawable(metrics: MarqueeMetrics): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(overlayColors.overlayFallbackBackground.toArgb())
            setStroke(metrics.fallbackStrokePx, overlayColors.overlayFallbackBorder.toArgb())
        }
    }

    private fun startAnimator(
        track: LinearLayout,
        plan: MarqueeTrackPlan,
        expectedSignature: String
    ) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            track.translationX = 0f
            return
        }
        track.post {
            if (trackView !== track || structureSignature != expectedSignature) return@post
            animator = ValueAnimator.ofFloat(0f, -plan.cycleWidthPx.toFloat()).apply {
                duration = plan.durationMillis
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { animation ->
                    track.translationX = animation.animatedValue as Float
                }
                start()
                if (isDragging) pause()
            }
        }
    }

    private fun applyTouchHandler(
        root: View,
        locked: Boolean,
        metrics: MarqueeMetrics,
        screenHeight: Int
    ) {
        if (locked) {
            root.setOnTouchListener(null)
            return
        }
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        root.setOnTouchListener(object : View.OnTouchListener {
            private var downRawY: Float = 0f
            private var startWindowY: Int = 0
            private var hasDragged: Boolean = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        hasDragged = false
                        downRawY = event.rawY
                        startWindowY = params.y
                        animator?.pause()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - downRawY
                        if (abs(deltaY) > touchSlop) hasDragged = true
                        if (hasDragged) {
                            params.x = MarqueeWindowPositionPolicy.resolveX()
                            params.y = MarqueeWindowPositionPolicy.resolveY(
                                requestedY = startWindowY + deltaY.roundToInt(),
                                overlayHeightPx = metrics.heightPx,
                                screenHeightPx = screenHeight
                            )
                            if (view.parent != null) windowManager.updateViewLayout(view, params)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        if (hasDragged) {
                            val resolvedY = params.y
                            lastSettingsWindowY = resolvedY
                            scope.launch { overlayRepository.setMarqueeWindowY(resolvedY) }
                        }
                        animator?.resume()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun cancelAnimator() {
        animator?.cancel()
        animator = null
        trackView?.translationX = 0f
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).roundToInt()

    private data class MarqueeItemHolder(
        val root: LinearLayout,
        val icon: ImageView,
        val price: TextView,
        var iconSignature: String? = null
    )

    private data class MarqueeMetrics(
        val fontScale: Float,
        val heightPx: Int,
        val iconSizePx: Int,
        val iconSlotWidthPx: Int,
        val priceWidthPx: Int,
        val itemSpacingPx: Int,
        val cycleSeparatorWidthPx: Int,
        val cycleSeparatorLineWidthPx: Int,
        val cycleSeparatorLineHeightPx: Int,
        val fallbackStrokePx: Int,
        val textSizeSp: Float
    ) {
        fun itemWidthPx(resolvedPriceWidthPx: Int): Int {
            return iconSlotWidthPx + resolvedPriceWidthPx + itemSpacingPx
        }

        val itemWidthPx: Int = itemWidthPx(priceWidthPx)

        companion object {
            fun from(context: Context, settings: MarqueeOverlaySettings): MarqueeMetrics {
                val density = context.resources.displayMetrics.density
                val fontScale = settings.fontScale.coerceIn(
                    MarqueeOverlaySettings.MIN_FONT_SCALE,
                    MarqueeOverlaySettings.MAX_FONT_SCALE
                )
                fun scaledDp(value: Int): Int = (value * density * fontScale).roundToInt()
                val itemSpacingPx = scaledDp(4)
                val iconSizePx = scaledDp(16)
                return MarqueeMetrics(
                    fontScale = fontScale,
                    heightPx = scaledDp(32),
                    iconSizePx = iconSizePx,
                    iconSlotWidthPx = MarqueeSpacingPolicy.iconSlotWidthPx(
                        iconSizePx = iconSizePx,
                        itemSpacingPx = itemSpacingPx
                    ),
                    priceWidthPx = scaledDp(58),
                    itemSpacingPx = itemSpacingPx,
                    cycleSeparatorWidthPx = MarqueeSpacingPolicy.cycleSeparatorWidthPx(
                        itemSpacingPx
                    ),
                    cycleSeparatorLineWidthPx = scaledDp(1),
                    cycleSeparatorLineHeightPx = scaledDp(10),
                    fallbackStrokePx = scaledDp(1),
                    textSizeSp = 10f * fontScale
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_WINDOW_Y_DP = 180
    }
}
