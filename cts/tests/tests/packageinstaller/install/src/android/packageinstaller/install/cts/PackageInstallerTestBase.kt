/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.packageinstaller.install.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_PRE_APPROVAL
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.PreapprovalDetails
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.provider.DeviceConfig
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FutureResultActivity
import com.android.compatibility.common.util.SystemUtil
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule

open class PackageInstallerTestBase {

    companion object {
        const val TAG = "PackageInstallerTest"

        const val INSTALL_BUTTON_ID = "button1"
        const val CANCEL_BUTTON_ID = "button2"

        const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
        const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
        const val TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller"

        const val INSTALL_ACTION_CB = "PackageInstallerTestBase.install_cb"

        const val CONTENT_AUTHORITY = "android.packageinstaller.install.cts.fileprovider"

        const val PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller"
        const val SYSTEM_PACKAGE_NAME = "android"
        const val SHELL_PACKAGE_NAME = "com.android.shell"
        const val APP_OP_STR = "REQUEST_INSTALL_PACKAGES"

        const val PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE = "is_preapproval_available"
        const val PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE =
                "is_update_ownership_enforcement_available"

        const val TIMEOUT = 60000L
        const val INSTALL_INSTANT_APP = 0x00000800
        const val INSTALL_REQUEST_UPDATE_OWNERSHIP = 0x02000000

        val context: Context = InstrumentationRegistry.getTargetContext()
        val testUserId: Int = context.user.identifier
    }

    @get:Rule
    val disableAnimationsRule = DisableAnimationRule()

    @get:Rule
    val installDialogStarter = ActivityTestRule(FutureResultActivity::class.java)

    protected val pm: PackageManager = context.packageManager
    protected val pi = pm.packageInstaller
    protected val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val apkFile = File(context.filesDir, TEST_APK_NAME)

    data class SessionResult(val status: Int?, val preapproval: Boolean?, val message: String?)

