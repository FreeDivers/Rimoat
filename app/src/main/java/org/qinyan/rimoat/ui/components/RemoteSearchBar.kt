package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource

/** Results stay in the page list so its filters and alphabet index remain usable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val clearSearchLabel = stringResource(R.string.control_clear_search)
    SearchBar(
        modifier = modifier.fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { keyboard?.hide(); focus.clearFocus() },
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(placeholder, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) ActionIconButton(clearSearchLabel, onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = null)
                    }
                }
            )
        },
        expanded = false,
        onExpandedChange = {},
        windowInsets = WindowInsets(0)
    ) {}
}
