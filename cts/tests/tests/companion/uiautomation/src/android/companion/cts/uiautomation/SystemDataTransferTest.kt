/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.companion.cts.uiautomation

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.annotation.CallSuper
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionException
import android.companion.Flags
import android.companion.cts.common.CompanionActivity
import android.content.Intent
import android.os.OutcomeReceiver
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.FeatureUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import libcore.util.EmptyArray
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the system data transfer.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:SystemDataTransferTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SystemDataTransferTest : UiAutomationTestBase(null, null) {
    companion object {
        private const val SYSTEM_DATA_TRANSFER_TIMEOUT = 10L // 10 seconds

        private const val ACTION_CLICK_ALLOW = 1
        private const val ACTION_CLICK_DISALLOW = 2
        private const val ACTION_PRESS_BACK = 3
    }

    @CallSuper
    override fun setUp() {
        super.setUp()

        assumeFalse(FeatureUtil.isWatch())

        // Assume Permission Transfer is enabled, otherwise skip the test.
        try {
            val association = associate()
            cdm.buildPermissionTransferUserConsentIntent(association.id)
            true
        } catch (e: UnsupportedOperationException) {
            false
        }.apply { assumeTrue("This test requires Permission Transfer to be enabled.", this) }

        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.enableSecureTransport(false)
        }
    }

    @CallSuper
    override fun tearDown() {
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.enableSecureTransport(true)
        }
        super.tearDown()
    }

    @Test
    fun test_userConsent_allow() {
        val association1 = associate()

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association1.id))
        }

        val resultCode = requestPermissionTransferUserConsent(association1.id, ACTION_CLICK_ALLOW)

        assertEquals(expected = RESULT_OK, actual = resultCode)
        if (Flags.permSyncUserConsent()) {
            assertTrue(cdm.isPermissionTransferUserConsented(association1.id))
        }
    }

    @Test
    fun test_userConsent_disallow() {
        val association = associate()

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }

        val resultCode = requestPermissionTransferUserConsent(
            association.id,
            ACTION_CLICK_DISALLOW
        )

        assertEquals(expected = RESULT_CANCELED, actual = resultCode)
        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }
    }

    @Test
    fun test_userConsent_cancel() {
        val association = associate()

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }

        requestPermissionTransferUserConsent(association.id, ACTION_PRESS_BACK)

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }
    }

    @Test
    fun test_userConsent_allowThenDisallow() {
        val association = associate()

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }

        val resultCode = requestPermissionTransferUserConsent(association.id, ACTION_CLICK_ALLOW)

        assertEquals(expected = RESULT_OK, actual = resultCode)
        if (Flags.permSyncUserConsent()) {
            assertTrue(cdm.isPermissionTransferUserConsented(association.id))
        }

        val resultCode2 = requestPermissionTransferUserConsent(
            association.id,
            ACTION_CLICK_DISALLOW
        )
        assertEquals(expected = RESULT_CANCELED, actual = resultCode2)
        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }
    }

    @Test
    fun test_userConsent_disallowThenAllow() {
        val association = associate()

        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }

        val resultCode = requestPermissionTransferUserConsent(association.id, ACTION_CLICK_DISALLOW)

        assertEquals(expected = RESULT_CANCELED, actual = resultCode)
        if (Flags.permSyncUserConsent()) {
            assertFalse(cdm.isPermissionTransferUserConsented(association.id))
        }

        val resultCode2 = requestPermissionTransferUserConsent(association.id, ACTION_CLICK_ALLOW)
        assertEquals(expected = RESULT_OK, actual = resultCode2)
        if (Flags.permSyncUserConsent()) {
            assertTrue(cdm.isPermissionTransferUserConsented(association.id))
        }
    }

    /**
     * Test that calling system data transfer API without first having acquired user consent
     * results in triggering error callback.
     */
    @Test(expected = CompanionException::class)
    fun test_startSystemDataTransfer_requiresUserConsent() {
        val association = associate()

        // Generate data packet with successful response
        val response = generatePacket(MESSAGE_RESPONSE_SUCCESS, "SUCCESS")

        // This will fail due to lack of user consent
        startSystemDataTransfer(association.id, response)
    }

    /**
     * Test that system data transfer triggers success callback when CDM receives successful
     * response from the device whose permissions are being restored.
     */
    @Test
    @Ignore("b/324260135")
    fun test_startSystemDataTransfer_success() {
        val association = associate()
        requestPermissionTransferUserConsent(association.id, ACTION_CLICK_ALLOW)

        // Generate data packet with successful response
        val response = generatePacket(MESSAGE_RESPONSE_SUCCESS, "SUCCESS")
        startSystemDataTransfer(association.id, response)
    }

    /**
     * Test that system data transfer triggers error callback when CDM receives failure response
     * from the device whose permissions are being restored.
     */
    @Test(expected = CompanionException::class)
    @Ignore("b/324260135")
    fun test_startSystemDataTransfer_failure() {
        val association = associate()
        requestPermissionTransferUserConsent(association.id, ACTION_CLICK_ALLOW)

        // Generate data packet with failure as response
        val response = generatePacket(MESSAGE_RESPONSE_FAILURE, "FAILURE")
        startSystemDataTransfer(association.id, response)
    }

    /**
     * Test that CDM sends a response to incoming request to restore permissions.
     *
     * This test uses a mock request with an empty body, so just assert that CDM sends any response.
     */
    @Test
    fun test_receivePermissionRestore() {
        assumeTrue(FeatureUtil.isWatch())

        val association = associate()

        // Generate data packet with permission restore request
        val bytes = generatePacket(MESSAGE_REQUEST_PERMISSION_RESTORE)
        val input = ByteArrayInputStream(bytes)

        // Monitor output response from CDM
        val messageSent = CountDownLatch(1)
        val sentMessage = AtomicInteger()
        val output = MonitoredOutputStream { message ->
            sentMessage.set(message)
            messageSent.countDown()
        }

        // "Receive" permission restore request
        cdm.attachSystemDataTransport(association.id, input, output)

        // Assert CDM sent a message
        assertTrue(messageSent.await(SYSTEM_DATA_TRANSFER_TIMEOUT, TimeUnit.SECONDS))

        // Assert that sent message was in response format (can be success or failure)
        assertTrue(isResponse(sentMessage.get()))
    }

    /**
     * Associate without checking the association data.
     */
    private fun associate(): AssociationInfo {
        sendRequestAndLaunchConfirmation(singleDevice = true)
        confirmationUi.scrollToBottom()
        callback.assertInvokedByActions {
            // User "approves" the request.
            confirmationUi.clickPositiveButton()
        }
        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()
        // Check the result code and the data delivered via onActivityResult()
        val (_: Int, associationData: Intent?) = CompanionActivity.waitForActivityResult()
        assertNotNull(associationData)
        val association: AssociationInfo? = associationData.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java
        )
        assertNotNull(association)

        return association
    }

    /**
     * Execute UI flow to request user consent for permission transfer for a given association
     * and grant permission.
     */
    private fun requestPermissionTransferUserConsent(associationId: Int, action: Int): Int {
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(associationId)
        CompanionActivity.startIntentSender(pendingUserConsent!!)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        when (action) {
            ACTION_CLICK_ALLOW -> confirmationUi.clickPositiveButton()
            ACTION_CLICK_DISALLOW -> confirmationUi.clickNegativeButton()
            ACTION_PRESS_BACK -> {
                uiDevice.pressBack()
                return -100 // an invalid result code which shouldn't be checked against
            }
            else -> throw IllegalStateException("Unknown action.")
        }
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        return resultCode
    }

    /**
     * Start system data transfer synchronously.
     */
    private fun startSystemDataTransfer(
            associationId: Int,
            simulatedResponse: ByteArray
    ) {
        // Piped input stream to simulate any response for CDM to receive
        val inputSource = PipedOutputStream()
        val pipedInput = PipedInputStream(inputSource)

        // Only receive simulated response after permission restore request is sent
        val monitoredOutput = MonitoredOutputStream { message ->
            if (message == MESSAGE_REQUEST_PERMISSION_RESTORE) {
                inputSource.write(simulatedResponse)
                inputSource.flush()
            }
        }
        cdm.attachSystemDataTransport(associationId, pipedInput, monitoredOutput)

        // Synchronously start system data transfer
        val transferFinished = CountDownLatch(1)
        val err = AtomicReference<CompanionException>()
        val callback = object : OutcomeReceiver<Void?, CompanionException> {
            override fun onResult(result: Void?) {
                transferFinished.countDown()
            }

            override fun onError(error: CompanionException) {
                err.set(error)
                transferFinished.countDown()
            }
        }
        cdm.startSystemDataTransfer(associationId, context.mainExecutor, callback)

        // Don't let it hang for too long!
        if (!transferFinished.await(SYSTEM_DATA_TRANSFER_TIMEOUT, TimeUnit.SECONDS)) {
            throw TimeoutException("System data transfer timed out.")
        }

        // Catch transfer failure
        if (err.get() != null) {
            throw err.get()
        }

        // Detach data transport
        cdm.detachSystemDataTransport(associationId)
    }
}

