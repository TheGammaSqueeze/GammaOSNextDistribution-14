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

import android.annotation.UserIdInt
import android.companion.AssociationInfo
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.CompanionDeviceManager
import android.companion.cts.common.AppHelper
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.waitFor
import android.net.MacAddress
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for backing up CDM states and restoring them.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:BackupAndRestoreTest
 *
 * @see android.companion.CompanionDeviceManager.getBackupPayload
 * @see android.companion.CompanionDeviceManager.applyRestoredPayload
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class BackupAndRestoreTest : CoreTestBase() {

    @Test
    fun test_backupPayload() {
        // Create two new associations and back up
        targetApp.associate(MAC_ADDRESS_A)
        targetApp.associate(MAC_ADDRESS_B)
        val payload = getBackupPayload(userId)

        // Assert a non-null payload is generated
        assertNotNull(payload)
    }

    @Test
    fun test_applyRestoredPayload_restoresAssociations() {
        // Create two new associations and back up
        targetApp.associate(MAC_ADDRESS_A)
        targetApp.associate(MAC_ADDRESS_B)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_B)
        val payload = getBackupPayload(userId)

        // Delete associations and then restore them
        targetApp.disassociateAll()
        assertAssociationsRemovedFor(targetApp)
        applyRestoredPayload(payload, userId)

        // They will have newly assigned association ID, so check for MAC address matches
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_B)
    }

    @Test
    fun test_applyRestoredPayload_skipsExisting() {
        // Create one association and back up
        targetApp.associate(MAC_ADDRESS_A)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
        val payload = getBackupPayload(userId)

        // Create another association and then restore
        targetApp.associate(MAC_ADDRESS_B)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_B)
        applyRestoredPayload(payload, userId)

        // Assert that CDM doesn't create a new association
        assertEquals(2, cdm.myAssociations.size)
    }

    @Test
    fun test_applyRestoredPayload_grantsRole() {
        // Create one association with watch profile
        targetApp.associate(MAC_ADDRESS_A, DEVICE_PROFILE_WATCH)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
        assertRoleGrantedToApp(targetApp, DEVICE_PROFILE_WATCH)
        val payload = getBackupPayload(userId)

        // Remove association and revoke role
        targetApp.disassociateAll()
        assertAssociationsRemovedFor(targetApp)

        // This normally happens automatically after disassociation, but CDM waits for the process
        // to go invisible first. So just forcefully remove app from the list of role holders
        targetApp.removeFromHoldersOfRole(DEVICE_PROFILE_WATCH)
        assertRoleRevokedFromApp(targetApp, DEVICE_PROFILE_WATCH)

        // Restoring adds association and re-grants role
        applyRestoredPayload(payload, userId)
        assertRoleGrantedToApp(targetApp, DEVICE_PROFILE_WATCH)
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
    }

    @Test
    fun test_applyRestoredPayload_fromDifferentUser() {
        val payload = asSecondaryProfile { secondaryUserId ->
            val secondaryApp = AppHelper(instrumentation, secondaryUserId, targetPackageName)

            // Create a new associations as secondary user and back up
            secondaryApp.associate(MAC_ADDRESS_A)
            assertAssociationsAddedFor(secondaryApp, MAC_ADDRESS_A)
            val payload = getBackupPayload(secondaryUserId)

            // Delete associations and then restore them as original user
            secondaryApp.disassociateAll()
            assertAssociationsRemovedFor(secondaryApp)

            // Return payload
            payload
        }
        // Apply backup from secondary user into current user
        applyRestoredPayload(payload, userId)

        // They will have newly assigned association ID, so check for MAC address matches
        assertAssociationsAddedFor(targetApp, MAC_ADDRESS_A)
    }

    @Test
    fun test_applyRestoredPayload_futurePackage() {
        // Create a new association and back up
        testApp.associate(MAC_ADDRESS_A)
        assertAssociationsAddedFor(testApp, MAC_ADDRESS_A)
        val payload = getBackupPayload(userId)

        // Uninstall app and assert that associations are removed
        testApp.uninstall()
        assertAssociationsRemovedFor(testApp)

        // Assert that association isn't added for a non-existent package
        applyRestoredPayload(payload, userId)
        assertAssociationsRemovedFor(testApp)

        // Re-install package and assert that association is automatically restored
        testApp.install()
        assertAssociationsAddedFor(testApp, MAC_ADDRESS_A)
    }

    @Test
    fun test_applyRestoredPayload_grantsRoleToFuturePackage() {
        // Create a new association and back up
        testApp.associate(MAC_ADDRESS_A, DEVICE_PROFILE_WATCH)
        assertAssociationsAddedFor(testApp, MAC_ADDRESS_A)
        assertRoleGrantedToApp(testApp, DEVICE_PROFILE_WATCH)
        val payload = getBackupPayload(userId)

        // Uninstall app and assert that associations are removed
        testApp.uninstall()
        assertAssociationsRemovedFor(testApp)
        assertRoleRevokedFromApp(testApp, DEVICE_PROFILE_WATCH)

        // Assert that association isn't added for a non-existent package
        applyRestoredPayload(payload, userId)
        assertAssociationsRemovedFor(testApp)
        assertRoleRevokedFromApp(testApp, DEVICE_PROFILE_WATCH)

        // Re-install package and assert that association is automatically restored
        testApp.install()
        assertAssociationsAddedFor(testApp, MAC_ADDRESS_A)
        assertRoleGrantedToApp(testApp, DEVICE_PROFILE_WATCH)
    }

    private fun getBackupPayload(userId: Int) =
            runShellCommand("cmd companiondevice get-backup-payload $userId")

    private fun applyRestoredPayload(payload: String, userId: Int) =
            runShellCommand("cmd companiondevice apply-restored-payload $userId $payload")

    private fun assertAssociationsAddedFor(app: AppHelper, macAddress: MacAddress) = waitFor {
        withShellPermissionIdentity {
            cdm.getAssociationForPackage(app.userId, app.packageName).stream().anyMatch {
                it.deviceMacAddress == macAddress
            }
        }
    }.let { matched ->
        if (!matched) {
            throw AssertionError("Device $macAddress was not associated with ${app.packageName}.")
        }
    }

    private fun assertAssociationsRemovedFor(app: AppHelper) = waitFor {
        withShellPermissionIdentity {
            cdm.getAssociationForPackage(app.userId, app.packageName).isEmpty()
        }
    }.let { removed ->
        if (!removed) {
            throw AssertionError("Associations for ${app.packageName} were not removed.")
        }
    }

    fun assertRoleGrantedToApp(app: AppHelper, role: String) =
            assertTrue(
                "Role $String should be granted to package ${app.userId}\\${app.packageName}"
            ) {
                waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                    app.isRoleHolder(role)
                }
            }

    fun assertRoleRevokedFromApp(app: AppHelper, role: String) =
            assertTrue(
                "$String should be revoked from package ${app.userId}\\${app.packageName}"
            ) {
                waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                    !app.isRoleHolder(role)
                }
            }

    private fun <T> asSecondaryProfile(name: String = "CDM-tester", action: (Int) -> T): T {
        // "Success: created user id XX" where XX is the new user ID
        val out = runShellCommand("pm create-user --profileOf $userId --ephemeral $name")
                .split(" ")
        assumeTrue("Failed to create a secondary user.", out[0].contains("Success"))
        val secondaryUserId = out[out.size - 1].trim().toInt()

        try {
            return action.invoke(secondaryUserId)
        } finally {
            runShellCommand("pm remove-user $secondaryUserId")
        }
    }
}

private fun CompanionDeviceManager.getAssociationForPackage(
        @UserIdInt userId: Int,
        packageName: String
): List<AssociationInfo> = getAllAssociations(userId).filter {
    it.belongsToPackage(userId, packageName)
}
