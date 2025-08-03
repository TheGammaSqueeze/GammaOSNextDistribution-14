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

package android.companion.cts.core

import android.Manifest
import android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED
import android.companion.DevicePresenceEvent.EVENT_BLE_DISAPPEARED
import android.companion.Flags
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SecondaryCompanionService
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.assertInvalidCompanionDeviceServicesNotBound
import android.companion.cts.common.assertOnlyPrimaryCompanionDeviceServiceNotified
import android.companion.cts.common.assertValidCompanionDeviceServicesBind
import android.companion.cts.common.assertValidCompanionDeviceServicesRemainBound
import android.companion.cts.common.assertValidCompanionDeviceServicesRemainUnbound
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.companion.cts.common.toUpperCaseString
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for observing device presence.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ObservingDevicePresenceTest
 *
 * @see android.companion.CompanionDeviceManager.startObservingDevicePresence
 * @see android.companion.CompanionDeviceManager.stopObservingDevicePresence
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class ObservingDevicePresenceTest : CoreTestBase() {

    @Test
    fun test_observingDevicePresence_isOffByDefault() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(associationId)
        }

        // Make sure CDM does not bind application
        assertValidCompanionDeviceServicesRemainUnbound()

        // ... and does not trigger onDeviceAppeared ()
        assertEmpty(PrimaryCompanionService.connectedDevices)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(associationId)
        }
    }

    @Test
    fun test_startObservingDevicePresence_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)

        // Attempts to call startObservingDevicePresence without the
        // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE  permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Same call with the REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_startObservingDevicePresence_singleDevice() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
        // Simulate device appeared.
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(associationId)
        }

        // Make sure valid CompanionDeviceServices are bound
        assertValidCompanionDeviceServicesBind()

        // ... and incorrectly defined CompanionDeviceServices are not
        assertInvalidCompanionDeviceServicesNotBound()

        // Check that only the primary CompanionDeviceService has received the onDeviceAppeared()
        // callback...
        assertOnlyPrimaryCompanionDeviceServiceNotified(associationId, appeared = true)

        // Make sure that both primary and secondary CompanionDeviceServices still bind.
        assertValidCompanionDeviceServicesRemainBound()

        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(associationId)
        }

        // Check that only the primary services has received the onDeviceDisappeared() callback.
        assertOnlyPrimaryCompanionDeviceServiceNotified(associationId, appeared = false)

        // Both primary and secondary CompanionDeviceServices should unbind now.
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_stopObservingDevicePresence() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Start and stop observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Simulate device appeared.
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(associationId)
        }

        // Make sure CDM does not bind application
        assertValidCompanionDeviceServicesRemainUnbound()

        // ... and does not trigger onDeviceAppeared ()
        assertEmpty(PrimaryCompanionService.connectedDevices)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        // Simulate device disappeared.
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(associationId)
        }
    }

    @Test
    fun test_startObservingDevicePresence_alreadyPresent() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        // Simulate device appearing before observing it
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(associationId)
        }

        // Make sure CDM doesn't bind application yet
        assertValidCompanionDeviceServicesRemainUnbound()
        // Start observing presence of an already present device.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Make sure valid CompanionDeviceServices are bound
        assertValidCompanionDeviceServicesBind()
        assertInvalidCompanionDeviceServicesNotBound()

        // Clean-up
        if (Flags.devicePresence()) {
            simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(associationId)
        }
    }

    @Test
    fun test_startObservingDevicePresence_multipleDevices() {
        // Create two regular (not self-managed) associations.
        targetApp.associate(MAC_ADDRESS_A)
        targetApp.associate(MAC_ADDRESS_B)
        val idA = cdm.myAssociations[0].id
        val idB = cdm.myAssociations[1].id

        // Start observing presence of both devices.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
            cdm.startObservingDevicePresence(MAC_ADDRESS_B.toUpperCaseString())
        }
        if (Flags.devicePresence()) {
            simulateDeviceEvent(idA, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(idA)
        }

        // Assert only the valid CompanionDeviceServices (primary + secondary) bind
        assertValidCompanionDeviceServicesBind()
        assertInvalidCompanionDeviceServicesNotBound()

        // Assert only the primary CompanionDeviceService is notified of device A's appearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idA, appeared = true)
        assertContentEquals(
                actual = PrimaryCompanionService.associationIdsForConnectedDevices,
                expected = setOf(idA)
        )

        if (Flags.devicePresence()) {
            simulateDeviceEvent(idB, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(idB)
        }

        // Assert only the primary CompanionDeviceService is notified of device B's appearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idB, appeared = true)
        assertContentEquals(
                actual = PrimaryCompanionService.associationIdsForConnectedDevices,
                expected = setOf(idA, idB)
        )

        // Make sure both valid services stay bound.
        assertValidCompanionDeviceServicesRemainBound()

        // "Disconnect" first device (A).
        if (Flags.devicePresence()) {
            simulateDeviceEvent(idA, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(idA)
        }

        // Assert only the primary CompanionDeviceService is notified of device A's disappearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idA, appeared = false)

        // Both valid services should stay bound for as long as there is at least one connected
        // device - device B in this case.
        assertValidCompanionDeviceServicesRemainBound()

        // "Disconnect" second (and last remaining) device (B).
        if (Flags.devicePresence()) {
            simulateDeviceEvent(idB, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(idB)
        }

        // Assert only the primary CompanionDeviceService is notified of device B's disappearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idB, appeared = false)

        // Both valid services should unbind now.
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_stopObservingDevicePresence_unbindsApplication() {
        // Create two regular (not self-managed) association.s
        targetApp.associate(MAC_ADDRESS_A)
        targetApp.associate(MAC_ADDRESS_B)
        val idA = cdm.myAssociations[0].id
        val idB = cdm.myAssociations[1].id

        // Start observing presence of both devices.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
            cdm.startObservingDevicePresence(MAC_ADDRESS_B.toUpperCaseString())
        }

        if (Flags.devicePresence()) {
            simulateDeviceEvent(idA, EVENT_BLE_APPEARED)
            simulateDeviceEvent(idB, EVENT_BLE_APPEARED)
        } else {
            simulateDeviceAppeared(idA)
            simulateDeviceAppeared(idB)
        }

        // Assert only the valid CompanionDeviceServices (primary + secondary) bind
        assertValidCompanionDeviceServicesBind()
        assertInvalidCompanionDeviceServicesNotBound()

        // Stop observing presence of device A.
        PrimaryCompanionService.forgetDevicePresence(idA)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Make sure both valid services stay bound.
        assertValidCompanionDeviceServicesRemainBound()

        // Stop observing presence of device B.
        PrimaryCompanionService.forgetDevicePresence(idB)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_B.toUpperCaseString())
        }

        // Both valid services should unbind now.
        assertValidCompanionDeviceServicesUnbind()
        if (Flags.devicePresence()) {
            simulateDeviceEvent(idA, EVENT_BLE_DISAPPEARED)
            simulateDeviceEvent(idB, EVENT_BLE_DISAPPEARED)
        } else {
            simulateDeviceDisappeared(idA)
            simulateDeviceDisappeared(idB)
        }
    }

    private fun simulateDeviceAppeared(associationId: Int) = runShellCommand(
            "cmd companiondevice simulate-device-appeared $associationId"
    )

    private fun simulateDeviceDisappeared(associationId: Int) = runShellCommand(
            "cmd companiondevice simulate-device-disappeared $associationId"
    )
}
