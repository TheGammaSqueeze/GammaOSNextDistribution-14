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

package android.permissionui.cts

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.os.Build
import android.provider.DeviceConfig
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.modules.utils.build.SdkLevel
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** App Permission page UI tests. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class AppPermissionTest : BaseUsePermissionTest() {

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

        val userSetupComplete =
            Settings.Secure.getInt(context.contentResolver, USER_SETUP_COMPLETE, 0) == 1

        Truth.assertWithMessage("User setup must be complete before running this test")
            .that(userSetupComplete)
            .isTrue()
    }

    @Test
    fun showPermissionRationaleContainer_withInstallSourceAndMetadata_packageSourceUnspecified() {
        // Unspecified is the default, so no need to explicitly set it
        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(true)

        clickPermissionRationaleContentInAppPermission()
        assertPermissionRationaleDialogIsVisible(expected = true, showSettingsSection = false)
    }

    @Test
    fun showPermissionRationaleContainer_withInstallSourceAndMetadata_packageSourceStore() {
        installPackageWithInstallSourceAndMetadataFromStore(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(true)

        clickPermissionRationaleContentInAppPermission()
        assertPermissionRationaleDialogIsVisible(expected = true, showSettingsSection = false)
    }

    @Test
    fun showPermissionRationaleContainer_withInstallSourceAndMetadata_packageSourceLocalFile() {
        installPackageWithInstallSourceAndMetadataFromLocalFile(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun showPermissionRationaleContainer_withInstallSourceAndMetadata_packageSourceDownloadedFile() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun showPermissionRationaleContainer_withInstallSourceAndMetadata_packageSourceOther() {
        installPackageWithInstallSourceAndMetadataFromOther(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndNoMetadata() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndNullMetadata() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndEmptyMetadata() {
        installPackageWithInstallSourceAndEmptyMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndInvalidMetadata() {
        installPackageWithInstallSourceAndInvalidMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndMetadataWithoutTopLevelVersion() {
        installPackageWithInstallSourceAndMetadataWithoutTopLevelVersion(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndMetadataWithInvalidTopLevelVersion() {
        installPackageWithInstallSourceAndMetadataWithInvalidTopLevelVersion(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndMetadataWithoutSafetyLabelVersion() {
        installPackageWithInstallSourceAndMetadataWithoutSafetyLabelVersion(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withInstallSourceAndMetadataWithInvalidSafetyLabelVersion() {
        installPackageWithInstallSourceAndMetadataWithInvalidSafetyLabelVersion(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withOutInstallSource() {
        installPackageWithoutInstallSource(APP_APK_PATH_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    @Test
    fun noShowPermissionRationaleContainer_withoutMetadata() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)

        navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

        assertAppPermissionRationaleContainerIsVisible(false)
    }

    private fun assertAppPermissionRationaleContainerIsVisible(expected: Boolean) {
        findView(By.res(APP_PERMISSION_RATIONALE_CONTAINER_VIEW), expected)
    }

    companion object {
        private const val PERMISSION_RATIONALE_ENABLED = "permission_rationale_enabled"
    }
}
