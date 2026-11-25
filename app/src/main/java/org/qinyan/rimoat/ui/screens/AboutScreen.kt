package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.qinyan.rimoat.i18n.AppStrings
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.ActionIconButton
import org.qinyan.rimoat.ui.components.AppSnackbarHost
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit, onLicensesClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    AboutContent(versionName, onBack, onLicensesClick, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutContent(versionName: String, onBack: () -> Unit, onLicensesClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repositoryUrl = stringResource(R.string.about_repository_url)
    val unavailableMessage = stringResource(R.string.about_repository_unavailable)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wide = rememberWideLayout()
    val identityScroll = rememberScrollState()
    val linksScroll = rememberScrollState()
    Scaffold(
        modifier = modifier,
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    ActionIconButton(AppStrings.get(R.string.screen_back, "返回"), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = AppStrings.get(R.string.screen_back, "返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        val identity: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (wide) 8.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher), contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val links: @Composable () -> Unit = {
            val repositoryInteraction = remember { MutableInteractionSource() }
            val licensesInteraction = remember { MutableInteractionSource() }
            val repositoryShape = rememberExpressiveShape(repositoryInteraction)
            val licensesShape = rememberExpressiveShape(licensesInteraction)
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clip(repositoryShape).clickable(interactionSource = repositoryInteraction, indication = ripple(), role = Role.Button) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, repositoryUrl.toUri()))
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar(unavailableMessage, duration = SnackbarDuration.Short) }
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.about_repository)) },
                    supportingContent = { Text(repositoryUrl) },
                    leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) }
                )
                ListItem(
                    modifier = Modifier.clip(licensesShape)
                        .clickable(interactionSource = licensesInteraction, indication = ripple(), role = Role.Button, onClick = onLicensesClick).heightIn(min = 64.dp),
                    headlineContent = { Text(stringResource(R.string.about_license)) },
                    leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                )
            }
        }
        if (wide) TwoPaneLayout(Modifier.fillMaxSize().padding(padding), primaryFraction = .4f,
            primary = { Column(Modifier.fillMaxSize().verticalScroll(identityScroll), horizontalAlignment = Alignment.CenterHorizontally) { identity() } },
            secondary = { Column(Modifier.fillMaxSize().verticalScroll(linksScroll)) { links() } })
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(identityScroll).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            identity(); links()
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, widthDp = 320, heightDp = 480, fontScale = 2f)
@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Preview(showBackground = true, widthDp = 480, heightDp = 320, fontScale = 2f)
@Composable
private fun AboutPreview() {
    IrRemoteTheme { AboutContent(versionName = "1.0.0", onBack = {}, onLicensesClick = {}) }
}