/**
 * Message codes defined in [com.android.server.companion.transport.CompanionTransportManager].
 */
private const val MESSAGE_RESPONSE_SUCCESS = 0x33838567
private const val MESSAGE_RESPONSE_FAILURE = 0x33706573
private const val MESSAGE_REQUEST_PERMISSION_RESTORE = 0x63826983
private const val HEADER_LENGTH = 12

/** Generate byte array containing desired header and data */
private fun generatePacket(message: Int, data: String? = null): ByteArray {
    val bytes = data?.toByteArray(StandardCharsets.UTF_8) ?: EmptyArray.BYTE

    // Construct data packet with header + data
    return ByteBuffer.allocate(bytes.size + 12)
            .putInt(message) // message type
            .putInt(1) // message sequence
            .putInt(bytes.size) // data size
            .put(bytes) // actual data
            .array()
}

/** Message is the first 4-bytes of the stream, so just wrap the whole packet in an Integer. */
private fun messageOf(packet: ByteArray) = ByteBuffer.wrap(packet).int

/**
 * Message is a response if the first byte of the message is 0x33.
 *
 * See [com.android.server.companion.transport.CompanionTransportManager].
 */
private fun isResponse(message: Int): Boolean {
    return (message and 0xFF000000.toInt()) == 0x33000000
}

/**
 * Monitors the output from transport manager to detect when a full header (12-bytes) is sent and
 * trigger callback with the message sent (4-bytes).
 */
private class MonitoredOutputStream(
        private val onHeaderSent: (Int) -> Unit
) : ByteArrayOutputStream() {
    private var callbackInvoked = false

    override fun flush() {
        super.flush()
        if (!callbackInvoked && size() >= HEADER_LENGTH) {
            onHeaderSent.invoke(messageOf(toByteArray()))
            callbackInvoked = true
        }
    }
}
