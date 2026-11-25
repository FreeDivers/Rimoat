@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.RemoteSearchBar
import org.qinyan.rimoat.ui.components.clearSearchFocusOnTapOutside
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.components.rememberSearchFocusController
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.qinyan.rimoat.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.StateFade
import androidx.compose.ui.unit.sp
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.data.db.IrCategory
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BrandSelectionView(
    brands: List<IrBrand>,
    listState: LazyListState,
    searchQuery: String,
    category: IrCategory?,
    isLoading: Boolean,
    onSearchChange: (String) -> Unit,
    onBrandClick: (IrBrand) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    precomputedGroupsCn: List<BrandIndexGroup>? = null,
    precomputedGroupsEn: List<BrandIndexGroup>? = null
) {
    val motion = AppMotion.current
    val brandContext = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = rememberSearchFocusController()
    val preferEnglish = remember(brandContext, configuration) {
        org.qinyan.rimoat.i18n.AppLanguage.wrap(brandContext).resources.configuration.locales[0].language != "zh"
    }
    val otherBrandLabel = stringResource(R.string.screen_other_brand)
    val precomputed = if (preferEnglish) precomputedGroupsEn else precomputedGroupsCn
    val groups = remember(brands, preferEnglish, precomputed) {
        precomputed ?: groupBrandsByIndex(brands, preferEnglish)
    }
    val wide = rememberWideLayout()
    val density = LocalDensity.current
    val showPopular = searchQuery.isBlank()
    val positions = remember(groups, showPopular, wide) { brandHeaderPositions(groups, if (showPopular && !wide) 1 else 0) }
    val letters = remember(positions) { ('A'..'Z').toList() + if ('#' in positions) listOf('#') else emptyList() }
    val popularNames = remember(category?.code) {
        when (category?.code) {
            "tv" -> listOf("小米", "创维", "海信", "索尼", "TCL", "飞利浦", "长虹", "三星", "其它")
            "air" -> listOf("格力", "美的", "海尔", "奥克斯", "志高", "三菱", "大金", "松下", "其它")
            "fan" -> listOf("美的", "艾美特", "格力", "先锋", "华生", "联创", "其它")
            else -> listOf("小米", "华为", "索尼", "飞利浦", "创维", "其它")
        }
    }
    val popular = remember(brands, popularNames) {
        val matches = popularNames.mapNotNull { name ->
            brands.firstOrNull { it.nameCn == name || it.nameEn.equals(name, true) }
        }
        matches.take(if (matches.size >= 6) 6 else 4)
    }

    val popularContent: @Composable () -> Unit = {
                            Column(Modifier.padding(bottom = 12.dp)) {
                                Text(
                                    stringResource(R.string.screen_popular_brands),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .semantics { heading() }
                                )
                                val measurer = rememberTextMeasurer()
                                val labelStyle = MaterialTheme.typography.labelLarge
                                val minimumWidth = remember(popular, preferEnglish, otherBrandLabel, labelStyle, density) {
                                    with(density) {
                                        (popular.maxOfOrNull {
                                            measurer.measure(it.displayName(preferEnglish, otherBrandLabel), labelStyle, softWrap = false).size.width
                                        } ?: 0).toDp()
                                    } + 16.dp
                                }
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val columns = ((maxWidth + 8.dp) / (minimumWidth + 8.dp)).toInt()
                                        .coerceIn(1, if (popular.size >= 6) 3 else 2)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        popular.chunked(columns).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                row.forEach { brand ->
                                                    val interactionSource = remember { MutableInteractionSource() }
                                                    val shape = rememberExpressiveShape(interactionSource, MaterialTheme.shapes.large)
                                                    Card(
                                                        shape = shape, interactionSource = interactionSource,
                                                        onClick = { keyboard?.hide(); onBrandClick(brand) },
                                                        enabled = !isLoading,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .semantics(mergeDescendants = true) { role = Role.Button },
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                                    ) {
                                                        Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center) {
                                                            BrandMark(brand)
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(brand.displayName(preferEnglish, otherBrandLabel), style = MaterialTheme.typography.labelLarge,
                                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                        }
                                                    }
                                                }
                                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                            }
    }
    val searchContent: @Composable () -> Unit = {
        RemoteSearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = stringResource(R.string.screen_brand_search),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            focusController = searchFocus
        )
    }
    val results: @Composable ColumnScope.() -> Unit = {
        key(category?.id, searchQuery) {
            val activeLetter by remember(positions, listState) {
                derivedStateOf {
                    positions.entries.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key
                }
            }
            var viewportHeight by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            // Even a short final section must be able to reach the sticky-header position.
            val bottomSpace = with(density) {
                (viewportHeight.toDp() - 40.dp - (64 * (groups.lastOrNull()?.brands?.size ?: 0)).dp)
                    .coerceAtLeast(16.dp)
            }
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewportHeight = it.height }) {
                if (brands.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 12.dp, bottom = bottomSpace)
                    ) {
                        if (showPopular && !wide) item(key = "popular", contentType = "popular") { popularContent() }
                        groups.forEach { group ->
                            stickyHeader(key = "header_${group.letter}", contentType = "header") {
                                Surface(color = containerColor) {
                                    Text(group.letter.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { heading() }
                                            .padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                            items(group.brands, key = { "brand_${it.brandIdx}_${it.nameCn}_${it.nameEn}" },
                                contentType = { "brand" }) { brand ->
                                BrandListItem(
                                    brand = brand,
                                    displayName = brand.displayName(preferEnglish, otherBrandLabel),
                                    enabled = !isLoading,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(),
                                        fadeOutSpec = motion.fastEffectsSpec()
                                    ),
                                    onClick = {
                                        keyboard?.hide()
                                        onBrandClick(brand)
                                    }
                                )
                            }
                        }
                    }
                    if (!isLoading) BrandAlphabetIndexRail(
                        letters, positions.keys, activeLetter,
                        onSelect = { letter -> positions[letter]?.let { listState.requestScrollToItem(it) } },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                    )
                } else {
                    StateFade(!isLoading, Modifier.align(Alignment.Center)) { showNoMatch ->
                        if (showNoMatch) Text(
                            stringResource(R.string.screen_no_brand_match, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
                // Keep populated results in place while a selected brand's codes load.
                if (isLoading && brands.isEmpty()) {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
    val focusDismissModifier = Modifier.clearSearchFocusOnTapOutside(searchFocus)
    if (wide) {
        TwoPaneLayout(
            modifier = Modifier.fillMaxSize().background(containerColor).imePadding().then(focusDismissModifier),
            primaryFraction = .4f, primaryMaxWidth = 320.dp,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    category?.let {
                        Text(it.displayName(brandContext), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(8.dp).semantics { heading() })
                    }
                    searchContent()
                    if (showPopular) Box(Modifier.padding(horizontal = 8.dp)) { popularContent() }
                }
            },
            secondary = { Column(Modifier.fillMaxSize()) { results() } }
        )
    } else {
        Column(Modifier.fillMaxSize().background(containerColor).imePadding().then(focusDismissModifier).contentWidth(720.dp)) {
            searchContent()
            results()
        }
    }
}

@Composable
private fun BrandListItem(
    brand: IrBrand,
    displayName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val compact = maxWidth < 280.dp
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberExpressiveShape(interactionSource)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(if (compact) 32.dp else 48.dp), contentAlignment = Alignment.Center) {
            BrandMark(brand)
        }
        Spacer(Modifier.width(if (compact) 8.dp else 16.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(if (compact) 8.dp else 16.dp))
        if (!compact) Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Typographic brand identifiers; no invented logo assets or decorative category badges. */
}

@Composable
private fun BrandMark(brand: IrBrand) {
    val mark = remember(brand.brandIdx, brand.nameEn, brand.pinyin, brand.nameCn) { brandMonogram(brand) }
    Text(mark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary, maxLines = 1,
        modifier = Modifier.clearAndSetSemantics { }, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

/** One 48dp-wide adjustable control: small letters are positions in a scrubber, not tiny buttons. */
@Composable
private fun BrandAlphabetIndexRail(
    letters: List<Char>,
    available: Set<Char>,
    activeLetter: Char?,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val select by rememberUpdatedState(onSelect)
    var pressedLetter by remember { mutableStateOf<Char?>(null) }
    val selected = pressedLetter ?: activeLetter
    val selectedIndex = letters.indexOf(selected)
    val usable = remember(letters, available) { letters.filter { it in available } }
    fun step(forward: Boolean): Boolean {
        val index = usable.indexOf(selected)
        val next = if (forward) usable.getOrNull(index + 1) else usable.getOrNull(index - 1)
        return next?.let { select(it); true } ?: false
    }
    val colors = MaterialTheme.colorScheme
    val indexDescription = stringResource(R.string.screen_brand_index)
    val popularBrandsLabel = stringResource(R.string.screen_popular_brands)
    val previousLetterLabel = stringResource(R.string.screen_previous_letter)
    val nextLetterLabel = stringResource(R.string.screen_next_letter)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Medium)
    val cachedLayouts = remember(letters, labelStyle, measurer) { arrayOfNulls<List<TextLayoutResult>>(1) }

    Box(modifier.width(48.dp).heightIn(max = (letters.size * 18).dp).fillMaxHeight()) {
        Canvas(Modifier.fillMaxSize()
            .drawWithCache {
                val railWidth = 24.dp.toPx()
                val outline = CircleShape.createOutline(Size(railWidth, size.height), layoutDirection, this)
                onDrawBehind {
                    translate((size.width - railWidth) / 2, 0f) { drawOutline(outline, colors.surfaceContainerHigh) }
                }
            }
            .semantics {
                contentDescription = indexDescription
                stateDescription = selected?.toString() ?: popularBrandsLabel
                progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.coerceAtLeast(0).toFloat(),
                    0f..letters.lastIndex.toFloat(), letters.size - 2)
                setProgress { value ->
                    val index = value.roundToInt().coerceIn(0, letters.lastIndex)
                    usable.minByOrNull { abs(letters.indexOf(it) - index) }?.let { select(it); true } ?: false
                }
                customActions = listOf(
                    CustomAccessibilityAction(previousLetterLabel) { step(false) },
                    CustomAccessibilityAction(nextLetterLabel) { step(true) })
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionDown, Key.DirectionRight -> step(true)
                    Key.DirectionUp, Key.DirectionLeft -> step(false)
                    Key.MoveHome -> usable.firstOrNull()?.let { select(it); true } ?: false
                    Key.MoveEnd -> usable.lastOrNull()?.let { select(it); true } ?: false
                    else -> false
                }
            }.focusable()
            .pointerInput(letters, available) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var previous: Char? = null
                    fun selectAt(y: Float) {
                        val letter = letters[brandIndexAt(y, size.height, letters.size)]
                        if (letter in available && letter != previous) {
                            previous = letter
                            pressedLetter = letter
                            select(letter)
                            org.qinyan.rimoat.ui.HapticHelper.performSelection(haptic, context)
                        }
                    }
                    try {
                        selectAt(down.position.y)
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) selectAt(change.position.y)
                            change.consume()
                        } while (change.pressed)
                    } finally { pressedLetter = null }
                }
            }
        ) {
            val layouts = cachedLayouts[0] ?: letters.map { measurer.measure(it.toString(), labelStyle) }
                .also { cachedLayouts[0] = it }
            val step = size.height / letters.size
            val textHeight = layouts.maxOf { it.size.height }.toFloat()
            val stride = ceil(textHeight / step).toInt().coerceAtLeast(1)
            val selectedRadius = (textHeight / 2 + 1.dp.toPx()).coerceAtLeast(11.dp.toPx())
            letters.forEachIndexed { index, letter ->
                val center = Offset(size.width / 2, step * (index + 0.5f))
                val isSelected = index == selectedIndex
                if (isSelected) drawCircle(colors.primary, selectedRadius, center)
                // A short viewport or large font samples labels; every letter remains reachable by drag/accessibility.
                val nearSelected = selectedIndex >= 0 && abs(index - selectedIndex) * step < selectedRadius + textHeight / 2
                if (isSelected || (!nearSelected && (index % stride == 0 || index == letters.lastIndex))) {
                    val layout = layouts[index]
                    drawText(layout, color = when {
                        isSelected -> colors.onPrimary
                        letter in available -> colors.onSurfaceVariant
                        else -> colors.onSurfaceVariant.copy(alpha = 0.3f)
                    }, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2))
                } else if (!nearSelected) {
                    drawCircle(colors.onSurfaceVariant.copy(alpha = 0.3f), 1.dp.toPx(), center)
                }
            }
        }
        if (pressedLetter != null) Surface(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-56).dp).requiredSize(56.dp).clearAndSetSemantics { },
            color = colors.primaryContainer, shape = MaterialTheme.shapes.large
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(pressedLetter.toString(), style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimaryContainer)
            }
        }
    }
}
