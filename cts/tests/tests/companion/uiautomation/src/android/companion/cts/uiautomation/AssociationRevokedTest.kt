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

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.cts.common.sleepFor
import android.platform.test.annotations.AppModeFull
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * Test Association role revoked.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationRevokedTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
class AssociationRevokedTest : AssociationRevokedTestBase() {

    @Test
    fun test_disassociate_app_should_not_crash() = with(associationApp) {
        // Launch the test app with the user consent dialog.
        launchAppAndConfirmationUi()
        // Scroll to the Bottom.
        confirmationUi.scrollToBottom()
        // Press the `Allow` button.
        confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
        // Check the association until the CDM UI is gone.
        confirmationUi.waitUntilGone()

        val associations = withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.allAssociations.filter {
                it.belongsToPackage(userId, packageName)
            }
        }
        // Terminate the test if association is not created.
        assumeFalse(associations.isEmpty())

        // Give it 2 seconds for the app to grant the role.
        sleepFor(2.seconds)

        // Make sure the Watch Profile is granted.
        assertContains(getRoleHolders(DEVICE_PROFILE_WATCH),
            packageName, "Not a holder of $DEVICE_PROFILE_WATCH")

        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.disassociate(associations[0].id)
        }

        // Make sure the Watch Profile is not revoked after disassociate.
        assertContains(getRoleHolders(DEVICE_PROFILE_WATCH),
            packageName, "Not a holder of $DEVICE_PROFILE_WATCH")

        // The test app should not crash after disassociate the watch profile device.
        assertAppStillRunning(packageName)
    }

    @Test
    fun test_disassociate_role_revoked_after_app_is_killed() = with(associationApp) {
        // Launch the test app with the user consent dialog.
        launchAppAndConfirmationUi()
        // Scroll to the Bottom
        confirmationUi.scrollToBottom()
        // Press the `Allow` button.
        confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
        // Check the association until the CDM UI is gone.
        confirmationUi.waitUntilGone()

        val associations = withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.allAssociations.filter {
                it.belongsToPackage(userId, packageName)
            }
        }

        // Terminate the test if association is not created.
        assumeFalse(associations.isEmpty())
        // Give it 2 seconds for the app to grant the role.
        sleepFor(2.seconds)

        // Make sure the Watch Profile is granted.
        assertContains(getRoleHolders(DEVICE_PROFILE_WATCH),
            packageName, "Not a holder of $DEVICE_PROFILE_WATCH")

        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.disassociate(associations[0].id)
        }

        // Make sure the Watch Profile is not revoked after disassociate.
        assertContains(getRoleHolders(DEVICE_PROFILE_WATCH),
            packageName, "Not a holder of $DEVICE_PROFILE_WATCH")

        forceKillApp(packageName)
        // Give it 2 seconds to make sure app is in the desire state.
        sleepFor(2.seconds)
        // The role holder should be removed.
        assertFalse("$DEVICE_PROFILE_WATCH should be removed") {
            getRoleHolders(DEVICE_PROFILE_WATCH).contains(packageName)
        }
    }

    private fun forceKillApp(packageName: String) =
        runShellCommand("am force-stop $packageName")

    private fun assertAppStillRunning(packageName: String) {
        val output = runShellCommand("pidof $packageName")
        if (output.isEmpty()) fail("$packageName is still running.")
    }
}
