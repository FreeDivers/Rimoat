@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.qinyan.rimoat.ui.components.getCategoryIcon
import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import org.qinyan.rimoat.data.db.IrCategory
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

// ---------------------------------------------------------------------------
// Step 1: Category Selection Grid
// ---------------------------------------------------------------------------

@Composable
fun CategorySelectionView(
    categories: List<IrCategory>,
    isLoading: Boolean,
    onCategoryClick: (IrCategory) -> Unit,
    onCustomWizardClick: () -> Unit = {}
) {
    if (isLoading && categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val categoryGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val categoryLabels = categories.map { it to it.displayName(context) }
    val labelWidth = categoryLabels.maxOfOrNull { (_, label) ->
        textMeasurer.measure(label, labelStyle, softWrap = false).size.width
    } ?: 0
    val minimumCardWidth = with(LocalDensity.current) { labelWidth.toDp() } + 24.dp
    val wide = rememberWideLayout()
    val learnEntry: @Composable () -> Unit = {
                val interactionSource = remember { MutableInteractionSource() }
                val shape = rememberExpressiveShape(interactionSource, RoundedCornerShape(20.dp), RoundedCornerShape(28.dp))
                Card(
                    shape = shape, interactionSource = interactionSource,
                    onClick = onCustomWizardClick,
                    colors = CardDefaults.cardColors(containerColor = ExpressiveSurface.card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.compact)
                        .semantics(mergeDescendants = true) { role = Role.Button }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(Spacing.large))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_learn_physical),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(Spacing.small))

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
    }
    val categoriesContent: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
            val columns = ((availableWidth + Spacing.medium) / (minimumCardWidth + Spacing.medium)).toInt().coerceIn(1, 3)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = categoryGridState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                modifier = Modifier.fillMaxSize()
            ) {
                if (!wide) item(key = "learn_physical_hero", span = { GridItemSpan(maxLineSpan) }) { learnEntry() }
                items(categoryLabels, key = { (cat, _) -> cat.id }) { (cat, label) ->
                    CategoryGridCard(category = cat, label = label, onClick = { onCategoryClick(cat) })
                }
            }
        }
    }
    if (wide) {
        TwoPaneLayout(
            modifier = Modifier.fillMaxSize(), primaryFraction = .4f, primaryMaxWidth = 320.dp,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { learnEntry() }
            },
            secondary = categoriesContent
        )
    } else {
        Box(Modifier.contentWidth(720.dp).fillMaxHeight()) { categoriesContent() }
    }
}

@Composable
fun CategoryGridCard(
    category: IrCategory,
    onClick: () -> Unit,
    label: String = category.displayName(LocalContext.current)
) {
    val icon = getCategoryIcon(category.code)
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberExpressiveShape(interactionSource, RoundedCornerShape(20.dp), RoundedCornerShape(28.dp))

    Card(
        shape = shape, interactionSource = interactionSource,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ExpressiveSurface.card),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth().heightIn(min = 96.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3: Code Matching Wizard
// ---------------------------------------------------------------------------
