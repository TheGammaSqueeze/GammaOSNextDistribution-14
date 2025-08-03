/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.UiAutomation
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.PersistableBundle
import android.os.SystemClock
import android.platform.test.rule.ScreenRecordRule
import android.provider.DeviceConfig
import android.provider.Settings
import android.text.Html
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.modules.utils.build.SdkLevel
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule

@ScreenRecordRule.ScreenRecord
abstract class BasePermissionTest {
    companion object {
        private const val TAG = "BasePermissionTest"

        private const val INSTALL_ACTION_CALLBACK = "BasePermissionTest.install_callback"

        private const val MAX_SWIPES = 5

        const val APK_DIRECTORY = "/data/local/tmp/cts-permissionui"

        const val QUICK_CHECK_TIMEOUT_MILLIS = 100L
        const val IDLE_TIMEOUT_MILLIS: Long = 1000
        const val UNEXPECTED_TIMEOUT_MILLIS = 1000
        const val TIMEOUT_MILLIS: Long = 20000
        const val PACKAGE_INSTALLER_TIMEOUT = 60000L
        const val NEW_WINDOW_TIMEOUT_MILLIS: Long = 20_000

        @JvmStatic
        protected val instrumentation: Instrumentation =
            InstrumentationRegistry.getInstrumentation()
        @JvmStatic protected val context: Context = instrumentation.context
        @JvmStatic protected val uiAutomation: UiAutomation = instrumentation.uiAutomation
        @JvmStatic protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
        @JvmStatic protected val packageManager: PackageManager = context.packageManager
        private val packageInstaller = packageManager.packageInstaller
        @JvmStatic
        private val mPermissionControllerResources: Resources =
            context
                .createPackageContext(context.packageManager.permissionControllerPackageName, 0)
                .resources

        @JvmStatic
        protected val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        @JvmStatic
        protected val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        @JvmStatic
        protected val isAutomotive =
            packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        @JvmStatic
        protected val isAutomotiveSplitscreen = isAutomotive &&
            packageManager.hasSystemFeature(
                    /* PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                    "android.software.car.splitscreen_multitasking")
    }

    @get:Rule val screenRecordRule = ScreenRecordRule(false, false)

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    var activityScenario: ActivityScenario<StartForFutureActivity>? = null

    data class SessionResult(val status: Int?)

    /** If a status was received the value of the status, otherwise null */
    private var installSessionResult = LinkedBlockingQueue<SessionResult>()

    private val installSessionResultReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
                val msg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)
                Log.d(TAG, "status: $status, msg: $msg")

                installSessionResult.offer(SessionResult(status))
            }
        }

    private var screenTimeoutBeforeTest: Long = 0L

    @Before
    fun setUp() {
        runWithShellPermissionIdentity {
            screenTimeoutBeforeTest =
                Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            Settings.System.putLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                1800000L
            )
        }

        uiDevice.wakeUp()
        runShellCommand(instrumentation, "wm dismiss-keyguard")

        uiDevice.findObject(By.text("Close"))?.click()
    }

    @Before
    fun registerInstallSessionResultReceiver() {
        context.registerReceiver(
            installSessionResultReceiver,
            IntentFilter(INSTALL_ACTION_CALLBACK),
            RECEIVER_EXPORTED
        )
    }

    @After
    fun unregisterInstallSessionResultReceiver() {
        try {
            context.unregisterReceiver(installSessionResultReceiver)
        } catch (ignored: IllegalArgumentException) {}
    }

    @After
    fun tearDown() {
        runWithShellPermissionIdentity {
            Settings.System.putLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                screenTimeoutBeforeTest
            )
        }

        try {
            activityScenario?.close()
        } catch (e: NullPointerException) {
            // ignore
        }

        pressHome()
    }

    protected fun setDeviceConfigPrivacyProperty(
        propertyName: String,
        value: String,
    ) {
        runWithShellPermissionIdentity(instrumentation.uiAutomation) {
            val valueWasSet =
                DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    /* name = */ propertyName,
                    /* value = */ value,
                    /* makeDefault = */ false
                )
            check(valueWasSet) { "Could not set $propertyName to $value" }
        }
    }

    protected fun getPermissionControllerString(res: String, vararg formatArgs: Any): Pattern {
        val textWithHtml =
            mPermissionControllerResources.getString(
                mPermissionControllerResources.getIdentifier(
                    res,
                    "string",
                    "com.android.permissioncontroller"
                ),
                *formatArgs
            )
        val textWithoutHtml = Html.fromHtml(textWithHtml, 0).toString()
        return Pattern.compile(
            Pattern.quote(textWithoutHtml),
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
    }

    protected fun getPermissionControllerResString(res: String): String? {
        try {
            return mPermissionControllerResources.getString(
                mPermissionControllerResources.getIdentifier(
                    res,
                    "string",
                    "com.android.permissioncontroller"
                )
            )
        } catch (e: Resources.NotFoundException) {
            return null
        }
    }

    protected fun byAnyText(vararg texts: String?): BySelector {
        var regex = ""
        for (text in texts) {
            if (text != null) {
                regex = regex + Pattern.quote(text) + "|"
            }
        }
        if (regex.endsWith("|")) {
            regex = regex.dropLast(1)
        }
        return By.text(Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE))
    }

    protected open fun installPackage(
        apkPath: String,
        reinstall: Boolean = false,
        grantRuntimePermissions: Boolean = false,
        expectSuccess: Boolean = true,
        installSource: String? = null
    ) {
        val output =
            runShellCommandOrThrow(
                    "pm install${if (SdkLevel.isAtLeastU()) " --bypass-low-target-sdk-block" else ""} " +
                        "${if (reinstall) " -r" else ""}${if (grantRuntimePermissions) " -g"
                else ""}${if (installSource != null) " -i $installSource" else ""} $apkPath"
                )
                .trim()
        if (expectSuccess) {
            assertEquals("Success", output)
        } else {
            assertNotEquals("Success", output)
        }
    }

    protected fun installPackageViaSession(
        apkName: String,
        appMetadata: PersistableBundle? = null,
        packageSource: Int? = null,
        allowlistedRestrictedPermissions: Set<String>? = null
    ) {
        val (sessionId, session) = createPackageInstallerSession(
            packageSource,
            allowlistedRestrictedPermissions
        )
        runWithShellPermissionIdentity {
            writePackageInstallerSession(session, apkName)
            if (appMetadata != null) {
                setAppMetadata(session, appMetadata)
            }
            commitPackageInstallerSession(session)

            // No need to click installer UI here due to running in shell permission identity and
            // not needing user interaciton to complete install. Install should have succeeded.
            val result = getInstallSessionResult()
            assertThat(result.status).isEqualTo(STATUS_SUCCESS)
        }
    }

    protected fun uninstallPackage(packageName: String, requireSuccess: Boolean = true) {
        val output = runShellCommand("pm uninstall $packageName").trim()
        if (requireSuccess) {
            assertEquals("Success", output)
        }
    }

    protected fun waitFindObject(selector: BySelector): UiObject2 {
        return findObjectWithRetry({ t -> UiAutomatorUtils2.waitFindObject(selector, t) })!!
    }

    protected fun waitFindObject(selector: BySelector, timeoutMillis: Long): UiObject2 {
        return findObjectWithRetry(
            { t -> UiAutomatorUtils2.waitFindObject(selector, t) },
            timeoutMillis
        )!!
    }

    protected fun waitFindObjectOrNull(selector: BySelector): UiObject2? {
        return findObjectWithRetry({ t -> UiAutomatorUtils2.waitFindObjectOrNull(selector, t) })
    }

    protected fun waitFindObjectOrNull(selector: BySelector, timeoutMillis: Long): UiObject2? {
        return findObjectWithRetry(
            { t -> UiAutomatorUtils2.waitFindObjectOrNull(selector, t) },
            timeoutMillis
        )
    }

    private fun findObjectWithRetry(
        automatorMethod: (timeoutMillis: Long) -> UiObject2?,
        timeoutMillis: Long = 20_000L
    ): UiObject2? {
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
    }

    protected fun clickAndWaitForWindowTransition(
        selector: BySelector,
        timeoutMillis: Long = 20_000
    ) {
        waitFindObject(selector, timeoutMillis)
            .clickAndWait(Until.newWindow(), NEW_WINDOW_TIMEOUT_MILLIS)
    }

    protected fun findView(selector: BySelector, expected: Boolean) {
        val timeoutMs =
            if (expected) {
                10000L
            } else {
                1000L
            }

        val exception =
            try {
                waitFindObject(selector, timeoutMs)
                null
            } catch (e: Exception) {
                e
            }
        Assert.assertTrue("Expected to find view: $expected", (exception == null) == expected)
    }

    protected fun clickPermissionControllerUi(selector: BySelector, timeoutMillis: Long = 20_000) {
        click(selector.pkg(context.packageManager.permissionControllerPackageName), timeoutMillis)
    }

    /**
     * Clicks Permission Controller UI with a swipe based timeout instead of a time based one
     *
     * Use this if finding some Permission Controller UI isn't time bound.
     *
     * @param text The text to search for
     * @param maxSearchSwipes See {@link UiScrollable#setMaxSearchSwipes}
     */
    protected fun clickPermissionControllerUi(text: String, maxSearchSwipes: Int = 5) {
        scrollToText(text, maxSearchSwipes).click()
    }

    private fun scrollToText(text: String, maxSearchSwipes: Int = MAX_SWIPES): UiObject2 {
        val scrollable =
            UiScrollable(UiSelector().scrollable(true)).apply {
                this.maxSearchSwipes = maxSearchSwipes
            }

        scrollable.scrollTextIntoView(text)

        val foundObject =
            uiDevice.findObject(
                By.text(text).pkg(context.packageManager.permissionControllerPackageName)
            )
        Assert.assertNotNull("View not found after scrolling", foundObject)

        return foundObject
    }

    protected fun pressBack() {
        uiDevice.pressBack()
    }

    protected fun pressHome() {
        uiDevice.pressHome()
    }

    protected fun pressDPadDown() {
        uiDevice.pressDPadDown()
        waitForIdle()
    }

    protected fun waitForIdle() = uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)

    protected fun startActivityForFuture(
        intent: Intent
    ): CompletableFuture<Instrumentation.ActivityResult> =
        CompletableFuture<Instrumentation.ActivityResult>().also {
            activityScenario =
                ActivityScenario.launch(StartForFutureActivity::class.java).onActivity { activity ->
                    activity.startActivityForFuture(intent, it)
                }
        }

    open fun enableComponent(component: ComponentName) {
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    open fun disableComponent(component: ComponentName) {
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun createPackageInstallerSession(
        packageSource: Int? = null,
        allowlistedRestrictedPermissions: Set<String>? = null
    ): Pair<Int, PackageInstaller.Session> {
        // Create session
        val sessionParam = SessionParams(SessionParams.MODE_FULL_INSTALL)
        allowlistedRestrictedPermissions?.let {
            sessionParam.setWhitelistedRestrictedPermissions(it)
        }

        if (packageSource != null) {
            sessionParam.setPackageSource(packageSource)
        }

        val sessionId = packageInstaller.createSession(sessionParam)
        val session = packageInstaller.openSession(sessionId)!!

        return Pair(sessionId, session)
    }

    private fun writePackageInstallerSession(session: PackageInstaller.Session, apkName: String) {
        val apkFile = File(APK_DIRECTORY, apkName)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session
                .openWrite(/* name= */ apkName, /* offsetBytes= */ 0, /* lengthBytes= */ -1)
                .use { sessionFile -> fileOnDisk.copyTo(sessionFile) }
        }
    }

    private fun commitPackageInstallerSession(session: PackageInstaller.Session) {
        // PendingIntent that triggers a INSTALL_ACTION_CALLBACK broadcast that gets received by
        // installSessionResultReceiver when install actions occur with this session
        val installActionPendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(INSTALL_ACTION_CALLBACK).setPackage(context.packageName),
                FLAG_UPDATE_CURRENT or FLAG_MUTABLE
            )
        session.commit(installActionPendingIntent.intentSender)
    }

    private fun setAppMetadata(session: PackageInstaller.Session, data: PersistableBundle) {
        try {
            session.setAppMetadata(data)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    /** Wait for session's install result and return it */
    private fun getInstallSessionResult(timeout: Long = PACKAGE_INSTALLER_TIMEOUT): SessionResult {
        return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
            ?: SessionResult(null /* status */)
    }
}
