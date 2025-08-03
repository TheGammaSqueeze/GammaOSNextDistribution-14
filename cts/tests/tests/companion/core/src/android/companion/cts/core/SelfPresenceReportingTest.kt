/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED
import android.companion.Flags
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.assertInvalidCompanionDeviceServicesNotBound
import android.companion.cts.common.assertOnlyPrimaryCompanionDeviceServiceNotified
import android.companion.cts.common.assertValidCompanionDeviceServicesBind
import android.companion.cts.common.assertValidCompanionDeviceServicesRemainBound
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests CDM APIs for notifying the presence of status of the companion devices for self-managed
 * associations.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerCoreTestCases:SelfPresenceReportingTest
 *
 * @see android.companion.CompanionDeviceManager.notifyDeviceAppeared
 * @see android.companion.CompanionDeviceManager.notifyDeviceDisappeared
 * @see android.companion.CompanionDeviceService.onDeviceAppeared
 * @see android.companion.CompanionDeviceService.onDeviceDisappeared
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SelfPresenceReportingTest : CoreTestBase() {

    @Test
    fun test_selfReporting_singleDevice_multipleServices() =
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        cdm.notifyDeviceAppeared(associationId)

        // Assert only the valid CompanionDeviceServices (primary + secondary) are bound
        assertValidCompanionDeviceServicesBind()
        assertInvalidCompanionDeviceServicesNotBound()

        // Assert only the primary CompanionDeviceService is notified of device appearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(associationId, appeared = true)

        // Assert both valid CompanionDeviceServices stay bound
        assertValidCompanionDeviceServicesRemainBound()
        if (Flags.devicePresence()) {
            assertEquals(
                    expected = EVENT_SELF_MANAGED_APPEARED,
                    actual = PrimaryCompanionService.getCurrentEvent()
            )
        }

        cdm.notifyDeviceDisappeared(associationId)

        // Assert only the primary CompanionDeviceService is notified of device disappearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(associationId, appeared = false)
        if (Flags.devicePresence()) {
            assertEquals(
                    expected = EVENT_SELF_MANAGED_DISAPPEARED,
                    actual = PrimaryCompanionService.getCurrentEvent()
            )
        }
        // Assert both services are unbound now
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_selfReporting_multipleDevices_multipleServices() {
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        val idB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        cdm.notifyDeviceAppeared(idA)

        // Assert only the valid CompanionDeviceServices (primary + secondary) are bound
        assertValidCompanionDeviceServicesBind()
        assertInvalidCompanionDeviceServicesNotBound()

        // Assert only the primary CompanionDeviceService is notified of device A's appearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idA, appeared = true)
        assertContentEquals(
                actual = PrimaryCompanionService.associationIdsForConnectedDevices,
                expected = setOf(idA)
        )

        cdm.notifyDeviceAppeared(idB)

        // Assert only the primary CompanionDeviceService is notified of device B's appearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idB, appeared = true)
        assertContentEquals(
            actual = PrimaryCompanionService.associationIdsForConnectedDevices,
            expected = setOf(idA, idB)
        )

        // Make sure both valid services stay bound.
        assertValidCompanionDeviceServicesRemainBound()

        // "Disconnect" first device (A).
        cdm.notifyDeviceDisappeared(idA)

        // Assert only the primary CompanionDeviceService is notified of device A's disappearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idA, appeared = false)

        // Both valid services should stay bound for as long as there is at least one connected
        // device - device B in this case.
        assertValidCompanionDeviceServicesRemainBound()

        // "Disconnect" second (and last remaining) device (B).
        cdm.notifyDeviceDisappeared(idB)

        // Assert only the primary CompanionDeviceService is notified of device B's disappearance
        assertOnlyPrimaryCompanionDeviceServiceNotified(idB, appeared = false)

        // Both valid services should unbind now.
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_notifyAppearAndDisappear_invalidId() {
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(-1) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(0) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(1) }

        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(-1) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(0) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(1) }
    }

    @Test
    fun test_notifyAppears_requires_selfManagedAssociation() {
        // Create NOT "self-managed" association
        targetApp.associate(MAC_ADDRESS_A)

        val id = cdm.myAssociations[0].id

        // notifyDeviceAppeared can only be called for self-managed associations.
        assertFailsWith(IllegalArgumentException::class) {
            cdm.notifyDeviceAppeared(id)
        }
    }

    @Test
    fun test_notifyAppears_from_onAssociationCreated() {
        // Create a self-managed association and call notifyDeviceAppeared() right from the
        // Callback.onAssociationCreated()
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A) {
            cdm.notifyDeviceAppeared(it.id)
        }

        // Avoid race condition where onDeviceDisappeared() is sometimes processed first
        PrimaryCompanionService.waitAssociationToAppear(associationId)

        // Make sure CDM binds both CompanionDeviceServices.
        assertValidCompanionDeviceServicesBind()

        // Notify CDM that devices has disconnected.
        cdm.notifyDeviceDisappeared(associationId)

        // Make sure CDM unbinds both CompanionDeviceServices.
        assertValidCompanionDeviceServicesUnbind()
    }
}
