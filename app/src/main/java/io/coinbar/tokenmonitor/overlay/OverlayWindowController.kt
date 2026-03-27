package io.coinbar.tokenmonitor.overlay

import android.content.Context
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import io.coinbar.tokenmonitor.R
import io.coinbar.tokenmonitor.domain.model.OverlayLeadingDisplayMode
import io.coinbar.tokenmonitor.domain.model.OverlaySettings
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.domain.repository.AppPreferencesRepository
import io.coinbar.tokenmonitor.domain.repository.OverlayRepository
import io.coinbar.tokenmonitor.ui.AppConfigurationApplier
import io.coinbar.tokenmonitor.ui.resolveLivePriceColor
import io.coinbar.tokenmonitor.ui.theme.resolveTokenMonitorColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayWindowController(
    private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val scope: CoroutineScope
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coinIconService = CoinIconService.get(context)

    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var batchCursor: Int = 0
    private var isDragging: Boolean = false
    private var pendingItems: List<WatchItem>? = null
    private var pendingSettings: OverlaySettings? = null
    private val rowHolders = LinkedHashMap<String, RowHolder>()
    private var currentBatchIDs: List<String> = emptyList()
    private var currentLeadingDisplayMode: OverlayLeadingDisplayMode? = null
    private var footerView: TextView? = null
    private var emptyView: TextView? = null

    private val overlayColors
        get() = resolveTokenMonitorColors(context, appPreferencesRepository.getPreferences())

    private val localizedContext: Context
        get() = AppConfigurationApplier.wrapContext(context, appPreferencesRepository.getPreferences())

    fun showOrUpdate(items: List<WatchItem>, settings: OverlaySettings) {
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            hide()
            return
        }

        ensureWindow(settings)
        val root = rootView ?: return
        val params = layoutParams ?: return

        if (isDragging) {
            pendingItems = items
            pendingSettings = settings
            return
        }

        root.background = buildBackground(settings.opacity)
        syncThemeAwareViews()
        val limitedItems = items.take(settings.maxItems)

        if (limitedItems.isEmpty()) {
            showEmptyState(root)
        } else {
            val batch = OverlayBatchPlanner.plan(
                items = limitedItems,
                maxPerPage = 5,
                cursor = batchCursor
            )
            batchCursor = batch.nextCursor
            val symbolColumnWidth = measureLeadingColumnWidth(batch.items, settings.leadingDisplayMode)
            val priceColumnWidth = measurePriceColumnWidth(batch.items)
            renderRows(
                root = root,
                items = batch.items,
                leadingDisplayMode = settings.leadingDisplayMode,
                symbolColumnWidth = symbolColumnWidth,
                priceColumnWidth = priceColumnWidth,
                pageLabel = batch.pageLabel
            )
        }

        applyTouchHandler(root, settings)

        if (root.parent == null) {
            windowManager.addView(root, params)
        } else {
            windowManager.updateViewLayout(root, params)
        }
    }

    fun hide() {
        rootView?.let { view ->
            if (view.parent != null) {
                windowManager.removeView(view)
            }
        }
        rowHolders.clear()
        currentBatchIDs = emptyList()
        currentLeadingDisplayMode = null
        footerView = null
        emptyView = null
        rootView = null
        layoutParams = null
    }

    private fun ensureWindow(settings: OverlaySettings) {
        if (rootView != null && layoutParams != null) return

        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val padding = 4.dp
            setPadding(padding, padding, padding, padding)
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.windowX ?: 48.dp
            y = settings.windowY ?: 180.dp
        }
    }

    private fun buildBackground(opacity: Float): GradientDrawable {
        val colors = overlayColors
        return GradientDrawable().apply {
            cornerRadius = 10.dp.toFloat()
            setColor(colors.overlayBackground.copy(alpha = opacity).toArgb())
            setStroke(1.dp, colors.overlayBorder.toArgb())
        }
    }

    private fun buildEmptyText(): TextView {
        val colors = overlayColors
        return TextView(context).apply {
            text = localizedContext.getString(R.string.overlay_empty_state)
            setTextColor(colors.overlayText.copy(alpha = 0.92f).toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
    }

    private fun buildFooter(): TextView {
        val colors = overlayColors
        return TextView(context).apply {
            setTextColor(colors.overlayMutedText.copy(alpha = 0.88f).toArgb())
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setPadding(0, 2.dp, 0, 0)
        }
    }

    private fun createRowHolder(): RowHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val verticalPadding = 2.dp
            setPadding(0, verticalPadding, 0, verticalPadding)
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
        width: Int
    ): View {
        val colors = overlayColors
        return when (displayMode) {
            OverlayLeadingDisplayMode.ICON -> buildIconView(item, width)

            OverlayLeadingDisplayMode.PAIR_NAME -> TextView(context).apply {
                text = item.symbol.uppercase()
                setTextColor(colors.overlayText.copy(alpha = 0.96f).toArgb())
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also {
                    it.marginEnd = 4.dp
                }
            }
        }
    }

    private fun buildIconView(item: WatchItem, width: Int): View {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(width, width).also {
                it.marginEnd = 4.dp
            }
            background = null

            scope.launch {
                val bitmap = coinIconService.loadBitmap(item.baseSymbol)
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    setImageDrawable(buildDefaultBitcoinDrawable())
                }
            }
        }
    }

    private fun buildDefaultBitcoinDrawable(): GradientDrawable {
        val colors = overlayColors
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.overlayFallbackBackground.toArgb())
            setStroke(1.dp, colors.overlayFallbackBorder.toArgb())
        }
    }

    private fun measureLeadingColumnWidth(
        items: List<WatchItem>,
        displayMode: OverlayLeadingDisplayMode
    ): Int {
        val maxWidth = items.maxOfOrNull { item ->
            when (displayMode) {
                OverlayLeadingDisplayMode.ICON -> 11.dp
                OverlayLeadingDisplayMode.PAIR_NAME -> measureTextWidth(item.symbol.uppercase(), 9.5f)
            }
        } ?: 0
        return maxWidth + 1.dp
    }

    private fun measurePriceColumnWidth(items: List<WatchItem>): Int {
        val maxWidth = items.maxOfOrNull { item ->
            val priceText = QuoteFormatter.formatPrice(item.lastPrice)
            val textSizeSp = PriceTextSizer.resolveTextSizeSp(priceText)
            measureTextWidth(priceText, textSizeSp)
        } ?: 0
        return maxWidth + 2.dp
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

    private fun applyTouchHandler(root: View, settings: OverlaySettings) {
        if (settings.locked) {
            root.setOnTouchListener(null)
            return
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            private var touchOffsetX: Float = 0f
            private var touchOffsetY: Float = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        touchOffsetX = event.rawX - params.x
                        touchOffsetY = event.rawY - params.y
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = max(0, (event.rawX - touchOffsetX).roundToInt())
                        params.y = max(0, (event.rawY - touchOffsetY).roundToInt())
                        windowManager.updateViewLayout(view, params)
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        scope.launch {
                            overlayRepository.setWindowPosition(params.x, params.y)
                        }
                        val latestItems = pendingItems
                        val latestSettings = pendingSettings
                        pendingItems = null
                        pendingSettings = null
                        if (latestItems != null && latestSettings != null) {
                            showOrUpdate(latestItems, latestSettings)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).roundToInt()

    private fun showEmptyState(root: LinearLayout) {
        if (emptyView == null) {
            emptyView = buildEmptyText()
        }
        rowHolders.values.forEach { holder ->
            holder.row.visibility = GONE
        }
        footerView?.visibility = GONE
        val view = emptyView ?: return
        if (view.parent == null) {
            root.addView(view)
        }
        view.visibility = VISIBLE
        currentBatchIDs = emptyList()
        currentLeadingDisplayMode = null
    }

    private fun renderRows(
        root: LinearLayout,
        items: List<WatchItem>,
        leadingDisplayMode: OverlayLeadingDisplayMode,
        symbolColumnWidth: Int,
        priceColumnWidth: Int,
        pageLabel: String?
    ) {
        emptyView?.visibility = GONE
        val batchIDs = items.map { it.id }
        val layoutChanged = batchIDs != currentBatchIDs || currentLeadingDisplayMode != leadingDisplayMode

        if (layoutChanged) {
            root.removeAllViews()
            items.forEach { item ->
                val holder = rowHolders.getOrPut(item.id) { createRowHolder() }
                updateRowHolder(holder, item, leadingDisplayMode, symbolColumnWidth, priceColumnWidth)
                root.addView(holder.row)
            }

            if (pageLabel != null) {
                val footer = footerView ?: buildFooter().also { footerView = it }
                footer.text = localizedContext.getString(R.string.overlay_page_format, pageLabel)
                footer.visibility = VISIBLE
                root.addView(footer)
            } else {
                footerView?.visibility = GONE
            }
        } else {
            items.forEach { item ->
                val holder = rowHolders[item.id] ?: return@forEach
                updateRowHolder(holder, item, leadingDisplayMode, symbolColumnWidth, priceColumnWidth)
                holder.row.visibility = VISIBLE
            }
            if (pageLabel != null) {
                val footer = footerView ?: buildFooter().also { footerView = it }
                footer.text = localizedContext.getString(R.string.overlay_page_format, pageLabel)
                footer.visibility = VISIBLE
                if (footer.parent == null) {
                    root.addView(footer)
                }
            } else {
                footerView?.visibility = GONE
            }
        }

        rowHolders.forEach { (id, holder) ->
            if (batchIDs.contains(id).not()) {
                holder.row.visibility = GONE
            }
        }

        currentBatchIDs = batchIDs
        currentLeadingDisplayMode = leadingDisplayMode
    }

    private fun updateRowHolder(
        holder: RowHolder,
        item: WatchItem,
        leadingDisplayMode: OverlayLeadingDisplayMode,
        symbolColumnWidth: Int,
        priceColumnWidth: Int
    ) {
        holder.row.visibility = VISIBLE
        val colors = overlayColors
        val leadingSignature = "${leadingDisplayMode.name}:${item.symbol}:${item.baseSymbol}:$symbolColumnWidth:${colors.overlayText.toArgb()}:${colors.overlayFallbackBorder.toArgb()}"
        if (holder.leadingSignature != leadingSignature) {
            holder.leadingContainer.removeAllViews()
            val leadingView = buildLeadingView(item, leadingDisplayMode, symbolColumnWidth)
            holder.leadingContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            holder.leadingContainer.addView(leadingView)
            holder.leadingSignature = leadingSignature
        }

        val priceText = QuoteFormatter.formatPrice(item.lastPrice)
        val priceTextSizeSp = PriceTextSizer.resolveTextSizeSp(priceText)
        holder.priceView.text = priceText
        holder.priceView.setTextColor(
            item.resolveLivePriceColor(
                colors = colors,
                defaultColor = colors.overlayText
            ).toArgb()
        )
        holder.priceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, priceTextSizeSp)
        holder.priceView.layoutParams = LinearLayout.LayoutParams(
            priceColumnWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun syncThemeAwareViews() {
        val colors = overlayColors
        emptyView?.setTextColor(colors.overlayText.copy(alpha = 0.92f).toArgb())
        footerView?.setTextColor(colors.overlayMutedText.copy(alpha = 0.88f).toArgb())
    }

    private data class RowHolder(
        val row: LinearLayout,
        val leadingContainer: LinearLayout,
        val priceView: TextView,
        var leadingSignature: String? = null
    )
}
