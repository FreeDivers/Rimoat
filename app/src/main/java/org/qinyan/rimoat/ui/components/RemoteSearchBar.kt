package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource

@Stable
class SearchFocusController {
    internal var containerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var searchCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var hasFocus by mutableStateOf(false)
}

@Composable
fun rememberSearchFocusController(): SearchFocusController = remember { SearchFocusController() }

/** Clears search focus and hides the soft keyboard when a pointer down lands outside the search bar. */
@Composable
fun Modifier.clearSearchFocusOnTapOutside(controller: SearchFocusController): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentController by rememberUpdatedState(controller)
    return this
        .onGloballyPositioned { controller.containerCoordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val state = currentController
                if (state.hasFocus) {
                    val container = state.containerCoordinates?.takeIf { it.isAttached }
                    val search = state.searchCoordinates?.takeIf { it.isAttached }
                    val tappedInsideSearch = if (container != null && search != null) {
                        search.boundsInRoot().contains(container.localToRoot(down.position))
                    } else {
                        false
                    }
                    if (!tappedInsideSearch) {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                    }
                }
            }
        }
}

/** Results stay in the page list so its filters and alphabet index remain usable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusController: SearchFocusController? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val clearSearchLabel = stringResource(R.string.control_clear_search)
    if (focusController != null) {
        DisposableEffect(focusController) {
            onDispose {
                focusController.hasFocus = false
                focusController.searchCoordinates = null
            }
        }
    }
    SearchBar(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusController != null) {
                    Modifier
                        .onGloballyPositioned { focusController.searchCoordinates = it }
                        .onFocusChanged { focusController.hasFocus = it.hasFocus }
                } else {
                    Modifier
                }
            ),
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
