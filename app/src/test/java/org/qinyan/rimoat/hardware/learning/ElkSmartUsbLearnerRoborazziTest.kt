package org.qinyan.rimoat.hardware.learning

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Looper
import android.os.Parcelable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.UsbSessionAccess
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.learning.SingleButtonLearningDialogContent
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi", application = Application::class,
    shadows = [ElkSmartOpeningManagerShadow::class, ElkSmartOpeningConnectionShadow::class]
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ElkSmartUsbLearnerRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val usb get() = app.getSystemService(Context.USB_SERVICE) as UsbManager
    private val manager get() = Shadow.extract<ElkSmartOpeningManagerShadow>(usb)
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Before fun initializeStrings() {
        AppStrings.initialize(app)
    }

    private fun endpoint(address: Int, type: Int): UsbEndpoint =
        ReflectionHelpers.callConstructor(
            UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, type),
            ClassParameter.from(Int::class.javaPrimitiveType, 64),
            ClassParameter.from(Int::class.javaPrimitiveType, 1)
        )

    private fun intf(id: Int = 0, vararg pairs: Pair<Int, Int>): UsbInterface =
        ReflectionHelpers.callConstructor(
            UsbInterface::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "fixture"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0)
        ).also { intf ->
            val endpoints = pairs.flatMap { (number, type) ->
                listOf(endpoint(number, type), endpoint(number or UsbConstants.USB_DIR_IN, type))
            }
            ReflectionHelpers.setField(intf, "mEndpoints", endpoints.toTypedArray<Parcelable>())
        }

    private fun device(vararg interfaces: UsbInterface): UsbDevice =
        Shadow.newInstanceOf(UsbDevice::class.java).also {
            ReflectionHelpers.setField(it, "mName", "fixture-usb")
            ReflectionHelpers.setField(it, "mVendorId", UsbDeviceFilter.ELKSMART_VID)
            ReflectionHelpers.setField(it, "mProductId", 0x0131)
            ReflectionHelpers.setField(it, "mInterfaces", arrayOf(*interfaces))
        }

    private fun assertReleased(attempt: ElkSmartOpeningAttempt, claimed: Boolean = true) {
        assertEquals(if (claimed) attempt.claims else emptyList<Int>(), attempt.releases)
        assertEquals(1, attempt.closes)
    }

    @Test fun skipsUnresponsiveBulkPairAndKeepsVerifiedPairUntilClose() = runBlocking {
        val first = ElkSmartOpeningAttempt(replies = emptyList())
        val second = ElkSmartOpeningAttempt()
        manager.attempts.addAll(listOf(first, second))
        val device = device(intf(0, 1 to BULK, 2 to BULK))

        val learner = requireNotNull(ElkSmartUsbLearner.open(usb, device))
        try {
            assertReleased(first)
            assertEquals(listOf(1), first.writes.map { it.first.address })
            assertEquals(listOf(2), second.writes.map { it.first.address })
            assertTrue(second.releases.isEmpty())
            assertEquals(0, second.closes)
            assertEquals(LearningState.Idle, learner.state.value)
        } finally {
            learner.close()
        }
        learner.close()
        assertReleased(second)
    }

    @Test fun triesBulkBeforeInterruptEvenWhenDescriptorsListInterruptFirst() = runBlocking {
        val bulk = ElkSmartOpeningAttempt(replies = listOf(ByteArray(6)))
        val interrupt = ElkSmartOpeningAttempt()
        manager.attempts.addAll(listOf(bulk, interrupt))
        val device = device(intf(0, 3 to INTERRUPT, 1 to BULK))

        requireNotNull(ElkSmartUsbLearner.open(usb, device)).use {
            assertReleased(bulk)
            assertEquals(BULK, bulk.writes.single().first.type)
            assertEquals(INTERRUPT, interrupt.writes.single().first.type)
            assertEquals(3, interrupt.writes.single().first.endpointNumber)
        }
        assertReleased(interrupt)
    }

    @Test fun failedClaimClosesConnectionAndContinuesToNextInterface() = runBlocking {
        val denied = ElkSmartOpeningAttempt(claimSucceeds = false)
        val accepted = ElkSmartOpeningAttempt()
        manager.attempts.addAll(listOf(denied, accepted))
        val device = device(intf(0, 1 to BULK), intf(1, 2 to INTERRUPT))

        requireNotNull(ElkSmartUsbLearner.open(usb, device)).use {
            assertReleased(denied, claimed = false)
            assertTrue(denied.writes.isEmpty())
            assertEquals(listOf(1), accepted.claims)
        }
        assertReleased(accepted)
    }

    @Test fun recognizesBothFirmwareRepliesIncludingFragmentedIdentification() = runBlocking {
        for (model in listOf(byteArrayOf(0x70, 1), byteArrayOf(2, 0xAA.toByte()))) {
            val reply = ElkSmartProtocol.AUTHORIZE + model
            val attempt = ElkSmartOpeningAttempt(replies = listOf(reply.copyOfRange(0, 3), reply.copyOfRange(3, 6)))
            manager.attempts.add(attempt)
            requireNotNull(ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK)))).close()
            assertArrayEquals(ElkSmartProtocol.AUTHORIZE, attempt.writes.single().second)
            assertReleased(attempt)
        }
    }

    @Test fun unknownFirmwareAndMissingRepliesReleaseEveryCandidate() = runBlocking {
        val unknown = ElkSmartOpeningAttempt(replies = listOf(ElkSmartProtocol.AUTHORIZE + byteArrayOf(7, 8)))
        val missing = ElkSmartOpeningAttempt(replies = emptyList())
        manager.attempts.addAll(listOf(unknown, missing))
        val device = device(intf(0, 1 to BULK), intf(1, 2 to INTERRUPT))

        assertNull(ElkSmartUsbLearner.open(usb, device))
        assertEquals(2, manager.openCalls)
        assertReleased(unknown)
        assertReleased(missing)
        assertTrue(manager.attempts.flatMap { it.writes }.all { it.second.contentEquals(ElkSmartProtocol.AUTHORIZE) })
    }

    @Test fun incompleteProbeWriteReleasesPairBeforeTryingAnother() = runBlocking {
        val shortWrite = ElkSmartOpeningAttempt(shortWrite = true)
        val accepted = ElkSmartOpeningAttempt()
        manager.attempts.addAll(listOf(shortWrite, accepted))

        requireNotNull(ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK, 2 to BULK)))).close()
        assertReleased(shortWrite)
        assertReleased(accepted)
    }

    @Test fun releaseFailureStillClosesFailedConnectionAndTriesNextPair() = runBlocking {
        val failed = ElkSmartOpeningAttempt(replies = emptyList(), releaseFailure = IOException("Release failed"))
        val accepted = ElkSmartOpeningAttempt()
        manager.attempts.addAll(listOf(failed, accepted))

        requireNotNull(ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK, 2 to BULK)))).close()
        assertReleased(failed)
        assertReleased(accepted)
    }

    @Test fun cancellationDuringSuccessfulProbeReleasesConnectionWithoutTryingAnother() = runBlocking {
        val attempt = ElkSmartOpeningAttempt()
        manager.attempts.add(attempt)
        val job = launch {
            val openingJob = currentCoroutineContext()[Job]!!
            attempt.onReply = { openingJob.cancel() }
            ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK, 2 to BULK)))?.close()
            fail("A cancelled probe must not return a learner")
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, manager.openCalls)
        assertReleased(attempt)
    }

    @Test fun cancelledLazyOpeningReleasesUsbSessionLock() = runBlocking {
        val attempt = ElkSmartOpeningAttempt().apply {
            onReply = { throw CancellationException("Opening cancelled") }
        }
        manager.attempts.add(attempt)
        val device = device(intf(0, 1 to BULK))
        val learner = LazyUsbLearner(device.deviceName, LearnerType.USB_ELKSMART) {
            ElkSmartUsbLearner.open(usb, device)
        }
        try {
            learner.startLearning(3)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertReleased(attempt)
            assertFalse(UsbSessionAccess.mutex.isLocked)
        } finally {
            learner.close()
        }
    }

    @Test fun unavailableConnectionAndMissingEndpointsReturnNull() = runBlocking {
        assertNull(ElkSmartUsbLearner.open(usb, device(intf())))
        assertEquals(0, manager.openCalls)
        assertNull(ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK, 2 to BULK))))
        assertEquals(1, manager.openCalls)
    }

    private val authorizationReply get() = ElkSmartProtocol.AUTHORIZE + byteArrayOf(0x70, 1)
    private val learnedPattern get() = intArrayOf(9008, 4496, 560, 560, 560, 1696, 560, 560)
    private fun learnedPacket(): ByteArray {
        val payload = ElkSmartProtocol.encodePatternToPayload(learnedPattern)
        return ElkSmartProtocol.START_LEARN + byteArrayOf(0, payload.size.toByte()) + payload
    }

    private suspend fun assertAuthorization(
        replies: List<List<ByteArray?>>,
        succeeds: Boolean = true,
        expectedAttempts: Int = 1
    ) {
        val attempt = ElkSmartOpeningAttempt(authorizationReplies = replies, learnPacket = learnedPacket())
        manager.attempts.add(attempt)
        requireNotNull(ElkSmartUsbLearner.open(usb, device(intf(0, 1 to BULK)))).use { learner ->
            val result = learner.startLearning(3)
            if (succeeds) {
                val signal = result.getOrThrow()
                assertArrayEquals(learnedPattern, signal.patternMicros)
                assertEquals(38000, signal.frequencyHz)
                assertTrue(learner.state.value is LearningState.Captured)
            } else {
                assertTrue(result.exceptionOrNull() is IOException)
                assertEquals(
                    app.getString(R.string.msg_elksmart_handshake_failed),
                    (learner.state.value as LearningState.Error).message
                )
            }
            val expectedCommands = List(expectedAttempts + 1) { 0xFC } +
                (if (succeeds) listOf(0xFA, 0xFE) else emptyList()) + 0xFD
            assertEquals(expectedCommands, attempt.writes.map { it.second[0].toInt() and 255 })
        }
        assertReleased(attempt)
    }

    @Test fun acceptsPaddedAuthorizationResponse() = runBlocking {
        assertAuthorization(listOf(listOf(authorizationReply + ByteArray(58) { 0xA5.toByte() })))
    }

    @Test fun acceptsFragmentedAuthorizationAcrossEmptyAndFailedReads() = runBlocking {
        assertAuthorization(listOf(listOf(
            authorizationReply.copyOfRange(0, 3), byteArrayOf(), null,
            authorizationReply.copyOfRange(3, 6)
        )))
    }

    @Test fun acceptsEveryAuthorizationSplitAndBytewiseReplies() = runBlocking {
        for (split in 1 until authorizationReply.size) {
            assertAuthorization(listOf(listOf(
                authorizationReply.copyOfRange(0, split),
                authorizationReply.copyOfRange(split, 6)
            )))
        }
        assertAuthorization(listOf(authorizationReply.map { byteArrayOf(it) }))
    }

    @Test fun retriesIncompleteAuthorizationWithFreshResponseBuffer() = runBlocking {
        assertAuthorization(
            listOf(listOf(authorizationReply.copyOf(5)), listOf(authorizationReply.copyOf(64))),
            expectedAttempts = 2
        )
    }

    @Test fun partialAuthorizationCannotCombineAcrossRetries() = runBlocking {
        assertAuthorization(
            listOf(
                listOf(authorizationReply.copyOfRange(0, 3)),
                listOf(authorizationReply.copyOfRange(3, 6)),
                emptyList()
            ),
            succeeds = false, expectedAttempts = 3
        )
    }

    @Test fun rejectsWrongPrefixTruncatedAndMissingAuthorizationReplies() = runBlocking {
        for (reply in listOf(listOf(ByteArray(64)), listOf(authorizationReply.copyOf(5)), emptyList())) {
            assertAuthorization(List(3) { reply }, succeeds = false, expectedAttempts = 3)
        }
    }

    @Test fun acceptsValidAuthorizationAfterUnrelatedPacket() = runBlocking {
        assertAuthorization(listOf(listOf(ByteArray(64), authorizationReply)))
    }

    @Test fun interruptOnlyReceiverRecordsAndSavesSignalThroughLearningDialog() {
        assertDialogLearning(listOf(authorizationReply), "elksmart_interrupt_learning")
    }

    @Test fun paddedAuthorizationRecordsAndSavesSignalThroughLearningDialog() {
        assertDialogLearning(listOf(authorizationReply.copyOf(64)), "elksmart_padded_learning")
    }

    @Test fun fragmentedAuthorizationRecordsAndSavesSignalThroughLearningDialog() {
        assertDialogLearning(
            listOf(authorizationReply.copyOfRange(0, 3), authorizationReply.copyOfRange(3, 6)),
            "elksmart_fragmented_learning"
        )
    }

    private fun assertDialogLearning(replies: List<ByteArray>, screenshotName: String) {
        val attempt = ElkSmartOpeningAttempt(
            authorizationReplies = listOf(replies),
            learnPacket = learnedPacket()
        )
        manager.attempts.add(attempt)
        val device = device(intf(0, 1 to INTERRUPT))
        val learner = LazyUsbLearner(device.deviceName, LearnerType.USB_ELKSMART) {
            ElkSmartUsbLearner.open(usb, device)
        }
        var saved: IrSignal.Raw? = null
        try {
            compose.setContent {
                val state by learner.state.collectAsState()
                val scope = rememberCoroutineScope()
                IrRemoteTheme(dynamicColor = false, darkTheme = false) {
                    SingleButtonLearningDialogContent(
                        button = RemoteButton("power", "Power"), state = state, isTesting = false,
                        learnerType = learner.type, isHardwareAvailable = true,
                        onStartLearning = { scope.launch { learner.startLearning(3) } },
                        onCancelLearning = learner::cancel, onTestEmit = {},
                        onSave = { saved = it }, onDismiss = learner::cancel
                    )
                }
            }
            compose.onNodeWithText(app.getString(R.string.screen_start_record)).assertIsEnabled()
            compose.onNode(isDialog()).captureRoboImage(
                "src/test/screenshots/${screenshotName}_ready.webp", roborazziOptions = options
            )
            compose.onNodeWithText(app.getString(R.string.screen_start_record)).performClick()
            compose.waitUntil(10_000) { learner.state.value is LearningState.Captured }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(app.getString(R.string.screen_received_waveform)).assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsEnabled().performClick()
            assertArrayEquals(learnedPattern, requireNotNull(saved).patternMicros)
            assertEquals(38000, saved!!.frequencyHz)
            assertFalse(attempt.transferredOnMain)
            assertTrue(attempt.writes.all { it.first.type == INTERRUPT })
            assertEquals(listOf(0xFC, 0xFC, 0xFA, 0xFE, 0xFD), attempt.writes.map { it.second[0].toInt() and 255 })
            assertReleased(attempt)
            assertFalse(UsbSessionAccess.mutex.isLocked)
            compose.onNode(isDialog()).captureRoboImage(
                "src/test/screenshots/${screenshotName}_captured.webp", roborazziOptions = options
            )
        } finally {
            learner.close()
        }
    }

    companion object {
        private const val BULK = UsbConstants.USB_ENDPOINT_XFER_BULK
        private const val INTERRUPT = UsbConstants.USB_ENDPOINT_XFER_INT
    }
}

