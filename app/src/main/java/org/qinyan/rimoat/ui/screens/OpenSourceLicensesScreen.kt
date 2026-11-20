package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.i18n.AppStrings

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.OpenSourceLicenseCatalog
import org.qinyan.rimoat.ui.components.ActionIconButton

private val licenseJson = Json { ignoreUnknownKeys = true }

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
    val catalog = result?.getOrNull()
    val isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val libraries = remember(catalog, isDebug) { catalog?.libraries.orEmpty().filter { isDebug || !it.debugOnly } }
    val library = remember(libraries, libraryId) { libraries.find { it.id == libraryId } }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(libraries, query) {
        val search = query.trim()
        libraries.filter { it.name.contains(search, true) || it.id.contains(search, true) || it.license.contains(search, true) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    ActionIconButton(AppStrings.get(R.string.screen_back, "返回"), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = AppStrings.get(R.string.screen_back, "返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when {
                result == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                catalog == null || (libraryId != null && library == null) ->
                    Text(stringResource(R.string.licenses_unavailable), Modifier.padding(24.dp))
                library != null -> LazyColumn(
                    Modifier.widthIn(max = 800.dp).fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(library.name, style = MaterialTheme.typography.headlineSmall)
                        SelectionContainer {
                            Text(library.id, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { Text(library.license, style = MaterialTheme.typography.titleMedium) }
                    library.documents.forEach { id ->
                        item(key = id) {
                            SelectionContainer {
                                Text(catalog.documents.getValue(id), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                else -> Column(Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        label = { Text(stringResource(R.string.licenses_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (filtered.isEmpty()) item {
                            Text(stringResource(R.string.licenses_empty), Modifier.padding(24.dp))
                        }
                        items(filtered, key = { it.id }) { entry ->
                            ListItem(
                                modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                    .clickable(role = Role.Button, onClick = { onLibraryClick(entry.id) })
                                    .heightIn(min = 64.dp),
                                headlineContent = { Text(entry.name) },
                                supportingContent = {
                                    Column {
                                        Text("${entry.version}  /  ${entry.license}")
                                        if (entry.debugOnly) Text(stringResource(R.string.licenses_debug_only))
                                    }
                                },
                                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}
