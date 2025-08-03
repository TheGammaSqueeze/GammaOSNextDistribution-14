/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hibernation.cts

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.UiAutomation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.DeviceConfig
import android.util.Log
import androidx.test.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.ExceptionUtils.wrappingExceptions
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.compatibility.common.util.UiDumpUtils
import com.android.compatibility.common.util.click
import com.android.compatibility.common.util.depthFirstSearch
import com.android.compatibility.common.util.textAsString
import com.android.modules.utils.build.SdkLevel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse

private const val BROADCAST_TIMEOUT_MS = 60000L

const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"
const val HIBERNATION_BOOT_RECEIVER_CLASS_NAME =
    "com.android.permissioncontroller.hibernation.HibernationOnBootReceiver"
const val ACTION_SET_UP_HIBERNATION =
    "com.android.permissioncontroller.action.SET_UP_HIBERNATION"

const val SYSUI_PKG_NAME = "com.android.systemui"
const val NOTIF_LIST_ID = "notification_stack_scroller"
const val NOTIF_LIST_ID_AUTOMOTIVE = "notifications"
const val CLEAR_ALL_BUTTON_ID = "dismiss_text"
const val MANAGE_BUTTON_AUTOMOTIVE = "manage_button"
// Time to find a notification. Unlikely, but in cases with a lot of notifications, it may take
// time to find the notification we're looking for
const val NOTIF_FIND_TIMEOUT = 20000L
const val VIEW_WAIT_TIMEOUT = 3000L
const val JOB_RUN_TIMEOUT = 40000L
const val JOB_RUN_WAIT_TIME = 3000L

const val CMD_EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
const val CMD_COLLAPSE = "cmd statusbar collapse"
const val CMD_CLEAR_NOTIFS = "service call notification 1"

const val APK_PATH_S_APP = "/data/local/tmp/cts/hibernation/CtsAutoRevokeSApp.apk"
const val APK_PACKAGE_NAME_S_APP = "android.hibernation.cts.autorevokesapp"
const val APK_PATH_R_APP = "/data/local/tmp/cts/hibernation/CtsAutoRevokeRApp.apk"
const val APK_PACKAGE_NAME_R_APP = "android.hibernation.cts.autorevokerapp"
const val APK_PATH_Q_APP = "/data/local/tmp/cts/hibernation/CtsAutoRevokeQApp.apk"
const val APK_PACKAGE_NAME_Q_APP = "android.hibernation.cts.autorevokeqapp"

