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

package android.security.cts

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.StaleObjectException
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.text.Html
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.UiAutomatorUtils
import com.android.modules.utils.build.SdkLevel
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

abstract class BasePermissionUiTest : StsExtraBusinessLogicTestCase() {
    protected val mInstrumentation = InstrumentationRegistry.getInstrumentation()
    protected val mContext = mInstrumentation.targetContext
    protected val packageManager = mContext.packageManager
    protected val uiAutomation: UiAutomation = mInstrumentation.uiAutomation
    protected val uiDevice: UiDevice = UiDevice.getInstance(mInstrumentation)

    protected val isAutomotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    protected val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    protected val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    companion object {
        const val SPLIT_PERMISSION_APK_PATH =
            "/data/local/tmp/cts/security/SplitBluetoothPermissionTestApp.apk"

        const val APP_PACKAGE_NAME = "android.security.cts.usepermission"
        const val NOTIF_TEXT = "permgrouprequest_notifications"
        const val ALLOW_BUTTON_TEXT = "grant_dialog_button_allow"
        const val ALLOW_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_button"
        const val DENY_BUTTON_TEXT = "grant_dialog_button_deny"
        const val DENY_BUTTON = "com.android.permissioncontroller:id/permission_deny_button"
        const val IDLE_TIMEOUT_MILLIS: Long = 1000
        const val TIMEOUT_MILLIS: Long = 120000
    }

    @get:Rule
    val activityRule = ActivityTestRule(StartForFutureActivity::class.java, false, false)

    private var screenTimeoutBeforeTest: Long = 0L

    @Before
    fun setUp() {
        SystemUtil.runWithShellPermissionIdentity {
            screenTimeoutBeforeTest = Settings.System.getLong(
                mContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT
            )
            Settings.System.putLong(
                mContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1800000L
            )
        }

        uiDevice.wakeUp()
        SystemUtil.runShellCommand(mInstrumentation, "wm dismiss-keyguard")

        uiDevice.findObject(By.text("Close"))?.click()
    }

    @After
    fun tearDown() {
        SystemUtil.runWithShellPermissionIdentity {
            Settings.System.putLong(
                mContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                screenTimeoutBeforeTest
            )
        }

        pressHome()
    }

