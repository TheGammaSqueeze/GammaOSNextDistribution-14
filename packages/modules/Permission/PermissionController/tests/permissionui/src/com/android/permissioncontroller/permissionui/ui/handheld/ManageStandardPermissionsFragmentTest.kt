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

package com.android.permissioncontroller.permissionui.ui.handheld

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.revokePermission
import android.permission.cts.PermissionUtils.uninstallApp
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import com.android.permissioncontroller.permissionui.getUsageCountsFromUi
import com.android.permissioncontroller.permissionui.wakeUpScreen
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Simple tests for {@link ManageStandardPermissionsFragment} */
@RunWith(AndroidJUnit4::class)
class ManageStandardPermissionsFragmentTest : BaseHandheldPermissionUiTest() {
    @Before
    fun setup() {
        wakeUpScreen()

        runWithShellPermissionIdentity {
            removePackageIfInstalled()
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_PERMISSIONS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
        }
    }

    @After
    fun tearDown() {
        uninstallApp(LOCATION_USER_PKG)
        uninstallApp(ADDITIONAL_DEFINER_PKG)
        uninstallApp(ADDITIONAL_USER_PKG)
        uiDevice.pressBack()
    }

    /**
     * The test packages are not expected to be installed already, remove them if they are already
     * installed (i.e. leftover from another test) when a test starts.
     */
    private fun removePackageIfInstalled() {
        val packageNames = listOf(LOCATION_USER_PKG, ADDITIONAL_DEFINER_PKG, ADDITIONAL_USER_PKG)
        for (packageName in packageNames) {
            try {
                val packageInfo =
                    instrumentationContext.packageManager.getPackageInfo(packageName, 0)
                if (packageInfo != null) {
                    Log.w(LOG_TAG, "package $packageName not expected to be installed.")
                    uninstallApp(packageName)
                    Thread.sleep(1000)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // ignore
            }
        }
    }

    /**
     * Read the number of additional permissions from the Ui.
     *
     * @return number of additional permissions
     */
    private fun getAdditionalPermissionCount(): Int {
        waitFindObjectOrNull(By.textContains(ADDITIONAL_PERMISSIONS_LABEL)) ?: return 0
        // Sometimes the entire preference disappears while it's searching for the app count
        // (during uninstalling). Hence also return the count as 0 if count doesn't exist
        val additionalPermissionsSummary =
            waitFindObjectOrNull(By.textContains(ADDITIONAL_PERMISSIONS_SUMMARY)) ?: return 0
        val additionalPermissionsSummaryText = additionalPermissionsSummary.getText()

        // Matches a single number out of the summary line, i.e. "...3..." -> "3"
        return getEventually {
            Regex("^[^\\d]*(\\d+)[^\\d]*\$")
                .find(additionalPermissionsSummaryText)!!
                .groupValues[1]
                .toInt()
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsInstalled() {
        val original = getUsageCountsFromUi(LOCATION_GROUP_LABEL)

        install(LOCATION_USER_APK)
        eventually(
            {
                val afterInstall = getUsageCountsFromUi(LOCATION_GROUP_LABEL)
                assertThat(afterInstall.granted).isEqualTo(original.granted)
                assertThat(afterInstall.total).isEqualTo(original.total + 1)
            },
            TIMEOUT
        )
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsUninstalled() {
        val original = getUsageCountsFromUi(LOCATION_GROUP_LABEL)
        install(LOCATION_USER_APK)
        eventually(
            { assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL)).isNotEqualTo(original) },
            TIMEOUT
        )

        uninstallApp(LOCATION_USER_PKG)
        eventually(
            { assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL)).isEqualTo(original) },
            TIMEOUT
        )
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        val original = getUsageCountsFromUi(LOCATION_GROUP_LABEL)

        install(LOCATION_USER_APK)
        eventually(
            {
                assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL).total)
                    .isEqualTo(original.total + 1)
            },
            TIMEOUT
        )

        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL).granted)
                .isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsRevoked() {
        val original = getUsageCountsFromUi(LOCATION_GROUP_LABEL)

        install(LOCATION_USER_APK)
        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually(
            {
                assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL).total)
                    .isNotEqualTo(original.total)
                assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL).granted)
                    .isNotEqualTo(original.granted)
            },
            TIMEOUT
        )

        revokePermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(LOCATION_GROUP_LABEL).granted)
                .isEqualTo(original.granted)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenAppGetsInstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually(
            {
                assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore + 1)
            },
            TIMEOUT
        )
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenUserGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually(
            { assertThat(getAdditionalPermissionCount()).isNotEqualTo(additionalPermissionBefore) },
            TIMEOUT
        )

        uninstallApp(ADDITIONAL_USER_PKG)
        eventually(
            { assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore) },
            TIMEOUT
        )
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenDefinerGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually(
            { assertThat(getAdditionalPermissionCount()).isNotEqualTo(additionalPermissionBefore) },
            TIMEOUT
        )

        uninstallApp(ADDITIONAL_DEFINER_PKG)
        eventually(
            { assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore) },
            TIMEOUT
        )
    }

    companion object {
        private val LOG_TAG = ManageStandardPermissionsFragmentTest::class.java.simpleName

        private const val LOCATION_USER_APK =
            "/data/local/tmp/pc-permissionui/AppThatRequestsLocation.apk"
        private const val ADDITIONAL_DEFINER_APK =
            "/data/local/tmp/pc-permissionui/" + "PermissionUiDefineAdditionalPermissionApp.apk"
        private const val ADDITIONAL_USER_APK =
            "/data/local/tmp/pc-permissionui/" + "PermissionUiUseAdditionalPermissionApp.apk"
        private const val LOCATION_USER_PKG = "android.permission.cts.appthatrequestpermission"
        private const val ADDITIONAL_DEFINER_PKG =
            "com.android.permissioncontroller.tests.appthatdefinespermission"
        private const val ADDITIONAL_USER_PKG =
            "com.android.permissioncontroller.tests.appthatrequestpermission"
        private const val ADDITIONAL_PERMISSIONS_LABEL = "Additional permissions"
        private const val ADDITIONAL_PERMISSIONS_SUMMARY = "more"
        private const val LOCATION_GROUP_LABEL = "Location"

        // Package Added/Removed broadcast are pretty slow on cf devices, we may want to increase
        // this in future if the test still fails.
        private const val TIMEOUT = 30000L
    }
}
