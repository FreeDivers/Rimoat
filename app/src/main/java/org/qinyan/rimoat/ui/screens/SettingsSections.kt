@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.ExpressiveShapeTokens
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp

internal class SettingsSectionScope {
    val items = mutableListOf<@Composable (index: Int, count: Int) -> Unit>()
    fun item(content: @Composable (index: Int, count: Int) -> Unit) {
        items += content
    }
}

@Composable
internal fun SettingsSection(title: String, block: SettingsSectionScope.() -> Unit) {
    val scope = SettingsSectionScope().apply(block)
    val count = scope.items.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            Modifier.padding(horizontal = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)
        ) {
            scope.items.forEachIndexed { index, render ->
                render(index, count)
            }
        }
    }
}