class ElkSmartOpeningAttempt(
    val claimSucceeds: Boolean = true,
    val replies: List<ByteArray> = listOf(ElkSmartProtocol.AUTHORIZE + byteArrayOf(0x70, 1)),
    val shortWrite: Boolean = false,
    val releaseFailure: IOException? = null,
    val learnPacket: ByteArray? = null,
    val authorizationReplies: List<List<ByteArray?>> = listOf(replies)
) {
    val claims = mutableListOf<Int>()
    val releases = mutableListOf<Int>()
    val writes = mutableListOf<Pair<UsbEndpoint, ByteArray>>()
    val pendingReads = ArrayDeque<ByteArray?>()
    var authorizationRequests = 0
    var onReply: () -> Unit = {}
    var closes = 0
    var transferredOnMain = false
}

@Implements(UsbManager::class)
class ElkSmartOpeningManagerShadow {
    val attempts = mutableListOf<ElkSmartOpeningAttempt>()
    var openCalls = 0

    @Implementation fun openDevice(device: UsbDevice): UsbDeviceConnection? {
        val attempt = attempts.getOrNull(openCalls++) ?: return null
        return ReflectionHelpers.callConstructor(
            UsbDeviceConnection::class.java, ClassParameter.from(UsbDevice::class.java, device)
        ).also {
            Shadow.extract<ElkSmartOpeningConnectionShadow>(it).attempt = attempt
        }
    }
}

