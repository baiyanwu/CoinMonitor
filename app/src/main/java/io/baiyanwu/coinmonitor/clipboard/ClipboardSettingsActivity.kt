package io.baiyanwu.coinmonitor.clipboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ChevronRight
import java.util.UUID

class ClipboardSettingsActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container -> ClipboardSettingsScreen(container.clipboardSettingsStore, ::finish) }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ClipboardSettingsActivity::class.java)
            if (context is Activity) {
                DetailPageTransitions.start(context, intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardSettingsScreen(store: ClipboardSettingsStore, onBack: () -> Unit) = CoinMonitorDesignTheme {
    val colors = CoinMonitorThemeTokens.colors
    val settings by store.settings.collectAsState()
    var editing by remember { mutableStateOf<ClipboardDestination?>(null) }
    var isDraggingSite by remember { mutableStateOf(false) }
    Scaffold(containerColor = colors.pageBackground, topBar = {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.pageBackground),
            title = { Text(stringResource(R.string.clipboard_settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.clipboard_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState(), enabled = !isDraggingSite)
            .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    ClipboardSwitch(stringResource(R.string.clipboard_enabled), settings.enabled) { value ->
                        store.update { it.copy(enabled = value) }
                    }
                    Text(stringResource(R.string.clipboard_enabled_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.clipboard_sites), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.clipboard_sites_hint), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { editing = ClipboardDestination(UUID.randomUUID().toString(), "", "https://") }) {
                    Text(stringResource(R.string.clipboard_new_site))
                }
                TextButton(onClick = { store.update { current ->
                    val defaults = clipboardDestinations()
                    current.copy(destinations = defaults + current.destinations.filter { site -> defaults.none { it.id == site.id } })
                } }) { Text(stringResource(R.string.clipboard_restore)) }
            }
            ReorderableClipboardDestinations(
                sites = settings.destinations,
                onOrderChange = { ordered -> store.update { it.copy(destinations = ordered) } },
                onDraggingChange = { isDraggingSite = it },
                onEnabledChange = { site, enabled -> store.update { current ->
                    current.copy(destinations = current.destinations.map { if (it.id == site.id) it.copy(enabled = enabled) else it })
                } },
                onEdit = { editing = it },
                onDelete = { site -> store.update { current ->
                    current.copy(destinations = current.destinations.filterNot { it.id == site.id })
                } }
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    var question by remember(settings.aiQuestion) { mutableStateOf(settings.aiQuestion.orEmpty()) }
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text(stringResource(R.string.clipboard_ai_question)) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { store.update { it.copy(aiQuestion = question) } }) { Text(stringResource(R.string.clipboard_save)) }
                    TextButton(onClick = { store.update { it.copy(aiQuestion = null) } }) { Text(stringResource(R.string.clipboard_ai_restore)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    editing?.let { site ->
        key(site.id) {
            ClipboardDestinationEditor(site, onDismiss = { editing = null }) { result ->
                store.update { current ->
                    current.copy(destinations = if (current.destinations.any { it.id == result.id })
                        current.destinations.map { if (it.id == result.id) result else it }
                    else current.destinations + result)
                }
                editing = null
            }
        }
    }
}

@Composable
private fun ClipboardSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReorderableClipboardDestinations(
    sites: List<ClipboardDestination>,
    onOrderChange: (List<ClipboardDestination>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onEnabledChange: (ClipboardDestination, Boolean) -> Unit,
    onEdit: (ClipboardDestination) -> Unit,
    onDelete: (ClipboardDestination) -> Unit
) {
    var draggedSites by remember { mutableStateOf<List<ClipboardDestination>?>(null) }
    var dragState by remember { mutableStateOf<ClipboardSiteDragState?>(null) }
    var pendingOrderIds by remember { mutableStateOf<List<String>?>(null) }
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    val displaySites = draggedSites ?: sites
    val upstreamOrderIds = sites.map(ClipboardDestination::id)

    LaunchedEffect(upstreamOrderIds, pendingOrderIds) {
        val pendingIds = pendingOrderIds ?: return@LaunchedEffect
        if (upstreamOrderIds == pendingIds || upstreamOrderIds.toSet() != pendingIds.toSet()) {
            draggedSites = null
            pendingOrderIds = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        displaySites.forEachIndexed { index, site ->
            key(site.id) {
                val isDragging = dragState?.siteId == site.id
                ClipboardDestinationRow(
                    site = site,
                    position = index + 1,
                    dragOffsetY = if (isDragging) dragState?.dragOffsetY ?: 0f else 0f,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .onGloballyPositioned { coordinates ->
                            if (!isDragging) itemBounds[site.id] = coordinates.boundsInParent()
                        },
                    onEnabledChange = { onEnabledChange(site, it) },
                    onEdit = { onEdit(site) },
                    onDelete = { onDelete(site) },
                    onDragStart = {
                        draggedSites = displaySites
                        pendingOrderIds = null
                        dragState = ClipboardSiteDragState(site.id)
                        onDraggingChange(true)
                    },
                    onDragBy = { amount ->
                        val current = dragState
                        if (current != null && current.siteId == site.id) {
                            var updated = current.copy(dragOffsetY = current.dragOffsetY + amount)
                            var reordered = draggedSites ?: sites
                            var draggedBounds = itemBounds[site.id]
                            val direction = amount.compareTo(0f)
                            var remainingSwaps = reordered.lastIndex
                            while (draggedBounds != null && remainingSwaps > 0) {
                                val from = reordered.indexOfFirst { it.id == site.id }
                                if (from < 0) break
                                val centerY = draggedBounds.center.y + updated.dragOffsetY
                                val targetSite = when {
                                    direction < 0 -> reordered.getOrNull(from - 1)
                                    direction > 0 -> reordered.getOrNull(from + 1)
                                    else -> null
                                } ?: break
                                val targetBounds = itemBounds[targetSite.id] ?: break
                                val crossed = if (direction < 0) centerY < targetBounds.center.y else centerY > targetBounds.center.y
                                if (!crossed) break
                                val targetIndex = reordered.indexOfFirst { it.id == targetSite.id }
                                reordered = reordered.toMutableList().apply { add(targetIndex, removeAt(from)) }
                                itemBounds[site.id] = targetBounds
                                itemBounds[targetSite.id] = draggedBounds
                                updated = updated.copy(
                                    dragOffsetY = updated.dragOffsetY - (targetBounds.top - draggedBounds.top),
                                    didReorder = true
                                )
                                draggedBounds = targetBounds
                                remainingSwaps -= 1
                            }
                            draggedSites = reordered
                            dragState = updated
                        }
                    },
                    onDragEnd = {
                        val finalSites = draggedSites ?: sites
                        val changed = dragState?.didReorder == true
                        dragState = null
                        if (changed) {
                            draggedSites = finalSites
                            pendingOrderIds = finalSites.map(ClipboardDestination::id)
                            onOrderChange(finalSites)
                        } else {
                            draggedSites = null
                            pendingOrderIds = null
                        }
                        onDraggingChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClipboardDestinationRow(
    site: ClipboardDestination,
    position: Int,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val dragDescription = stringResource(R.string.clipboard_drag_handle_description, site.name)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragBy by rememberUpdatedState(onDragBy)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Card(modifier.fillMaxWidth().graphicsLayer { translationY = dragOffsetY }) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClipboardSiteIcon(site.id, iconUrl = site.iconUrl)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.clipboard_site_network_count, (site.chainValues.keys + site.chainTemplates.keys).size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.clipboard_edit), Modifier.size(18.dp)) }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.clipboard_more), Modifier.size(18.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.clipboard_delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
                Switch(checked = site.enabled, onCheckedChange = onEnabledChange)
                Box(
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = dragDescription }
                        .pointerInput(site.id) {
                            awaitEachGesture {
                                val down = awaitClipboardDragDown()
                                down.consume()
                                currentOnDragStart()
                                try {
                                    var change = down
                                    while (change.pressed) {
                                        val event = awaitPointerEvent()
                                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val amount = change.position.y - change.previousPosition.y
                                        if (amount != 0f) {
                                            change.consume()
                                            currentOnDragBy(amount)
                                        }
                                    }
                                } finally {
                                    currentOnDragEnd()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class ClipboardSiteDragState(
    val siteId: String,
    val dragOffsetY: Float = 0f,
    val didReorder: Boolean = false
)

private suspend fun AwaitPointerEventScope.awaitClipboardDragDown(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.firstOrNull { it.pressed }?.let { return it }
    }
}

@Composable
private fun ClipboardDestinationEditor(site: ClipboardDestination, onDismiss: () -> Unit, onSave: (ClipboardDestination) -> Unit) {
    val sample = "0x0000000000000000000000000000000000000001"
    var source by remember { mutableStateOf(site.sourceUrl ?: site.template.ifBlank { site.chainTemplates.values.firstOrNull().orEmpty() }
        .replace("{ca}", sample).replace("{chain}", site.chainValues.values.firstOrNull() ?: "ethereum")) }
    var draft by remember { mutableStateOf(site.copy(sourceUrl = site.sourceUrl ?: source.takeIf { safeClipboardUrl(it) != null })) }
    var valid by remember { mutableStateOf(validClipboardDestination(site)) }
    var nameEdited by remember { mutableStateOf(site.name.isNotBlank()) }
    var outcome by remember { mutableStateOf<ClipboardWebsiteRecognition.Outcome?>(null) }
    var customNetwork by remember { mutableStateOf("") }
    val recognize: (String?) -> Unit = { selected ->
        val result = ClipboardWebsiteRecognition.recognize(source, draft.copy(name = if (nameEdited) draft.name else ""), selected)
        outcome = result
        if (result is ClipboardWebsiteRecognition.Outcome.Recognized) { draft = result.result.destination; valid = true }
        else valid = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.clipboard_edit_site), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactWebsiteField(source, { source = it; valid = false; outcome = null },
                                stringResource(R.string.clipboard_site_source_link), Modifier.weight(1f))
                            TextButton(onClick = { recognize(null) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(R.string.clipboard_site_recognize), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        when (val result = outcome) {
                            ClipboardWebsiteRecognition.Outcome.Invalid -> Text(stringResource(R.string.clipboard_site_invalid_link),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            ClipboardWebsiteRecognition.Outcome.NoAddress -> Text(stringResource(R.string.clipboard_site_no_address),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            is ClipboardWebsiteRecognition.Outcome.Ambiguous -> {
                                Text(stringResource(R.string.clipboard_site_choose_address), style = MaterialTheme.typography.bodySmall)
                                result.addresses.forEach { address -> TextButton(onClick = { recognize(address) }) { Text(shortClipboardAddress(address)) } }
                            }
                            else -> Unit
                        }
                        if (valid) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ClipboardSiteIcon(draft.id, iconUrl = draft.iconUrl, size = 24.dp)
                                CompactWebsiteField(draft.name, { draft = draft.copy(name = it); nameEdited = true },
                                    stringResource(R.string.clipboard_site_name), Modifier.weight(1f))
                            }
                            val perChain = draft.template.isBlank() && (draft.chainTemplates.isNotEmpty() || draft.removedNetworkRules.isNotEmpty())
                            if (draft.template.contains("{chain}") || perChain) {
                                Text(stringResource(R.string.clipboard_site_network_rules), style = MaterialTheme.typography.labelLarge)
                                val rows = if (perChain) draft.chainTemplates else draft.chainValues
                                rows.forEach { (chain, mapped) ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(chain, Modifier.width(76.dp), style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        CompactWebsiteField(
                                            if (perChain) mapped.replace("{ca}", ClipboardAddressParser.extract(source).firstOrNull() ?: sample) else mapped,
                                            { value ->
                                                if (perChain) {
                                                    val ca = ClipboardAddressParser.extract(value).singleOrNull()
                                                    draft = draft.copy(chainTemplates = draft.chainTemplates + (chain to if (ca != null) value.replace(ca, "{ca}") else value), removedNetworkRules = draft.removedNetworkRules - chain)
                                                } else draft = draft.copy(chainValues = draft.chainValues + (chain to value), removedNetworkRules = draft.removedNetworkRules - chain)
                                            }, stringResource(if (perChain) R.string.clipboard_site_source_link else R.string.clipboard_site_route_name),
                                            Modifier.weight(1f), showLabel = false, descriptionPrefix = chain)
                                        IconButton(onClick = {
                                            draft = if (perChain) draft.copy(chainTemplates = draft.chainTemplates - chain, removedNetworkRules = draft.removedNetworkRules + chain)
                                                else draft.copy(chainValues = draft.chainValues - chain, removedNetworkRules = draft.removedNetworkRules + chain)
                                        }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Rounded.DeleteOutline, "$chain · ${stringResource(R.string.clipboard_delete)}", Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CompactWebsiteField(customNetwork, { customNetwork = it }, stringResource(R.string.clipboard_site_new_network), Modifier.weight(1f))
                                    IconButton(enabled = customNetwork.trim().matches(Regex("[a-z0-9_-]+")) && customNetwork.trim() !in rows,
                                        onClick = {
                                            val key = customNetwork.trim()
                                            draft = if (perChain) draft.copy(chainTemplates = draft.chainTemplates + (key to ""), removedNetworkRules = draft.removedNetworkRules - key)
                                                else draft.copy(chainValues = draft.chainValues + (key to key), removedNetworkRules = draft.removedNetworkRules - key)
                                            customNetwork = ""
                                        }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Rounded.Add, stringResource(R.string.clipboard_site_add_network), Modifier.size(20.dp))
                                    }
                                }
                            }
                            Text(stringResource(R.string.clipboard_site_preview), style = MaterialTheme.typography.labelMedium)
                            Text((if (draft.template.isNotBlank()) draft.template else draft.chainTemplates.values.firstOrNull().orEmpty())
                                .replace("{ca}", ClipboardAddressParser.extract(source).firstOrNull() ?: sample)
                                .replace("{chain}", draft.chainValues.values.firstOrNull().orEmpty()),
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.clipboard_cancel)) }
                        TextButton(enabled = valid && validClipboardDestination(draft), onClick = { onSave(draft.copy(name = draft.name.trim())) }) {
                            Text(stringResource(R.string.clipboard_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWebsiteField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, showLabel: Boolean = true, descriptionPrefix: String = "") {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(value, onValueChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = listOf(descriptionPrefix, label).filter(String::isNotBlank).joinToString(" · ") }
            .border(1.dp, if (focused) colors.primary else colors.outlineVariant, RoundedCornerShape(8.dp)),
        decorationBox = { input ->
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                if (showLabel) Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    if (value.isEmpty() && !showLabel) Text(label, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    input()
                }
            }
        })
}

internal fun clipboardMapping(value: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    value.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val key = line.substringBefore('=').trim()
        val mapped = line.substringAfter('=', "").trim()
        require(key.matches(Regex("[a-z0-9_-]+")) && mapped.isNotEmpty() && key !in result)
        result[key] = mapped
    }
    return result
}

internal fun validClipboardDestination(site: ClipboardDestination): Boolean {
    fun validTemplate(value: String): Boolean = safeClipboardUrl(value.replace("{ca}", "sample").replace("{chain}", "ethereum")) != null
    return site.name.isNotBlank() && site.chainValues.values.all { it.matches(Regex("[A-Za-z0-9_-]+")) } &&
        site.chainTemplates.values.all(::validTemplate) &&
        (if (site.template.isNotBlank()) validTemplate(site.template) else site.id == "dex" || site.chainTemplates.isNotEmpty() || site.removedNetworkRules.isNotEmpty() && safeClipboardUrl(site.sourceUrl) != null)
}
