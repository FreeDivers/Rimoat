@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.R

@Composable
internal fun EmptyStateView(
    isSearch: Boolean,
    onResetSearch: () -> Unit,
    onAddRemote: () -> Unit,
    hasSearchQuery: Boolean,
    actionsEnabled: Boolean,
    showAddAction: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.DevicesOther,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.large))
        Text(
            text = if (isSearch) stringResource(if (hasSearchQuery) R.string.screen_search_empty else R.string.screen_filter_empty) else stringResource(R.string.screen_no_saved_remotes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.large))
        if (isSearch) {
            TextButton(onClick = onResetSearch, enabled = actionsEnabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (hasSearchQuery) R.string.screen_clear_search else R.string.screen_reset_filters))
            }
        } else if (showAddAction) {
            androidx.compose.material3.Button(onClick = onAddRemote, enabled = actionsEnabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.screen_add_remote))
            }
        }
    }
}
