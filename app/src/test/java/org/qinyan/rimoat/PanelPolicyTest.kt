package org.qinyan.rimoat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.*
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.screens.acPanelActions

class PanelPolicyTest {
    private fun button(id: String, label: String = id) = RemoteButton(id, label, rawPattern = listOf(9000, 4500, 560))

    @Test fun learnedStateKeysRemainIndependentAndCanBeHiddenWithoutHidingDynamicControls() {
        val keys = listOf(
            button("power", "电源").copy(iconName = "power", acAction = AcAction.POWER_ON),
            button("temperature", "升温").copy(sourceKeyType = AcAction.TEMP_UP.keyType),
            button("mode", AcAction.MODE.label)
        ).map { it.copy(isLearned = true) }
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 27, buttons = keys)
        assertEquals(keys, acRawExtensionButtons(profile))
        assertEquals(keys[1], acButtonForAction(profile, AcAction.TEMP_UP))
        val hidden = profile.copy(panelLayout = RemotePanelLayout(hidden = keys.map(::panelKey).toSet())).withPanelPresentation()
        assertTrue(acRawExtensionButtons(hidden).isEmpty())
        assertTrue(AcAction.MODE in acPanelActions(hidden, AcAction.entries.toSet(), false))
        assertTrue(panelAction(AcAction.MODE) !in hidden.panelLayout.hidden)
    }

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
        val power = button("power", "电源").copy(key = ButtonKey.POWER)
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

    @Test fun genericDirectionsMatchedByIdOrBuiltinLabelStayFixedInPanelPolicy() {
        val up = button("up", "向上")
        val down = button("nav_down", "Down")
        val left = button("custom_left", "左移").copy(builtinLabel = "左")
        val right = button("right", "Right")
        val ok = button("remote_enter", "Confirm")
        val extra = button("mute", "静音")
        val profile = RemoteProfile(
            id = "projector", name = "Projector", category = DeviceCategory.PROJECTOR,
            buttons = listOf(up, down, left, right, ok, extra)
        )

        val fixed = fixedPanelKeys(profile)
        assertTrue(fixed.containsAll(listOf(up.id, down.id, left.id, right.id, ok.id)))
        assertFalse(extra.id in fixed)

        val presented = profile.copy(
            panelLayout = RemotePanelLayout(hidden = setOf(panelKey(up), panelKey(ok), panelKey(extra)))
        ).withPanelPresentation()
        assertTrue(presented.buttons.containsAll(listOf(up, down, left, right, ok)))
        assertFalse(extra in presented.buttons)
    }

    @Test fun numericKeysMatchedByIdOrBuiltinLabelStayFixedInPanelPolicy() {
        val tvDigits = listOf(
            button("tv_num_1", "频道一"),
            button("num_2", "Two"),
            button("catalog_3", "自定义数字").copy(builtinLabel = "3"),
            button("plain_4", "4"),
            button("extra", "画中画")
        )
        val tvProfile = RemoteProfile("tv", "TV", category = DeviceCategory.TV, buttons = tvDigits)
        val tvFixed = fixedPanelKeys(tvProfile)
        assertTrue(tvFixed.containsAll(listOf("tv_num_1", "num_2", "catalog_3", "plain_4")))
        assertFalse("extra" in tvFixed)

        val customProfile = RemoteProfile("custom", "Custom", category = DeviceCategory.CUSTOM, buttons = tvDigits)
        val customFixed = fixedPanelKeys(customProfile)
        assertTrue(customFixed.containsAll(listOf("tv_num_1", "num_2", "catalog_3", "plain_4")))
        assertFalse("extra" in customFixed)
    }

    @Test fun duplicateDirectionKeysOnlyFixActiveDpadAndExtraDuplicateRemainsEditable() {
        val up1 = button("up", "上")
        val up2 = button("extra_up", "上")
        val down = button("down", "下")
        val left = button("left", "左")
        val right = button("right", "右")
        val profile = RemoteProfile(
            id = "custom_dup", name = "Custom", category = DeviceCategory.CUSTOM,
            buttons = listOf(up1, down, left, right, up2)
        )
        val fixed = fixedPanelKeys(profile)
        assertTrue(up1.id in fixed)
        assertFalse(up2.id in fixed)
    }

    @Test fun stablePolicyUsesOnlyKeyAndLeavesCustomNamesUnclassified() {
        val power = RemoteButton("custom_id", "Not a power label", key = ButtonKey.POWER)
        assertTrue(power.isPowerKey())
        assertFalse(RemoteButton("power", "电源", iconName = "power", builtinLabel = "电源").isPowerKey())
        assertEquals(FunctionGroup.SOUND, ButtonKey.VOLUME_UP.group)
        assertEquals(FunctionGroup.NAVIGATION, ButtonKey.LEFT.group)
        assertEquals(FunctionGroup.PLAYBACK, ButtonKey.PAUSE.group)
        for (direction in DpadDirection.entries) assertEquals(direction, DpadDirection.from(direction.key))
        assertNull(DpadDirection.from(null))
        assertNull(DpadDirection.from(ButtonKey.POWER))
        assertEquals(listOf(ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT, ButtonKey.RIGHT, ButtonKey.OK), DpadDirection.entries.map { it.key })
    }

    @Test fun powerPoliciesUseTheSameStableIdentityAcrossRemoteCategories() {
        val ordinary = listOf(
            button("power_saving", "Power").copy(key = ButtonKey.ECO),
            button("custom_switch", "开关"),
            button("power", "Power").copy(iconName = "power"),
            button("renamed_volume", "Power").copy(key = ButtonKey.VOLUME_UP, iconName = "power")
        ) + (0..4).map { button("extra_$it", "Custom $it") }
        val powerKeys = listOf(ButtonKey.POWER, ButtonKey.POWER_ON, ButtonKey.POWER_OFF, ButtonKey.SECOND_POWER)
        for (powerKey in powerKeys) {
            val power = button("fan_power", "Personal name").copy(key = powerKey)
            for (category in listOf(DeviceCategory.TV, DeviceCategory.FAN, DeviceCategory.CUSTOM)) {
                val profile = RemoteProfile("remote", "Remote", category = category, buttons = ordinary + power)
                assertTrue(power.isPowerKey())
                assertTrue(ordinary.none { it.isPowerKey() })
                assertEquals(setOf(power.id), fixedPanelKeys(profile))
                if (category == DeviceCategory.TV) {
                    assertEquals(power, tvStandardButtons(profile).first())
                    assertEquals(ordinary, remoteExtraButtons(profile))
                } else {
                    assertEquals(ordinary.drop(6), remoteExtraButtons(profile))
                }
                val hidden = profile.copy(panelLayout = RemotePanelLayout(
                    hidden = profile.buttons.map(::panelKey).toSet()
                )).withPanelPresentation()
                assertEquals(listOf(power), hidden.buttons)
                assertEquals(ordinary + power, profile.buttons)
            }
        }
    }

    @Test fun semanticIconsFollowKeysAndKeepUnidentifiedCustomNamesAsText() {
        val expected = mapOf(
            ButtonKey.SOURCE to Icons.AutoMirrored.Rounded.Input,
            ButtonKey.LOCK to Icons.Rounded.Lock,
            ButtonKey.ECO to Icons.Rounded.Eco,
            ButtonKey.OSD to Icons.Rounded.Monitor,
            ButtonKey.SWITCH to Icons.Rounded.SwapHoriz,
            ButtonKey.BRIGHTNESS to Icons.Rounded.Brightness6,
            ButtonKey.ION to Icons.Rounded.Air,
            ButtonKey.MODE to Icons.Rounded.Tune
        )
        for ((key, icon) in expected) {
            for (label in listOf("Personal name", "Input", "入力切替", "訊號來源")) {
                assertSame(icon, resolveButtonIcon(button("custom", label).copy(key = key)))
            }
        }
        for (label in listOf("Power", "Input", "OK", "开关", "参数", "Movie night")) {
            assertNull(resolveButtonIcon(button("custom", label)))
        }
        assertNull(resolveButtonIcon(button("tv_power", "Personal name")))
        assertSame(Icons.Rounded.Lock, resolveButtonIcon(button("custom").copy(iconName = "lock")))
        assertSame(Icons.AutoMirrored.Rounded.Input,
            resolveButtonIcon(button("custom").copy(key = ButtonKey.SOURCE, iconName = "power")))
        for (key in listOf(ButtonKey.OK, ButtonKey.DIGIT_0, ButtonKey.DIGIT_9, ButtonKey.PLUS_TEN)) {
            assertNull(resolveButtonIcon(button("custom").copy(key = key)))
        }
        assertSame(getIconVector("swing"), resolveButtonIcon(button("custom").copy(key = ButtonKey.SWING)))
    }

    @Test fun independentClimateActionsHaveDistinctIconsFromTheirModes() {
        val state = AcSettings()
        val distinctPairs = listOf(
            state.copy(mode = AcMode.FAN).controlAppearance(AcAction.MODE).icon to
                state.controlAppearance(AcAction.VENTILATION).icon,
            state.copy(mode = AcMode.DRY).controlAppearance(AcAction.MODE).icon to
                state.controlAppearance(AcAction.DRY).icon,
            state.copy(mode = AcMode.COOL).controlAppearance(AcAction.MODE).icon to
                state.controlAppearance(AcAction.QUICK_COOL).icon,
            state.copy(mode = AcMode.HEAT).controlAppearance(AcAction.MODE).icon to
                state.controlAppearance(AcAction.QUICK_HEAT).icon,
            state.copy(mode = AcMode.HEAT).controlAppearance(AcAction.MODE).icon to
                state.controlAppearance(AcAction.AUXILIARY_HEAT).icon
        )
        distinctPairs.forEach { (mode, action) -> assertNotEquals(mode, action) }
        val actions = listOf(AcAction.VENTILATION, AcAction.DRY, AcAction.AUXILIARY_HEAT,
            AcAction.QUICK_COOL, AcAction.QUICK_HEAT, AcAction.TURBO,
            AcAction.HORIZONTAL_SWING, AcAction.SWING, AcAction.TEMP_DOWN)
        val icons = actions.map { state.controlAppearance(it).icon }
        assertEquals(actions.size, icons.distinct().size)
        for (action in listOf(AcAction.HORIZONTAL_SWING, AcAction.SWING)) {
            val off = state.controlAppearance(action)
            val on = state.apply(action).controlAppearance(action)
            assertSame(off.icon, on.icon)
            assertEquals(false, off.active)
            assertEquals(true, on.active)
        }
        assertSame(Icons.Rounded.SwapHoriz, state.controlAppearance(AcAction.HORIZONTAL_SWING).icon)
        assertSame(Icons.Rounded.SwapVert, state.controlAppearance(AcAction.SWING).icon)
    }

}