fun runBootCompleteReceiver(context: Context, testTag: String) {
    val pkgManager = context.packageManager
    val permissionControllerPkg = pkgManager.permissionControllerPackageName
    var permissionControllerSetupIntent = Intent(ACTION_SET_UP_HIBERNATION).apply {
        setPackage(permissionControllerPkg)
        setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    val receivers = pkgManager.queryBroadcastReceivers(
        permissionControllerSetupIntent, /* flags= */ 0)
    if (receivers.size == 0) {
        // May be on an older, pre-built PermissionController. In this case, try sending directly.
        permissionControllerSetupIntent = Intent().apply {
            setPackage(permissionControllerPkg)
            setClassName(permissionControllerPkg, HIBERNATION_BOOT_RECEIVER_CLASS_NAME)
            setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
    }
    val countdownLatch = CountDownLatch(1)
    Log.d(testTag, "Sending boot complete broadcast directly to $permissionControllerPkg")
    context.sendOrderedBroadcast(
        permissionControllerSetupIntent,
        /* receiverPermission= */ null,
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                countdownLatch.countDown()
                Log.d(testTag, "Broadcast received by $permissionControllerPkg")
            }
        },
        Handler.createAsync(Looper.getMainLooper()),
        Activity.RESULT_OK,
        /* initialData= */ null,
        /* initialExtras= */ null)
    assertTrue("Timed out while waiting for boot receiver broadcast to be received",
        countdownLatch.await(BROADCAST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
}

fun bypassBatterySavingRestrictions(context: Context) {
    if (SdkLevel.isAtLeastU()) {
        val userId = Process.myUserHandle().identifier
        val permissionControllerPackageName =
            context.packageManager.permissionControllerPackageName
        runShellCommandOrThrow("cmd tare set-vip $userId $permissionControllerPackageName true")
    }
}

fun resetBatterySavingRestrictions(context: Context) {
    if (SdkLevel.isAtLeastU()) {
        val userId = Process.myUserHandle().identifier
        val permissionControllerPackageName =
            context.packageManager.permissionControllerPackageName
        runShellCommandOrThrow("cmd tare set-vip $userId $permissionControllerPackageName default")
    }
}

fun resetJob(context: Context) {
    val userId = Process.myUserHandle().identifier
    val permissionControllerPackageName =
        context.packageManager.permissionControllerPackageName
    runShellCommandOrThrow("cmd jobscheduler reset-execution-quota -u " +
            "$userId $permissionControllerPackageName")
    runShellCommandOrThrow("cmd jobscheduler reset-schedule-quota")
}

fun runAppHibernationJob(context: Context, tag: String) {
    runAppHibernationJobInternal(context, tag)
    if (Build.VERSION.SDK_INT == 31) {
        // On S and S only, run the job twice as a workaround for a deadlock. See b/291147868
        runAppHibernationJobInternal(context, tag)
    }
}

private fun runAppHibernationJobInternal(context: Context, tag: String) {
    val userId = Process.myUserHandle().identifier
    val permissionControllerPackageName =
        context.packageManager.permissionControllerPackageName
    runShellCommandOrThrow("cmd jobscheduler run -u " +
            "$userId -f " +
            "$permissionControllerPackageName 2")
    eventually({
        Thread.sleep(JOB_RUN_WAIT_TIME)
        val jobState = runShellCommandOrThrow("cmd jobscheduler get-job-state -u " +
            "$userId " +
            "$permissionControllerPackageName 2")
        Log.d(tag, "Job output: $jobState")
        assertTrue("Job expected waiting but is $jobState", jobState.contains("waiting"))
    }, JOB_RUN_TIMEOUT)
}

fun runPermissionEventCleanupJob(context: Context) {
    eventually {
        runShellCommandOrThrow("cmd jobscheduler run -u " +
            "${Process.myUserHandle().identifier} -f " +
            "${context.packageManager.permissionControllerPackageName} 3")
    }
}

inline fun withApp(
    apk: String,
    packageName: String,
    action: () -> Unit
) {
    installApk(apk)
    try {
        // Try to reduce flakiness caused by new package update not propagating in time
        Thread.sleep(1000)
        action()
    } finally {
        uninstallApp(packageName)
    }
}

inline fun withAppNoUninstallAssertion(
    apk: String,
    packageName: String,
    action: () -> Unit
) {
    installApk(apk)
    try {
        // Try to reduce flakiness caused by new package update not propagating in time
        Thread.sleep(1000)
        action()
    } finally {
        uninstallAppWithoutAssertion(packageName)
    }
}

inline fun <T> withDeviceConfig(
    namespace: String,
    name: String,
    value: String,
    action: () -> T
): T {
    val oldValue = runWithShellPermissionIdentity(ThrowingSupplier {
        DeviceConfig.getProperty(namespace, name)
    })
    try {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, value, false /* makeDefault */)
        }
        return action()
    } finally {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, oldValue, false /* makeDefault */)
        }
    }
}

inline fun <T> withUnusedThresholdMs(threshold: Long, action: () -> T): T {
    return withDeviceConfig(
        DeviceConfig.NAMESPACE_PERMISSIONS, "auto_revoke_unused_threshold_millis2",
        threshold.toString(), action)
}

inline fun <T> withSafetyCenterEnabled(action: () -> T): T {
    assumeFalse("This test is only supported on phones",
        hasFeatureWatch() || hasFeatureTV() || hasFeatureAutomotive()
    )

    return withDeviceConfig(
        DeviceConfig.NAMESPACE_PRIVACY, PROPERTY_SAFETY_CENTER_ENABLED,
        true.toString(), action)
}

fun awaitAppState(pkg: String, stateMatcher: Matcher<Int>) {
    val context: Context = InstrumentationRegistry.getTargetContext()
    eventually {
        runWithShellPermissionIdentity {
            val packageImportance = context
                .getSystemService(ActivityManager::class.java)!!
                .getPackageImportance(pkg)
            assertThat(packageImportance, stateMatcher)
        }
    }
}

fun startApp(packageName: String) {
    val context = InstrumentationRegistry.getTargetContext()
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    context.startActivity(intent)
    awaitAppState(packageName, Matchers.lessThanOrEqualTo(IMPORTANCE_TOP_SLEEPING))
    waitForIdle()
}

fun goHome() {
    runShellCommandOrThrow("input keyevent KEYCODE_HOME")
    waitForIdle()
}

/**
 * Clear notifications from shade
 */
fun clearNotifications() {
    runWithShellPermissionIdentity {
        runShellCommandOrThrow(CMD_CLEAR_NOTIFS)
    }
}

/**
 * Open the "unused apps" notification which is sent after the hibernation job.
 */
