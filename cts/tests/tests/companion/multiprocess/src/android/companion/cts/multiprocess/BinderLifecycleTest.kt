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

package android.companion.cts.multiprocess

import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.PRIMARY_PROCESS_NAME
import android.companion.cts.common.SECONDARY_PROCESS_NAME
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertApplicationBinds
import android.companion.cts.common.assertApplicationRemainsBound
import android.companion.cts.common.assertApplicationUnbinds
import android.companion.cts.common.killProcess
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM behavior at various parts of binder lifecycle.
 *
 * Run: atest CtsCompanionDeviceManagerMultiProcessTestCases:BinderLifecycleTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class BinderLifecycleTest : TestBase() {
    @Test
    fun test_binderDied_primaryProcess() {
        // Create a self-managed association.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        // Publish device's presence and wait for callback.
        cdm.notifyDeviceAppeared(associationId)
        assertApplicationBinds(cdm)
        // Give 2 seconds to start the process
        SystemClock.sleep(2000)

        // Kill primary process
        killProcess(PRIMARY_PROCESS_NAME)

        assertApplicationUnbinds(cdm)
    }

    @Test
    fun test_binderDied_secondaryProcess() {
        // Create a self-managed association.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        // Publish device's presence and wait for callback.
        cdm.notifyDeviceAppeared(associationId)
        assertApplicationBinds(cdm)

        // Wait for secondary service to start
        SystemClock.sleep(2000)

        // Kill secondary process
        killProcess(SECONDARY_PROCESS_NAME)
        assertApplicationRemainsBound(cdm)
    }
}
