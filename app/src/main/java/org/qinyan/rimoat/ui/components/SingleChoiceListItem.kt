package org.qinyan.rimoat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SingleChoiceListItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    index: Int = 0,
    count: Int = 1
) {
    ExpressiveSingleChoiceRow(
        title = title,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        index = index,
        count = count,
        description = description,
        enabled = enabled,
        icon = icon
    )
}
