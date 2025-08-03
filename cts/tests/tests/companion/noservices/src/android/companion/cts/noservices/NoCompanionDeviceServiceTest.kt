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

package android.companion.cts.noservices

import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.MissingIntentFilterActionCompanionService
import android.companion.cts.common.MissingPermissionCompanionService
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SecondaryCompanionService
import android.companion.cts.common.TestBase
import android.companion.cts.common.waitFor
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests CDM handling the case when the companion application does not define a valid
 * [CompanionDeviceService][android.companion.CompanionDeviceService].
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerNoCompanionServicesTestCases:NoCompanionDeviceServiceTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class NoCompanionDeviceServiceTest : TestBase() {

    /**
     * Ensures that CDM service DOES NOT try to bind
     * [CompanionDeviceServices][android.companion.CompanionDeviceService] that do not meet all the
     * requirements, as well as that the system's stability in case when the companion applications
     * do not define any valid CompanionDeviceServices.
     */
    @Test
    fun test_noService() =
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
                val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

                // This should neither throw an Exception nor cause system to crash, even when the
                // companion application does not define any valid CompanionDeviceServices.
                // (If the system crashes this instrumentation test won't complete).
                cdm.notifyDeviceAppeared(associationId)

                // Every 100ms check if any of the services is bound or received a callback.
                assertFalse("None of the services should be bound or receive a callback") {
                    waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                        val isBound = PrimaryCompanionService.isBound ||
                                SecondaryCompanionService.isBound ||
                                MissingPermissionCompanionService.isBound ||
                                MissingIntentFilterActionCompanionService.isBound
                        val receivedCallback =
                                PrimaryCompanionService.connectedDevices.isNotEmpty() ||
                                SecondaryCompanionService.connectedDevices.isNotEmpty() ||
                                MissingPermissionCompanionService.connectedDevices.isNotEmpty() ||
                                MissingIntentFilterActionCompanionService.connectedDevices
                                        .isNotEmpty()
                        return@waitFor isBound || receivedCallback
                    }
                }

                // This should neither throw an Exception nor cause system to crash, even when the
                // companion application does not define any valid CompanionDeviceServices.
                // (If the system crashes this instrumentation test won't complete).
                cdm.notifyDeviceDisappeared(associationId)
            }
}