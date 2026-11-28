package org.qinyan.rimoat.widget

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.layout.*
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.controlAppearance

@Composable
internal fun KeyRow(id: Int, shortcuts: List<WidgetShortcut>, state: WidgetSnapshot, busy: Boolean,
                   modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEachIndexed { index, shortcut ->
            if (index > 0) Spacer(GlanceModifier.width(WidgetLayoutSpec.KeyGap))
            ShortcutIcon(id, shortcut, state, busy)
        }
    }
}

private fun shortcutAction(shortcut: WidgetShortcut): Action = actionRunCallback<SendWidgetCommand>(
    actionParametersOf(SendWidgetCommand.remoteKey to shortcut.remoteId, SendWidgetCommand.buttonKey to shortcut.buttonId))

@Composable
internal fun ShortcutIcon(id: Int, shortcut: WidgetShortcut, state: WidgetSnapshot, busy: Boolean,
                         primary: Boolean = false, size: androidx.compose.ui.unit.Dp = WidgetLayoutSpec.MinimumTouchSize) {
    val context = LocalContext.current
    val command = state.commands[shortcut.remoteId].orEmpty().firstOrNull { it.id == shortcut.buttonId }
    val profile = state.profiles.firstOrNull { it.id == shortcut.remoteId }
    val enabled = !busy && widgetCommandEnabled(shortcut, profile, state.records[shortcut.remoteId], state.simulated)
    if (command == null) {
        SquareIconButton(widgetImage(Icons.Rounded.Edit), LocalContext.current.getString(R.string.widget_reselect_button),
            actionStartActivity(configurationIntent(LocalContext.current, id)),
            modifier = GlanceModifier.size(size),
            backgroundColor = GlanceTheme.colors.errorContainer, contentColor = GlanceTheme.colors.onErrorContainer)
    } else {
        val acAction = widgetAcAction(profile, command)
        val appearance = if (profile != null && acAction != null)
            widgetAcSettings(profile, state.records[profile.id], state.simulated).controlAppearance(acAction, context) else null
        val power = command.isPower(profile)
        val active = appearance?.active == true
        val failure = LocalWidgetFeedback.current?.takeIf { it.error && it.shortcut == shortcut }
        val stateLabel = if (acAction == AcAction.TIMER && profile != null) {
            val minutes = widgetAcSettings(profile, state.records[profile.id], state.simulated).timerMinutes
            if (minutes > 0) localizedTimerDurationLabel(minutes, context) else context.getString(R.string.widget_off)
        } else appearance?.state
        val description = widgetShortcutDescription(context, profile, command.displayLabel(profile, context), stateLabel,
            failure?.let { context.getString(R.string.widget_failure_retry, it.text.resolve(context)) })
        WidgetActionIcon(widgetImage(if (failure != null) Icons.Rounded.ErrorOutline else widgetCommandVector(profile, command, state, context)),
            description,
            shortcutAction(shortcut), enabled = enabled,
            primary = primary || power, size = size,
            backgroundColor = when { !enabled -> LocalWidgetSurfaces.current.container; failure != null -> GlanceTheme.colors.errorContainer; primary || power -> GlanceTheme.colors.primary; active -> GlanceTheme.colors.primaryContainer; else -> LocalWidgetSurfaces.current.container },
            contentColor = when { !enabled -> GlanceTheme.colors.outline; failure != null -> GlanceTheme.colors.onErrorContainer; primary || power -> GlanceTheme.colors.onPrimary; active -> GlanceTheme.colors.onPrimaryContainer; else -> GlanceTheme.colors.onSurface })
    }
}

@Composable
private fun WidgetActionIcon(image: ImageProvider, description: String, onClick: Action, enabled: Boolean,
                             primary: Boolean, size: androidx.compose.ui.unit.Dp,
                             backgroundColor: androidx.glance.unit.ColorProvider,
                             contentColor: androidx.glance.unit.ColorProvider) {
    if (primary) SquareIconButton(image, description, onClick, GlanceModifier.size(size), enabled,
        backgroundColor = backgroundColor, contentColor = contentColor)
    else CircleIconButton(image, description, onClick, GlanceModifier.size(size), enabled,
        backgroundColor = backgroundColor, contentColor = contentColor)
}
