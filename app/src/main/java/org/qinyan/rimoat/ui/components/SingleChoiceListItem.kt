package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
