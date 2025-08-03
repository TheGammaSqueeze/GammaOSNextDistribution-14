/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.permissionui.cts

import android.content.pm.PackageManager
import android.permission.cts.MtsIgnore
import androidx.test.filters.FlakyTest
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/** Runtime permission behavior tests for apps targeting API 23. */
@FlakyTest
class PermissionTest23 : BaseUsePermissionTest() {
    companion object {
        private const val NON_EXISTENT_PERMISSION = "permission.does.not.exist"
        private const val INVALID_PERMISSION = "$APP_PACKAGE_NAME.abadname"
    }

    @Before
    fun installApp23() {
        installPackage(APP_APK_PATH_23)
    }

    @Test
    fun testDefault() {
        // New permission model is denied by default
        assertAppHasAllOrNoPermissions(false)
    }

    @Test
    fun testGranted() {
        grantAppPermissionsByUi(android.Manifest.permission.READ_CALENDAR)

        // Read/write access should be allowed
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, true)
        assertAppHasPermission(android.Manifest.permission.WRITE_CALENDAR, true)
        assertAppHasCalendarAccess(true)
    }

    @Test
    fun testInteractiveGrant() {
        // Start out without permission
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, false)
        assertAppHasPermission(android.Manifest.permission.WRITE_CALENDAR, false)
        assertAppHasCalendarAccess(false)

        // Go through normal grant flow
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.READ_CALENDAR to true,
            android.Manifest.permission.WRITE_CALENDAR to true
        ) {
            clickPermissionRequestAllowButton()
        }

        // We should have permission now!
        assertAppHasCalendarAccess(true)
    }

    @Test
    fun testRuntimeGroupGrantSpecificity() {
        // Start out without permission
        assertAppHasPermission(android.Manifest.permission.READ_CONTACTS, false)
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)

        // Request only one permission from the 'contacts' permission group
        // Expect the permission is granted
        requestAppPermissionsAndAssertResult(android.Manifest.permission.WRITE_CONTACTS to true) {
            clickPermissionRequestAllowButton()
        }

        // Make sure no undeclared as used permissions are granted
        assertAppHasPermission(android.Manifest.permission.READ_CONTACTS, false)
    }

    @Test
    fun testCancelledPermissionRequest() {
        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)

        // Request the permission and cancel the request
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(android.Manifest.permission.WRITE_CONTACTS to false) {
            clickPermissionRequestDenyButton()
        }
    }

    @Test
    fun testRequestGrantedPermission() {
        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)

        // Request the permission and allow it
        // Expect the permission is granted
        requestAppPermissionsAndAssertResult(android.Manifest.permission.WRITE_CONTACTS to true) {
            clickPermissionRequestAllowButton()
        }

        // Request the permission and do nothing
        // Expect the permission is granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.WRITE_CONTACTS to true,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testDenialWithPrejudice() {
        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)

        // Request the permission and deny it twice
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.WRITE_CONTACTS to false,
            askTwice = true
        ) {
            clickPermissionRequestDenyButton()
            denyPermissionRequestWithPrejudice()
        }

        // Request the permission and do nothing
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.WRITE_CONTACTS to false,
            waitForWindowTransition = false
        ) {}
    }

    @FlakyTest
    @MtsIgnore
    @Test
    fun testRevokeAffectsWholeGroup() {
        // Grant the group
        grantAppPermissionsByUi(android.Manifest.permission.READ_CALENDAR)

        // Make sure we have the permissions
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, true)
        assertAppHasPermission(android.Manifest.permission.WRITE_CALENDAR, true)

        // Revoke the group
        revokeAppPermissionsByUi(android.Manifest.permission.READ_CALENDAR)

        // Make sure we don't have the permissions
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, false)
        assertAppHasPermission(android.Manifest.permission.WRITE_CALENDAR, false)
    }

    @Test
    fun testGrantPreviouslyRevokedWithPrejudiceShowsPrompt() {
        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, false)

        // Request the permission and deny it twice
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.READ_CALENDAR to false,
            askTwice = true
        ) {
            clickPermissionRequestDenyButton()
            denyPermissionRequestWithPrejudice()
        }

        // Clear the denial with prejudice
        uiAutomation.grantRuntimePermission(
            APP_PACKAGE_NAME,
            android.Manifest.permission.READ_CALENDAR
        )
        revokeAppPermissionsByUi(android.Manifest.permission.READ_CALENDAR)

        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, false)

        // Request the permission and allow it
        // Make sure the permission is granted
        requestAppPermissionsAndAssertResult(android.Manifest.permission.READ_CALENDAR to true) {
            clickPermissionRequestAllowButton()
        }
    }

    @Test
    fun testRequestNonRuntimePermission() {
        // Make sure we don't have the permission
        assertAppHasPermission(android.Manifest.permission.BIND_PRINT_SERVICE, false)

        // Request the permission and do nothing
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BIND_PRINT_SERVICE to false,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testRequestNonExistentPermission() {
        // Make sure we don't have the permission
        assertAppHasPermission(NON_EXISTENT_PERMISSION, false)

        // Request the permission and do nothing
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            NON_EXISTENT_PERMISSION to false,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testRequestPermissionFromTwoGroups() {
        // Make sure we don't have the permissions
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)
        assertAppHasPermission(android.Manifest.permission.WRITE_CALENDAR, false)
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, false)

        // Request the permission and allow it
        // Expect the permission are granted
        val result =
            requestAppPermissionsAndAssertResult(
                android.Manifest.permission.WRITE_CONTACTS to true,
                android.Manifest.permission.WRITE_CALENDAR to true
            ) {
                clickPermissionRequestAllowButton()
                clickPermissionRequestAllowButton()
            }

        // In API < N_MR1 all permissions of a group are granted. I.e. the grant was "expanded"
        assertAppHasPermission(android.Manifest.permission.READ_CALENDAR, true)
        // Even the contacts group was expanded, the read-calendar permission is not in the
        // manifest, hence not granted.
        assertAppHasPermission(android.Manifest.permission.READ_CONTACTS, false)
    }

    @Test(timeout = 180000)
    @FlakyTest
    @MtsIgnore
    fun testNoResidualPermissionsOnUninstall() {
        Assume.assumeFalse(packageManager.arePermissionsIndividuallyControlled())

        // Grant one permission via UI, and the rest via automation
        grantAppPermissionsByUi(android.Manifest.permission.WRITE_CALENDAR)
        grantRuntimePermissions(
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BODY_SENSORS,
        )
        uninstallPackage(APP_PACKAGE_NAME)
        installPackage(APP_APK_PATH_23)

        // Make no permissions are granted after uninstalling and installing the app
        assertAppHasAllOrNoPermissions(false)
    }

    @Test
    fun testNullPermissionRequest() {
        val permissions: Array<String?> = arrayOf(null)
        val results: Array<Pair<String?, Boolean>> = arrayOf()
        // Go through normal grant flow
        requestAppPermissionsAndAssertResult(
            permissions,
            results,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testNullAndRealPermission() {
        // Make sure we don't have the permissions
        assertAppHasPermission(android.Manifest.permission.WRITE_CONTACTS, false)
        assertAppHasPermission(android.Manifest.permission.RECORD_AUDIO, false)

        // Request the permission and allow it
        // Expect the permission are granted
        requestAppPermissionsAndAssertResult(
            arrayOf(
                android.Manifest.permission.WRITE_CONTACTS,
                null,
                android.Manifest.permission.RECORD_AUDIO,
                null
            ),
            arrayOf(
                android.Manifest.permission.WRITE_CONTACTS to true,
                android.Manifest.permission.RECORD_AUDIO to true
            )
        ) {
            clickPermissionRequestAllowForegroundButton()
            clickPermissionRequestAllowButton()
        }
    }

    @Test
    fun testInvalidPermission() {
        // Request the permission and allow it
        // Expect the permission is not granted
        requestAppPermissionsAndAssertResult(
            INVALID_PERMISSION to false,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testAskButtonSetsFlags() {
        Assume.assumeFalse(
            "other form factors might not support the ask button",
            isTv || isAutomotive || isWatch
        )

        grantAppPermissionsByUi(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, true)
        assertAppHasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, true)
        assertAppHasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, true)

        revokeAppPermissionsByUi(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        SystemUtil.runWithShellPermissionIdentity {
            val perms =
                listOf(
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            for (perm in perms) {
                var flags = packageManager.getPermissionFlags(perm, APP_PACKAGE_NAME, context.user)
                Assert.assertEquals(
                    "USER_SET should not be set for $perm",
                    0,
                    flags and PackageManager.FLAG_PERMISSION_USER_SET
                )
                Assert.assertEquals(
                    "USER_FIXED should not be set for $perm",
                    0,
                    flags and PackageManager.FLAG_PERMISSION_USER_FIXED
                )
                Assert.assertEquals(
                    "ONE_TIME should be set for $perm",
                    PackageManager.FLAG_PERMISSION_ONE_TIME,
                    flags and PackageManager.FLAG_PERMISSION_ONE_TIME
                )
            }
        }
    }

    private fun denyPermissionRequestWithPrejudice() {
        if (isTv || isWatch) {
            clickPermissionRequestDontAskAgainButton()
        } else {
            clickPermissionRequestDenyAndDontAskAgainButton()
        }
    }

    private fun assertAppHasAllOrNoPermissions(expectPermissions: Boolean) {
        arrayOf(
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.RECEIVE_WAP_PUSH,
                android.Manifest.permission.RECEIVE_MMS,
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR,
                android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.WRITE_CALL_LOG,
                android.Manifest.permission.ADD_VOICEMAIL,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.USE_SIP,
                android.Manifest.permission.PROCESS_OUTGOING_CALLS,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.BODY_SENSORS,
                android.Manifest.permission.READ_CELL_BROADCASTS,
                // Split permissions
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                // Storage permissions
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .forEach { assertAppHasPermission(it, expectPermissions) }
    }
}
