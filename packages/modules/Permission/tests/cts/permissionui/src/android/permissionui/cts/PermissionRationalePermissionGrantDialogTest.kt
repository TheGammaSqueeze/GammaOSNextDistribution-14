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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Permission rationale in Grant Permission Dialog tests. Permission rationale is only available on
 * U+
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class PermissionRationalePermissionGrantDialogTest : BaseUsePermissionTest() {

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PERMISSION_RATIONALE_ENABLED,
            true.toString()
        )

    @Before
    fun setup() {
        Assume.assumeTrue("Permission rationale is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun requestLocationPerm_flagDisabled_noPermissionRationale() {
        setDeviceConfigPrivacyProperty(PERMISSION_RATIONALE_ENABLED, false.toString())
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_apkHasNoInstallSource_noPermissionRationale() {
        installPackageWithoutInstallSource(APP_APK_PATH_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_noAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_nullAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_emptyAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndEmptyMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndInvalidMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadataWithoutTopLevelVersion_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadataWithoutTopLevelVersion(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadataWithInvalidTopLevelVersion_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadataWithInvalidTopLevelVersion(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadataWithoutSafetyLabelVersion_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadataWithoutSafetyLabelVersion(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadataWithInvalidSafetyLabelVersion_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadataWithInvalidSafetyLabelVersion(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestCameraPerm_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(CAMERA, false)

        requestAppPermissionsForNoResult(CAMERA) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale_packageSourceUnspecified() {
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale_packageSourceStore() {
        installPackageWithInstallSourceAndMetadataFromStore(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale_packageSourceLocalFile() {
        installPackageWithInstallSourceAndMetadataFromLocalFile(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale_packageSourceDownloadedFile() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale_packageSourceOther() {
        installPackageWithInstallSourceAndMetadataFromOther(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestFineLocationPerm_hasPermissionRationale() {
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestLocationPerm_clicksPermissionRationale_startsPermissionRationaleActivity() {
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            clickPermissionRationaleViewInGrantDialog()
            assertPermissionRationaleDialogIsVisible(true)
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_clicksPermissionRationale_startsPermissionRationaleActivity_comesBack() {
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            clickPermissionRationaleViewInGrantDialog()
            assertPermissionRationaleDialogIsVisible(true)
            pressBack()
            assertPermissionRationaleDialogIsVisible(false)
            assertPermissionRationaleContainerOnGrantDialogIsVisible(true)
        }
    }
}
