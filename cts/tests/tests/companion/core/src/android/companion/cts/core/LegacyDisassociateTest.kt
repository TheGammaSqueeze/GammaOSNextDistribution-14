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

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.MAC_ADDRESS_C
import android.companion.cts.common.assertAssociations
import android.companion.cts.common.assertEmpty
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * Test legacy CDM APIs for removing existing associations (via MAC address)
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DisassociateTest
 *
 * @see android.companion.CompanionDeviceManager.disassociate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class LegacyDisassociateTest : CoreTestBase() {
    @Test
    fun test_legacy_disassociate_sameApp() = with(targetApp) {
        associate(MAC_ADDRESS_A)
        associate(MAC_ADDRESS_B)
        associate(MAC_ADDRESS_C)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A,
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                ))

        cdm.disassociate(MAC_ADDRESS_A.toString())
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                ))

        cdm.disassociate(MAC_ADDRESS_B.toString())
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_C
                ))

        cdm.disassociate(MAC_ADDRESS_C.toString())
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_legacy_disassociate_anotherApp() = with(testApp) {
        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        /** Cannot remove another app's association by MAC address. */
        assertFailsWith(IllegalArgumentException::class) {
            cdm.disassociate(MAC_ADDRESS_A.toString())
        }
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        /** ...not even with [MANAGE_COMPANION_DEVICES] permission. */
        assertFailsWith(IllegalArgumentException::class) {
            withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
                cdm.disassociate(MAC_ADDRESS_A.toString())
            }
        }
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))
    }

    @Test
    fun test_disassociate_invalidId() {
        assertEmpty(
                withShellPermissionIdentity {
                    cdm.allAssociations
                })

        assertFailsWith(IllegalArgumentException::class) {
            cdm.disassociate(MAC_ADDRESS_A.toString())
        }
        assertFailsWith(IllegalArgumentException::class) {
            cdm.disassociate(MAC_ADDRESS_B.toString())
        }
        assertFailsWith(IllegalArgumentException::class) {
            cdm.disassociate(MAC_ADDRESS_C.toString())
        }
    }
}