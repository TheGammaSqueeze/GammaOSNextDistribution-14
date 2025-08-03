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
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.res.Resources
import android.provider.DeviceConfig
import androidx.test.filters.FlakyTest
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/** Tests for Safety Protection related features. This feature should only be enabled on T+. */
@FlakyTest
class SafetyProtectionTest : BaseUsePermissionTest() {
    @get:Rule
    val safetyProtectionEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SAFETY_PROTECTION_ENABLED_FLAG,
            true.toString()
        )

    @Before
    fun setup() {
        assumeFalse(isAutomotive)
        assumeFalse(isTv)
        assumeFalse(isWatch)
    }

    @Ignore("b/276944839")
    @Test
    fun testSafetyProtectionSectionView_safetyProtection_belowT() {
        assumeFalse("Safety Protection should only be enabled on T+", SdkLevel.isAtLeastT())
        installPackageViaSession(APP_APK_NAME_31)
        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            findView(By.res(SAFETY_PROTECTION_DISPLAY_TEXT), false)
        }
    }

    @Test
    fun testSafetyProtectionSectionView_safetyProtectionDisabled_aboveT() {
        assumeTrue("Safety Protection should only be enabled on T+", SdkLevel.isAtLeastT())
        setDeviceConfigPrivacyProperty(SAFETY_PROTECTION_ENABLED_FLAG, false.toString())
        installPackageViaSession(APP_APK_NAME_31)
        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            findView(By.res(SAFETY_PROTECTION_DISPLAY_TEXT), false)
        }
    }

    @Test
    fun testSafetyProtectionSectionView_safetyProtectionEnabled_aboveT() {
        assumeTrue("Safety Protection should only be enabled on T+", SdkLevel.isAtLeastT())
        assumeTrue(safetyProtectionResourcesExist)
        installPackageViaSession(APP_APK_NAME_31)
        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            findView(By.res(SAFETY_PROTECTION_DISPLAY_TEXT), true)
        }
    }

    @Test
    fun testSafetyProtectionSectionView_safetyProtectionResourcesNotExist_aboveT() {
        assumeTrue("Safety Protection should only be enabled on T+", SdkLevel.isAtLeastT())
        assumeFalse(safetyProtectionResourcesExist)
        installPackageViaSession(APP_APK_NAME_31)
        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            findView(By.res(SAFETY_PROTECTION_DISPLAY_TEXT), false)
        }
    }

    companion object {
        private const val SAFETY_PROTECTION_ENABLED_FLAG = "safety_protection_enabled"
        private const val SAFETY_PROTECTION_DISPLAY_TEXT =
            "com.android.permissioncontroller:id/safety_protection_display_text"
        private val safetyProtectionResourcesExist =
            try {
                context
                    .getResources()
                    .getBoolean(
                        Resources.getSystem()
                            .getIdentifier("config_safetyProtectionEnabled", "bool", "android")
                    ) &&
                    context.getDrawable(android.R.drawable.ic_safety_protection) != null &&
                    !context
                        .getString(android.R.string.safety_protection_display_text)
                        .isNullOrEmpty()
            } catch (e: Resources.NotFoundException) {
                false
            }
    }
}
