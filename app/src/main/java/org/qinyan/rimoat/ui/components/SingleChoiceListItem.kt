package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

@Composable
fun SingleChoiceListItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    ListItem(
        modifier = modifier.clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    )
}
