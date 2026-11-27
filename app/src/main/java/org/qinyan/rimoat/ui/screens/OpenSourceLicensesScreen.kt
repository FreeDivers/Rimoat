@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.OpenSourceLicenseCatalog
import org.qinyan.rimoat.data.OpenSourceLibrary
import androidx.compose.ui.tooling.preview.Preview
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.components.ActionIconButton
import org.qinyan.rimoat.ui.components.ExpressivePageTopBar
import org.qinyan.rimoat.ui.components.ExpressiveSegmentedSurface
import org.qinyan.rimoat.ui.components.ExpressiveShapeTokens
import org.qinyan.rimoat.ui.components.RemoteSearchBar
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.clearSearchFocusOnTapOutside
import org.qinyan.rimoat.ui.components.rememberSearchFocusController
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.motion.MotionContent
import org.qinyan.rimoat.ui.motion.LocalMotionActive

private val licenseJson = Json { ignoreUnknownKeys = true }
private data class LicensePage(val libraryId: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    onLibraryClick: (String) -> Unit,
    libraryId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val result by produceState<Result<OpenSourceLicenseCatalog>?>(null, context) {
        value = try {
            Result.success(withContext(Dispatchers.IO) {
                val json = context.assets.open("open_source_licenses.json").bufferedReader().use { it.readText() }
                licenseJson.decodeFromString<OpenSourceLicenseCatalog>(json)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    OpenSourceLicensesContent(result, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        onBack, onLibraryClick, libraryId, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesContent(
    result: Result<OpenSourceLicenseCatalog>?,
    isDebug: Boolean,
    onBack: () -> Unit,
    onLibraryClick: (String) -> Unit,
    libraryId: String? = null,
    modifier: Modifier = Modifier
) {
    val active = LocalMotionActive.current
    val wide = rememberWideLayout()
    val listState = rememberLazyListState()
    val detailStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val catalog = result?.getOrNull()
    val libraries = remember(catalog, isDebug) { catalog?.libraries.orEmpty().filter { isDebug || !it.debugOnly } }
    var selectedId by rememberSaveable { mutableStateOf(libraryId) }
    LaunchedEffect(libraryId) { if (libraryId != null) selectedId = libraryId }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(libraries, query) {
        val search = query.trim()
        libraries.filter { it.name.contains(search, true) || it.id.contains(search, true) || it.license.contains(search, true) }
    }
    val library = if (wide) filtered.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull() else libraries.firstOrNull { it.id == libraryId }
    LaunchedEffect(wide, library?.id, active) {
        if (active && wide && library != null) {
            selectedId = library.id
            if (libraryId != library.id) onLibraryClick(library.id)
        }
    }
    val isListRoot = !wide && libraryId == null
    val scrollBehavior = if (isListRoot) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null

    ProvideExpressiveCanvas {
    Scaffold(
        modifier = modifier.let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior.nestedScrollConnection) else it },
        containerColor = ExpressiveSurface.canvas,
        topBar = {
            ExpressivePageTopBar(
                title = stringResource(R.string.licenses_title),
                wide = !isListRoot,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back), onClick = { if (active) onBack() }, enabled = active) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.screen_back))
                    }
                }
            )
        }
    ) { padding ->
        val searchFocus = rememberSearchFocusController()
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .clearSearchFocusOnTapOutside(searchFocus),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                result == null -> LoadingIndicator(Modifier.padding(24.dp))
                catalog == null || (!wide && libraryId != null && library == null) ->
                    Text(stringResource(R.string.licenses_unavailable), Modifier.padding(24.dp))
                else -> {
                    val list: @Composable () -> Unit = {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = if (wide) 0.dp else 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)
                    ) {
                        item(key = "search") {
                            RemoteSearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = stringResource(R.string.licenses_search),
                                modifier = Modifier.padding(bottom = 10.dp),
                                focusController = searchFocus
                            )
                        }
                        if (filtered.isEmpty()) item {
                            Text(stringResource(R.string.licenses_empty), Modifier.padding(24.dp))
                        }
                        itemsIndexed(filtered, key = { _, entry -> entry.id }) { index, entry ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isSelected = wide && entry.id == library?.id
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = filtered.size,
                                interactionSource = interactionSource,
                                containerColor = containerColor,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListItem(
                                    modifier = Modifier
                                        .clickable(interactionSource = interactionSource, indication = ripple(), enabled = active, role = Role.Button, onClick = { if (active) { selectedId = entry.id; onLibraryClick(entry.id) } })
                                        .heightIn(min = 64.dp),
                                    headlineContent = { Text(entry.name) },
                                    supportingContent = {
                                        Column {
                                            Text(stringResource(R.string.licenses_version_license, entry.version, entry.license))
                                            if (entry.debugOnly) Text(stringResource(R.string.licenses_debug_only))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                                )
                            }
                        }
                    }
                    }
                    val detail: @Composable (String?) -> Unit = { id ->
                        val library = libraries.firstOrNull { it.id == id }
                        if (library == null) Text(stringResource(R.string.licenses_empty), Modifier.padding(16.dp))
                        else detailStates.SaveableStateProvider(library.id) {
                            val detailState = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize(), state = detailState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = ExpressiveSurface.card,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(library.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                            SelectionContainer { Text(library.id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            Surface(
                                                shape = RoundedCornerShape(100),
                                                color = MaterialTheme.colorScheme.surfaceContainer
                                            ) {
                                                Text(
                                                    library.license,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                library.documents.forEach { docId -> item(key = docId) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = ExpressiveSurface.card,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SelectionContainer {
                                            Text(catalog.documents.getValue(docId), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                                        }
                                    }
                                } }
                            }
                        }
                    }
                    MotionContent(
                        targetState = LicensePage(libraryId),
                        contentKey = { if (wide) "wide" else if (it.libraryId == null) "list" else "detail" },
                        isBack = { _, to -> to.libraryId == null },
                        backTarget = if (!wide && libraryId != null) LicensePage(null) else null,
                        onBack = onBack
                    ) { page ->
                        if (wide) TwoPaneLayout(Modifier.fillMaxSize(), primaryFraction = .4f, primaryMaxWidth = 320.dp, primary = list, secondary = { detail(library?.id) })
                        else Box(Modifier.widthIn(max = 800.dp).fillMaxSize()) { if (page.libraryId != null) detail(page.libraryId) else list() }
                    }
                }
            }
        }
    }
    }
}

@Preview(widthDp = 640, heightDp = 360, showBackground = true)
@Preview(widthDp = 480, heightDp = 320, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OpenSourceLicensesLandscapePreview() {
    val catalog = OpenSourceLicenseCatalog(
        libraries = listOf(OpenSourceLibrary("androidx.compose.ui:ui", "Jetpack Compose UI", "1.8.3", "Apache-2.0", documents = listOf("apache"))),
        documents = mapOf("apache" to "Apache License\nVersion 2.0, January 2004\n\nTerms and conditions for use, reproduction, and distribution.")
    )
    IrRemoteTheme { OpenSourceLicensesContent(Result.success(catalog), false, {}, {}) }
}
