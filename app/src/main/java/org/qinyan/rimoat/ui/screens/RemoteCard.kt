@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import org.qinyan.rimoat.ui.components.getCategoryIcon
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.components.rememberSegmentedExpressiveShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenuGroup
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenuPopup
import org.qinyan.rimoat.ui.components.ExpressiveMenuItem
import androidx.compose.material3.Icon
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteProfile

@Composable
fun RemoteCard(
    remote: RemoteProfile,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onExport: () -> Unit = {},
    index: Int = 0,
    count: Int = 1
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = rememberSegmentedExpressiveShape(interactionSource, index, count)
    var showActions by remember { mutableStateOf(false) }
    var actionAfterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeWithAction: (() -> Unit) -> Unit = { action ->
        if (showActions) {
            actionAfterDismiss = action
            showActions = false
        }
    }
    val visibleBrand = remote.brand.takeIf { it.isNotBlank() && !remote.name.contains(it, ignoreCase = true) }
    val subtitle = when {
        remote.room.isNotBlank() && visibleBrand != null -> stringResource(R.string.screen_remote_room_brand, remote.room, visibleBrand)
        remote.room.isNotBlank() -> remote.room
        else -> visibleBrand.orEmpty()
    }
    Card(
        onClick = onClick,
        shape = cardShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = ExpressiveSurface.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp,
            focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (remote.isFavorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getCategoryIcon(remote),
                        contentDescription = null,
                        tint = if (remote.isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.large))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = remote.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    softWrap = true
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                }
            }

            // Keep the title column stable when the favorite state changes.
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (remote.isFavorite) Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.screen_favorited),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                ActionIconButton(stringResource(R.string.screen_manage_name, remote.name), onClick = { showActions = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.screen_manage_name, remote.name))
                }
                ExpressiveDropdownMenuPopup(
                    expanded = LocalMotionActive.current && showActions,
                    onDismissRequest = { showActions = false }
                ) {
                    // DropdownMenuPopup keeps its content alive for the exit transition.
                    // Apply mutations only after it leaves composition: reordering a
                    // favorite must not move the anchor or change a fading menu's text.
                    DisposableEffect(Unit) {
                        onDispose {
                            val action = actionAfterDismiss
                            actionAfterDismiss = null
                            action?.invoke()
                        }
                    }
                    ExpressiveDropdownMenuGroup(index = 0, count = 2) {
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onToggleFavorite) },
                            text = { Text(if (remote.isFavorite) stringResource(R.string.screen_unfavorite) else stringResource(R.string.screen_favorite)) },
                            index = 0,
                            count = 3,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (remote.isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                    contentDescription = null
                                )
                            }
                        )
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onEdit) },
                            text = { Text(stringResource(R.string.screen_edit_details)) },
                            index = 1,
                            count = 3,
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        )
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onExport) },
                            text = { Text(stringResource(R.string.screen_export_remote)) },
                            index = 2,
                            count = 3,
                            leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) }
                        )
                    }
                    ExpressiveDropdownMenuGroup(index = 1, count = 2) {
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onDelete) },
                            text = { Text(stringResource(R.string.screen_trash_remote)) },
                            index = 0,
                            count = 1,
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}
