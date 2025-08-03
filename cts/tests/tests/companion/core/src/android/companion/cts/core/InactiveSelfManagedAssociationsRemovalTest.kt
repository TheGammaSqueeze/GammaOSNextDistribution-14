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

import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.assertAssociations
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.assertSelfManagedAssociations
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.companion.cts.common.sleepFor
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.ZERO

/**
 * Test [com.android.server.companion.CompanionDeviceManagerService.removeInactiveSelfManagedAssociations].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:InactiveSelfManagedAssociationsRemovalTest
 *
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class InactiveSelfManagedAssociationsRemovalTest : CoreTestBase() {

    @Test
    fun test_inactive_associations_removal() {
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A) {
            cdm.notifyDeviceAppeared(it.id)
        }
        val idB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B) {
            cdm.notifyDeviceAppeared(it.id)
        }

        setSystemPropertyDuration(1.seconds, SYS_PROP_DEBUG_REMOVAL_TIME_WINDOW)
        // Give it 1 second for setting up the removalWindow.
        sleepFor(1.seconds)
        // "Disconnect" device A and B since we are not able to disconnect them after the
        // associations are removed.
        cdm.notifyDeviceDisappeared(idA)
        cdm.notifyDeviceDisappeared(idB)
        // Assert both services are unbound now
        assertValidCompanionDeviceServicesUnbind()
        // Start running the removal job.
        removeInactiveSelfManagedAssociations()
        // Give it 1 seconds for running the removal job.
        sleepFor(1.seconds)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_inactive_associations_removal_multiple_types() = with(targetApp) {
        // Create the selfManaged association.
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A) {
            cdm.notifyDeviceAppeared(it.id)
        }
        // Create the normal association.
        associate(MAC_ADDRESS_A)

        setSystemPropertyDuration(1.seconds, SYS_PROP_DEBUG_REMOVAL_TIME_WINDOW)
        // Give it 1 second for setting up the removalWindow.
        sleepFor(1.seconds)
        // "Disconnect" device A since we are not able to  disconnect it after the associations
        // are removed.
        cdm.notifyDeviceDisappeared(idA)
        // Assert both services are unbound now
        assertValidCompanionDeviceServicesUnbind()
        // Start running the removal job.
        removeInactiveSelfManagedAssociations()
        // Give it 1 seconds for running the removal job.
        sleepFor(1.seconds)
        // selfManaged association should be removed
        assertAssociations(
            actual = cdm.myAssociations,
            expected = setOf(
                packageName to MAC_ADDRESS_A
            )
        )
    }

    @Test
    fun test_active_associations_do_not_get_removed() = with(targetApp) {
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A) {
            cdm.notifyDeviceAppeared(it.id)
        }

        setSystemPropertyDuration(10.seconds, SYS_PROP_DEBUG_REMOVAL_TIME_WINDOW)
        // Give it 1 second for setting up the removalWindow.
        sleepFor(1.seconds)
        // "Disconnect" device A and B since we are not able to disconnect them after the
        // associations are removed.
        cdm.notifyDeviceDisappeared(idA)
        // Assert both services are unbound now
        assertValidCompanionDeviceServicesUnbind()
        // Start running the removal job.
        removeInactiveSelfManagedAssociations()
        // Give it 1 seconds for running the removal job.
        sleepFor(1.seconds)
        // Active selfManaged associations should not be removed
        assertSelfManagedAssociations(
            actual = cdm.myAssociations,
            expected = setOf(
                packageName to idA
            )
        )
    }

    override fun tearDown() {
        setSystemPropertyDuration(ZERO, SYS_PROP_DEBUG_REMOVAL_TIME_WINDOW)
        super.tearDown()
    }

    private fun removeInactiveSelfManagedAssociations() =
        runShellCommand("cmd companiondevice remove-inactive-associations")

    companion object {
        private const val SYS_PROP_DEBUG_REMOVAL_TIME_WINDOW =
            "debug.cdm.cdmservice.removal_time_window"
    }
}