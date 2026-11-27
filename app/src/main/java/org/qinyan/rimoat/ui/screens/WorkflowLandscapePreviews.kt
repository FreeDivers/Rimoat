package org.qinyan.rimoat.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardUiState
import org.qinyan.rimoat.ui.learning.LearningSetupContent
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState

@Preview(widthDp = 640, heightDp = 360, locale = "en")
@Preview(widthDp = 480, heightDp = 320, locale = "ja", fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CodeMatchingLandscapePreview() {
    val power = RemoteButton("power", stringResource(R.string.control_power), key = ButtonKey.POWER,
        iconName = "power", rawPattern = listOf(9000, 4500, 560))
    val model = IrRemoteModel("preview_code", 1, "TV", 1, "", "Example", 1, "", 1)
    IrRemoteTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            CodeMatchingWizardView(AddRemoteUiState(remotes = listOf(model), primaryTestKey = power,
                allPreviewButtons = listOf(power), testedButton = power, canConfirmTest = true), {}, {}, {}, {})
        }
    }
}

@Preview(widthDp = 640, heightDp = 360, locale = "en")
@Preview(widthDp = 480, heightDp = 320, locale = "ja", fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(widthDp = 411, heightDp = 891, locale = "zh")
@Composable
private fun LearningSetupLandscapePreview() {
    IrRemoteTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            LearningSetupContent(CustomRemoteWizardUiState(remoteName = "Living room", category = DeviceCategory.TV), {}, {}, {}, {}, {})
        }
    }
}

@Preview(widthDp = 640, heightDp = 360, locale = "en")
@Composable
private fun SaveRemoteLandscapePreview() {
    IrRemoteTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            ConfirmRemoteView(AddRemoteUiState(selectedCategory = IrCategory(1, stringResource(R.string.screen_category_tv), "TV", "tv"),
                customRemoteName = "Living room"), {}, {})
        }
    }
}
