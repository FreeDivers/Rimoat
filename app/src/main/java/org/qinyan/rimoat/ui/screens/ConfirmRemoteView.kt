@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.material3.ButtonDefaults
import org.qinyan.rimoat.ui.components.getCategoryIcon
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import org.qinyan.rimoat.ui.components.ExpressiveTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

// ---------------------------------------------------------------------------
// Step 4: Name Customization & Save Confirmation
// ---------------------------------------------------------------------------

@Composable
fun ConfirmRemoteView(
    state: AddRemoteUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val model = state.currentRemote
    val icon = getCategoryIcon(state.selectedCategory?.code.orEmpty())
    val name = state.defaultRemoteName?.asString()?.take(100) ?: state.customRemoteName
    val canSave = name.isNotBlank() && !state.isLoading && !state.isSaving

    val wide = rememberWideLayout()
    val primaryScroll = rememberScrollState()
    val secondaryScroll = rememberScrollState()
    val summary: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.page))

        val brandLabel = model?.brandDisplayName(context)?.takeIf(String::isNotBlank)
        val categoryLabel = state.selectedCategory?.displayName(context)?.takeIf(String::isNotBlank)
        val codeNumber = state.currentRemoteIndex + 1
        val summaryText = when {
            brandLabel != null && categoryLabel != null ->
                stringResource(R.string.screen_save_summary, brandLabel, categoryLabel, codeNumber)
            brandLabel != null ->
                stringResource(R.string.screen_save_summary_short, brandLabel, codeNumber)
            categoryLabel != null ->
                stringResource(R.string.screen_save_summary_short, categoryLabel, codeNumber)
            else -> stringResource(R.string.screen_code_number, codeNumber)
        }
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.section))

    }
    val form: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExpressiveTextField(
                    value = name,
                    onValueChange = onNameChange,
                    enabled = !state.isSaving,
                    label = { Text(stringResource(R.string.screen_remote_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        if (canSave) onSave()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                state.error?.let {
                    Text(
                        it.asString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes()
        ) {
            if (state.isSaving) {
                LoadingIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = stringResource(R.string.screen_saving),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = stringResource(R.string.screen_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    if (wide) {
        TwoPaneLayout(
            modifier = modifier.fillMaxSize().imePadding(), primaryFraction = .4f,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(primaryScroll).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) { summary() }
            },
            secondary = {
                Column(Modifier.fillMaxSize().verticalScroll(secondaryScroll).padding(8.dp)) { form() }
            }
        )
    } else {
        Column(modifier.fillMaxSize().imePadding().verticalScroll(secondaryScroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) { summary(); form() }
    }
}

internal fun AddRemoteUiState.displayRemoteName(context: android.content.Context): String =
    defaultRemoteName?.resolve(context)?.take(100) ?: customRemoteName
