package org.qinyan.rimoat

import org.qinyan.rimoat.ui.learning.CustomRemoteWizardUiState
import org.qinyan.rimoat.ui.learning.KeyLearnStatus
import org.qinyan.rimoat.ui.learning.WizardKeyItem
import org.qinyan.rimoat.ui.learning.WizardStep
import org.qinyan.rimoat.ui.motion.backPreview
import org.qinyan.rimoat.ui.motion.motionKey
import org.qinyan.rimoat.ui.motion.motionOrder
import org.qinyan.rimoat.ui.motion.pageOffset
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.junit.Assert.*
import org.junit.Test

class MotionNavigationTest {
    @Test fun wizardReturnsToAddInTheOppositeDirection() {
        val wizard = AppScreen.CustomRemoteWizard(sessionId = "test")
        assertEquals(AppScreen.AddRemote, wizard.backDestination())
        assertNotEquals(wizard, AppScreen.AddRemote.backDestination())
        assertNotEquals(AppScreen.RemoteControl("saved"), wizard.backDestination())
    }

    @Test fun rootDoesNotInterceptSystemBackToHome() {
        assertNull(AppScreen.RemoteList.backDestination())
        assertEquals(AppScreen.RemoteList, AppScreen.AddRemote.backDestination())
        assertEquals(AppScreen.RemoteList, AppScreen.RemoteControl("tv").backDestination())
    }

    @Test fun differentWizardSessionsAndRemotesHaveIndependentSavedUiState() {
        assertNotEquals(AppScreen.CustomRemoteWizard(sessionId = "a").motionKey(),
            AppScreen.CustomRemoteWizard(sessionId = "b").motionKey())
        assertNotEquals(AppScreen.RemoteControl("a").motionKey(), AppScreen.RemoteControl("b").motionKey())
    }

    @Test fun matchingPreviewPreservesQueryAndMovesBackExactlyOneStep() {
        for (step in AddRemoteStep.entries.drop(1)) {
            val state = AddRemoteUiState(step = step, brandSearchQuery = "格力", customRemoteName = "客厅")
            val preview = requireNotNull(state.backPreview())
            assertEquals(step.ordinal - 1, preview.step.ordinal)
            assertEquals("格力", preview.brandSearchQuery)
            assertEquals("客厅", preview.customRemoteName)
            assertEquals(step, state.step)
        }
        assertNull(AddRemoteUiState().backPreview())
    }

    @Test fun inFlightSaveOrTransmissionCannotPreviewAnUnreachableDestination() {
        assertNull(AddRemoteUiState(step = AddRemoteStep.CONFIRM, isSaving = true).backPreview())
        assertNull(AddRemoteUiState(step = AddRemoteStep.WIZARD, isSendingTest = true).backPreview())
        assertNull(CustomRemoteWizardUiState(step = WizardStep.REVIEW, isSaving = true).backPreview())
        assertNull(CustomRemoteWizardUiState(step = WizardStep.LEARNING, isRestoring = true).backPreview())
    }

    @Test fun learningKeysShareOnePageAndBackPreviewsSetupWithoutChangingCapturedData() {
        val queue = listOf(WizardKeyItem(label = "电源", status = KeyLearnStatus.LEARNED), WizardKeyItem(label = "音量"))
        val current = CustomRemoteWizardUiState(step = WizardStep.LEARNING, remoteName = "客厅", keyQueue = queue, currentIndex = 1)
        assertEquals(current.motionKey, current.copy(currentIndex = 0).motionKey)
        val preview = requireNotNull(current.backPreview())
        assertEquals(WizardStep.SETUP, preview.step)
        assertSame(queue, preview.keyQueue)
        assertEquals(1, current.currentIndex)
        assertNull(preview.backPreview())
    }

    @Test fun managementPagesReturnToSettings() {
        assertEquals(AppScreen.Settings, AppScreen.Rooms.backDestination())
        assertEquals(AppScreen.Settings, AppScreen.Trash.backDestination())
        assertEquals(AppScreen.Settings, AppScreen.About.backDestination())
        assertEquals(AppScreen.About, AppScreen.OpenSourceLicenses.backDestination())
        assertEquals(AppScreen.OpenSourceLicenses, AppScreen.LibraryLicense("library").backDestination())
        assertEquals(AppScreen.Settings, AppScreen.Trash.backDestination())
    }

    @Test fun licensePanesShareStateAndReturnToAboutWhenWide() {
        assertEquals(AppScreen.OpenSourceLicenses.motionKey(), AppScreen.LibraryLicense("library").motionKey())
        assertEquals(AppScreen.About, AppScreen.LibraryLicense("library").backDestination(wide = true))
        assertEquals(AppScreen.OpenSourceLicenses, AppScreen.LibraryLicense("library").backDestination(wide = false))
    }

    @Test fun reviewBackPreviewsTheActualLastLearningIndex() {
        val review = CustomRemoteWizardUiState(step = WizardStep.REVIEW,
            keyQueue = List(4) { WizardKeyItem(label = "$it") }, currentIndex = 2)
        val preview = requireNotNull(review.backPreview())
        assertEquals(WizardStep.LEARNING, preview.step)
        assertEquals(2, preview.currentIndex)
        assertTrue(preview.motionOrder < review.motionOrder)
    }

    @Test fun forwardAndBackHaveContinuousMirroredEndpoints() {
        for (direction in listOf(1f, -1f)) {
            assertEquals(0f, pageOffset(0f, true, false, direction), 0f)
            assertEquals(0f, pageOffset(1f, false, false, direction), 0f)
            for (p in listOf(0f, .25f, .5f, .75f, 1f)) {
                assertEquals(pageOffset(p, true, true, direction),
                    pageOffset(1f - p, false, false, direction), .00001f)
                assertEquals(pageOffset(p, false, true, direction),
                    pageOffset(1f - p, true, false, direction), .00001f)
            }
        }
    }

    @Test fun rightEdgeFollowsTheFingerToTheLeftAndCancelRestoresOrigin() {
        assertTrue(pageOffset(.4f, true, true, -1f) < 0f)
        assertTrue(pageOffset(.4f, false, true, -1f) > 0f)
        assertEquals(0f, pageOffset(0f, true, true, -1f), 0f)
        assertEquals(0f, pageOffset(0f, true, true, 1f), 0f)
    }
}
