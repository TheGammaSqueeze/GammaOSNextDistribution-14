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
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.MAC_ADDRESS_C
import android.companion.cts.common.assertAssociations
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.getAssociationForPackage
import android.net.MacAddress
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for removing existing associations.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DisassociateTest
 *
 * @see android.companion.CompanionDeviceManager.disassociate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class DisassociateTest : CoreTestBase() {
    @Test
    fun test_disassociate_sameApp_singleAssociation() = with(targetApp) {
        associate(MAC_ADDRESS_A)

        val associations = cdm.myAssociations
        assertAssociations(
                actual = associations,
                expected = setOf(packageName to MAC_ADDRESS_A)
        )

        cdm.disassociate(associations[0].id)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_disassociate_sameApp_multipleAssociations() = with(targetApp) {
        associate(MAC_ADDRESS_A)
        associate(MAC_ADDRESS_B)
        associate(MAC_ADDRESS_C)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A,
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                )
        )

        cdm.disassociate(cdm.getMyAssociationLinkedTo(MAC_ADDRESS_A).id)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                )
        )

        cdm.disassociate(cdm.getMyAssociationLinkedTo(MAC_ADDRESS_B).id)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(packageName to MAC_ADDRESS_C)
        )

        cdm.disassociate(cdm.getMyAssociationLinkedTo(MAC_ADDRESS_C).id)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_disassociate_anotherApp_requiresPermission() = with(testApp) {
        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(packageName to MAC_ADDRESS_A)
        )

        val association = withShellPermissionIdentity {
            getAssociationForPackage(userId, packageName, MAC_ADDRESS_A, cdm)
        }

        /**
         * Attempts to remove another app's association without [MANAGE_COMPANION_DEVICES]
         * permission should throw an Exception and should not change the existing associations.
         */
        assertFailsWith(IllegalArgumentException::class) {
            cdm.disassociate(association.id)
        }
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(packageName to MAC_ADDRESS_A)
        )

        /**
         * Re-running with [MANAGE_COMPANION_DEVICES] permissions: now should succeed and remove
         * the association.
         */
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.disassociate(association.id)
        }
        assertEmpty(
                withShellPermissionIdentity {
                    cdm.allAssociations
                }
        )
    }

    @Test
    fun test_disassociate_invalidId() {
        assertEmpty(
                withShellPermissionIdentity {
                    cdm.allAssociations
                }
        )

        assertFailsWith(IllegalArgumentException::class) { cdm.disassociate(0) }
        assertFailsWith(IllegalArgumentException::class) { cdm.disassociate(1) }
        assertFailsWith(IllegalArgumentException::class) { cdm.disassociate(-1) }
    }

    private fun CompanionDeviceManager.getMyAssociationLinkedTo(
        macAddress: MacAddress
    ): AssociationInfo = myAssociations.find { it.deviceMacAddress == macAddress }
                    ?: fail("Association linked to address $macAddress does not exist")
}
