package org.qinyan.rimoat

import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.screens.acPanelActions

class PanelPolicyTest {
    private fun button(id: String, label: String = id) = RemoteButton(id, label, rawPattern = listOf(9000, 4500, 560))

    @Test fun hidingAcActionNeverResurrectsItsLegacyOrMetadataKeyAsRawExtension() {
        val fan = button("legacy_wind", "风量")
        val swing = button("swing", "User swing").copy(sourceKeyType = AcAction.SWING.keyType)
        val light = button("lamp", "User light").copy(acAction = AcAction.LIGHT)
        val custom = button("custom", "Custom")
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 1,
            buttons = listOf(fan, swing, light, custom),
            panelLayout = RemotePanelLayout(hidden = setOf(panelAction(AcAction.FAN), panelAction(AcAction.SWING), panelAction(AcAction.LIGHT))))
        assertEquals(listOf(custom), acRawExtensionButtons(profile))
        val visible = acPanelActions(profile, setOf(AcAction.FAN, AcAction.SWING, AcAction.LIGHT), false)
        assertFalse(visible.contains(AcAction.FAN))
        assertFalse(visible.contains(AcAction.SWING))
        assertFalse(visible.contains(AcAction.LIGHT))
        assertTrue(acPanelActions(profile, emptySet(), true).containsAll(listOf(AcAction.FAN, AcAction.SWING, AcAction.LIGHT)))
    }

    @Test fun duplicateBindingsAndLegacyExtensionAliasesDoNotEscapeHiddenActions() {
        val aliases = listOf(button("old_fan", "风量"), button("other_fan", "风速"))
        val source = button("source_fan").copy(sourceKeyType = AcAction.FAN.keyType)
        val explicit = button("explicit_fan").copy(acAction = AcAction.FAN)
        val sleep = button("legacy_sleep", AcAction.SLEEP.label)
        val custom = button("custom", "Custom")
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 1,
            buttons = aliases + listOf(source, explicit, sleep, custom),
            panelLayout = RemotePanelLayout(hidden = setOf(panelAction(AcAction.FAN))))
        assertEquals(explicit, acButtonForAction(profile, AcAction.FAN))
        assertEquals(listOf(custom), acRawExtensionButtons(profile))
        assertFalse(AcAction.FAN in acPanelActions(profile, emptySet(), false))
        assertTrue(AcAction.SLEEP in acPanelActions(profile, emptySet(), false))
        assertEquals(aliases + listOf(source, explicit, sleep, custom), profile.buttons)
    }

    @Test fun renamedMetadataKeyCannotBeBoundToTwoDifferentStateControls() {
        val lightWithFanLabel = button("light", "风速").copy(acAction = AcAction.LIGHT)
        val sourceSwingWithSleepLabel = button("swing", AcAction.SLEEP.label).copy(sourceKeyType = AcAction.SWING.keyType)
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC,
            buttons = listOf(lightWithFanLabel, sourceSwingWithSleepLabel))
        assertNull(acButtonForAction(profile, AcAction.FAN))
        assertNull(acButtonForAction(profile, AcAction.SLEEP))
        assertEquals(lightWithFanLabel, acButtonForAction(profile, AcAction.LIGHT))
        assertEquals(sourceSwingWithSleepLabel, acButtonForAction(profile, AcAction.SWING))
        assertTrue(acRawExtensionButtons(profile).isEmpty())
    }

    @Test fun layoutChangesPreserveSpatialCombinationsSignalsAndDefaultOrder() {
        val power = button("power", "电源")
        val up = button("up", "上"); val down = button("down", "下")
        val left = button("left", "左"); val right = button("right", "右")
        val first = button("first"); val second = button("second"); val rare = button("rare")
        val originals = listOf(power, up, down, left, right, first, second, rare)
        val original = RemoteProfile("custom", "Custom", buttons = originals)
        val layout = RemotePanelLayout(order = listOf(panelKey(second), panelKey(first)),
            hidden = setOf(panelKey(rare), panelKey(power), panelKey(up)))
        val display = original.copy(panelLayout = layout).withPanelPresentation()
        assertEquals(originals, original.buttons)
        assertTrue(display.buttons.containsAll(listOf(power, up, down, left, right)))
        assertFalse(display.buttons.contains(rare))
        assertTrue(display.buttons.indexOf(second) < display.buttons.indexOf(first))
        assertTrue(display.buttons.all { it == originals.first { candidate -> candidate.id == it.id } })
        assertEquals(originals, original.copy(panelLayout = RemotePanelLayout()).withPanelPresentation().buttons)
    }

    @Test fun ordinaryOrderMatchesMainPageAndExtensionsWithoutDuplicatingControls() {
        val keys = (0..8).map { button("key$it", "Custom $it") }
        val order = keys.reversed().map(::panelKey)
        val profile = RemoteProfile("custom", "Custom", buttons = keys, panelLayout = RemotePanelLayout(order = order))
        val display = profile.withPanelPresentation()
        assertEquals(keys.reversed(), display.buttons)
        assertEquals(keys.reversed().drop(6), remoteExtraButtons(display))
        assertEquals(keys.size, (display.buttons.take(6) + remoteExtraButtons(display)).distinctBy { it.id }.size)
    }

    @Test fun acStateOrderAndRawOrderStayWithinTheirExplicitEditorGroups() {
        val custom1 = button("custom1"); val custom2 = button("custom2")
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 1,
            buttons = listOf(custom1, custom2), panelLayout = RemotePanelLayout(order = listOf(
                "preset:2", panelKey(custom2), panelAction(AcAction.LIGHT), panelAction(AcAction.ECO), panelKey(custom1))))
        val display = profile.withPanelPresentation()
        assertEquals(listOf(custom2, custom1), acRawExtensionButtons(display))
        val actions = acPanelActions(display, setOf(AcAction.ECO, AcAction.LIGHT), false)
        assertTrue(actions.indexOf(AcAction.LIGHT) < actions.indexOf(AcAction.ECO))
        assertEquals(profile.buttons, profile.copy(panelLayout = RemotePanelLayout()).buttons)
    }
}