    /** If a status was received the value of the status, otherwise null */
    private var installSessionResult = LinkedBlockingQueue<SessionResult>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
            val preapproval = intent.getBooleanExtra(EXTRA_PRE_APPROVAL, false /* defaultValue */)
            val msg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)
            Log.d(TAG, "status: $status, msg: $msg")

            if (status == STATUS_PENDING_USER_ACTION) {
                val activityIntent = intent.getParcelableExtra(EXTRA_INTENT, Intent::class.java)
                Assert.assertEquals(activityIntent!!.extras!!.keySet().size, 1)
                activityIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
                installDialogStarter.activity.startActivityForResult(activityIntent)
            }

            installSessionResult.offer(SessionResult(status, preapproval, msg))
        }
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
    }

    @Before
    fun wakeUpScreen() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun assertTestPackageNotInstalled() {
        try {
            context.packageManager.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(receiver, IntentFilter(INSTALL_ACTION_CB),
            Context.RECEIVER_EXPORTED)
    }

    @Before
    fun waitForUIIdle() {
        uiDevice.waitForIdle()
    }

    @After
    fun pressBack() {
        uiDevice.pressBack()
    }

    /**
     * Wait for session's install result and return it
     */
    protected fun getInstallSessionResult(timeout: Long = TIMEOUT): SessionResult {
        return getInstallSessionResult(installSessionResult, timeout)
    }

    protected fun getInstallSessionResult(
        installResult: LinkedBlockingQueue<SessionResult>,
        timeout: Long = TIMEOUT
    ): SessionResult {
        return installResult.poll(timeout, TimeUnit.MILLISECONDS)
            ?: SessionResult(null /* status */, null /* preapproval */, "Fail to poll result")
    }

    protected fun startInstallationViaSessionNoPrompt() {
        startInstallationViaSession(
                0 /* installFlags */,
                TEST_APK_NAME,
                null /* packageSource */,
                false /* expectedPrompt */
        )
    }

    protected fun startInstallationViaSessionWithPackageSource(packageSource: Int?) {
        startInstallationViaSession(0 /* installFlags */, TEST_APK_NAME, packageSource)
    }

    protected fun createSession(
        installFlags: Int,
        isMultiPackage: Boolean,
        packageSource: Int?,
        paramsBlock: (PackageInstaller.SessionParams) -> Unit = {},
    ): Pair<Int, Session> {
        // Create session
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        // Handle additional install flags
        if (installFlags and INSTALL_INSTANT_APP != 0) {
            sessionParam.setInstallAsInstantApp(true)
        }
        if (installFlags and INSTALL_REQUEST_UPDATE_OWNERSHIP != 0) {
            sessionParam.setRequestUpdateOwnership(true)
        }
        if (isMultiPackage) {
            sessionParam.setMultiPackage()
        }
        if (packageSource != null) {
            sessionParam.setPackageSource(packageSource)
        }

        paramsBlock(sessionParam)

        val sessionId = pi.createSession(sessionParam)
        val session = pi.openSession(sessionId)!!

        return Pair(sessionId, session)
    }

    protected fun writeSession(session: Session, apkName: String) {
        val apkFile = File(context.filesDir, apkName)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(apkName, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    protected fun commitSession(
            session: Session,
            expectedPrompt: Boolean = true,
            needFuture: Boolean = false
    ): CompletableFuture<Int>? {
        var intent = Intent(INSTALL_ACTION_CB)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pendingIntent = PendingIntent.getBroadcast(
                context, 0 /* requestCode */, intent, FLAG_UPDATE_CURRENT or FLAG_MUTABLE)

        var dialog: CompletableFuture<Int>? = null

        if (!expectedPrompt) {
            session.commit(pendingIntent.intentSender)
            return dialog
        }

        // Commit session
        if (needFuture) {
            dialog = FutureResultActivity.doAndAwaitStart {
                session.commit(pendingIntent.intentSender)
            }
        } else {
            session.commit(pendingIntent.intentSender)
        }

        // The system should have asked us to launch the installer
        val result = getInstallSessionResult()
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, result.status)
        Assert.assertEquals(false, result.preapproval)

        return dialog
    }

    protected fun startRequestUserPreapproval(
        session: Session,
        details: PreapprovalDetails,
        expectedPrompt: Boolean = true
    ) {
        // In some abnormal cases, passing expectedPrompt as false to return immediately without
        // waiting for timeout (60 secs).
        if (!expectedPrompt) { requestSession(session, details); return }

        FutureResultActivity.doAndAwaitStart {
            requestSession(session, details)
        }

        // The system should have asked us to launch the installer
        val result = getInstallSessionResult()
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, result.status)
        Assert.assertEquals(true, result.preapproval)
    }

    private fun requestSession(session: Session, details: PreapprovalDetails) {
        val pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                Intent(INSTALL_ACTION_CB).setPackage(context.packageName),
                FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
        session.requestUserPreapproval(details, pendingIntent.intentSender)
    }

    protected fun startInstallationViaSession(
        installFlags: Int = 0,
        apkName: String = TEST_APK_NAME,
        packageSource: Int? = null,
        expectedPrompt: Boolean = true,
        needFuture: Boolean = false,
        paramsBlock: (PackageInstaller.SessionParams) -> Unit = {}
    ): CompletableFuture<Int>? {
        val (_, session) = createSession(installFlags, false, packageSource, paramsBlock)
        writeSession(session, apkName)
        return commitSession(session, expectedPrompt, needFuture)
    }

    protected fun writeAndCommitSession(
            apkName: String,
            session: Session,
            expectedPrompt: Boolean = true
    ) {
        writeSession(session, apkName)
        commitSession(session, expectedPrompt)
    }

    protected fun startInstallationViaMultiPackageSession(
        installFlags: Int,
        vararg apkNames: String,
        needFuture: Boolean = false
    ): CompletableFuture<Int>? {
        val (sessionId, session) = createSession(installFlags, true, null)
        for (apkName in apkNames) {
            val (childSessionId, childSession) = createSession(installFlags, false, null)
            writeSession(childSession, apkName)
            session.addChildSessionId(childSessionId)
        }
        return commitSession(session, needFuture = needFuture)
    }

    /**
     * Start an installation via an Intent
     */
    protected fun startInstallationViaIntent(
            intent: Intent = getInstallationIntent()
    ): CompletableFuture<Int> {
        return installDialogStarter.activity.startActivityForResult(intent)
    }

    protected fun getInstallationIntent(): Intent {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)

        return intent
    }

    protected fun startInstallationViaPreapprovalSession(session: Session) {
        val pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                Intent(INSTALL_ACTION_CB).setPackage(context.packageName),
                FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
        session.commit(pendingIntent.intentSender)
    }

    fun assertInstalled(
        flags: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(0)
    ): PackageInfo {
        // Throws exception if package is not installed.
        return pm.getPackageInfo(TEST_APK_PACKAGE_NAME, flags)
    }

    fun assertInstalled(packageName: String,
        flags: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(0),
    ): PackageInfo {
        // Throws exception if package is not installed.
        return pm.getPackageInfo(packageName, flags)
    }

    fun assertNotInstalled() {
        try {
            pm.getPackageInfo(TEST_APK_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param resId The resource ID of the button to click
     */
    fun clickInstallerUIButton(resId: String) {
        clickInstallerUIButton(getBySelector(resId))
    }

    private fun getBySelector(id: String): BySelector {
        // Normally, we wouldn't need to look for buttons from 2 different packages.
        // However, to fix b/297132020, AlertController was replaced with AlertDialog and shared
        // to selective partners, leading to fragmentation in which button surfaces in an OEM's
        // installer app.
        return By.res(Pattern.compile(String.format(
                    "(?:^%s|^%s):id/%s", PACKAGE_INSTALLER_PACKAGE_NAME, SYSTEM_PACKAGE_NAME, id)))
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param bySelector The bySelector of the button to click
     */
    fun clickInstallerUIButton(bySelector: BySelector) {
        var button: UiObject2? = null
        val startTime = System.currentTimeMillis()
        while (startTime + TIMEOUT > System.currentTimeMillis()) {
            try {
                button = uiDevice.wait(Until.findObject(bySelector), 1000)
                if (button != null) {
                    Log.d(TAG, "Found bounds: ${button.getVisibleBounds()} of button $bySelector," +
                            " text: ${button.getText()}," +
                            " package: ${button.getApplicationPackage()}")
                    button.click()
                    return
                }
            } catch (ignore: Throwable) {
            }
        }
        Assert.fail("Failed to click the button: $bySelector")
    }

    /**
     * Sets the given secure setting to the provided value.
     */
    fun setSecureSetting(secureSetting: String, value: Int) {
        uiDevice.executeShellCommand("settings put --user $testUserId secure $secureSetting $value")
    }

    fun setSecureFrp(secureFrp: Boolean) {
        uiDevice.executeShellCommand("settings " +
                "put global secure_frp_mode ${if (secureFrp) 1 else 0}")
    }

    @After
    fun unregisterInstallResultReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    @After
    @Before
    fun uninstallTestPackage() {
        uninstallPackage(TEST_APK_PACKAGE_NAME)
    }

    fun uninstallPackage(packageName: String) {
        uiDevice.executeShellCommand("pm uninstall $packageName")
    }

    fun installTestPackage(extraArgs: String = "") {
        installPackage(TEST_APK_NAME, extraArgs)
    }

    fun installPackage(apkName: String, extraArgs: String = "") {
        Log.d(TAG, "installPackage(): apkName=$apkName, extraArgs='$extraArgs'")
        uiDevice.executeShellCommand("pm install $extraArgs " +
                File(TEST_APK_LOCATION, apkName).canonicalPath)
    }

    fun getDeviceProperty(name: String): String? {
        return SystemUtil.callWithShellPermissionIdentity {
            DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name)
        }
    }

    fun setDeviceProperty(name: String, value: String?) {
        SystemUtil.callWithShellPermissionIdentity {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name, value,
                    false /* makeDefault */)
        }
    }
}
