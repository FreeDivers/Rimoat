@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.semantics.Role
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.FilledTonalButton
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.automirrored.rounded.Sort
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveSelectableMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.R

private data class CategoryOption(val category: DeviceCategory?, @androidx.annotation.StringRes val labelRes: Int)

private val categoryOptions = listOf(
    CategoryOption(null, R.string.screen_category_all),
    CategoryOption(DeviceCategory.TV, R.string.screen_category_tv),
    CategoryOption(DeviceCategory.AC, R.string.screen_category_ac),
    CategoryOption(DeviceCategory.FAN, R.string.screen_category_fan),
    CategoryOption(DeviceCategory.PROJECTOR, R.string.screen_category_projector),
    CategoryOption(DeviceCategory.AUDIO, R.string.screen_category_audio),
    CategoryOption(DeviceCategory.CUSTOM, R.string.screen_category_custom)
)
internal val remoteCategoryFilters = categoryOptions.map { it.category }

/** Wrapping choices keep every category reachable in a narrow landscape pane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryFilterChips(
    selectedCategory: DeviceCategory?,
    onSelectCategory: (DeviceCategory?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categoryOptions.forEach { (category, labelRes) ->
            val checked = selectedCategory == category
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelectCategory(category) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = ExpressiveSurface.card,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    selected = checked
                    role = Role.RadioButton
                }
            ) { Text(stringResource(labelRes)) }
        }
    }
}

private val CategoryButtonRigidHorizontalPadding = 6.dp
private val CategoryButtonCompressibleHorizontalPadding = 10.dp
private val CategoryButtonCompressionLimit = 6.dp

/**
 * Reserves resting horizontal padding for [ButtonGroup] intrinsic measurement while allowing
 * inner slack to absorb neighbor compression and spring overshoot without squeezing [content].
 */
@Composable
private fun CompressibleHorizontalSlot(
    horizontalSlack: Dp,
    content: @Composable () -> Unit
) {
    val measurePolicy = remember(horizontalSlack) {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                val slackPx = horizontalSlack.roundToPx()
                val placeable = measurables.single().measure(constraints.copy(minWidth = 0))
                val width = constraints.constrainWidth(placeable.width + slackPx * 2)
                val height = constraints.constrainHeight(placeable.height)
                return layout(width, height) {
                    placeable.placeRelative(
                        x = (width - placeable.width) / 2,
                        y = (height - placeable.height) / 2
                    )
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = measurables.single().minIntrinsicWidth(height)

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = measurables.single().maxIntrinsicWidth(height) + horizontalSlack.roundToPx() * 2

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                val innerWidth = if (width == Constraints.Infinity) {
                    width
                } else {
                    (width - horizontalSlack.roundToPx() * 2).coerceAtLeast(0)
                }
                return measurables.single().minIntrinsicHeight(innerWidth)
            }

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                val innerWidth = if (width == Constraints.Infinity) {
                    width
                } else {
                    (width - horizontalSlack.roundToPx() * 2).coerceAtLeast(0)
                }
                return measurables.single().maxIntrinsicHeight(innerWidth)
            }
        }
    }
    Layout(content = content, measurePolicy = measurePolicy)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterBar(
    selectedCategory: DeviceCategory?,
    onSelectCategory: (DeviceCategory?) -> Unit
) {
    val categories = categoryOptions
    val buttonPadding = PaddingValues(
        horizontal = CategoryButtonRigidHorizontalPadding,
        vertical = 8.dp
    )

    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp).selectableGroup(),
        expandedRatio = .08f,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (category, labelRes) ->
            customItem(
                buttonGroupContent = {
                    val checked = selectedCategory == category
                    val interactionSource = remember { MutableInteractionSource() }
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(checked) {
                        if (checked) {
                            withFrameNanos { }
                            bringIntoView.bringIntoView()
                        }
                    }
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onSelectCategory(category) },
                        interactionSource = interactionSource,
                        colors = ToggleButtonDefaults.colors(
                            containerColor = ExpressiveSurface.card,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = buttonPadding,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .animateWidth(interactionSource, compressionLimit = CategoryButtonCompressionLimit)
                            .bringIntoViewRequester(bringIntoView).semantics {
                                selected = checked
                                role = Role.RadioButton
                            }
                    ) {
                        CompressibleHorizontalSlot(CategoryButtonCompressibleHorizontalPadding) {
                            Text(stringResource(labelRes), maxLines = 1, softWrap = false)
                        }
                    }
                },
                menuContent = { menu ->
                    SoundEffectOnInteraction(enabled = false) {
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            onClick = { menu.dismiss(); onSelectCategory(category) }
                        )
                    }
                }
            )
        }
    }
}

@Composable
internal fun RoomAndSortBar(
    rooms: List<String>,
    selectedRoom: String?,
    recentFirst: Boolean,
    onSelectRoom: (String?) -> Unit,
    onSetRecentFirst: (Boolean) -> Unit
) {
    val screenActive = LocalMotionActive.current
    var showRooms by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val roomLabel = stringResource(R.string.screen_room)
    val allRoomsLabel = stringResource(R.string.screen_all_rooms)
    val selectedRoomLabel = selectedRoom ?: allRoomsLabel
    val roomExpansionLabel = stringResource(R.string.screen_room_expansion, selectedRoomLabel,
        stringResource(if (showRooms) R.string.screen_expanded else R.string.screen_collapsed))
    val sortStateLabel = stringResource(if (recentFirst) R.string.screen_recent else R.string.screen_default_sort)
    LaunchedEffect(screenActive) {
        if (!screenActive) {
            showRooms = false
            showSort = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            val roomActive = selectedRoom != null
            FilledTonalButton(
                onClick = { showRooms = true },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = roomLabel
                    stateDescription = roomExpansionLabel
                },
                contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (roomActive) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card,
                    contentColor = if (roomActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                ),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Rounded.MeetingRoom,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (roomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedRoomLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            val roomOptions = buildList {
                add(null to allRoomsLabel)
                rooms.forEach { add(it to it) }
            }
            ExpressiveDropdownMenu(
                expanded = screenActive && showRooms,
                onDismissRequest = { showRooms = false },
                selectable = true
            ) {
                roomOptions.forEachIndexed { index, (room, label) ->
                    ExpressiveSelectableMenuItem(
                        selected = selectedRoom == room,
                        onClick = {
                            showRooms = false
                            onSelectRoom(room)
                        },
                        text = { Text(label) },
                        index = index,
                        count = roomOptions.size
                    )
                }
            }
        }
        Box {
            Surface(
                shape = CircleShape,
                color = if (recentFirst) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card,
                contentColor = if (recentFirst) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                ActionIconButton(stringResource(R.string.screen_sort),
                    onClick = { showSort = true },
                    modifier = Modifier.semantics {
                        stateDescription = sortStateLabel
                    }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.screen_sort))
                }
            }
            val sortOptions = listOf(
                false to stringResource(R.string.screen_default_sort),
                true to stringResource(R.string.screen_recent)
            )
            ExpressiveDropdownMenu(
                expanded = screenActive && showSort,
                onDismissRequest = { showSort = false },
                selectable = true
            ) {
                sortOptions.forEachIndexed { index, (recent, label) ->
                    ExpressiveSelectableMenuItem(
                        selected = recentFirst == recent,
                        onClick = {
                            showSort = false
                            onSetRecentFirst(recent)
                        },
                        text = { Text(label) },
                        index = index,
                        count = sortOptions.size
                    )
                }
            }
        }
    }
}
