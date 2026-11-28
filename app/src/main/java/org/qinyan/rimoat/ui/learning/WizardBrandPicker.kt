package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.ui.screens.displayName

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.asString

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.screens.BrandSelectionView
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WizardBrandPicker(
    viewModel: CustomRemoteWizardViewModel,
    state: CustomRemoteWizardUiState,
    wide: Boolean,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val brandContext = androidx.compose.ui.platform.LocalContext.current
    var brands by remember(state.selectedApplianceType) { mutableStateOf<List<IrBrand>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiText?>(null) }
    LaunchedEffect(state.selectedApplianceType) {
        loading = true
        error = null
        try { brands = viewModel.brandsFor(state.selectedApplianceType) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = UiText.Resource(R.string.screen_brand_load_failed) }
        finally { loading = false }
    }
    val brandScrollBehavior = if (!wide) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
    ProvideExpressiveCanvas {
    Scaffold(
        modifier = Modifier.let { if (brandScrollBehavior != null) it.nestedScroll(brandScrollBehavior.nestedScrollConnection) else it },
        containerColor = ExpressiveSurface.canvas,
        topBar = {
            ExpressivePageTopBar(
                title = stringResource(R.string.screen_choose_brand),
                wide = wide,
                scrollBehavior = brandScrollBehavior,
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back_to_brand), { onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { brandPadding ->
        Column(Modifier.fillMaxSize().padding(brandPadding)) {
            error?.let {
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            BrandSelectionView(brands.filter { query.isBlank() || it.nameCn.contains(query, true) || it.nameEn.contains(query, true) || it.pinyin.contains(query, true) },
                rememberLazyListState(), query,
                org.qinyan.rimoat.data.db.IrCategory(0, state.selectedApplianceType.label(), "",
                    state.selectedApplianceType.catalogCode), loading, { query = it }, {
                    HapticHelper.performRemoteClick(haptic, context)
                    viewModel.setBrand(it.displayName(brandContext))
                    onBack()
                })
        }
    }
    }
}
