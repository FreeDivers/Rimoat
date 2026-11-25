@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.qinyan.rimoat.i18n.AppStrings
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.components.ActionIconButton
import org.qinyan.rimoat.ui.components.TwoPaneLayout
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    ActionIconButton(AppStrings.get(R.string.screen_back, "返回"), onClick = { if (active) onBack() }, enabled = active) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = AppStrings.get(R.string.screen_back, "返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when {
                result == null -> LoadingIndicator(Modifier.padding(24.dp))
                catalog == null || (!wide && libraryId != null && library == null) ->
                    Text(stringResource(R.string.licenses_unavailable), Modifier.padding(24.dp))
                else -> {
                    val list: @Composable () -> Unit = {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "search") { OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            label = { Text(stringResource(R.string.licenses_search)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true
                        ) }
                        if (filtered.isEmpty()) item {
                            Text(stringResource(R.string.licenses_empty), Modifier.padding(24.dp))
                        }
                        items(filtered, key = { it.id }) { entry ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val shape = rememberExpressiveShape(interactionSource)
                            ListItem(
                                modifier = Modifier.clip(shape)
                                    .clickable(interactionSource = interactionSource, indication = ripple(), enabled = active, role = Role.Button, onClick = { if (active) { selectedId = entry.id; onLibraryClick(entry.id) } })
                                    .heightIn(min = 64.dp),
                                headlineContent = { Text(entry.name) },
                                supportingContent = {
                                    Column {
                                        Text("${entry.version}  /  ${entry.license}")
                                        if (entry.debugOnly) Text(stringResource(R.string.licenses_debug_only))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = if (wide && entry.id == library?.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                            )
                        }
                    }
                    }
                    val detail: @Composable (String?) -> Unit = { id ->
                        val library = libraries.firstOrNull { it.id == id }
                        if (library == null) Text(stringResource(R.string.licenses_empty), Modifier.padding(16.dp))
                        else detailStates.SaveableStateProvider(library.id) {
                            val detailState = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize(), state = detailState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    Text(library.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                    SelectionContainer { Text(library.id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                item { Text(library.license, style = MaterialTheme.typography.titleMedium) }
                                library.documents.forEach { id -> item(key = id) {
                                    SelectionContainer { Text(catalog.documents.getValue(id), style = MaterialTheme.typography.bodyMedium) }
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
