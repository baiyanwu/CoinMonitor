package io.baiyanwu.coinmonitor.overlay.arranged

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.overlay.ArrangedOverlayWindowHost
import io.baiyanwu.coinmonitor.overlay.CoinIconService
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.PriceTextSizer
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.resolveCoinMonitorColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal class ArrangedOverlayWindowController(
    private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val scope: CoroutineScope,
    private val onTap: (View) -> Unit = {}
) : ArrangedOverlayWindowHost {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coinIconService = CoinIconService.get(context)

    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var batchCursor: Int = 0
    private var isDragging: Boolean = false
    private var pendingItems: List<WatchItem>? = null
    private var pendingSettings: ArrangedOverlaySettings? = null
    private var pendingLocked: Boolean? = null
    private val rowHolders = LinkedHashMap<String, RowHolder>()
    private var currentBatchIds: List<String> = emptyList()
    private var currentLeadingDisplayMode: OverlayLeadingDisplayMode? = null
    private var currentRenderMode: OverlayRenderMode? = null
    private var footerView: TextView? = null
    private var emptyView: TextView? = null
    private var sidebarTickerHolder: SidebarTickerHolder? = null
    private var edgeTabView: FrameLayout? = null
    private var edgeTabIndicatorView: View? = null
    private var isHiddenEdgeTabExpanded: Boolean = false
    private var hiddenEdgeTransitionState: HiddenEdgeTransitionState =
        HiddenEdgeTransitionState.IDLE
    private var hiddenEdgeAnimationGeneration: Int = 0
    private var scheduledHiddenEdgeAutoCollapseSeconds: Int? = null
    private var latestItems: List<WatchItem> = emptyList()
    private var latestSettings: ArrangedOverlaySettings? = null
    private var latestLocked: Boolean = false
    private val hiddenEdgeEnterInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val hiddenEdgeExitInterpolator = PathInterpolator(0.7f, 0f, 0.84f, 0f)
    private val hiddenEdgeAutoCollapseRunnable = object : Runnable {
        override fun run() {
            if (
                !isHiddenEdgeTabExpanded ||
                hiddenEdgeTransitionState != HiddenEdgeTransitionState.IDLE
            ) {
                return
            }
            if (isDragging) {
                rootView?.postDelayed(this, HIDDEN_EDGE_AUTO_COLLAPSE_RETRY_MS)
                return
            }
            val settings = latestSettings ?: return
            val items = latestItems
            val stillUsesHiddenEdgeMode = settings.snapToEdge &&
                settings.edgeDisplayMode == ArrangedEdgeDisplayMode.HIDDEN_TAB &&
                items.take(settings.maxItems).isNotEmpty()
            if (stillUsesHiddenEdgeMode) {
                animateHiddenEdgeCollapse()
            } else {
                isHiddenEdgeTabExpanded = false
            }
        }
    }
    private val iconBitmapCache = LinkedHashMap<String, Bitmap>()

    private val overlayColors
        get() = resolveCoinMonitorColors(context, appPreferencesRepository.getPreferences())

    private val localizedContext: Context
        get() = AppConfigurationApplier.wrapContext(context, appPreferencesRepository.getPreferences())

    override fun showOrUpdate(
        items: List<WatchItem>,
        locked: Boolean,
        settings: ArrangedOverlaySettings
    ) {
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            hide()
            return
        }

        latestItems = items
        latestSettings = settings
        latestLocked = locked
        ensureWindow(settings, locked)
        val root = rootView ?: return
        val params = layoutParams ?: return

        if (isDragging) {
            pendingItems = items
            pendingSettings = settings
            pendingLocked = locked
            return
        }

        val limitedItems = items.take(settings.maxItems)
        val usesHiddenEdgeMode = settings.snapToEdge &&
            settings.edgeDisplayMode == ArrangedEdgeDisplayMode.HIDDEN_TAB &&
            limitedItems.isNotEmpty()
        if (!usesHiddenEdgeMode) {
            isHiddenEdgeTabExpanded = false
            cancelHiddenEdgeAutoCollapse()
            cancelHiddenEdgeTransition()
        } else if (!isHiddenEdgeTabExpanded) {
            cancelHiddenEdgeAutoCollapse()
        } else if (
            hiddenEdgeTransitionState == HiddenEdgeTransitionState.IDLE &&
            scheduledHiddenEdgeAutoCollapseSeconds != null &&
            scheduledHiddenEdgeAutoCollapseSeconds !=
            OverlayEdgeAutoCollapsePolicy.normalizeSeconds(
                settings.edgeAutoCollapseSeconds
            )
        ) {
            scheduleHiddenEdgeAutoCollapse()
        }
        val presentation = OverlayEdgePresentationPolicy.resolve(
            settings = settings,
            hasItems = limitedItems.isNotEmpty(),
            hiddenTabExpanded = isHiddenEdgeTabExpanded
        )

        val resolvedFlags = resolveWindowFlags(
            locked = locked,
            allowEdgeToggle = usesHiddenEdgeMode
        )
        if (params.flags != resolvedFlags) {
            params.flags = resolvedFlags
        }

        val metrics = OverlayMetrics.from(context, settings, presentation)

        applyRootStyle(
            root = root,
            opacity = settings.opacity,
            metrics = metrics
        )
        renderContent(
            root = root,
            items = limitedItems,
            settings = settings,
            metrics = metrics,
            presentation = presentation
        )
        root.contentDescription = when {
            !usesHiddenEdgeMode -> null
            isHiddenEdgeTabExpanded -> localizedContext.getString(
                R.string.overlay_edge_docked_auto_collapse,
                OverlayEdgeAutoCollapsePolicy.normalizeSeconds(
                    settings.edgeAutoCollapseSeconds
                )
            )
            else -> localizedContext.getString(R.string.overlay_edge_tab_expand)
        }
        applyTouchHandler(
            root = root,
            items = limitedItems,
            settings = settings,
            locked = locked,
            hiddenEdgeMode = usesHiddenEdgeMode
        )
        applyResolvedPosition(
            view = root,
            params = params,
            sidebarMode = presentation != OverlayEdgePresentation.STANDARD
        )

        if (root.parent == null) {
            windowManager.addView(root, params)
        } else {
            windowManager.updateViewLayout(root, params)
        }
    }

    override fun hide() {
        cancelHiddenEdgeAutoCollapse()
        cancelHiddenEdgeTransition()
        rootView?.let { view ->
            if (view.parent != null) {
                // 临时隐藏后要立即释放窗口占位，避免原位置还残留一帧触摸拦截区域。
                windowManager.removeViewImmediate(view)
            }
        }
        isDragging = false
        pendingItems = null
        pendingSettings = null
        pendingLocked = null
        batchCursor = 0
        rowHolders.clear()
        currentBatchIds = emptyList()
        currentLeadingDisplayMode = null
        currentRenderMode = null
        footerView = null
        emptyView = null
        sidebarTickerHolder?.stop()
        sidebarTickerHolder = null
        edgeTabView = null
        edgeTabIndicatorView = null
        isHiddenEdgeTabExpanded = false
        hiddenEdgeTransitionState = HiddenEdgeTransitionState.IDLE
        latestItems = emptyList()
        latestSettings = null
        iconBitmapCache.clear()
        rootView = null
        layoutParams = null
    }

    private fun ensureWindow(settings: ArrangedOverlaySettings, locked: Boolean) {
        if (rootView != null && layoutParams != null) return

        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            resolveWindowFlags(locked, allowEdgeToggle = false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.windowX ?: 48.dp
            y = settings.windowY ?: 180.dp
        }
    }

    /**
     * 锁定状态下关闭普通悬浮窗触摸；隐藏吸附模式仍保留点击能力，避免唤出条无法展开。
     */
    private fun resolveWindowFlags(
        locked: Boolean,
        allowEdgeToggle: Boolean
    ): Int {
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        return if (locked && !allowEdgeToggle) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
    }

    private fun applyRootStyle(
        root: LinearLayout,
        opacity: Float,
        metrics: OverlayMetrics
    ) {
        root.setPadding(
            metrics.horizontalPaddingPx,
            metrics.verticalPaddingPx,
            metrics.horizontalPaddingPx,
            metrics.verticalPaddingPx
        )
        if (metrics.edgeTabMode) {
            root.background = null
            return
        }
        root.background = GradientDrawable().apply {
            cornerRadius = metrics.cornerRadiusPx.toFloat()
            setColor(
                overlayColors.overlayBackground.copy(
                    alpha = opacity.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_OPACITY,
                        maximumValue = ArrangedOverlaySettings.MAX_OPACITY
                    )
                ).toArgb()
            )
            setStroke(1.dp, overlayColors.overlayBorder.toArgb())
        }
    }

    private fun renderContent(
        root: LinearLayout,
        items: List<WatchItem>,
        settings: ArrangedOverlaySettings,
        metrics: OverlayMetrics,
        presentation: OverlayEdgePresentation
    ) {
        if (items.isEmpty()) {
            sidebarTickerHolder?.stop()
            renderEmptyState(root, metrics)
            return
        }

        if (presentation == OverlayEdgePresentation.HIDDEN_TAB) {
            renderEdgeTabState(
                root = root,
                metrics = metrics,
                edgeTabOpacity = settings.edgeTabOpacity
            )
            return
        }

        if (presentation == OverlayEdgePresentation.DOCKED_CAROUSEL) {
            renderSidebarState(root, items, metrics)
            return
        }

        sidebarTickerHolder?.stop()
        currentRenderMode = OverlayRenderMode.STANDARD
        val batch = OverlayBatchPlanner.plan(
            items = items,
            maxPerPage = 5,
            cursor = batchCursor
        )
        batchCursor = batch.nextCursor

        val symbolColumnWidth = measureLeadingColumnWidth(
            items = batch.items,
            displayMode = settings.leadingDisplayMode,
            metrics = metrics
        )
        val priceColumnWidth = measurePriceColumnWidth(
            items = batch.items,
            metrics = metrics
        )
        renderStandardRows(
            root = root,
            items = batch.items,
            displayMode = settings.leadingDisplayMode,
            leadingWidth = symbolColumnWidth,
            priceWidth = priceColumnWidth,
            metrics = metrics,
            pageLabel = batch.pageLabel
        )
    }

    private fun buildEmptyText(metrics: OverlayMetrics): TextView {
        return TextView(context).apply {
            text = localizedContext.getString(R.string.overlay_empty_state)
            setTextColor(overlayColors.overlayText.copy(alpha = 0.92f).toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.emptyTextSizeSp)
        }
    }

    private fun buildFooter(pageLabel: String, metrics: OverlayMetrics): TextView {
        return TextView(context).apply {
            text = localizedContext.getString(R.string.overlay_page_format, pageLabel)
            setTextColor(overlayColors.overlayMutedText.copy(alpha = 0.88f).toArgb())
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.footerTextSizeSp)
            setPadding(0, metrics.footerTopPaddingPx, 0, 0)
        }
    }

    private fun createRowHolder(): RowHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val leadingContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val priceView = TextView(context).apply {
            maxLines = 1
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(leadingContainer)
        row.addView(priceView)
        return RowHolder(
            row = row,
            leadingContainer = leadingContainer,
            priceView = priceView
        )
    }

    private fun buildLeadingView(
        item: WatchItem,
        displayMode: OverlayLeadingDisplayMode,
        width: Int,
        metrics: OverlayMetrics
    ): View {
        return when (displayMode) {
            OverlayLeadingDisplayMode.ICON -> buildIconView(item, width, metrics)

            OverlayLeadingDisplayMode.PAIR_NAME -> TextView(context).apply {
                text = item.symbol.uppercase()
                setTextColor(overlayColors.overlayText.copy(alpha = 0.96f).toArgb())
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.leadingTextSizeSp)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also {
                    it.marginEnd = metrics.leadingSpacingPx
                }
            }
        }
    }

    private fun buildIconView(
        item: WatchItem,
        width: Int,
        metrics: OverlayMetrics
    ): View {
        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(metrics.iconSizePx, metrics.iconSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            background = buildDefaultBitcoinDrawable(metrics)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setImageDrawable(buildDefaultBitcoinDrawable(metrics))
        }

        scope.launch {
            val bitmap = coinIconService.loadBitmap(
                symbol = item.baseSymbol,
                preferredIconUrl = item.iconUrl,
                fallbackIconUrls = OnchainChainIconRegistry.resolveIconUrls(item.chainIndex),
                grayscaleFallback = item.chainIndex != null
            )
            if (bitmap != null) {
                iconView.setImageBitmap(bitmap)
            } else {
                iconView.setImageDrawable(buildDefaultBitcoinDrawable(metrics))
            }
        }

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, metrics.iconSizePx).also {
                it.marginEnd = metrics.leadingSpacingPx
            }
            addView(iconView)
        }
    }

    private fun buildDefaultBitcoinDrawable(metrics: OverlayMetrics): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(overlayColors.overlayFallbackBackground.toArgb())
            setStroke(metrics.fallbackStrokePx, overlayColors.overlayFallbackBorder.toArgb())
        }
    }

    private fun measureLeadingColumnWidth(
        items: List<WatchItem>,
        displayMode: OverlayLeadingDisplayMode,
        metrics: OverlayMetrics
    ): Int {
        val maxWidth = items.maxOfOrNull { item ->
            when (displayMode) {
                OverlayLeadingDisplayMode.ICON -> metrics.iconSizePx
                OverlayLeadingDisplayMode.PAIR_NAME -> measureTextWidth(
                    text = item.symbol.uppercase(),
                    textSizeSp = metrics.leadingTextSizeSp
                )
            }
        } ?: 0
        return maxWidth + metrics.leadingWidthExtraPx
    }

    private fun measurePriceColumnWidth(
        items: List<WatchItem>,
        metrics: OverlayMetrics
    ): Int {
        val maxWidth = items.maxOfOrNull { item ->
            val priceText = QuoteFormatter.formatOverlayPrice(item)
            measureTextWidth(
                text = priceText,
                textSizeSp = PriceTextSizer.resolveTextSizeSp(priceText) * metrics.fontScale
            )
        } ?: 0
        return maxWidth + metrics.priceWidthExtraPx
    }

    private fun measureTextWidth(text: String, textSizeSp: Float): Int {
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
        }
        return paint.measureText(text).roundToInt()
    }

    private fun renderEmptyState(
        root: LinearLayout,
        metrics: OverlayMetrics
    ) {
        currentRenderMode = OverlayRenderMode.EMPTY
        currentBatchIds = emptyList()
        currentLeadingDisplayMode = null
        val view = emptyView ?: buildEmptyText(metrics).also { emptyView = it }
        view.setTextColor(overlayColors.overlayText.copy(alpha = 0.92f).toArgb())
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.emptyTextSizeSp)
        if (view.parent !== root || root.childCount != 1) {
            root.removeAllViews()
            root.addView(view)
        }
    }

    private fun renderSidebarState(
        root: LinearLayout,
        items: List<WatchItem>,
        metrics: OverlayMetrics
    ) {
        val dockedCarousel = bindDockedCarousel(items, metrics)
        attachAsOnlyChild(root, dockedCarousel)
    }

    private fun bindDockedCarousel(
        items: List<WatchItem>,
        metrics: OverlayMetrics
    ): FrameLayout {
        currentRenderMode = OverlayRenderMode.SIDEBAR
        currentBatchIds = items.map { it.id }
        currentLeadingDisplayMode = null
        val contentWidth = measureSidebarTickerWidth(items, metrics)
        val holder = sidebarTickerHolder ?: SidebarTickerHolder(buildSidebarTickerContainer(contentWidth)).also {
            sidebarTickerHolder = it
        }
        holder.bind(items, metrics, contentWidth)
        return holder.container
    }

    private fun renderEdgeTabState(
        root: LinearLayout,
        metrics: OverlayMetrics,
        edgeTabOpacity: Float
    ) {
        currentRenderMode = OverlayRenderMode.EDGE_TAB
        currentBatchIds = emptyList()
        currentLeadingDisplayMode = null
        sidebarTickerHolder?.stop()
        val container = prepareEdgeTab(metrics, edgeTabOpacity)
        attachAsOnlyChild(root, container)
    }

    private fun prepareEdgeTab(
        metrics: OverlayMetrics,
        edgeTabOpacity: Float
    ): FrameLayout {
        val container = edgeTabView ?: FrameLayout(context).also { edgeTabView = it }
        container.layoutParams = LinearLayout.LayoutParams(
            metrics.edgeTabTouchWidthPx,
            metrics.edgeTabTouchHeightPx
        )
        val indicator = edgeTabIndicatorView ?: View(context).also { edgeTabIndicatorView = it }
        indicator.layoutParams = FrameLayout.LayoutParams(
            metrics.edgeTabIndicatorWidthPx,
            metrics.edgeTabIndicatorHeightPx
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or (
                if (isNearestLeftEdge()) Gravity.START else Gravity.END
            )
        }
        indicator.background = GradientDrawable().apply {
            cornerRadius = metrics.edgeTabIndicatorWidthPx.toFloat()
            setColor(
                overlayColors.accent.copy(
                    alpha = edgeTabOpacity.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY,
                        maximumValue = ArrangedOverlaySettings.MAX_EDGE_TAB_OPACITY
                    )
                ).toArgb()
            )
        }
        if (indicator.parent !== container || container.childCount != 1) {
            container.removeAllViews()
            container.addView(indicator)
        }
        return container
    }

    private fun attachAsOnlyChild(root: LinearLayout, view: View) {
        if (view.parent === root && root.childCount == 1) return
        detachFromParent(view)
        root.removeAllViews()
        root.addView(view)
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun isNearestLeftEdge(): Boolean {
        val params = layoutParams ?: return true
        val currentWidth = rootView?.measuredWidth?.takeIf { it > 0 } ?: 0
        val screenWidth = context.resources.displayMetrics.widthPixels
        return params.x + currentWidth / 2 <= screenWidth / 2
    }

    private fun buildSidebarTickerContainer(contentWidth: Int): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = true
            clipToPadding = true
        }
    }

    private fun createSidebarItemView(metrics: OverlayMetrics, contentWidth: Int): SidebarItemView {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                metrics.iconSizePx + metrics.leadingSpacingPx,
                metrics.iconSizePx
            ).also {
                it.marginEnd = metrics.leadingSpacingPx
            }
        }
        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(metrics.iconSizePx, metrics.iconSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            background = buildDefaultBitcoinDrawable(metrics)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setImageDrawable(buildDefaultBitcoinDrawable(metrics))
        }
        val priceView = TextView(context).apply {
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
        }
        iconContainer.addView(iconView)
        row.addView(iconContainer)
        row.addView(priceView)
        return SidebarItemView(
            root = row,
            iconView = iconView,
            priceView = priceView
        )
    }

    private fun bindSidebarItemView(
        sidebarItemView: SidebarItemView,
        item: WatchItem,
        metrics: OverlayMetrics
    ) {
        val priceText = QuoteFormatter.formatOverlayPrice(item)
        sidebarItemView.priceView.text = priceText
        sidebarItemView.priceView.setTextColor(
            item.resolveLivePriceColor(
                colors = overlayColors,
                defaultColor = overlayColors.overlayText
            ).toArgb()
        )
        sidebarItemView.priceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.sidebarTextSizeSp)
        sidebarItemView.priceView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val iconSignature = listOf(
            item.id,
            item.iconUrl,
            item.chainIndex,
            item.baseSymbol,
            metrics.iconSizePx,
            overlayColors.overlayFallbackBorder.toArgb()
        ).joinToString("|")
        if (sidebarItemView.iconSignature != iconSignature) {
            sidebarItemView.iconSignature = iconSignature
            val cachedBitmap = iconBitmapCache[iconSignature]
            if (cachedBitmap != null) {
                sidebarItemView.iconView.setImageBitmap(cachedBitmap)
            } else {
                sidebarItemView.iconView.setImageDrawable(buildDefaultBitcoinDrawable(metrics))
                scope.launch {
                    val bitmap = coinIconService.loadBitmap(
                        symbol = item.baseSymbol,
                        preferredIconUrl = item.iconUrl,
                        fallbackIconUrls = OnchainChainIconRegistry.resolveIconUrls(item.chainIndex),
                        grayscaleFallback = item.chainIndex != null
                    )
                    if (bitmap != null) {
                        iconBitmapCache[iconSignature] = bitmap
                        if (sidebarItemView.iconSignature == iconSignature) {
                            sidebarItemView.iconView.setImageBitmap(bitmap)
                        }
                    } else if (sidebarItemView.iconSignature == iconSignature) {
                        sidebarItemView.iconView.setImageDrawable(buildDefaultBitcoinDrawable(metrics))
                    }
                }
            }
        }
    }

    private fun measureSidebarTickerWidth(
        items: List<WatchItem>,
        metrics: OverlayMetrics
    ): Int {
        val priceWidth = measurePriceColumnWidth(items, metrics)
        return metrics.iconSizePx +
            (metrics.leadingSpacingPx * 2) +
            priceWidth
    }

    private fun renderStandardRows(
        root: LinearLayout,
        items: List<WatchItem>,
        displayMode: OverlayLeadingDisplayMode,
        leadingWidth: Int,
        priceWidth: Int,
        metrics: OverlayMetrics,
        pageLabel: String?
    ) {
        val batchIds = items.map { it.id }
        val layoutChanged = currentRenderMode != OverlayRenderMode.STANDARD ||
            batchIds != currentBatchIds ||
            currentLeadingDisplayMode != displayMode

        if (layoutChanged) {
            root.removeAllViews()
            items.forEach { item ->
                val holder = rowHolders.getOrPut(item.id) { createRowHolder() }
                updateRowHolder(
                    holder = holder,
                    item = item,
                    displayMode = displayMode,
                    leadingWidth = leadingWidth,
                    priceWidth = priceWidth,
                    metrics = metrics
                )
                root.addView(holder.row)
            }
        } else {
            items.forEach { item ->
                val holder = rowHolders[item.id] ?: return@forEach
                updateRowHolder(
                    holder = holder,
                    item = item,
                    displayMode = displayMode,
                    leadingWidth = leadingWidth,
                    priceWidth = priceWidth,
                    metrics = metrics
                )
            }
        }

        val footer = if (pageLabel != null) {
            (footerView ?: buildFooter(pageLabel, metrics).also { footerView = it }).apply {
                text = localizedContext.getString(R.string.overlay_page_format, pageLabel)
                setTextColor(overlayColors.overlayMutedText.copy(alpha = 0.88f).toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.footerTextSizeSp)
                setPadding(0, metrics.footerTopPaddingPx, 0, 0)
            }
        } else {
            null
        }

        if (layoutChanged) {
            footer?.let { root.addView(it) }
        } else {
            val existingFooter = footerView
            if (footer != null && existingFooter?.parent !== root) {
                root.addView(footer)
            } else if (footer == null && existingFooter?.parent === root) {
                root.removeView(existingFooter)
            }
        }

        currentRenderMode = OverlayRenderMode.STANDARD
        currentBatchIds = batchIds
        currentLeadingDisplayMode = displayMode
    }

    private fun updateRowHolder(
        holder: RowHolder,
        item: WatchItem,
        displayMode: OverlayLeadingDisplayMode,
        leadingWidth: Int,
        priceWidth: Int,
        metrics: OverlayMetrics
    ) {
        holder.row.setPadding(0, metrics.rowVerticalPaddingPx, 0, metrics.rowVerticalPaddingPx)
        val leadingSignature = buildLeadingSignature(
            item = item,
            displayMode = displayMode,
            leadingWidth = leadingWidth,
            metrics = metrics
        )
        if (holder.leadingSignature != leadingSignature) {
            holder.leadingContainer.removeAllViews()
            holder.leadingContainer.addView(
                buildLeadingView(
                    item = item,
                    displayMode = displayMode,
                    width = leadingWidth,
                    metrics = metrics
                )
            )
            holder.leadingSignature = leadingSignature
        }

        val priceText = QuoteFormatter.formatOverlayPrice(item)
        holder.priceView.text = priceText
        holder.priceView.setTextColor(
            item.resolveLivePriceColor(
                colors = overlayColors,
                defaultColor = overlayColors.overlayText
            ).toArgb()
        )
        holder.priceView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            PriceTextSizer.resolveTextSizeSp(priceText) * metrics.fontScale
        )
        holder.priceView.layoutParams = LinearLayout.LayoutParams(
            priceWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildLeadingSignature(
        item: WatchItem,
        displayMode: OverlayLeadingDisplayMode,
        leadingWidth: Int,
        metrics: OverlayMetrics
    ): String {
        return listOf(
            item.id,
            item.baseSymbol,
            item.symbol,
            displayMode.name,
            leadingWidth,
            metrics.iconSizePx,
            metrics.leadingSpacingPx,
            metrics.leadingTextSizeSp,
            overlayColors.overlayText.toArgb(),
            overlayColors.overlayFallbackBorder.toArgb()
        ).joinToString(separator = "|")
    }

    private fun applyTouchHandler(
        root: View,
        items: List<WatchItem>,
        settings: ArrangedOverlaySettings,
        locked: Boolean,
        hiddenEdgeMode: Boolean
    ) {
        if (hiddenEdgeTransitionState != HiddenEdgeTransitionState.IDLE) {
            root.setOnTouchListener(null)
            root.setOnClickListener(null)
            return
        }

        if (locked && !hiddenEdgeMode) {
            root.setOnTouchListener(null)
            root.setOnClickListener(null)
            return
        }

        if (hiddenEdgeMode && !isHiddenEdgeTabExpanded) {
            root.setOnClickListener {
                if (isHiddenEdgeTabExpanded) return@setOnClickListener
                isHiddenEdgeTabExpanded = true
                renderLatestTouchState(items, locked, settings)
                animateHiddenEdgeExpansion()
            }
        } else {
            root.setOnClickListener { if (!locked) onTap(root) }
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        root.setOnTouchListener(object : View.OnTouchListener {
            private var touchOffsetX: Float = 0f
            private var touchOffsetY: Float = 0f
            private var downRawX: Float = 0f
            private var downRawY: Float = 0f
            private var hasDragged: Boolean = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        hasDragged = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        touchOffsetX = event.rawX - params.x
                        touchOffsetY = event.rawY - params.y
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (
                            abs(event.rawX - downRawX) > touchSlop ||
                            abs(event.rawY - downRawY) > touchSlop
                        ) {
                            hasDragged = true
                        }
                        if (!locked && hasDragged) {
                            measureOverlay(view)
                            val bounds = resolveScreenBounds(view)
                            params.x = (event.rawX - touchOffsetX).roundToInt()
                                .coerceIn(0, bounds.maxX)
                            params.y = (event.rawY - touchOffsetY).roundToInt()
                                .coerceIn(0, bounds.maxY)
                            windowManager.updateViewLayout(view, params)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        if (hiddenEdgeMode && !isHiddenEdgeTabExpanded && !hasDragged) {
                            view.performClick()
                            return true
                        }
                        if (!hasDragged && !locked) {
                            view.performClick()
                            consumePendingUpdate()
                            return true
                        }
                        if (!locked) {
                            finishDrag(view, params, settings)
                        }
                        if (hiddenEdgeMode) {
                            renderLatestTouchState(items, locked, settings)
                            return true
                        }
                        consumePendingUpdate()
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        if (!locked && hasDragged) {
                            finishDrag(view, params, settings)
                        }
                        if (hiddenEdgeMode && hasDragged) {
                            renderLatestTouchState(items, locked, settings)
                            return true
                        }
                        consumePendingUpdate()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun animateHiddenEdgeExpansion() {
        val root = rootView ?: return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            scheduleHiddenEdgeAutoCollapse()
            return
        }

        measureOverlay(root)
        val offset = resolveHiddenEdgeTranslation(root.measuredWidth)
        if (offset == 0f) {
            scheduleHiddenEdgeAutoCollapse()
            return
        }

        cancelHiddenEdgeTransition()
        hiddenEdgeTransitionState = HiddenEdgeTransitionState.EXPANDING
        val generation = ++hiddenEdgeAnimationGeneration
        root.setOnTouchListener(null)
        root.setOnClickListener(null)
        root.translationX = offset
        root.animate()
            .translationX(0f)
            .setDuration(HIDDEN_EDGE_EXPANSION_DURATION_MS)
            .setInterpolator(hiddenEdgeEnterInterpolator)
            .withEndAction {
                if (
                    generation != hiddenEdgeAnimationGeneration ||
                    rootView !== root
                ) {
                    return@withEndAction
                }
                hiddenEdgeTransitionState = HiddenEdgeTransitionState.IDLE
                root.translationX = 0f
                latestSettings?.let { settings ->
                    showOrUpdate(latestItems, latestLocked, settings)
                }
                scheduleHiddenEdgeAutoCollapse()
            }
            .start()
    }

    private fun animateHiddenEdgeCollapse() {
        val root = rootView ?: return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            completeHiddenEdgeCollapse()
            return
        }

        measureOverlay(root)
        val offset = resolveHiddenEdgeTranslation(root.measuredWidth)
        if (offset == 0f) {
            completeHiddenEdgeCollapse()
            return
        }

        cancelHiddenEdgeTransition()
        hiddenEdgeTransitionState = HiddenEdgeTransitionState.COLLAPSING
        val generation = ++hiddenEdgeAnimationGeneration
        root.setOnTouchListener(null)
        root.setOnClickListener(null)
        root.translationX = 0f
        root.animate()
            .translationX(offset)
            .setDuration(HIDDEN_EDGE_COLLAPSE_DURATION_MS)
            .setInterpolator(hiddenEdgeExitInterpolator)
            .withEndAction {
                if (
                    generation != hiddenEdgeAnimationGeneration ||
                    rootView !== root
                ) {
                    return@withEndAction
                }
                hiddenEdgeTransitionState = HiddenEdgeTransitionState.IDLE
                root.translationX = 0f
                completeHiddenEdgeCollapse()
            }
            .start()
    }

    private fun completeHiddenEdgeCollapse() {
        isHiddenEdgeTabExpanded = false
        latestSettings?.let { settings ->
            showOrUpdate(latestItems, latestLocked, settings)
        }
    }

    private fun resolveHiddenEdgeTranslation(overlayWidth: Int): Float {
        return OverlayEdgeMotionPolicy.resolveTranslation(
            overlayWidth = overlayWidth,
            nearestLeftEdge = isNearestLeftEdge()
        )
    }

    private fun cancelHiddenEdgeTransition() {
        hiddenEdgeAnimationGeneration += 1
        hiddenEdgeTransitionState = HiddenEdgeTransitionState.IDLE
        rootView?.let { root ->
            root.animate().cancel()
            root.translationX = 0f
        }
    }

    private fun scheduleHiddenEdgeAutoCollapse() {
        val root = rootView ?: return
        val seconds = OverlayEdgeAutoCollapsePolicy.normalizeSeconds(
            latestSettings?.edgeAutoCollapseSeconds
                ?: ArrangedOverlaySettings.DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS
        )
        scheduledHiddenEdgeAutoCollapseSeconds = seconds
        root.removeCallbacks(hiddenEdgeAutoCollapseRunnable)
        root.postDelayed(
            hiddenEdgeAutoCollapseRunnable,
            OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(seconds)
        )
    }

    private fun cancelHiddenEdgeAutoCollapse() {
        rootView?.removeCallbacks(hiddenEdgeAutoCollapseRunnable)
        scheduledHiddenEdgeAutoCollapseSeconds = null
    }

    private fun finishDrag(
        view: View,
        params: WindowManager.LayoutParams,
        settings: ArrangedOverlaySettings
    ) {
        applyResolvedPosition(
            view = view,
            params = params,
            sidebarMode = settings.snapToEdge
        )
        if (view.parent != null) {
            windowManager.updateViewLayout(view, params)
        }
        scope.launch {
            overlayRepository.setArrangedWindowPosition(params.x, params.y)
        }
    }

    private fun renderLatestTouchState(
        items: List<WatchItem>,
        locked: Boolean,
        settings: ArrangedOverlaySettings
    ) {
        val latestItems = pendingItems ?: items
        val latestSettings = pendingSettings ?: settings
        val latestLocked = pendingLocked ?: locked
        pendingItems = null
        pendingSettings = null
        pendingLocked = null
        showOrUpdate(latestItems, latestLocked, latestSettings)
    }

    private fun applyResolvedPosition(
        view: View,
        params: WindowManager.LayoutParams,
        sidebarMode: Boolean
    ) {
        measureOverlay(view)
        val bounds = resolveScreenBounds(view)
        params.y = params.y.coerceIn(0, bounds.maxY)
        params.x = if (sidebarMode) {
            resolveSnappedX(
                currentX = params.x,
                overlayWidth = view.measuredWidth,
                screenWidth = bounds.screenWidth
            )
        } else {
            params.x.coerceIn(0, bounds.maxX)
        }
    }

    private fun resolveSnappedX(
        currentX: Int,
        overlayWidth: Int,
        screenWidth: Int
    ): Int {
        val centerX = currentX + overlayWidth / 2
        return if (centerX <= screenWidth / 2) {
            0
        } else {
            max(0, screenWidth - overlayWidth)
        }
    }

    private fun resolveScreenBounds(view: View): ScreenBounds {
        val displayMetrics = context.resources.displayMetrics
        val maxX = max(0, displayMetrics.widthPixels - view.measuredWidth)
        val maxY = max(0, displayMetrics.heightPixels - view.measuredHeight)
        return ScreenBounds(
            screenWidth = displayMetrics.widthPixels,
            maxX = maxX,
            maxY = maxY
        )
    }

    private fun measureOverlay(view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
    }

    private fun consumePendingUpdate() {
        val latestItems = pendingItems
        val latestSettings = pendingSettings
        val latestLocked = pendingLocked
        pendingItems = null
        pendingSettings = null
        pendingLocked = null
        if (latestItems != null && latestSettings != null && latestLocked != null) {
            showOrUpdate(latestItems, latestLocked, latestSettings)
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).roundToInt()

    private data class ScreenBounds(
        val screenWidth: Int,
        val maxX: Int,
        val maxY: Int
    )

    private enum class HiddenEdgeTransitionState {
        IDLE,
        EXPANDING,
        COLLAPSING
    }

    private data class RowHolder(
        val row: LinearLayout,
        val leadingContainer: LinearLayout,
        val priceView: TextView,
        var leadingSignature: String? = null
    )

    private data class SidebarItemView(
        val root: LinearLayout,
        val iconView: ImageView,
        val priceView: TextView,
        var iconSignature: String? = null
    )

    private inner class SidebarTickerHolder(
        val container: FrameLayout
    ) {
        private var items: List<WatchItem> = emptyList()
        private var currentIndex: Int = 0
        private var currentView: SidebarItemView? = null
        private var nextView: SidebarItemView? = null
        private var metrics: OverlayMetrics? = null
        private var isSwitchScheduled: Boolean = false
        private var isAnimating: Boolean = false
        private val switchRunnable = Runnable { animateToNext() }

        fun bind(items: List<WatchItem>, metrics: OverlayMetrics, contentWidth: Int) {
            val previousCurrentId = this.items.getOrNull(currentIndex)?.id
            this.items = items
            this.metrics = metrics
            currentIndex = previousCurrentId?.let { id ->
                items.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            } ?: 0
            if (items.isEmpty()) {
                stop()
                container.removeAllViews()
                currentView = null
                nextView = null
                return
            }
            ensureViews(metrics, contentWidth)
            val visibleView = currentView ?: return
            bindSidebarItemView(visibleView, items[currentIndex], metrics)
            visibleView.root.translationX = 0f
            visibleView.root.visibility = View.VISIBLE
            if (items.size == 1) {
                container.removeCallbacks(switchRunnable)
                isSwitchScheduled = false
                isAnimating = false
                nextView?.root?.visibility = View.GONE
                return
            }
            val preparedNext = nextView ?: return
            bindSidebarItemView(preparedNext, items[nextIndex()], metrics)
            preparedNext.root.translationX = contentWidth.toFloat()
            preparedNext.root.visibility = View.VISIBLE
            if (!isSwitchScheduled && !isAnimating) {
                scheduleNext()
            }
        }

        fun stop() {
            container.removeCallbacks(switchRunnable)
            isSwitchScheduled = false
            isAnimating = false
            currentView?.root?.animate()?.cancel()
            nextView?.root?.animate()?.cancel()
        }

        private fun ensureViews(metrics: OverlayMetrics, contentWidth: Int) {
            if (currentView != null && nextView != null) {
                updateLayout(contentWidth)
                return
            }
            container.removeAllViews()
            currentView = createSidebarItemView(metrics, contentWidth)
            nextView = createSidebarItemView(metrics, contentWidth)
            container.addView(currentView!!.root)
            container.addView(nextView!!.root)
            updateLayout(contentWidth)
        }

        private fun updateLayout(contentWidth: Int) {
            (container.layoutParams as? LinearLayout.LayoutParams)?.width = contentWidth
            (currentView?.root?.layoutParams as? FrameLayout.LayoutParams)?.width = contentWidth
            (nextView?.root?.layoutParams as? FrameLayout.LayoutParams)?.width = contentWidth
        }

        private fun scheduleNext() {
            isSwitchScheduled = true
            container.postDelayed(switchRunnable, SIDEBAR_SWITCH_INTERVAL_MS)
        }

        private fun animateToNext() {
            val metrics = metrics ?: return
            if (items.size <= 1) return
            isSwitchScheduled = false
            isAnimating = true
            val outgoing = currentView ?: return
            val incoming = nextView ?: return
            val contentWidth = (container.layoutParams as? LinearLayout.LayoutParams)?.width ?: return
            incoming.root.translationX = contentWidth.toFloat()
            incoming.root.visibility = View.VISIBLE
            outgoing.root.animate()
                .translationX(-contentWidth.toFloat())
                .setDuration(SIDEBAR_SWITCH_DURATION_MS)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
            incoming.root.animate()
                .translationX(0f)
                .setDuration(SIDEBAR_SWITCH_DURATION_MS)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    outgoing.root.translationX = contentWidth.toFloat()
                    outgoing.root.visibility = View.VISIBLE
                    currentIndex = nextIndex()
                    currentView = incoming
                    nextView = outgoing
                    bindSidebarItemView(outgoing, items[nextIndex()], metrics)
                    isAnimating = false
                    scheduleNext()
                }
                .start()
        }

        private fun nextIndex(): Int = (currentIndex + 1).mod(items.size)
    }

    private enum class OverlayRenderMode {
        EMPTY,
        EDGE_TAB,
        SIDEBAR,
        STANDARD
    }

    private data class OverlayMetrics(
        val fontScale: Float,
        val edgeTabMode: Boolean,
        val horizontalPaddingPx: Int,
        val verticalPaddingPx: Int,
        val rowVerticalPaddingPx: Int,
        val leadingSpacingPx: Int,
        val leadingWidthExtraPx: Int,
        val priceWidthExtraPx: Int,
        val footerTopPaddingPx: Int,
        val fallbackStrokePx: Int,
        val cornerRadiusPx: Int,
        val iconSizePx: Int,
        val sidebarWidthPx: Int,
        val edgeTabTouchWidthPx: Int,
        val edgeTabTouchHeightPx: Int,
        val edgeTabIndicatorWidthPx: Int,
        val edgeTabIndicatorHeightPx: Int,
        val leadingTextSizeSp: Float,
        val sidebarTextSizeSp: Float,
        val emptyTextSizeSp: Float,
        val footerTextSizeSp: Float
    ) {
        companion object {
            fun from(
                context: Context,
                settings: ArrangedOverlaySettings,
                presentation: OverlayEdgePresentation
            ): OverlayMetrics {
                val fontScale = settings.fontScale.coerceIn(
                    minimumValue = ArrangedOverlaySettings.MIN_FONT_SCALE,
                    maximumValue = ArrangedOverlaySettings.MAX_FONT_SCALE
                )
                val sidebarMode = presentation == OverlayEdgePresentation.DOCKED_CAROUSEL
                val edgeTabMode = presentation == OverlayEdgePresentation.HIDDEN_TAB

                fun scaledDp(baseDp: Int): Int {
                    return (baseDp * context.resources.displayMetrics.density * fontScale).roundToInt()
                }

                return OverlayMetrics(
                    fontScale = fontScale,
                    edgeTabMode = edgeTabMode,
                    horizontalPaddingPx = scaledDp(
                        when {
                            edgeTabMode -> 0
                            sidebarMode -> 6
                            else -> 4
                        }
                    ),
                    verticalPaddingPx = scaledDp(if (edgeTabMode) 0 else 4),
                    rowVerticalPaddingPx = scaledDp(1),
                    leadingSpacingPx = scaledDp(4),
                    leadingWidthExtraPx = scaledDp(1),
                    priceWidthExtraPx = scaledDp(2),
                    footerTopPaddingPx = scaledDp(1),
                    fallbackStrokePx = max(1, scaledDp(1)),
                    cornerRadiusPx = scaledDp(
                        when {
                            edgeTabMode -> 8
                            sidebarMode -> 12
                            else -> 10
                        }
                    ),
                    iconSizePx = scaledDp(11),
                    sidebarWidthPx = max(scaledDp(92), (92 * context.resources.displayMetrics.density).roundToInt()),
                    edgeTabTouchWidthPx = scaledDp(24),
                    edgeTabTouchHeightPx = scaledDp(48),
                    edgeTabIndicatorWidthPx = scaledDp(5),
                    edgeTabIndicatorHeightPx = scaledDp(36),
                    leadingTextSizeSp = 9.5f * fontScale,
                    sidebarTextSizeSp = 10f * fontScale,
                    emptyTextSizeSp = 11f * fontScale,
                    footerTextSizeSp = 8f * fontScale
                )
            }
        }
    }

    private companion object {
        const val HIDDEN_EDGE_AUTO_COLLAPSE_RETRY_MS = 100L
        const val HIDDEN_EDGE_EXPANSION_DURATION_MS = 260L
        const val HIDDEN_EDGE_COLLAPSE_DURATION_MS = 195L
        const val SIDEBAR_SWITCH_INTERVAL_MS = 2200L
        const val SIDEBAR_SWITCH_DURATION_MS = 420L
    }
}