    protected fun waitForIdle() = uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)

    protected fun waitFindObject(selector: BySelector): UiObject2 {
        waitForIdle()
        return findObjectWithRetry({ t -> UiAutomatorUtils.waitFindObject(selector, t) })!!
    }

    protected fun waitFindObject(selector: BySelector, timeoutMillis: Long): UiObject2 {
        waitForIdle()
        return findObjectWithRetry({ t -> UiAutomatorUtils.waitFindObject(selector, t) },
            timeoutMillis)!!
    }

    protected fun waitFindObjectOrNull(selector: BySelector): UiObject2? {
        waitForIdle()
        return findObjectWithRetry({ t -> UiAutomatorUtils.waitFindObjectOrNull(selector, t) })
    }

    protected fun waitFindObjectOrNull(selector: BySelector, timeoutMillis: Long): UiObject2? {
        waitForIdle()
        return findObjectWithRetry({ t -> UiAutomatorUtils.waitFindObjectOrNull(selector, t) },
            timeoutMillis)
    }

    protected fun pressHome() {
        uiDevice.pressHome()
        waitForIdle()
    }

    private fun findObjectWithRetry(
        automatorMethod: (timeoutMillis: Long) -> UiObject2?,
        timeoutMillis: Long = 20_000L
    ): UiObject2? {
        waitForIdle()
        val startTime = SystemClock.elapsedRealtime()
        return try {
            automatorMethod(timeoutMillis)
        } catch (e: StaleObjectException) {
            val remainingTime = timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
            if (remainingTime <= 0) {
                throw e
            }
            automatorMethod(remainingTime)
        }
    }

    protected fun click(selector: BySelector, timeoutMillis: Long = 20_000) {
        waitFindObject(selector, timeoutMillis).click()
        waitForIdle()
    }

    protected fun clickPermissionControllerUi(selector: BySelector, timeoutMillis: Long = 20_000) {
        click(selector.pkg(mContext.packageManager.permissionControllerPackageName), timeoutMillis)
    }

    protected fun requestAppPermissionsAndAssertResult(
        vararg permissionAndExpectedGrantResults: Pair<String?, Boolean>,
        block: () -> Unit
    ) = requestAppPermissionsAndAssertResult(
        permissionAndExpectedGrantResults.map { it.first }.toTypedArray(),
        permissionAndExpectedGrantResults,
        block
    )

    protected fun requestAppPermissionsAndAssertResult(
        permissions: Array<out String?>,
        permissionAndExpectedGrantResults: Array<out Pair<String?, Boolean>>,
        block: () -> Unit
    ) {
        val result = requestAppPermissions(*permissions, block = block)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(
            result.resultData!!.getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!.size,
            result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!.size
        )

        assertEquals(
            permissionAndExpectedGrantResults.toList(),
            result.resultData!!.getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!
                .zip(
                    result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!
                        .map { it == PackageManager.PERMISSION_GRANTED }
                )
        )
        permissionAndExpectedGrantResults.forEach {
            it.first?.let { permission ->
                assertAppHasPermission(permission, it.second)
            }
        }
    }

    protected fun requestAppPermissions(
        vararg permissions: String?,
        block: () -> Unit
    ): Instrumentation.ActivityResult {
        // Request the permissions

        Log.v("BaseClass", "$APP_PACKAGE_NAME.PERMISSIONS = $permissions")

        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.RequestPermissionActivity"
                )
                putExtra("$APP_PACKAGE_NAME.PERMISSIONS", permissions)
            }
        )
        waitForIdle()
        // Notification permission prompt is shown first, so get it out of the way
        clickNotificationPermissionRequestAllowButtonIfAvailable()
        // Perform the post-request action
        block()
        return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    }

    /**
     * Only for use in tests that are not testing the notification permission popup
     */
    private fun clickNotificationPermissionRequestAllowButtonIfAvailable() {
        if (!SdkLevel.isAtLeastT()) {
            return
        }

        if (waitFindObjectOrNull(
                By.text(getPermissionControllerString(
                    NOTIF_TEXT,
                    APP_PACKAGE_NAME
                )), 1000) != null ||
            waitFindObjectOrNull(
                By.text(getPermissionControllerString(
                    NOTIF_TEXT, APP_PACKAGE_NAME
                )), 1000) != null) {
            if (isAutomotive) {
                click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)))
            } else {
                click(By.res(ALLOW_BUTTON))
            }
        }
    }

    private val mPermissionControllerResources: Resources = mContext.createPackageContext(
        mContext.packageManager.permissionControllerPackageName, 0).resources

    private fun getPermissionControllerString(res: String, vararg formatArgs: Any): Pattern {
        val textWithHtml = mPermissionControllerResources.getString(
            mPermissionControllerResources.getIdentifier(
                res, "string", "com.android.permissioncontroller"), *formatArgs)
        val textWithoutHtml = Html.fromHtml(textWithHtml, 0).toString()
        return Pattern.compile(
            Pattern.quote(textWithoutHtml),
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    private fun startActivityForFuture(
        intent: Intent
    ): CompletableFuture<Instrumentation.ActivityResult> =
        CompletableFuture<Instrumentation.ActivityResult>().also {
            activityRule.launchActivity(null).startActivityForFuture(intent, it)
        }

    protected fun assertAppHasPermission(permissionName: String, expectPermission: Boolean) {
        assertEquals(
            if (expectPermission) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            },
            packageManager.checkPermission(permissionName, APP_PACKAGE_NAME)
        )
    }

    private val user = Process.myUserHandle()

    protected fun installPackage(apkPath: String) {
        SystemUtil.runShellCommand("pm install -r --user ${user.identifier} $apkPath")
    }

    protected fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    protected fun clickPermissionRequestDenyButton() {
        if (isAutomotive || isWatch || isTv) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(By.res(DENY_BUTTON))
        }
    }

    protected fun clickPermissionRequestAllowButton(timeoutMillis: Long = 20000) {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)), timeoutMillis)
        } else {
            click(By.res(ALLOW_BUTTON), timeoutMillis)
        }
    }
}
