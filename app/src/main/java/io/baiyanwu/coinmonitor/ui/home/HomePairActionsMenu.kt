package io.baiyanwu.coinmonitor.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private val PairActionsInset = 16.dp
private val PairActionsButtonSize = 56.dp
internal val HomePairActionsContentPadding = PairActionsInset + PairActionsButtonSize + 24.dp

/** FAB Menu presentation built with the project's existing Material 3 components. */
@Composable
internal fun HomePairActionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddPair: () -> Unit,
    onConfigureOverlayItems: () -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val colors = CoinMonitorThemeTokens.colors
    val menuLabel = stringResource(R.string.home_pair_actions)
    val closeLabel = stringResource(R.string.home_pair_actions_close)
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    val transition = rememberTransition(visibleState, label = "home-pair-actions")
    val progress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 180, easing = FastOutSlowInEasing) },
        label = "menu-progress"
    ) { visible -> if (visible) 1f else 0f }

    BoxWithConstraints(modifier = modifier) {
        val contentWidth = maxWidth
        val contentHeight = maxHeight

        FloatingActionButton(
            onClick = { onExpandedChange(true) },
            containerColor = colors.fabContainer,
            contentColor = colors.fabContent,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    start = PairActionsInset,
                    top = PairActionsInset,
                    end = PairActionsInset,
                    bottom = PairActionsInset + bottomInset
                )
                .size(PairActionsButtonSize)
                .testTag("home-pair-actions-trigger")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = menuLabel,
                modifier = Modifier.size(24.dp)
            )
        }

        // Match the home content bounds so the close button stays at the trigger's position.
        // A focusable popup owns Back and consumes outside taps, including on the bottom bar.
        if (visibleState.currentState || visibleState.targetState) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    excludeFromSystemGesture = false
                )
            ) {
                Box(modifier = Modifier.size(contentWidth, contentHeight)) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f * progress))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = closeLabel,
                                onClick = { onExpandedChange(false) }
                            )
                            .semantics { contentDescription = closeLabel }
                            .testTag("home-pair-actions-scrim")
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                start = PairActionsInset,
                                top = PairActionsInset,
                                end = PairActionsInset,
                                bottom = PairActionsInset + bottomInset
                            )
                            .semantics {
                                paneTitle = menuLabel
                                isTraversalGroup = true
                            },
                        horizontalAlignment = Alignment.End
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = (contentWidth - PairActionsInset * 2).coerceAtLeast(0.dp))
                                .width(IntrinsicSize.Max)
                                .heightIn(
                                    max = (contentHeight - PairActionsButtonSize - PairActionsInset * 3 - bottomInset)
                                        .coerceAtLeast(0.dp)
                                )
                                .graphicsLayer {
                                    alpha = progress
                                    translationY = 12.dp.toPx() * (1f - progress)
                                }
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomePairActionItem(
                                label = stringResource(R.string.overlay_items_settings_title),
                                icon = Icons.Rounded.Reorder,
                                enabled = expanded,
                                onClick = onConfigureOverlayItems,
                                modifier = Modifier.testTag("home-pair-actions-overlay")
                            )
                            HomePairActionItem(
                                label = stringResource(R.string.home_add_pair),
                                icon = Icons.Rounded.Add,
                                enabled = expanded,
                                onClick = onAddPair,
                                modifier = Modifier.testTag("home-pair-actions-add")
                            )
                        }
                        Spacer(modifier = Modifier.height(PairActionsInset))
                        FloatingActionButton(
                            onClick = { onExpandedChange(false) },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = lerp(colors.fabContainer, colors.heroBackground, progress),
                            contentColor = lerp(colors.fabContent, colors.accent, progress),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .size(PairActionsButtonSize)
                                .testTag("home-pair-actions-close")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).graphicsLayer { alpha = 1f - progress }
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = closeLabel,
                                    modifier = Modifier.size(24.dp).graphicsLayer {
                                        alpha = progress
                                        rotationZ = -45f * (1f - progress)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePairActionItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = colors.fabContainer,
        contentColor = colors.fabContent,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 172.dp)
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}
