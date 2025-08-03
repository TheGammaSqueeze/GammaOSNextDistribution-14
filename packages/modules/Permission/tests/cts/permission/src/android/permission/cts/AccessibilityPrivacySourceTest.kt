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

package android.permission.cts

import android.accessibility.cts.common.InstrumentedAccessibilityService
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule
import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import android.permission.cts.CtsNotificationListenerServiceUtils.assertEmptyNotification
import android.permission.cts.CtsNotificationListenerServiceUtils.assertNotificationExist
import android.permission.cts.CtsNotificationListenerServiceUtils.cancelNotification
import android.permission.cts.CtsNotificationListenerServiceUtils.cancelNotifications
import android.permission.cts.CtsNotificationListenerServiceUtils.getNotification
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterIssueDoesNotExist
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterIssueExist
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterStarted
import android.permission.cts.SafetyCenterUtils.deleteDeviceConfigPrivacyProperty
import android.permission.cts.SafetyCenterUtils.deviceSupportsSafetyCenter
import android.permission.cts.SafetyCenterUtils.setDeviceConfigPrivacyProperty
import android.platform.test.annotations.AppModeFull
import android.platform.test.rule.ScreenRecordRule
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterManager
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(
    reason =
        "Cannot set system settings as instant app. Also we never show an accessibility " +
            "notification for instant apps."
)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@ScreenRecordRule.ScreenRecord
@FlakyTest
class AccessibilityPrivacySourceTest {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val permissionControllerPackage = context.packageManager.permissionControllerPackageName
    private val accessibilityTestService =
        ComponentName(context, AccessibilityTestService::class.java).flattenToString()
    private val safetyCenterIssueId = "accessibility_$accessibilityTestService"
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)

    @get:Rule val screenRecordRule = ScreenRecordRule(false, false)

    @get:Rule
    val mAccessibilityServiceRule =
        InstrumentedAccessibilityServiceTestRule(AccessibilityTestService::class.java, false)

    @get:Rule
    val deviceConfigSafetyCenterEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SAFETY_CENTER_ENABLED,
            true.toString()
        )

    @get:Rule
    val deviceConfigA11yListenerDisabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            ACCESSIBILITY_LISTENER_ENABLED,
            false.toString()
        )

    @Before
    fun setup() {
        Assume.assumeTrue(deviceSupportsSafetyCenter(context))
        InstrumentedAccessibilityService.disableAllServices()
        runShellCommand("input keyevent KEYCODE_WAKEUP")
        resetPermissionController()
        // Bypass battery saving restrictions
        runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackage true"
        )
        cancelNotifications(permissionControllerPackage)
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
        runWithShellPermissionIdentity { safetyCenterManager?.clearAllSafetySourceDataForTests() }
        assertSafetyCenterIssueDoesNotExist(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetyCenterIssueId,
            SC_ACCESSIBILITY_ISSUE_TYPE_ID
        )
    }

    @After
    fun cleanup() {
        cancelNotifications(permissionControllerPackage)
        // Reset battery saving restrictions
        runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackage default"
        )
        runWithShellPermissionIdentity { safetyCenterManager?.clearAllSafetySourceDataForTests() }
    }

    @Test
    fun testJobSendsNotification() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobSendsNotificationOnEnable() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)

        setDeviceConfigPrivacyProperty(ACCESSIBILITY_LISTENER_ENABLED, true.toString())
        cancelNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
        InstrumentedAccessibilityService.disableAllServices()
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_LISTENER_ENABLED, false.toString())
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_JOB_INTERVAL_MILLIS, "0")

        // enable service again and verify a notification
        try {
            mAccessibilityServiceRule.enableService()
            runJobAndWaitUntilCompleted()
            assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
        } finally {
            deleteDeviceConfigPrivacyProperty(ACCESSIBILITY_JOB_INTERVAL_MILLIS)
        }
    }

    @Test
    fun testJobSendsIssuesToSafetyCenter() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueExist(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetyCenterIssueId,
            SC_ACCESSIBILITY_ISSUE_TYPE_ID
        )
    }

    @Test
    fun testJobDoesNotSendNotificationInSecondRunForSameService() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)

        cancelNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)

        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testAccessibilityListenerSendsIssueToSafetyCenter() {
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_LISTENER_ENABLED, true.toString())
        try {
            val automation = getAutomation()
            mAccessibilityServiceRule.enableService()
            TestUtils.eventually(
                {
                    assertSafetyCenterIssueExist(
                        SC_ACCESSIBILITY_SOURCE_ID,
                        safetyCenterIssueId,
                        SC_ACCESSIBILITY_ISSUE_TYPE_ID,
                        automation
                    )
                },
                TIMEOUT_MILLIS
            )
            automation.destroy()
        } finally {
            setDeviceConfigPrivacyProperty(ACCESSIBILITY_LISTENER_ENABLED, false.toString())
        }
    }

    @Test
    fun testJobWithDisabledServiceDoesNotSendNotification() {
        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobWithDisabledServiceDoesNotSendIssueToSafetyCenter() {
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueDoesNotExist(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetyCenterIssueId,
            SC_ACCESSIBILITY_ISSUE_TYPE_ID
        )
    }

    @Test
    fun testJobWithSafetyCenterDisabledDoesNotSendNotification() {
        setDeviceConfigPrivacyProperty(SAFETY_CENTER_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobWithSafetyCenterDisabledDoesNotSendIssueToSafetyCenter() {
        setDeviceConfigPrivacyProperty(SAFETY_CENTER_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueDoesNotExist(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetyCenterIssueId,
            SC_ACCESSIBILITY_ISSUE_TYPE_ID
        )
    }

    @Test
    fun testNotificationClickOpenSafetyCenter() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
        val statusBarNotification =
            getNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
        Assert.assertNotNull(statusBarNotification)
        val contentIntent = statusBarNotification!!.notification.contentIntent
        if (SdkLevel.isAtLeastU()) {
            val options =
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
            contentIntent.send(
                /* context = */ null,
                /* code = */ 0,
                /* intent = */ null,
                /* onFinished = */ null,
                /* handler = */ null,
                /* requiredPermission = */ null,
                /* options = */ options.toBundle()
            )
        } else {
            contentIntent.send()
        }
        assertSafetyCenterStarted()
    }

    private fun getAutomation(): UiAutomation {
        return instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        )
    }

    private fun runJobAndWaitUntilCompleted() {
        TestUtils.runJobAndWaitUntilCompleted(
            permissionControllerPackage,
            ACCESSIBILITY_JOB_ID,
            TIMEOUT_MILLIS,
            getAutomation()
        )
    }

    /** Reset the permission controllers state. */
    @Throws(Throwable::class)
    private fun resetPermissionController() {
        PermissionUtils.resetPermissionControllerJob(
            getAutomation(),
            permissionControllerPackage,
            ACCESSIBILITY_JOB_ID,
            45000,
            ACTION_SET_UP_ACCESSIBILITY_CHECK,
            AccessibilityOnBootReceiver
        )
    }

    companion object {
        private const val SC_ACCESSIBILITY_SOURCE_ID = "AndroidAccessibility"
        private const val SAFETY_CENTER_ENABLED = "safety_center_is_enabled"
        private const val ACCESSIBILITY_LISTENER_ENABLED = "sc_accessibility_listener_enabled"
        private const val ACCESSIBILITY_JOB_INTERVAL_MILLIS = "sc_accessibility_job_interval_millis"

        private const val ACCESSIBILITY_JOB_ID = 6
        private const val ACCESSIBILITY_NOTIFICATION_ID = 4
        private const val TIMEOUT_MILLIS: Long = 10000

        private const val SC_ACCESSIBILITY_ISSUE_TYPE_ID = "accessibility_privacy_issue"

        private const val AccessibilityOnBootReceiver =
            "com.android.permissioncontroller.privacysources.AccessibilityOnBootReceiver"
        private const val ACTION_SET_UP_ACCESSIBILITY_CHECK =
            "com.android.permissioncontroller.action.SET_UP_ACCESSIBILITY_CHECK"

        @get:ClassRule
        @JvmStatic
        val ctsNotificationListenerHelper =
            CtsNotificationListenerHelperRule(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
    }
}