@Implements(UsbDeviceConnection::class)
class ElkSmartOpeningConnectionShadow {
    lateinit var attempt: ElkSmartOpeningAttempt

    @Implementation fun claimInterface(intf: UsbInterface, force: Boolean): Boolean {
        attempt.claims.add(intf.id)
        return attempt.claimSucceeds
    }

    @Implementation fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int {
        attempt.transferredOnMain = attempt.transferredOnMain || Looper.myLooper() == Looper.getMainLooper()
        if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
            val command = buffer.copyOf(length)
            attempt.writes.add(endpoint to command)
            if (attempt.shortWrite) return length - 1
            when {
                command.contentEquals(ElkSmartProtocol.AUTHORIZE) -> {
                    val request = attempt.authorizationRequests++
                    attempt.pendingReads.addAll(
                        if (request == 0) attempt.replies
                        else attempt.authorizationReplies.getOrElse(request - 1) { emptyList() }
                    )
                }
                command.contentEquals(ElkSmartProtocol.START_LEARN) -> attempt.learnPacket?.let(attempt.pendingReads::addLast)
            }
            return length
        }
        val reply = attempt.pendingReads.removeFirstOrNull() ?: return -1
        attempt.onReply()
        reply.copyInto(buffer)
        return reply.size
    }

    @Implementation fun releaseInterface(intf: UsbInterface): Boolean {
        attempt.releases.add(intf.id)
        attempt.releaseFailure?.let { throw it }
        return true
    }

    @Implementation fun close() {
        attempt.closes++
    }
}
