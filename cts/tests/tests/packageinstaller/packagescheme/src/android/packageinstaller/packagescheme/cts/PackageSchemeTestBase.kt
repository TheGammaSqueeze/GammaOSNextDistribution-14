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

package android.packageinstaller.packagescheme.cts

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.regex.Pattern
import org.junit.After
import org.junit.Before

open class PackageSchemeTestBase {
    val TARGET_APP_PKG_NAME = "android.packageinstaller.emptytestapp.cts"
    val TARGET_APP_APK = "CtsEmptyTestApp.apk"
    val RECEIVER_ACTION = "android.packageinstaller.emptytestapp.cts.action"
    val POSITIVE_BTN_ID = "button1"
    val NEGATIVE_BTN_ID = "button2"
    val SYSTEM_PACKAGE_NAME = "android"
    val PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller"
    val DEFAULT_TIMEOUT = 5000L

    var mScenario: ActivityScenario<TestActivity>? = null
    val mInstrumentation = InstrumentationRegistry.getInstrumentation()
    val mUiDevice = UiDevice.getInstance(mInstrumentation)
    var mButton: UiObject2? = null
    val mContext: Context = mInstrumentation.context
    val mInstaller: PackageInstaller = mContext.packageManager.packageInstaller

    class TestActivity : Activity() {
        val mLatch: CountDownLatch = CountDownLatch(1)
        var mResultCode = RESULT_OK

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val appInstallIntent: Intent? = intent.getExtra(Intent.EXTRA_INTENT) as Intent?
            startActivityForResult(appInstallIntent, 1)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            mResultCode = resultCode
            mLatch.countDown()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        private val results = ArrayBlockingQueue<Intent>(1)

        override fun onReceive(context: Context, intent: Intent) {
            // Added as a safety net. Have observed instances where the Queue isn't empty which
            // causes the test suite to crash.
            if (results.size != 0) {
                clear()
            }
            results.add(intent)
        }

        fun makeIntentSender(sessionId: Int) = PendingIntent.getBroadcast(
            mContext, sessionId,
            Intent(RECEIVER_ACTION).setPackage(mContext.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_UPDATE_CURRENT
                or PendingIntent.FLAG_MUTABLE_UNAUDITED
        ).intentSender

        fun getResult(unit: TimeUnit, timeout: Long) = results.poll(timeout, unit)

        fun clear() = results.clear()
    }

    @Before
    fun setup() {
        receiver.clear()
        mContext.registerReceiver(receiver, IntentFilter(RECEIVER_ACTION),
            Context.RECEIVER_EXPORTED)
    }

    @Before
    @After
    fun uninstall() {
        mInstrumentation.uiAutomation.executeShellCommand("pm uninstall $TARGET_APP_PKG_NAME")
    }

    @After
    fun tearDown() {
        mContext.unregisterReceiver(receiver)
        mInstrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    fun runTest(packageName: String, packageHasVisibility: Boolean, needTargetApp: Boolean) {
        if (packageHasVisibility) {
            mInstrumentation.uiAutomation.executeShellCommand(
                "appops set $packageName android:query_all_packages allow"
            )
            mInstrumentation.uiAutomation.executeShellCommand(
                "appops set $packageName android:request_install_packages allow"
            )
        }

        if (needTargetApp) {
            installTargetApp(resourceSupplier(TARGET_APP_APK))
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), TestActivity::class.java)
        intent.putExtra(Intent.EXTRA_INTENT, getAppInstallIntent())

        var latch: CountDownLatch? = null
        mScenario = ActivityScenario.launch(intent)
        mScenario!!.onActivity {
            var button: UiObject2?
            val btnName: String
            // Need to scroll the screen to get to the buttons on some form factors
            // (e.g. on a watch).
            val scrollable = UiScrollable(UiSelector().scrollable(true))
            if (scrollable.exists()) {
                scrollable.flingToEnd(10)
            }
            if (packageHasVisibility && needTargetApp) {
                button = mUiDevice.wait(
                    Until.findObject(getBySelector(NEGATIVE_BTN_ID)), DEFAULT_TIMEOUT)
                btnName = "Cancel"
            } else {
                button = mUiDevice.wait(
                    Until.findObject(getBySelector(POSITIVE_BTN_ID)), DEFAULT_TIMEOUT)
                btnName = "OK"
            }
            assertWithMessage("$btnName not found").that(button).isNotNull()
            button?.click()
            latch = it.mLatch
        }
        latch!!.await()
        mScenario!!.onActivity {
            val resultCode: Int = it.mResultCode
            if (packageHasVisibility && needTargetApp) {
                assertThat(resultCode).isNotEqualTo(Activity.RESULT_FIRST_USER)
            } else {
                assertThat(resultCode).isEqualTo(Activity.RESULT_FIRST_USER)
            }
        }
        mScenario!!.close()
    }

    private fun installTargetApp(apkStreamSupplier: Supplier<InputStream>) {
        setupPermissions()
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val sessionId: Int = mInstaller.createSession(params)
        val session: PackageInstaller.Session = mInstaller.openSession(sessionId)

        session.openWrite("apk", 0, -1).use { os ->
            apkStreamSupplier.get().copyTo(os)
        }

        session.commit(receiver.makeIntentSender(sessionId))
        session.close()

        val result = receiver.getResult(TimeUnit.SECONDS, 30)
        val installStatus = result.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (installStatus != PackageInstaller.STATUS_SUCCESS) {
            val id = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, Int.MIN_VALUE)
            mContext.packageManager.packageInstaller.abandonSession(id)
        }
        assertThat(installStatus).isEqualTo(PackageInstaller.STATUS_SUCCESS)
    }

    private fun resourceSupplier(resourceName: String): Supplier<InputStream> {
        return Supplier<InputStream> {
            val resourceAsStream = javaClass.classLoader.getResourceAsStream(resourceName)
                ?: throw RuntimeException("Resource $resourceName could not be found.")
            resourceAsStream
        }
    }

    private fun setupPermissions() {
        mInstrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.INSTALL_PACKAGES
        )
    }

    private fun getAppInstallIntent(): Intent {
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(Uri.parse("package:$TARGET_APP_PKG_NAME"))
    }

    private fun getBySelector(id: String): BySelector {
        // Normally, we wouldn't need to look for buttons from 2 different packages.
        // However, to fix b/297132020, AlertController was replaced with AlertDialog and shared
        // to selective partners, leading to fragmentation in which button surfaces in an OEM's
        // installer app.
        return By.res(Pattern.compile(String.format("(?:^%s|^%s):id/%s",
                PACKAGE_INSTALLER_PACKAGE_NAME, SYSTEM_PACKAGE_NAME, id)))
    }
}
