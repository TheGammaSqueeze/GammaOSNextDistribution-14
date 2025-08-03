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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
import android.os.Build
import android.os.PersistableBundle
import android.permission.cts.PermissionUtils
import android.permissionui.cts.AppMetadata.createAppMetadataWithLocationSharingAds
import android.permissionui.cts.AppMetadata.createAppMetadataWithLocationSharingNoAds
import android.permissionui.cts.AppMetadata.createAppMetadataWithNoSharing
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.util.Log
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/** Tests the UI that displays information about apps' updates to their data sharing policies. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class AppDataSharingUpdatesTest : BaseUsePermissionTest() {
    // TODO(b/263838456): Add tests for personal and work profile.

    private var activityManager: ActivityManager? = null

    @get:Rule
    val deviceConfigSafetyLabelChangeNotificationsEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
            true.toString()
        )

    @get:Rule
    val deviceConfigDataSharingUpdatesPeriod =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
            "600000"
        )

    /**
     * This rule serves to limit the max number of safety labels that can be persisted, so that
     * repeated tests don't overwhelm the disk storage on the device.
     */
    @get:Rule
    val deviceConfigMaxSafetyLabelsPersistedPerApp =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP,
            "2"
        )

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Data sharing updates page is only available on U+",
            SdkLevel.isAtLeastU()
        )
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)

        PermissionUtils.clearAppState(context.packageManager.permissionControllerPackageName)
        waitForBroadcasts()
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedCoarseLocation_noSharingToNoAdsSharing_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedFineLocation_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedBackgroundLocation_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantBackgroundLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_noSharingToAdsSharing_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingAds())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES_FOR_ADS), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_noAdsSharingToAdsSharing_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingAds())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES_FOR_ADS), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_adsSharingToNoAdsSharing_showsNoUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingAds(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_noAdsSharingToNoSharing_showsNoUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_adsSharingToNoSharing_showsNoUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingAds(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        grantCoarseLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    @Ignore("b/282063206")
    @Test
    fun clickLearnMore_opensHelpCenter() {
        Assume.assumeFalse(getPermissionControllerResString(HELP_CENTER_URL_ID).isNullOrEmpty())

        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), true)

            clickAndWaitForWindowTransition(By.textContains(LEARN_ABOUT_DATA_SHARING))

            eventually({ assertHelpCenterLinkClickSuccessful() }, HELP_CENTER_TIMEOUT_MILLIS)
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun noHelpCenterLinkAvailable_noHelpCenterClickAction() {
        Assume.assumeTrue(getPermissionControllerResString(HELP_CENTER_URL_ID).isNullOrEmpty())

        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), false)
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun clickUpdate_opensAppLocationPermissionPage() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantFineLocationPermission(APP_PACKAGE_NAME)
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)

            clickAndWaitForWindowTransition(By.textContains(APP_PACKAGE_NAME_SUBSTRING))

            findView(By.descContains(LOCATION_PERMISSION), true)
            findView(By.textContains(APP_PACKAGE_NAME), true)
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppNotGrantedLocation_showsNoUpdates() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_noMetadata_showsNoUpdates() {
        installPackageWithoutInstallSource(APP_APK_PATH_31)
        waitForBroadcasts()
        installPackageWithoutInstallSource(APP_APK_PATH_31)
        waitForBroadcasts()

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_featureDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())

        startAppDataSharingUpdatesActivity()

        findView(By.descContains(DATA_SHARING_UPDATES), false)
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedFineLocation_packageSourceUnspecified_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_UNSPECIFIED,
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_UNSPECIFIED
        )
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_packageSourceOther_doesntShowUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_OTHER,
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_OTHER
        )
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_packageSourceStore_showsUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_STORE,
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_STORE
        )
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertUpdatesPresent()
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(NOW_SHARED_WITH_THIRD_PARTIES), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_packageSourceLocalFile_doesntShowUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_LOCAL_FILE,
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_LOCAL_FILE
        )
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_whenAppGrantedLocation_packageSourceDownloaded_doesntShowUpdate() {
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_DOWNLOADED_FILE,
            waitTillBroadcastProcessed = true
        )
        installAndWaitTillPackageAdded(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_DOWNLOADED_FILE
        )
        grantFineLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            assertNoUpdatesPresent()
        } finally {
            pressBack()
        }
    }

    /** Installs an app and waits for the package added broadcast to be dispatched. */
    private fun installAndWaitTillPackageAdded(
        apkPath: String,
        appMetadata: PersistableBundle,
        packageSource: Int? = null,
        waitTillBroadcastProcessed: Boolean = false
    ) {
        installPackageViaSession(apkPath, appMetadata, packageSource)
        waitForBroadcasts()
        // TODO(b/279455955): Investigate why this is necessary and remove if possible.
        if (waitTillBroadcastProcessed) Thread.sleep(500)
    }

    private fun assertUpdatesPresent() {
        findView(By.descContains(DATA_SHARING_UPDATES), true)
        findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
        findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
        findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
        findView(By.textContains(LEARN_ABOUT_DATA_SHARING), shouldShowLearnMoreLink())
    }

    private fun assertNoUpdatesPresent() {
        findView(By.descContains(DATA_SHARING_UPDATES), true)
        findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
        findView(By.textContains(DATA_SHARING_NO_UPDATES_MESSAGE), true)
        findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
        findView(By.textContains(UPDATES_IN_LAST_30_DAYS), false)
        findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
        findView(By.textContains(LEARN_ABOUT_DATA_SHARING), shouldShowLearnMoreLink())
    }

    private fun grantFineLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    private fun grantCoarseLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    private fun grantBackgroundLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }

    private fun assertHelpCenterLinkClickSuccessful() {
        runWithShellPermissionIdentity {
            val runningTasks = activityManager!!.getRunningTasks(5)

            Log.v(TAG, "# running tasks: ${runningTasks.size}")
            assertFalse("Expected runningTasks to not be empty", runningTasks.isEmpty())

            runningTasks.forEachIndexed { index, runningTaskInfo ->
                Log.v(TAG, "task $index ${runningTaskInfo.baseIntent}")
            }

            val taskInfo = runningTasks[0]
            val observedIntentAction = taskInfo.baseIntent.action
            val observedIntentDataString = taskInfo.baseIntent.dataString
            val observedIntentScheme: String? = taskInfo.baseIntent.scheme

            Log.v(TAG, "task base intent: ${taskInfo.baseIntent}")
            assertEquals("Unexpected intent action", Intent.ACTION_VIEW, observedIntentAction)

            val expectedUrl = getPermissionControllerResString(HELP_CENTER_URL_ID)!!
            assertFalse(observedIntentDataString.isNullOrEmpty())
            assertTrue(observedIntentDataString?.startsWith(expectedUrl) ?: false)

            assertFalse(observedIntentScheme.isNullOrEmpty())
            assertEquals("https", observedIntentScheme)
        }
    }

    private fun shouldShowLearnMoreLink(): Boolean {
        return !getPermissionControllerResString(HELP_CENTER_URL_ID).isNullOrEmpty()
    }

    /** Companion object for [AppDataSharingUpdatesTest]. */
    companion object {
        private val TAG = AppDataSharingUpdatesTest::class.java.simpleName

        private const val HELP_CENTER_URL_ID = "data_sharing_help_center_link"
        private const val HELP_CENTER_TIMEOUT_MILLIS: Long = 20000
    }
}
