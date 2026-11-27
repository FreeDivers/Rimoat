package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

internal fun remoteFunctionGroup(label: String): String = when {
    listOf("音量", "静音", "声", "均衡").any { it in label } -> "声音"
    listOf("播放", "暂停", "停止", "快进", "快退", "录", "曲", "上一", "下一").any { it in label } -> "播放"
    listOf("频道", "节目", "收藏", "指南", "EPG", "列表").any { it in label } -> "频道与节目"
    listOf("画", "亮度", "对比", "字幕", "屏", "缩放", "对焦", "比例").any { it in label } -> "画面"
    listOf("风", "温", "定时", "睡眠", "模式", "制冷", "制热", "净化").any { it in label } -> "环境与定时"
    listOf("菜单", "设置", "信源", "输入", "主页", "返回", "选择").any { it in label } -> "导航与设置"
    else -> "其他"
}

internal fun remoteFunctionGroup(button: RemoteButton): String = when (button.acAction) {
    AcAction.LIGHT, AcAction.TEMPERATURE_DISPLAY -> "显示"
    AcAction.SLEEP, AcAction.ECO, AcAction.COMFORT, AcAction.QUIET, AcAction.TURBO -> "运行模式"
    AcAction.TIMER -> "定时"
    AcAction.VENTILATION, AcAction.ION, AcAction.CLEAN, AcAction.DRY, AcAction.AUXILIARY_HEAT,
    AcAction.QUICK_COOL, AcAction.QUICK_HEAT -> "空气与温控"
    else -> remoteFunctionGroup(button.label)
}

@Composable
fun RemoteFunctionGroups(buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit,
    onLearn: ((RemoteButton) -> Unit)?, learning: Boolean,
    onRepeat: ((RemoteButton) -> Unit)? = null, enabled: (RemoteButton) -> Boolean = { it.hasSignal },
    buttonContent: (@Composable RowScope.(RemoteButton) -> Unit)? = null,
    preserveOrder: Boolean = false, onUnavailable: ((RemoteButton) -> Unit)? = null,
    buttonLabel: (RemoteButton) -> String = { localizedButtonLabel(it) }) {
    if (buttons.isEmpty()) return
    val groups = remember(buttons, preserveOrder) {
        if (!preserveOrder) buttons.groupBy { remoteFunctionGroup(it) }.toList()
        else buildList<Pair<String,List<RemoteButton>>> {
            buttons.forEach { button ->
                val group = remoteFunctionGroup(button)
                if (lastOrNull()?.first == group) {
                    val previous = removeAt(lastIndex)
                    add(group to previous.second + button)
                } else add(group to listOf(button))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { (name, keys) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    localizedLabel(name),
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ExpressiveSurface.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                        RemoteFunctionGrid(keys, onSend, onLearn, learning, onRepeat, enabled, buttonContent, onUnavailable, buttonLabel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RemoteFunctionGrid(buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit,
    onLearn: ((RemoteButton) -> Unit)?, learning: Boolean, onRepeat: ((RemoteButton) -> Unit)? = null,
    enabled: (RemoteButton) -> Boolean = { it.hasSignal },
    buttonContent: (@Composable RowScope.(RemoteButton) -> Unit)? = null,
    onUnavailable: ((RemoteButton) -> Unit)? = null,
    buttonLabel: (RemoteButton) -> String = { localizedButtonLabel(it) }) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = extensionColumns(buttons.map(buttonLabel), maxWidth).coerceAtMost(buttons.size.coerceAtLeast(1))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buttons.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { button ->
                    val isPower = button.isPowerKey()
                    if (buttonContent != null) buttonContent(button) else
                    RemoteButtonView(buttonLabel(button), button.iconName, { onSend(button) }, modifier = Modifier.weight(1f),
                        onRepeat = onRepeat?.let { { it(button) } }, onLongClick = onLearn?.let { { it(button) } },
                        isLearningMode = learning, enabled = learning || enabled(button),
                        onUnavailable = onUnavailable?.let { explain -> { explain(button) } },
                        diameter = if (isPower) 72.dp else 64.dp,
                        variant = if (isPower) RemoteButtonVariant.POWER else RemoteButtonVariant.STANDARD,
                        buttonId = button.id, builtinLabel = button.builtinLabel)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}
