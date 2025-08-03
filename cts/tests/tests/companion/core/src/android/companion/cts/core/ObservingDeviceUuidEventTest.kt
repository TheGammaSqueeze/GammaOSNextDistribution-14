/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED
import android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED
import android.companion.Flags
import android.companion.ObservingDevicePresenceRequest
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.UUID_A
import android.companion.cts.common.UUID_B
import android.companion.cts.common.assertValidCompanionDeviceServicesBind
import android.companion.cts.common.assertValidCompanionDeviceServicesRemainBound
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for observing device presence base on the UUID.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ObservingDeviceUuidEventTest
 *
 * @see android.companion.CompanionDeviceManager.startObservingDevicePresence
 * @see android.companion.CompanionDeviceManager.stopObservingDevicePresence
 * @see android.companion.CompanionDeviceService.onDeviceEventByUuid
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_PRESENCE)
class ObservingDeviceUuidEventTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val request_A = ObservingDevicePresenceRequest.Builder().setUuid(UUID_A).build()
    private val request_B = ObservingDevicePresenceRequest.Builder().setUuid(UUID_B).build()
    override fun tearDown() {
        PrimaryCompanionService.clearDeviceUuidPresence()
        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.stopObservingDevicePresence(request_A)
            cdm.stopObservingDevicePresence(request_B)
        }

        super.tearDown()
    }

    @Test
    fun test_startObservingDeviceUuidPresence_requiresPermission() {
        assertFailsWith(SecurityException::class) {
            withShellPermissionIdentity(
                Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
            ) {
                cdm.startObservingDevicePresence(request_A)
            }
        }

        assertFailsWith(SecurityException::class) {
            withShellPermissionIdentity(
                Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
            ) {
                cdm.stopObservingDevicePresence(request_A)
            }
        }

        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
                cdm.startObservingDevicePresence(request_A)
        }

        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
                cdm.stopObservingDevicePresence(request_A)
        }
    }

    @Test
    fun test_DevicePresenceRequest_set_both_uuid_mac_address() {
        // Create a regular association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                    .setUuid(UUID_A).setAssociationId(associationId).build()
        }
    }

    @Test
    fun test_startObservingDeviceUuidPresence_singleDevice() {
        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE
        ) {
            cdm.startObservingDevicePresence(request_A)
        }

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitDeviceUuidConnect(UUID_A)

        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        assertValidCompanionDeviceServicesBind()

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitDeviceUuidDisconnect(UUID_A)

        assertEquals(
                expected = EVENT_BT_DISCONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.stopObservingDevicePresence(request_A)
        }

        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_startObservingDeviceUuidPresence_multiDevices() {
        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.startObservingDevicePresence(request_A)
            cdm.startObservingDevicePresence(request_B)
        }

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitDeviceUuidConnect(UUID_A)

        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        assertContentEquals(
                actual = PrimaryCompanionService.connectedUuidBondDevices,
                expected = setOf(UUID_A)
        )

        assertValidCompanionDeviceServicesBind()

        simulateDeviceUuidEvent(UUID_B, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitDeviceUuidConnect(UUID_B)

        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        assertContentEquals(
                actual = PrimaryCompanionService.connectedUuidBondDevices,
                expected = setOf(UUID_A, UUID_B)
        )

        assertValidCompanionDeviceServicesBind()

        simulateDeviceUuidEvent(UUID_B, EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitDeviceUuidDisconnect(UUID_B)

        assertValidCompanionDeviceServicesRemainBound()

        assertContentEquals(
                actual = PrimaryCompanionService.connectedUuidBondDevices,
                expected = setOf(UUID_A)
        )

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitDeviceUuidDisconnect(UUID_A)

        assertEquals(
                actual = PrimaryCompanionService.connectedUuidBondDevices.size,
                expected = 0
        )

        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.stopObservingDevicePresence(request_B)
            cdm.stopObservingDevicePresence(request_A)
        }

        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_observingDevicePresence_bothAssociationAndUuid() {
        // Create a regular association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val requestMacAddress = ObservingDevicePresenceRequest.Builder().setAssociationId(
                associationId
        ).build()
        // Start observing by MAC_ADDRESS.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(requestMacAddress)
        }

        simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertEquals(
                expected = EVENT_BLE_APPEARED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )
        // Start observing by UUID.
        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.startObservingDevicePresence(request_A)
        }

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitDeviceUuidConnect(UUID_A)
        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        // Now, stop observing by MAC_ADDRESS.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(requestMacAddress)
        }

        // Service should remain binding.
        assertValidCompanionDeviceServicesBind()

        simulateDeviceUuidEvent(UUID_A, EVENT_BT_DISCONNECTED)

        // Lastly, stop observing by UUID.
        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
        ) {
            cdm.stopObservingDevicePresence(request_A)
        }

        // Service should be unbound.
        assertValidCompanionDeviceServicesUnbind()
    }
}
