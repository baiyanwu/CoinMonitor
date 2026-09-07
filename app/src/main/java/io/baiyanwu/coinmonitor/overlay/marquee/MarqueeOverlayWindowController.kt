package io.baiyanwu.coinmonitor.overlay.marquee

import android.animation.ObjectAnimator
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
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
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
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorColors
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
    private val scope: CoroutineScope,
    private val onTap: (View) -> Unit = {}
) : MarqueeOverlayWindowHost {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coinIconService = CoinIconService.get(context)

    private var rootView: FrameLayout? = null
    private var trackView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var animator: ObjectAnimator? = null
    private var structureSignature: String? = null
    private var lastSettingsWindowY: Int? = null
    private var isDragging: Boolean = false
    private var appliedWindowLayout: MarqueeWindowLayoutSnapshot? = null
    private var appliedTouchConfig: MarqueeTouchConfig? = null
    private var lastRootBackgroundColor: Int? = null
    private var lastTrackColors: CoinMonitorColors? = null
    private var cachedColorPreferences: AppPreferences? = null
    private var cachedColorUiMode: Int? = null
    private var cachedOverlayColors: CoinMonitorColors? = null
    private val holdersById = LinkedHashMap<String, MutableList<MarqueeItemHolder>>()
    private val priceWidthsById = LinkedHashMap<String, Int>()
    private val quotePresentationsById = LinkedHashMap<String, MarqueeQuotePresentation>()
    private val iconBitmapCache = LinkedHashMap<String, Bitmap>()
    private val pendingIconLoads = mutableSetOf<String>()
    private val completedIconLoads = mutableSetOf<String>()

    private val overlayColors: CoinMonitorColors
        get() {
            val preferences = appPreferencesRepository.getPreferences()
            val uiMode = context.resources.configuration.uiMode
            val cachedColors = cachedOverlayColors
            if (
                cachedColors != null &&
                cachedColorPreferences == preferences &&
                cachedColorUiMode == uiMode
            ) {
                return cachedColors
            }
            return resolveCoinMonitorColors(context, preferences).also { colors ->
                cachedColorPreferences = preferences
                cachedColorUiMode = uiMode
                cachedOverlayColors = colors
            }
        }

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
        if (rootView == null && !OverlayPermissionHelper.canDrawOverlays(context)) {
            hide()
            return
        }

        val colors = overlayColors
        val metrics = MarqueeMetrics.from(context, settings)
        ensureWindow(settings, locked, metrics)
        val root = rootView ?: return
        val params = layoutParams ?: return
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        var desiredY = params.y
        if (!isDragging && settings.windowY != lastSettingsWindowY) {
            desiredY = settings.windowY ?: DEFAULT_WINDOW_Y_DP.dp
            lastSettingsWindowY = settings.windowY
        }
        desiredY = MarqueeWindowPositionPolicy.resolveY(
            requestedY = desiredY,
            overlayHeightPx = metrics.heightPx,
            screenHeightPx = screenHeight
        )
        val desiredLayout = MarqueeWindowLayoutSnapshot(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = metrics.heightPx,
            x = MarqueeWindowPositionPolicy.resolveX(),
            y = desiredY,
            flags = resolveWindowFlags(locked)
        )
        applyLayoutParams(params, desiredLayout)

        applyRootStyle(root, settings.opacity, colors)
        val visualStyleChanged = lastTrackColors != colors
        val visibleItems = items.take(settings.maxItems)
        if (visibleItems.isEmpty()) {
            renderEmptyState(
                root = root,
                metrics = metrics,
                colors = colors,
                forceRebuild = visualStyleChanged
            )
        } else {
            renderMarquee(
                root = root,
                items = visibleItems,
                settings = settings,
                metrics = metrics,
                screenWidth = screenWidth,
                colors = colors,
                forceRebuild = visualStyleChanged
            )
        }
        lastTrackColors = colors

        if (!isDragging) {
            ensureTouchHandler(root, locked, metrics, screenHeight)
        }

        if (root.parent == null) {
            windowManager.addView(root, params)
            appliedWindowLayout = desiredLayout
        } else if (
            MarqueeRenderPolicy.shouldUpdateWindowLayout(
                applied = appliedWindowLayout,
                desired = desiredLayout
            )
        ) {
            windowManager.updateViewLayout(root, params)
            appliedWindowLayout = desiredLayout
        }
    }

    override fun hide() {
        cancelAnimator()
        rootView?.let { view ->
            if (view.parent != null) windowManager.removeViewImmediate(view)
        }
        holdersById.clear()
        priceWidthsById.clear()
        quotePresentationsById.clear()
        iconBitmapCache.clear()
        pendingIconLoads.clear()
        completedIconLoads.clear()
        rootView = null
        trackView = null
        layoutParams = null
        structureSignature = null
        lastSettingsWindowY = null
        appliedWindowLayout = null
        appliedTouchConfig = null
        lastRootBackgroundColor = null
        lastTrackColors = null
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

    private fun applyLayoutParams(
        params: WindowManager.LayoutParams,
        desired: MarqueeWindowLayoutSnapshot
    ) {
        params.width = desired.width
        params.height = desired.height
        params.x = desired.x
        params.y = desired.y
        params.flags = desired.flags
    }

    private fun applyRootStyle(
        root: FrameLayout,
        opacity: Float,
        colors: CoinMonitorColors
    ) {
        val backgroundColor = colors.overlayBackground.copy(
            alpha = opacity.coerceIn(
                MarqueeOverlaySettings.MIN_OPACITY,
                MarqueeOverlaySettings.MAX_OPACITY
            )
        ).toArgb()
        if (lastRootBackgroundColor == backgroundColor) return

        val background = (root.background as? GradientDrawable) ?: GradientDrawable().also {
            root.background = it
        }
        background.setColor(backgroundColor)
        lastRootBackgroundColor = backgroundColor
    }

    private fun renderEmptyState(
        root: FrameLayout,
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors,
        forceRebuild: Boolean
    ) {
        val emptySignature = "empty|${metrics.fontScale}|${metrics.heightPx}"
        if (structureSignature != emptySignature || forceRebuild) {
            cancelAnimator()
            holdersById.clear()
            priceWidthsById.clear()
            quotePresentationsById.clear()
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
                    setTextColor(colors.overlayText.copy(alpha = 0.92f).toArgb())
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
        screenWidth: Int,
        colors: CoinMonitorColors,
        forceRebuild: Boolean
    ) {
        val signature = MarqueeTrackStructure.signature(
            itemIds = items.map(WatchItem::id),
            fontScale = metrics.fontScale,
            itemWidthPx = metrics.itemWidthPx,
            cycleSeparatorWidthPx = metrics.cycleSeparatorWidthPx,
            viewportWidthPx = screenWidth,
            speed = settings.speed
        )
        if (
            MarqueeRenderPolicy.shouldRebuildTrack(
                currentSignature = structureSignature,
                nextSignature = signature,
                visualStyleChanged = forceRebuild
            )
        ) {
            priceWidthsById.clear()
            priceWidthsById.putAll(resolveInitialPriceWidths(items, metrics))
            rebuildTrack(root, items, settings, metrics, screenWidth, signature, colors)
        } else {
            bindVisibleItems(items, metrics, colors)
        }
    }

    private fun rebuildTrack(
        root: FrameLayout,
        items: List<WatchItem>,
        settings: MarqueeOverlaySettings,
        metrics: MarqueeMetrics,
        screenWidth: Int,
        signature: String,
        colors: CoinMonitorColors
    ) {
        cancelAnimator()
        holdersById.clear()
        quotePresentationsById.clear()
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
                    priceWidthPx = priceWidthsById.getValue(item.id),
                    colors = colors
                )
                holdersById.getOrPut(item.id) { mutableListOf() }.add(holder)
                track.addView(holder.root)
            }
            track.addView(createCycleSeparator(metrics, colors))
        }
        bindVisibleItems(items, metrics, colors)
        root.addView(track)
        trackView = track
        structureSignature = signature
        startAnimator(track, plan, signature)
    }

    private fun resolveInitialPriceWidths(
        items: List<WatchItem>,
        metrics: MarqueeMetrics
    ): LinkedHashMap<String, Int> {
        val measurementView = marqueeTextView(metrics, Gravity.START).apply {
            setTypeface(typeface, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        return items.associateTo(LinkedHashMap()) { item ->
            val priceText = QuoteFormatter.formatOverlayPrice(item)
            val measuredWidthPx = if (priceText == "--") {
                metrics.priceWidthPx
            } else {
                ceil(measurementView.paint.measureText(priceText).toDouble()).toInt()
            }
            item.id to measuredWidthPx.coerceIn(1, metrics.priceWidthPx)
        }
    }

    private fun createItemHolder(
        metrics: MarqueeMetrics,
        priceWidthPx: Int,
        colors: CoinMonitorColors
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
            setImageDrawable(buildDefaultIconDrawable(metrics, colors))
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

    private fun createCycleSeparator(
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors
    ): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                metrics.cycleSeparatorWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        metrics.cycleSeparatorDotSizePx,
                        metrics.cycleSeparatorDotSizePx
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colors.overlayMutedText.copy(alpha = 0.55f).toArgb())
                    }
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

    private fun bindVisibleItems(
        items: List<WatchItem>,
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors
    ) {
        items.forEach { item -> bindItem(item, metrics, colors) }
    }

    private fun bindItem(
        item: WatchItem,
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors
    ) {
        val holders = holdersById[item.id].orEmpty()
        val presentation = MarqueeQuotePresentation(
            priceText = QuoteFormatter.formatOverlayPrice(item),
            priceColor = item.resolveLivePriceColor(colors, colors.overlayText).toArgb()
        )
        if (
            MarqueeRenderPolicy.shouldBindQuote(
                previous = quotePresentationsById[item.id],
                next = presentation
            )
        ) {
            holders.forEach { holder ->
                holder.price.text = presentation.priceText
                holder.price.setTextColor(presentation.priceColor)
            }
            quotePresentationsById[item.id] = presentation
        }
        bindItemIcons(holders, item, metrics, colors)
    }

    private fun bindItemIcons(
        holders: List<MarqueeItemHolder>,
        item: WatchItem,
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors
    ) {
        if (holders.isEmpty()) return
        val iconSignature = listOf(
            item.id,
            item.iconUrl,
            item.chainIndex,
            item.baseSymbol,
            metrics.iconSizePx,
            colors.overlayFallbackBackground.toArgb(),
            colors.overlayFallbackBorder.toArgb()
        ).joinToString("|")
        val holdersNeedingUpdate = holders.filter { holder ->
            holder.iconSignature != iconSignature
        }
        if (holdersNeedingUpdate.isEmpty()) return
        holdersNeedingUpdate.forEach { holder ->
            holder.iconSignature = iconSignature
            holder.icon.setImageDrawable(buildDefaultIconDrawable(metrics, colors))
        }
        iconBitmapCache[iconSignature]?.let { bitmap ->
            holdersNeedingUpdate.forEach { holder -> holder.icon.setImageBitmap(bitmap) }
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
                        holder.icon.setImageDrawable(buildDefaultIconDrawable(metrics, colors))
                    }
                }
            }
        }
    }

    private fun buildDefaultIconDrawable(
        metrics: MarqueeMetrics,
        colors: CoinMonitorColors
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.overlayFallbackBackground.toArgb())
            setStroke(metrics.fallbackStrokePx, colors.overlayFallbackBorder.toArgb())
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
            animator = ObjectAnimator.ofFloat(
                track,
                View.TRANSLATION_X,
                0f,
                -plan.cycleWidthPx.toFloat()
            ).apply {
                duration = plan.durationMillis
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
                if (isDragging) pause()
            }
        }
    }

    private fun ensureTouchHandler(
        root: View,
        locked: Boolean,
        metrics: MarqueeMetrics,
        screenHeight: Int
    ) {
        val config = MarqueeTouchConfig(
            locked = locked,
            overlayHeightPx = metrics.heightPx,
            screenHeightPx = screenHeight
        )
        if (appliedTouchConfig == config) return
        applyTouchHandler(root, config)
        appliedTouchConfig = config
    }

    private fun applyTouchHandler(
        root: View,
        config: MarqueeTouchConfig
    ) {
        if (config.locked) {
            root.setOnClickListener(null)
            root.setOnTouchListener(null)
            return
        }
        root.setOnClickListener { onTap(root) }
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        root.setOnTouchListener(object : View.OnTouchListener {
            private var downRawX: Float = 0f
            private var downRawY: Float = 0f
            private var startWindowY: Int = 0
            private var hasDragged: Boolean = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        hasDragged = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startWindowY = params.y
                        animator?.pause()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - downRawY
                        if (abs(deltaY) > touchSlop || abs(event.rawX - downRawX) > touchSlop) hasDragged = true
                        if (hasDragged) {
                            params.x = MarqueeWindowPositionPolicy.resolveX()
                            params.y = MarqueeWindowPositionPolicy.resolveY(
                                requestedY = startWindowY + deltaY.roundToInt(),
                                overlayHeightPx = config.overlayHeightPx,
                                screenHeightPx = config.screenHeightPx
                            )
                            if (view.parent != null) {
                                windowManager.updateViewLayout(view, params)
                                appliedWindowLayout = MarqueeWindowLayoutSnapshot(
                                    width = params.width,
                                    height = params.height,
                                    x = params.x,
                                    y = params.y,
                                    flags = params.flags
                                )
                            }
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
                        if (event.actionMasked == MotionEvent.ACTION_UP && !hasDragged) view.performClick()
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

    private data class MarqueeTouchConfig(
        val locked: Boolean,
        val overlayHeightPx: Int,
        val screenHeightPx: Int
    )

    private data class MarqueeMetrics(
        val fontScale: Float,
        val heightPx: Int,
        val iconSizePx: Int,
        val iconSlotWidthPx: Int,
        val priceWidthPx: Int,
        val itemSpacingPx: Int,
        val cycleSeparatorWidthPx: Int,
        val cycleSeparatorDotSizePx: Int,
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
                    cycleSeparatorDotSizePx = scaledDp(3),
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
