package io.baiyanwu.coinmonitor.clipboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private fun ClipboardSettingsScreen(store: ClipboardSettingsStore, onBack: () -> Unit) {
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
                Text(position.toString(), Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(site.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text(stringResource(R.string.clipboard_edit), style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text(stringResource(R.string.clipboard_delete), style = MaterialTheme.typography.labelMedium) }
                Switch(checked = site.enabled, onCheckedChange = onEnabledChange)
                Box(
                    modifier = Modifier.size(40.dp)
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
    var name by remember { mutableStateOf(site.name) }
    var template by remember { mutableStateOf(site.template) }
    var mapping by remember { mutableStateOf(site.chainValues.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    var templates by remember { mutableStateOf(site.chainTemplates.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    val candidate = runCatching { site.copy(name = name.trim(), template = template.trim(),
        chainValues = clipboardMapping(mapping), chainTemplates = clipboardMapping(templates)) }.getOrNull()
    val valid = candidate != null && validClipboardDestination(candidate)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.clipboard_edit_site)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.clipboard_site_name)) }, singleLine = true)
            OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text(stringResource(R.string.clipboard_site_template)) })
            Text(stringResource(R.string.clipboard_template_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = mapping, onValueChange = { mapping = it }, label = { Text(stringResource(R.string.clipboard_chain_mapping)) })
            OutlinedTextField(value = templates, onValueChange = { templates = it }, label = { Text(stringResource(R.string.clipboard_chain_templates)) })
            Text(stringResource(R.string.clipboard_mapping_hint), style = MaterialTheme.typography.bodySmall)
            if (!valid) Text(stringResource(R.string.clipboard_invalid_site), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = valid, onClick = { candidate?.let(onSave) }) { Text(stringResource(R.string.clipboard_save)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.clipboard_cancel)) } })
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
        (if (site.template.isNotBlank()) validTemplate(site.template) else site.id == "dex" || site.chainTemplates.isNotEmpty())
}
