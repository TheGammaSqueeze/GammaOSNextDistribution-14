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

import android.Manifest.permission.ACCESS_MEDIA_LOCATION
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.app.UiAutomation.ROTATION_FREEZE_270
import android.app.UiAutomation.ROTATION_UNFREEZE
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_ONE_TIME
import android.content.pm.PackageManager.FLAG_PERMISSION_REVOKED_COMPAT
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.net.Uri
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class PhotoPickerPermissionTest : BaseUsePermissionTest() {

    companion object {
        private var photoUri: Uri? = null
        private var videoUri: Uri? = null
        private var oldEnableState: Boolean = true

        @BeforeClass
        @JvmStatic
        fun enablePickerAndAddMedia() {
            // Initialize media provider package name
            PhotoPickerUtils.getMediaProviderPkgName(context)
            oldEnableState = isPhotoPickerPermissionPromptEnabled()
            runWithShellPermissionIdentity {
                if (!oldEnableState) {
                    DeviceConfig.setProperty(
                        NAMESPACE_PRIVACY,
                        PICKER_ENABLED_SETTING,
                        true.toString(),
                        false
                    )
                }
                photoUri = PhotoPickerUtils.createImage(context)
                videoUri = PhotoPickerUtils.createVideo(context)
            }
        }

        @AfterClass
        @JvmStatic
        fun resetPickerAndRemoveMedia() {
            if (!oldEnableState) {
                runWithShellPermissionIdentity {
                    DeviceConfig.setProperty(
                        NAMESPACE_PRIVACY,
                        PICKER_ENABLED_SETTING,
                        false.toString(),
                        false
                    )
                }
            }

            PhotoPickerUtils.deleteMedia(context, photoUri)
            PhotoPickerUtils.deleteMedia(context, videoUri)
        }
    }

    @Before
    fun assumeEnabled() {
        assumeTrue(isPhotoPickerPermissionPromptEnabled())
    }

    @Test
    fun testAppWithoutStoragePermsDoesntHaveUserSelectedAdded() {
        installPackage(APP_APK_PATH_LATEST_NONE)
        runWithShellPermissionIdentity {
            val packageInfo =
                packageManager.getPackageInfo(APP_PACKAGE_NAME, PackageManager.GET_PERMISSIONS)
            assertNotNull(packageInfo)
            val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList<String>()
            assertFalse(
                "Expected app to not request READ_MEDIA_VISUAL_USER_SELECTED",
                permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
            )
        }
    }

    @Test
    fun testAppWithStoragePermsHasUserSelectedAdded() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        runWithShellPermissionIdentity {
            val packageInfo =
                packageManager.getPackageInfo(APP_PACKAGE_NAME, PackageManager.GET_PERMISSIONS)
            assertNotNull(packageInfo)
            val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList<String>()
            assertTrue(
                "Expected app to request READ_MEDIA_VISUAL_USER_SELECTED",
                permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
            )
        }
    }

    @Test
    fun testAppWithUserSelectedPermShowsSelectOption() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissions(READ_MEDIA_IMAGES) {
            assertNotNull(waitFindObjectOrNull(By.res(SELECT_BUTTON)))
            click(By.res(DENY_BUTTON))
        }
    }

    @Test
    fun testNoPhotoSelectionTreatedAsCancel() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissionsAndAssertResult(
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED),
            arrayOf(READ_MEDIA_IMAGES to false, READ_MEDIA_VISUAL_USER_SELECTED to false),
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            findImageOrVideo(expected = true)
            uiDevice.pressBack()
        }
        assertPermissionFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_USER_SET to false)
        assertPermissionFlags(READ_MEDIA_VISUAL_USER_SELECTED, FLAG_PERMISSION_USER_SET to false)
    }

    @Test
    fun testImplicitUserSelectHasOneTimeGrantsWithoutAppOp() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissionsAndAssertResult(
            arrayOf(READ_MEDIA_IMAGES),
            arrayOf(READ_MEDIA_IMAGES to true),
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            clickAllow()
        }
        eventually {
            // USER_SELECTED should be granted, but not returned in the result
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, expectPermission = true)
            assertAppHasPermission(READ_MEDIA_VIDEO, expectPermission = true)
            assertPermissionFlags(
                READ_MEDIA_IMAGES,
                FLAG_PERMISSION_ONE_TIME to true,
                FLAG_PERMISSION_REVOKED_COMPAT to true
            )
            assertPermissionFlags(
                READ_MEDIA_VIDEO,
                FLAG_PERMISSION_ONE_TIME to true,
                FLAG_PERMISSION_REVOKED_COMPAT to true
            )
            assertPermissionFlags(
                READ_MEDIA_VISUAL_USER_SELECTED,
                FLAG_PERMISSION_ONE_TIME to false,
                FLAG_PERMISSION_REVOKED_COMPAT to false
            )
        }
    }

    @Test
    fun testImplicitShowsMorePhotosOnceSet() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        eventually {
            uiAutomation.grantRuntimePermission(APP_PACKAGE_NAME, READ_MEDIA_VISUAL_USER_SELECTED)
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, true)
        }

        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            waitFindObject(By.res(DONT_SELECT_MORE_BUTTON))
            uiDevice.pressBack()
        }
    }

    @Test
    fun testNonImplicitDoesntGrantOtherPermsWhenUserSelected() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED),
            arrayOf(READ_MEDIA_IMAGES to false, READ_MEDIA_VISUAL_USER_SELECTED to true),
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            clickAllow()
        }

        assertPermissionFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_USER_SET to true)
        assertPermissionFlags(READ_MEDIA_VISUAL_USER_SELECTED, FLAG_PERMISSION_USER_SET to true)
    }

    @Test
    fun testNonImplicitAutomaticallyShowsPickerWhenUserFixed() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            doAndWaitForWindowTransition { clickAllow() }
        }

        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            doAndWaitForWindowTransition { clickAllow() }
        }

        assertPermissionFlags(READ_MEDIA_VISUAL_USER_SELECTED, FLAG_PERMISSION_USER_FIXED to true)

        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            findImageOrVideo(expected = true)
            uiDevice.pressBack()
        }
    }

    @Test
    fun testRequestedPermsFilterMediaType() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            findImageOrVideo(expected = true)
            findVideo(expected = false)
            uiDevice.pressBack()
        }

        requestAppPermissions(READ_MEDIA_VIDEO, waitForWindowTransition = false) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            findVideo(expected = true)
            uiDevice.pressBack()
        }
    }

    @Test
    fun testGrantAllPhotosStateSameForImplicitAndNot() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissionsAndAssertResult(
            arrayOf(READ_MEDIA_IMAGES),
            arrayOf(READ_MEDIA_IMAGES to true)
        ) {
            click(By.res(ALLOW_ALL_BUTTON))
        }

        eventually {
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, expectPermission = true)
        }

        uninstallPackage(APP_PACKAGE_NAME)
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED),
            arrayOf(READ_MEDIA_IMAGES to true, READ_MEDIA_VISUAL_USER_SELECTED to true)
        ) {
            click(By.res(ALLOW_ALL_BUTTON))
        }
    }

    @Test
    fun testGrantAllPhotosInSettings() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        click(By.res(ALLOW_RADIO_BUTTON))

        eventually {
            assertAppHasPermission(READ_MEDIA_IMAGES, expectPermission = true)
            assertAppHasPermission(READ_MEDIA_VIDEO, expectPermission = true)
            assertAppHasPermission(ACCESS_MEDIA_LOCATION, expectPermission = true)
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, expectPermission = true)
        }
    }

    @Test
    fun testSelectPhotosInSettingsImplicit() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        click(By.res(SELECT_RADIO_BUTTON))

        eventually {
            assertAppHasPermission(READ_MEDIA_IMAGES, expectPermission = false)
            assertAppHasPermission(READ_MEDIA_VIDEO, expectPermission = false)
            assertAppHasPermission(ACCESS_MEDIA_LOCATION, expectPermission = false)
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, expectPermission = true)
        }
    }

    @Test
    fun testSelectPhotosInSettingsExplicit() {
        installPackage(APP_APK_PATH_LATEST)
        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        click(By.res(SELECT_RADIO_BUTTON))

        eventually {
            assertAppHasPermission(READ_MEDIA_IMAGES, expectPermission = false)
            assertAppHasPermission(READ_MEDIA_VIDEO, expectPermission = false)
            assertAppHasPermission(ACCESS_MEDIA_LOCATION, expectPermission = true)
            assertAppHasPermission(READ_MEDIA_VISUAL_USER_SELECTED, expectPermission = true)
        }
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testPre33AppDoesntShowSelect() {
        installPackage(APP_APK_PATH_30)
        runWithShellPermissionIdentity {
            val requestedPerms =
                packageManager
                    .getPackageInfo(APP_PACKAGE_NAME, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions!!
                    .toList()
            assertTrue(
                "Expected package to have USER_SELECTED",
                requestedPerms.contains(READ_MEDIA_VISUAL_USER_SELECTED)
            )
        }

        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            findView(By.res(SELECT_BUTTON), expected = false)
            pressBack()
        }

        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        findView(By.res(SELECT_RADIO_BUTTON), expected = false)
    }

    @Test
    fun test33AppWithImplicitUserSelectDoesntShowSelect() {
        installPackage(APP_APK_PATH_STORAGE_33)

        runWithShellPermissionIdentity {
            val requestedPerms =
                packageManager
                    .getPackageInfo(APP_PACKAGE_NAME, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions!!
                    .toList()
            assertTrue(
                "Expected package to have USER_SELECTED",
                requestedPerms.contains(READ_MEDIA_VISUAL_USER_SELECTED)
            )
        }

        requestAppPermissions(READ_MEDIA_IMAGES, waitForWindowTransition = false) {
            findView(By.res(SELECT_BUTTON), expected = false)
            pressBack()
        }

        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        findView(By.res(SELECT_RADIO_BUTTON), expected = false)
    }

    @Test
    fun testAppCantRequestOnlyPartialStoragePerms() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_VISUAL_USER_SELECTED to false,
            waitForWindowTransition = false
        ) {}
        uninstallPackage(APP_PACKAGE_NAME)
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_VISUAL_USER_SELECTED to false,
            ACCESS_MEDIA_LOCATION to false,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testImplicitAppCanExpandAccessMediaLocation() {
        installPackage(APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE)
        requestAppPermissions(ACCESS_MEDIA_LOCATION) { click(By.res(ALLOW_ALL_BUTTON)) }
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_IMAGES to true,
            READ_MEDIA_VIDEO to true,
            waitForWindowTransition = false
        ) {}
    }

    @Test
    fun testExplicitAppCannotExpandAccessMediaLocation() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_IMAGES to false,
            ACCESS_MEDIA_LOCATION to true,
            READ_MEDIA_VISUAL_USER_SELECTED to true,
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            clickAllow()
        }
        requestAppPermissions(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO) {
            click(By.res(ALLOW_ALL_BUTTON))
        }
    }

    @Test
    fun testExplicitAppCannotRequestOnlyPartialAccess() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            ACCESS_MEDIA_LOCATION to false,
            READ_MEDIA_VISUAL_USER_SELECTED to false,
            waitForWindowTransition = false
        ) {
            findView(By.res(SELECT_BUTTON), expected = false)
        }
    }

    @Test
    fun testMorePhotosDialogShowsAfterClickingSelect() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_IMAGES to false,
            ACCESS_MEDIA_LOCATION to true,
            READ_MEDIA_VISUAL_USER_SELECTED to true,
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            doAndWaitForWindowTransition { clickAllow() }
        }

        requestAppPermissions(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO) {
            findView(By.res(DONT_SELECT_MORE_BUTTON), expected = true)
            click(By.res(ALLOW_ALL_BUTTON))
        }
    }

    @Test
    fun testAMLNotGrantedIfNotRequested() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_IMAGES to false,
            READ_MEDIA_VISUAL_USER_SELECTED to true,
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            doAndWaitForWindowTransition { clickAllow() }
        }
        assertAppHasPermission(ACCESS_MEDIA_LOCATION, false)
    }

    @Test
    fun testDismissAfterActivityRecreatedWithPickerOpen() {
        installPackage(APP_APK_PATH_LATEST)
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_IMAGES to false,
            READ_MEDIA_VISUAL_USER_SELECTED to true,
            waitForWindowTransition = false
        ) {
            doAndWaitForWindowTransition { click(By.res(SELECT_BUTTON)) }
            clickImageOrVideo()
            try {
                doAndWaitForWindowTransition { uiAutomation.setRotation(ROTATION_FREEZE_270) }
                clickImageOrVideo()
                doAndWaitForWindowTransition { clickAllow() }
            } finally {
                uiAutomation.setRotation(ROTATION_UNFREEZE)
            }
        }
    }

    @Test
    fun testCanSelectPhotosInSettings() {
        installPackage(APP_APK_PATH_LATEST)
        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        click(By.res(SELECT_RADIO_BUTTON))
        doAndWaitForWindowTransition { click(By.res(EDIT_PHOTOS_BUTTON)) }
        clickImageOrVideo()
        clickAllow()
    }

    @Test
    fun testEditButtonNotShownInSettingsWhenNoPhotosRequested() {
        installPackage(APP_APK_PATH_LATEST)
        navigateToIndividualPermissionSetting(READ_MEDIA_IMAGES)
        UiAutomatorUtils2.waitUntilObjectGone(By.res(EDIT_PHOTOS_BUTTON))
    }

    private fun clickImageOrVideo() {
        click(By.res(PhotoPickerUtils.getImageOrVideoResId(context)))
    }

    private fun clickAllow() {
        click(By.res(PhotoPickerUtils.getAllowId(context)))
    }

    private fun findImageOrVideo(expected: Boolean) {
        findView(By.res(PhotoPickerUtils.getImageOrVideoResId(context)), expected)
    }

    private fun findVideo(expected: Boolean) {
        findView(By.res(PhotoPickerUtils.getVideoResId(context)), expected)
    }
}
