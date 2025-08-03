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

import android.Manifest
import android.annotation.CallSuper
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.role.RoleManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothDeviceFilterUtils
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceManager.REASON_CANCELED
import android.companion.CompanionDeviceManager.REASON_DISCOVERY_TIMEOUT
import android.companion.CompanionDeviceManager.REASON_USER_REJECTED
import android.companion.CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT
import android.companion.CompanionDeviceManager.RESULT_USER_REJECTED
import android.companion.DeviceFilter
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.DEVICE_PROFILES
import android.companion.cts.common.DEVICE_PROFILE_TO_NAME
import android.companion.cts.common.DEVICE_PROFILE_TO_PERMISSION
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.OnAssociationPending
import android.companion.cts.common.RecordingCallback.OnFailure
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.waitFor
import android.companion.cts.uicommon.CompanionDeviceManagerUi
import android.content.Intent
import android.net.MacAddress
import android.os.Parcelable
import android.os.SystemClock.sleep
import androidx.test.uiautomator.UiDevice
import java.util.regex.Pattern
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Assume.assumeFalse
import org.junit.BeforeClass

open class UiAutomationTestBase(
    protected val profile: String?,
    private val profilePermission: String?
) : TestBase() {
    private val roleManager: RoleManager by lazy {
        context.getSystemService(RoleManager::class.java)!!
    }

    val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    // CDM discovery requires bluetooth is enabled, enable the location if it was disabled.
    var bluetoothWasEnabled: Boolean = false
    protected val confirmationUi = CompanionDeviceManagerUi(uiDevice)
    protected val callback by lazy { RecordingCallback() }
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    @CallSuper
    override fun setUp() {
        super.setUp()

        assumeFalse(confirmationUi.isVisible)
        Assume.assumeTrue(CompanionActivity.waitUntilGone())

        uiDevice.waitForIdle()

        callback.clearRecordedInvocations()

        // Make RoleManager bypass role qualification, which would allow this self-instrumenting
        // test package to hold "systemOnly"" CDM roles (e.g. COMPANION_DEVICE_APP_STREAMING and
        // SYSTEM_AUTOMOTIVE_PROJECTION)
        withShellPermissionIdentity { roleManager.isBypassingRoleQualification = true }
    }

    @CallSuper
    override fun tearDown() {
        // If the profile (role) is not null: remove the app from the role holders.
        // Do it via Shell (using the targetApp) because RoleManager takes way too many arguments.
        profile?.let { roleName -> targetApp.removeFromHoldersOfRole(roleName) }

        // Restore disallowing role qualifications.
        withShellPermissionIdentity { roleManager.isBypassingRoleQualification = false }

        CompanionActivity.safeFinish()
        CompanionActivity.waitUntilGone()

        confirmationUi.dismiss()
        confirmationUi.waitUntilGone()

        restoreDiscoveryTimeout()

        super.tearDown()
    }

    protected fun test_userRejected(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null
    ) = test_cancelled(singleDevice, selfManaged, userRejected = true, displayName) {
            // User "rejects" the request.
            if (singleDevice || selfManaged) {
                confirmationUi.clickNegativeButton()
            } else {
                confirmationUi.clickNegativeButtonMultipleDevices()
            }
    }

    protected fun test_userDismissed(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null
    ) = test_cancelled(singleDevice, selfManaged, userRejected = false, displayName) {
            // User "dismisses" the request.
            uiDevice.pressBack()
        }

    private fun test_cancelled(
        singleDevice: Boolean,
        selfManaged: Boolean,
        userRejected: Boolean,
        displayName: String?,
        cancelAction: () -> Unit
    ) {
        // Give the discovery service extra time to find the first match device before
        // pressing the negative button for singleDevice && userRejected.
        if (singleDevice) {
            setSystemPropertyDuration(2.seconds, SYS_PROP_DEBUG_DISCOVERY_TIMEOUT)
        }

        sendRequestAndLaunchConfirmation(singleDevice, selfManaged, displayName)

        if (singleDevice) {
            // The discovery timeout is 2 sec, but let's wait for 3. So that we have enough
            // time to wait until the dialog appeared.
            sleep(3.seconds.inWholeMilliseconds)
        }

        if ((singleDevice || selfManaged) && profile != null) {
            confirmationUi.scrollToBottom()
        }
        // Test can stop here since there's no device found after discovery timeout.
        assumeFalse(callback.invocations.contains(OnFailure(REASON_DISCOVERY_TIMEOUT)))
        // Check callback invocations: There should be 0 invocation before any actions are made.
        assertEmpty(callback.invocations)

        callback.assertInvokedByActions {
            cancelAction()
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        val expectedError = if (userRejected) REASON_USER_REJECTED else REASON_CANCELED
        assertContentEquals(
            actual = callback.invocations,
            expected = listOf(OnFailure(expectedError))
        )
        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        val expectedResultCode = if (userRejected) RESULT_USER_REJECTED else RESULT_CANCELED
        assertEquals(actual = resultCode, expected = expectedResultCode)
        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

    protected fun test_timeout(singleDevice: Boolean = false) {
        // Set discovery timeout to 2 seconds to avoid flaky that
        // there's a chance CDM UI is disappeared before waitUntilVisible
        // is called.
        setSystemPropertyDuration(2.seconds, SYS_PROP_DEBUG_DISCOVERY_TIMEOUT)

        callback.assertInvokedByActions(2.seconds) {
            // Make sure no device will match the request
            sendRequestAndLaunchConfirmation(
                singleDevice = singleDevice,
                deviceFilter = UNMATCHABLE_BT_FILTER
            )
        }

        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        assertContentEquals(
            actual = callback.invocations,
            expected = listOf(OnFailure(REASON_DISCOVERY_TIMEOUT))
        )

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = RESULT_DISCOVERY_TIMEOUT)

        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

    protected fun test_userConfirmed_foundDevice(
        singleDevice: Boolean,
        confirmationAction: () -> Unit
    ) {
        sendRequestAndLaunchConfirmation(singleDevice = singleDevice)

        if (profile != null) {
            if (singleDevice) {
                confirmationUi.scrollToBottom()
                callback.assertInvokedByActions {
                    confirmationAction()
                }
            } else {
                // First, select the device in the device chooser dialog.
                confirmationUi.waitAndClickOnFirstFoundDevice()
                // Second, wait until the permissionList dialog shows up and scroll to the bottom.
                confirmationUi.scrollToBottom()
                // Third, tap the `Allow` bottom.
                callback.assertInvokedByActions {
                    confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
                }
            }
        } else {
            callback.assertInvokedByActions {
                confirmationAction()
            }
        }

        // Check callback invocations: there should have been exactly 1 invocation of the
        // OnAssociationCreated() method.
        assertEquals(1, callback.invocations.size)
        val associationInvocation = callback.invocations.first()
        assertIs<OnAssociationCreated>(associationInvocation)
        val associationFromCallback = associationInvocation.associationInfo

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code and the data delivered via onActivityResult()
        val (resultCode: Int, data: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_OK)
        assertNotNull(data)
        val associationFromActivityResult: AssociationInfo? = data.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java)
        assertNotNull(associationFromActivityResult)
        // Check that the association reported back via the callback same as the association
        // delivered via onActivityResult().
        assertEquals(associationFromCallback, associationFromActivityResult)

        // Make sure "device data" was included (for backwards compatibility)
        val deviceFromActivityResult = associationFromActivityResult.associatedDevice
        assertNotNull(deviceFromActivityResult)

        // At least one of three types of devices is not null and MAC address from this data
        // matches the MAC address from AssociationInfo
        val deviceData: Parcelable = listOf(
            deviceFromActivityResult.bluetoothDevice,
            deviceFromActivityResult.bleDevice,
            deviceFromActivityResult.wifiDevice
        ).firstNotNullOf { it }
        assertNotNull(deviceData)
        val deviceMacAddress = BluetoothDeviceFilterUtils.getDeviceMacAddress(deviceData)
        assertEquals(actual = MacAddress.fromString(deviceMacAddress),
                expected = associationFromCallback.deviceMacAddress)

        // Make sure getMyAssociations() returns the same association we received via the callback
        // as well as in onActivityResult()
        assertContentEquals(actual = cdm.myAssociations, expected = listOf(associationFromCallback))

        // Make sure that the role (for the current CDM device profile) was granted.
        assertIsProfileRoleHolder()
    }

    protected fun sendRequestAndLaunchConfirmation(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null,
        deviceFilter: DeviceFilter<*>? = null
    ) {
        val request = AssociationRequest.Builder()
                .apply {
                    // Set the single-device flag.
                    setSingleDevice(singleDevice)

                    // Set the self-managed flag.
                    setSelfManaged(selfManaged)

                    // Set profile if not null.
                    profile?.let { setDeviceProfile(it) }

                    // Set display name if not null.
                    displayName?.let { setDisplayName(it) }

                    // Add device filter if not null.
                    deviceFilter?.let { addDeviceFilter(it) }
                }
                .build()
        callback.clearRecordedInvocations()

        callback.assertInvokedByActions {
            // If the REQUEST_COMPANION_SELF_MANAGED and/or the profile permission is required:
            // run with these permissions as the Shell;
            // otherwise: just call associate().
            with(getRequiredPermissions(selfManaged)) {
                if (isNotEmpty()) {
                    withShellPermissionIdentity(*toTypedArray()) {
                        cdm.associate(request, SIMPLE_EXECUTOR, callback)
                    }
                } else {
                    cdm.associate(request, SIMPLE_EXECUTOR, callback)
                }
            }
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationPending() method.

        assertEquals(1, callback.invocations.size)
        val associationInvocation = callback.invocations.first()
        assertIs<OnAssociationPending>(associationInvocation)

        // Get intent sender and clear callback invocations.
        val pendingConfirmation = associationInvocation.intentSender
        callback.clearRecordedInvocations()

        // Launch CompanionActivity, and then launch confirmation UI from it.
        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingConfirmation)

        confirmationUi.waitUntilVisible()
    }

    /**
     * If the current CDM Device [profile] is not null, check that the application was "granted"
     * the corresponding role (all CDM device profiles are "backed up" by roles).
     */
    protected fun assertIsProfileRoleHolder() = profile?.let { roleName ->
        val roleHolders = withShellPermissionIdentity(Manifest.permission.MANAGE_ROLE_HOLDERS) {
            roleManager.getRoleHolders(roleName)
        }
        assertContains(roleHolders, targetPackageName, "Not a holder of $roleName")
    }

    protected fun getRequiredPermissions(selfManaged: Boolean): List<String> =
            mutableListOf<String>().also {
                if (selfManaged) it += Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
                if (profilePermission != null) it += profilePermission
            }

    private fun restoreDiscoveryTimeout() = setSystemPropertyDuration(
        ZERO, SYS_PROP_DEBUG_DISCOVERY_TIMEOUT
    )

    fun enableBluetoothIfNeeded() {
        bluetoothWasEnabled = bluetoothAdapter.isEnabled
        if (!bluetoothWasEnabled) {
            runShellCommand("svc bluetooth enable")
            val result = waitFor(timeout = 5.seconds, interval = 100.milliseconds) {
                bluetoothAdapter.isEnabled
            }
            assumeFalse("Not able to enable the bluetooth", !result)
        }
    }

    fun disableBluetoothIfNeeded() {
        if (!bluetoothWasEnabled) {
            runShellCommand("svc bluetooth disable")
            val result = waitFor(timeout = 5.seconds, interval = 100.milliseconds) {
                !bluetoothAdapter.isEnabled
            }
            assumeFalse("Not able to disable the bluetooth", !result)
        }
    }

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         */
        @JvmStatic
        protected fun supportedProfilesAndNull() = mutableListOf<Array<String?>>().apply {
            add(arrayOf(null, null, "null"))
            addAll(supportedProfiles())
        }

        /** List of (profile, permission, name) tuples that represent all supported profiles. */
        private fun supportedProfiles(): Collection<Array<String?>> = DEVICE_PROFILES.map {
            profile ->
            arrayOf(profile,
                    DEVICE_PROFILE_TO_PERMISSION[profile]!!,
                    DEVICE_PROFILE_TO_NAME[profile]!!)
        }

        private val UNMATCHABLE_BT_FILTER = BluetoothDeviceFilter.Builder()
                .setAddress("FF:FF:FF:FF:FF:FF")
                .setNamePattern(Pattern.compile("This Device Does Not Exist"))
                .build()

        private const val SYS_PROP_DEBUG_DISCOVERY_TIMEOUT = "debug.cdm.discovery_timeout"

        @JvmStatic
        @BeforeClass
        fun setupBeforeClass() {
            // Enable bluetooth if it was disabled.
            val uiAutomationTestBase = UiAutomationTestBase(null, null)
            uiAutomationTestBase.enableBluetoothIfNeeded()
        }

        @JvmStatic
        @AfterClass
        fun tearDownAfterClass() {
            // Disable bluetooth if it was disabled.
            val uiAutomationTestBase = UiAutomationTestBase(null, null)
            uiAutomationTestBase.disableBluetoothIfNeeded()
        }
    }
}
