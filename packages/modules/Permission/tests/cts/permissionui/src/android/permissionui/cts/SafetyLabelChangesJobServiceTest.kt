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

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.os.Process
import android.permission.cts.CtsNotificationListenerHelperRule
import android.permission.cts.CtsNotificationListenerServiceUtils
import android.permission.cts.CtsNotificationListenerServiceUtils.getNotification
import android.permission.cts.CtsNotificationListenerServiceUtils.getNotificationForPackageAndId
import android.permission.cts.PermissionUtils
import android.permission.cts.TestUtils
import android.permissionui.cts.AppMetadata.createAppMetadataWithLocationSharingNoAds
import android.permissionui.cts.AppMetadata.createAppMetadataWithNoSharing
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import androidx.test.InstrumentationRegistry
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/** End-to-end test for SafetyLabelChangesJobService. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class SafetyLabelChangesJobServiceTest : BaseUsePermissionTest() {

    @get:Rule
    val safetyLabelChangeNotificationsEnabledConfig =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
            true.toString()
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

    @get:Rule
    val deviceConfigDataSharingUpdatesPeriod =
        DeviceConfigStateChangerRule(
            BasePermissionTest.context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
            "600000"
        )

    @Before
    fun setup() {
        val packageManager = context.packageManager
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))

        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemUtil.runShellCommand("wm dismiss-keyguard")

        // Bypass battery saving restrictions
        SystemUtil.runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackageName true"
        )
        CtsNotificationListenerServiceUtils.cancelNotifications(permissionControllerPackageName)
        resetPermissionControllerAndSimulateReboot()
    }

    @After
    fun cancelJobsAndNotifications() {
        cancelJob(SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID)
        cancelJob(SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID)
        CtsNotificationListenerServiceUtils.cancelNotifications(permissionControllerPackageName)
        // Reset battery saving restrictions
        SystemUtil.runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackageName default"
        )
    }

    @Test
    fun runDetectUpdatesJob_initializesSafetyLabelsHistoryForApps() {
        installPackageNoBroadcast(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app install is
        // identified and recorded.
        runDetectUpdatesJob()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()

        assertNotificationNotShown()
        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_initializesSafetyLabelsHistoryForApps() {
        installPackageNoBroadcast(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app install is
        // identified and recorded.
        runNotificationJob()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()

        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runDetectUpdatesJob_updatesSafetyLabelHistoryForApps() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runDetectUpdatesJob()

        assertNotificationNotShown()
        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_updatesSafetyLabelHistoryForApps() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_whenLocationSharingUpdatesForLocationGrantedApps_showsNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        // TODO(b/279455955): Investigate why this is necessary and remove if possible.
        Thread.sleep(500)
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()
        grantLocationPermission(APP_PACKAGE_NAME)

        runNotificationJob()

        waitForNotificationShown()

        val statusBarNotification =
            getNotification(permissionControllerPackageName, SAFETY_LABEL_CHANGES_NOTIFICATION_ID)
        val contentIntent = statusBarNotification!!.notification.contentIntent
        contentIntent.send()

        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_whenNoLocationGrantedApps_doesNotShowNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()

        runNotificationJob()

        assertNotificationNotShown()
    }

    @Test
    fun runNotificationJob_whenNoLocationSharingUpdates_doesNotShowNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        grantLocationPermission(APP_PACKAGE_NAME)

        runNotificationJob()

        assertNotificationNotShown()
    }

    @Test
    fun runNotificationJob_packageSourceUnspecified_updatesSafetyLabelHistoryForApps() {
        installPackageViaSession(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_UNSPECIFIED
        )
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_UNSPECIFIED
        )
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_packageSourceOther_doesNotShowNotification() {
        installPackageViaSession(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_OTHER
        )
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_OTHER
        )
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertNotificationNotShown()
    }

    @Test
    fun runNotificationJob_packageSourceStore_updatesSafetyLabelHistoryForApps() {
        installPackageViaSession(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_STORE
        )
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_STORE
        )
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertDataSharingScreenHasUpdates()
    }

    @Test
    fun runNotificationJob_packageSourceLocalFile_doesNotShowNotification() {
        installPackageViaSession(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_LOCAL_FILE
        )
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_LOCAL_FILE
        )
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertNotificationNotShown()
    }

    @Test
    fun runNotificationJob_packageSourceDownloadedFile_udoesNotShowNotification() {
        installPackageViaSession(
            APP_APK_NAME_31,
            createAppMetadataWithNoSharing(),
            PACKAGE_SOURCE_DOWNLOADED_FILE
        )
        waitForBroadcastReceiverFinished()
        installPackageNoBroadcast(
            APP_APK_NAME_31,
            createAppMetadataWithLocationSharingNoAds(),
            PACKAGE_SOURCE_DOWNLOADED_FILE
        )
        grantLocationPermission(APP_PACKAGE_NAME)

        // Run the job to check whether the missing safety label for the above app update is
        // identified and recorded.
        runNotificationJob()

        assertNotificationNotShown()
    }

    private fun grantLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun installPackageNoBroadcast(
        apkName: String,
        appMetadata: PersistableBundle? = null,
        packageSource: Int? = null
    ) {
        // Disable the safety labels feature during install to simulate installing an app without
        // receiving an update about the change to its safety label.
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())
        installPackageViaSession(apkName, appMetadata, packageSource)
        waitForBroadcastReceiverFinished()
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, true.toString())
    }

    private fun assertDataSharingScreenHasUpdates() {
        startAppDataSharingUpdatesActivity()
        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
        } finally {
            pressBack()
        }
    }

    companion object {
        private const val TIMEOUT_TIME_MS = 60_000L
        private const val SHORT_SLEEP_MS = 2000L

        private const val SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID = 8
        private const val SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID = 9
        private const val SET_UP_SAFETY_LABEL_CHANGES_JOB =
            "com.android.permissioncontroller.action.SET_UP_SAFETY_LABEL_CHANGES_JOB"
        private const val SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS =
            "com.android.permissioncontroller.permission.service.v34" +
                ".SafetyLabelChangesJobService\$Receiver"
        private const val SAFETY_LABEL_CHANGES_NOTIFICATION_ID = 5
        private const val JOB_STATUS_UNKNOWN = "unknown"
        private const val JOB_STATUS_ACTIVE = "active"
        private const val JOB_STATUS_WAITING = "waiting"

        private val context: Context = InstrumentationRegistry.getTargetContext()
        private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        private fun uiAutomation(): UiAutomation = instrumentation.uiAutomation
        private val permissionControllerPackageName =
            context.packageManager.permissionControllerPackageName
        private val userId = Process.myUserHandle().identifier

        @get:ClassRule
        @JvmStatic
        val ctsNotificationListenerHelper =
            CtsNotificationListenerHelperRule(
                InstrumentationRegistry.getInstrumentation().targetContext
            )

        private fun waitForNotificationShown() {
            eventually {
                val notification = getNotification(false)
                assertThat(notification).isNotNull()
            }
        }

        private fun assertNotificationNotShown() {
            eventually {
                val notification = getNotification(false)
                assertThat(notification).isNull()
            }
        }

        private fun getNotification(cancelNotification: Boolean) =
            getNotificationForPackageAndId(
                    permissionControllerPackageName,
                    SAFETY_LABEL_CHANGES_NOTIFICATION_ID,
                    cancelNotification
                )
                ?.notification

        private fun cancelJob(jobId: Int) {
            SystemUtil.runShellCommandOrThrow(
                "cmd jobscheduler cancel -u $userId $permissionControllerPackageName $jobId"
            )
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName,
                jobId,
                TIMEOUT_TIME_MS,
                uiAutomation(),
                JOB_STATUS_UNKNOWN
            )
        }

        private fun runDetectUpdatesJob() {
            startJob(SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID)
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID,
                TIMEOUT_TIME_MS,
                uiAutomation(),
                JOB_STATUS_ACTIVE
            )
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID,
                TIMEOUT_TIME_MS,
                uiAutomation(),
                JOB_STATUS_UNKNOWN
            )
        }

        private fun runNotificationJob() {
            startJob(SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID)
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID,
                TIMEOUT_TIME_MS,
                uiAutomation(),
                JOB_STATUS_ACTIVE
            )
            // TODO(b/266449833): In theory we should only have to wait for "waiting" here, but
            // sometimes jobscheduler returns "unknown".
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID,
                TIMEOUT_TIME_MS,
                uiAutomation(),
                JOB_STATUS_WAITING,
                JOB_STATUS_UNKNOWN
            )
        }

        private fun startJob(jobId: Int) {
            val runJobCmd =
                "cmd jobscheduler run -u $userId -f " + "$permissionControllerPackageName $jobId"
            try {
                SystemUtil.runShellCommandOrThrow(runJobCmd)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }

        private fun resetPermissionControllerAndSimulateReboot() {
            PermissionUtils.resetPermissionControllerJob(
                uiAutomation(),
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID,
                TIMEOUT_TIME_MS,
                SET_UP_SAFETY_LABEL_CHANGES_JOB,
                SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS
            )
        }

        private fun waitForBroadcastReceiverFinished() {
            waitForBroadcasts()
            // Add a short sleep to ensure that the SafetyLabelChangedBroadcastReceiver finishes its
            // work based according to the current feature flag value before changing the flag
            // value.
            // While `waitForBroadcasts()` waits for broadcasts to be dispatched, it will not wait
            // for
            // the receivers' `onReceive` to finish.
            Thread.sleep(SHORT_SLEEP_MS)
        }
    }
}
