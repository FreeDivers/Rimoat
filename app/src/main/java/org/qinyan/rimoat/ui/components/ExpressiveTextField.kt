package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

/** True inside modal dialogs so tonal fields pick a container with crisp contrast against dialog surfaces. */
internal val LocalExpressiveDialogSurface = compositionLocalOf { false }

/**
 * MD3 Expressive tonal color palette for [ExpressiveTextField].
 * Uses [TextFieldDefaults.tonalColors] (transparent bottom indicators) while adapting the container
 * surface to form cards vs modal dialogs in both light, dark, and AMOLED themes.
 */
@Composable
fun expressiveTextFieldColors(): TextFieldColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = ExpressiveSurface.isDark
    val inDialog = LocalExpressiveDialogSurface.current
    val unfocusedContainer = when {
        inDialog && isDark -> colorScheme.surfaceContainerLow
        inDialog -> colorScheme.surfaceContainerLowest
        isDark -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surfaceContainer
    }
    val focusedContainer = lerp(
        unfocusedContainer,
        colorScheme.primaryContainer,
        if (isDark) 0.35f else 0.30f
    )
    return TextFieldDefaults.tonalColors().copy(
        focusedContainerColor = focusedContainer,
        unfocusedContainerColor = unfocusedContainer,
        disabledContainerColor = unfocusedContainer.copy(alpha = 0.38f),
        focusedLabelColor = colorScheme.primary,
        focusedLeadingIconColor = colorScheme.primary,
        errorTextColor = colorScheme.onErrorContainer,
        errorSupportingTextColor = colorScheme.error
    )
}

/**
 * Shared MD3 Expressive text field used across forms and modal dialogs.
 * Uses [TextFieldDefaults.roundedShape], interior floating labels, and borderless tonal containers.
 */
@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.roundedShape,
    colors: TextFieldColors = expressiveTextFieldColors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        shape = shape,
        colors = colors
    )
}