fun openUnusedAppsNotification() {
    val notifSelector = By.textContains("unused app")
    if (hasFeatureWatch()) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        expandNotificationsWatch(UiAutomatorUtils2.getUiDevice())
        waitFindObject(uiAutomation, notifSelector).click()
        // In wear os, notification has one additional button to open it
        waitFindObject(uiAutomation, By.textContains("Open")).click()
    } else {
        val permissionPkg: String = InstrumentationRegistry.getTargetContext()
            .packageManager.permissionControllerPackageName
        eventually({
            // Eventually clause because clicking is sometimes inconsistent if the screen is
            // scrolling
            runShellCommandOrThrow(CMD_EXPAND_NOTIFICATIONS)
            val notification = waitFindNotification(notifSelector, NOTIF_FIND_TIMEOUT)
            if (hasFeatureAutomotive()) {
                notification.click(Point(0, 0))
            } else {
                notification.click()
            }
            wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause) }) {
                assertTrue(
                    "Unused apps page did not open after tapping notification.",
                    UiAutomatorUtils2.getUiDevice().wait(
                        Until.hasObject(By.pkg(permissionPkg).depth(0)), VIEW_WAIT_TIMEOUT
                    )
                )
            }
        }, NOTIF_FIND_TIMEOUT)
    }
}

fun hasFeatureWatch(): Boolean {
    return InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
        PackageManager.FEATURE_WATCH)
}

fun hasFeatureTV(): Boolean {
    return InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_LEANBACK) ||
            InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEVISION)
}

fun hasFeatureAutomotive(): Boolean {
    return InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
        PackageManager.FEATURE_AUTOMOTIVE)
}

private fun expandNotificationsWatch(uiDevice: UiDevice) {
    with(uiDevice) {
        wakeUp()
        // Swipe up from bottom to reveal notifications
        val x = displayWidth / 2
        swipe(x, displayHeight, x, 0, 1)
    }
}

/**
 * Reset to the top of the notifications list.
 */
private fun resetNotifications(notificationList: UiScrollable) {
    runShellCommandOrThrow(CMD_COLLAPSE)
    notificationList.waitUntilGone(VIEW_WAIT_TIMEOUT)
    runShellCommandOrThrow(CMD_EXPAND_NOTIFICATIONS)
}

private fun waitFindNotification(selector: BySelector, timeoutMs: Long):
    UiObject2 {
    var view: UiObject2? = null
    val start = System.currentTimeMillis()
    val uiDevice = UiAutomatorUtils2.getUiDevice()

    var isAtEnd = false
    var wasScrolledUpAlready = false
    val notificationListId = if (FeatureUtil.isAutomotive()) {
        NOTIF_LIST_ID_AUTOMOTIVE
    } else {
        NOTIF_LIST_ID
    }
    val notificationEndViewId = if (FeatureUtil.isAutomotive()) {
        MANAGE_BUTTON_AUTOMOTIVE
    } else {
        CLEAR_ALL_BUTTON_ID
    }
    while (view == null && start + timeoutMs > System.currentTimeMillis()) {
        view = uiDevice.wait(Until.findObject(selector), VIEW_WAIT_TIMEOUT)
        if (view == null) {
            val notificationList = UiScrollable(UiSelector().resourceId(
                SYSUI_PKG_NAME + ":id/" + notificationListId))
            wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause) }) {
                Assert.assertTrue("Notification list view not found",
                    notificationList.waitForExists(VIEW_WAIT_TIMEOUT))
            }
            if (isAtEnd) {
                if (wasScrolledUpAlready) {
                    break
                }
                resetNotifications(notificationList)
                isAtEnd = false
                wasScrolledUpAlready = true
            } else {
                notificationList.scrollForward()
                isAtEnd = uiDevice.hasObject(By.res(SYSUI_PKG_NAME, notificationEndViewId))
            }
        }
    }
    wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause) }) {
        Assert.assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
            view)
    }
    return view!!
}

fun waitFindObject(uiAutomation: UiAutomation, selector: BySelector): UiObject2 {
    try {
        return UiAutomatorUtils2.waitFindObject(selector)
    } catch (e: RuntimeException) {
        val ui = uiAutomation.rootInActiveWindow

        val title = ui.depthFirstSearch { node ->
            node.viewIdResourceName?.contains("alertTitle") == true
        }
        val okCloseButton = ui.depthFirstSearch { node ->
            (node.textAsString?.equals("OK", ignoreCase = true) ?: false) ||
                (node.textAsString?.equals("Close app", ignoreCase = true) ?: false)
        }
        val titleString = title?.text?.toString()
        if (okCloseButton != null &&
            titleString != null &&
            (titleString == "Android System" ||
                titleString.endsWith("keeps stopping"))) {
            // Auto dismiss occasional system dialogs to prevent interfering with the test
            android.util.Log.w(AutoRevokeTest.LOG_TAG, "Ignoring exception", e)
            okCloseButton.click()
            return UiAutomatorUtils2.waitFindObject(selector)
        } else {
            throw e
        }
    }
}
