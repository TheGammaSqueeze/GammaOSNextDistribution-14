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

package android.permissionui.cts

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assert
import org.junit.Assume
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tests media storage supergroup behavior. I.e., on a T+ platform, for legacy (targetSdk<T) apps,
 * the storage permission groups (STORAGE, AURAL, and VISUAL) form a supergroup, which effectively
 * treats them as one group and therefore their permission state must always be equal.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@CddTest(requirement = "9.1/C-0-1")
@FlakyTest
class MediaPermissionTest : BaseUsePermissionTest() {
    private fun assertStorageAndMediaPermissionState(state: Boolean) {
        for (permission in STORAGE_AND_MEDIA_PERMISSIONS) {
            assertAppHasPermission(permission, state)
        }
    }

    @Test
    fun testWhenRESIsGrantedFromGrantDialogThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        requestAppPermissionsAndAssertResult(Manifest.permission.READ_EXTERNAL_STORAGE to true) {
            clickPermissionRequestAllowButton()
        }
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenRESIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenAuralIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_MEDIA_AUDIO)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenVisualIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_MEDIA_VIDEO)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenRESIsDeniedFromGrantDialogThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        requestAppPermissionsAndAssertResult(Manifest.permission.READ_EXTERNAL_STORAGE to false) {
            clickPermissionRequestDenyButton()
        }
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenRESIsDeniedManuallyThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertStorageAndMediaPermissionState(true)
        revokeAppPermissionsByUi(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenAuralIsDeniedManuallyThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_MEDIA_AUDIO)
        revokeAppPermissionsByUi(Manifest.permission.READ_MEDIA_AUDIO)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenVisualIsDeniedManuallyThenShouldDenyAllPermissions() {
        // TODO: Re-enable after b/239249703 is fixed
        Assume.assumeFalse("skip on TV due to flaky", isTv)
        installPackage(APP_APK_PATH_23)
        grantAppPermissionsByUi(Manifest.permission.READ_MEDIA_VIDEO)
        revokeAppPermissionsByUi(Manifest.permission.READ_MEDIA_VIDEO)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenA33AppRequestsStorageThenNoDialogAndNoGrant() {
        installPackage(APP_APK_PATH_MEDIA_PERMISSION_33_WITH_STORAGE)
        requestAppPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            waitForWindowTransition = false
        ) {}
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenA33AppRequestsAuralThenDialogAndGrant() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissions(Manifest.permission.READ_MEDIA_AUDIO) {
            clickPermissionRequestAllowButton()
        }
        assertAppHasPermission(Manifest.permission.READ_EXTERNAL_STORAGE, false)
        assertAppHasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, false)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_AUDIO, true)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_VIDEO, false)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_IMAGES, false)
    }

    @Test
    fun testWhenA33AppRequestsVisualThenDialogAndGrant() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissions(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        ) {
            if (isPhotoPickerPermissionPromptEnabled()) {
                clickPermissionRequestAllowAllButton()
            } else {
                clickPermissionRequestAllowButton()
            }
        }
        assertAppHasPermission(Manifest.permission.READ_EXTERNAL_STORAGE, false)
        assertAppHasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, false)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_AUDIO, false)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_VIDEO, true)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_IMAGES, true)
    }

    @Test
    fun testWhenA30AppRequestsStorageWhenMediaPermsHaveRWRFlag() {
        installPackage(APP_APK_PATH_30)

        requestAppPermissionsAndAssertResult(Manifest.permission.READ_EXTERNAL_STORAGE to true) {
            clickPermissionRequestAllowButton()
        }

        fun setRevokeWhenRequested(permission: String) =
            SystemUtil.runShellCommandOrThrow(
                "pm set-permission-flags android.permissionui.cts.usepermission " +
                    permission +
                    " revoke-when-requested"
            )
        setRevokeWhenRequested("android.permission.READ_MEDIA_AUDIO")
        setRevokeWhenRequested("android.permission.READ_MEDIA_VIDEO")
        setRevokeWhenRequested("android.permission.READ_MEDIA_IMAGES")

        requestAppPermissionsAndAssertResult(
            Manifest.permission.READ_EXTERNAL_STORAGE to true,
            waitForWindowTransition = false
        ) {
            // No dialog should appear
        }

        assertAppHasPermission(Manifest.permission.READ_EXTERNAL_STORAGE, true)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_AUDIO, true)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_VIDEO, true)
        assertAppHasPermission(Manifest.permission.READ_MEDIA_IMAGES, true)
    }


    @Test
    @AsbSecurityTest(cveBugId = [315320090])
    fun testGalleryAppListedAsFixed() {
        val galleryPkgs = SystemUtil.callWithShellPermissionIdentity {
            context.getSystemService(RoleManager::class.java)
                .getRoleHolders(SYSTEM_GALLERY_ROLE_NAME)
        }
        assumeTrue(galleryPkgs.isNotEmpty())
        val galleryPkg = galleryPkgs[0]
        val checkPermissionResult = SystemUtil.callWithShellPermissionIdentity {
            packageManager.checkPermission(Manifest.permission.READ_MEDIA_IMAGES, galleryPkg)
        }
        assumeTrue(checkPermissionResult == PackageManager.PERMISSION_GRANTED)
        navigateToIndividualPermissionSetting(Manifest.permission.READ_MEDIA_IMAGES, galleryPkg)
        // Attempt to deny the permission. It should not show the
        // "denying default permission dialog"
        click(By.res(DENY_RADIO_BUTTON))
        try {
            waitFindObject(By.res(CANCEL_BUTTON_ID), 1000L)
            Assert.fail("expected not to find the default deny dialog")
        } catch (_: Exception) {}
    }

    companion object {
        private val SYSTEM_GALLERY_ROLE_NAME = "android.app.role.SYSTEM_GALLERY"
        private val CANCEL_BUTTON_ID = "android:id/button1"
    }
}
