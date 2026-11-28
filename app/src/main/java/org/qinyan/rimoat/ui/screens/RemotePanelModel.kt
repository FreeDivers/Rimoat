@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.panelAction
import org.qinyan.rimoat.ui.components.acButtonForAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.RemoteProfile

internal fun LazyListScope.remoteItem(key: String, content: @Composable ColumnScope.() -> Unit) {
    item(key = key, contentType = "control_section") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

internal fun acPanelActions(profile: RemoteProfile, available: Set<AcAction>, learning: Boolean): List<AcAction> {
    val staticAc = profile.useStaticAcButtons || profile.acProtocolId == null
    val actions = listOf(AcAction.MODE, AcAction.FAN) + AcAction.entries.filter { action ->
        action.ordinal >= AcAction.SWING.ordinal && (action == AcAction.SWING ||
            !staticAc && (action in available || action == AcAction.HORIZONTAL_SWING || learning) ||
            acButtonForAction(profile, action) != null)
    }
    return if (learning) actions else actions.filter { panelAction(it) !in profile.panelLayout.hidden }
        .sortedBy { profile.panelLayout.rank(panelAction(it)) }
}
