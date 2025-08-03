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

package android.packageinstaller.install.cts

import android.Manifest
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.InstallConstraints
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.install.lib.Install
import com.android.cts.install.lib.InstallUtils
import com.android.cts.install.lib.InstallUtils.getInstalledVersion
import com.android.cts.install.lib.LocalIntentSender
import com.android.cts.install.lib.TestApp
import com.android.cts.install.lib.Uninstall
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@AppModeFull
class InstallConstraintsTest {
    companion object {
        private const val MATCH_STATIC_SHARED_AND_SDK_LIBRARIES = 0x04000000
        private val HelloWorldSdk1 = TestApp(
            "HelloWorldSdk1", "com.test.sdk1_1",
            1, false, "HelloWorldSdk1.apk"
        )
        private val HelloWorldUsingSdk1 = TestApp(
            "HelloWorldUsingSdk1",
            "com.test.sdk.user", 1, false, "HelloWorldUsingSdk1.apk"
        )
    }

    private val instr: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testUserId: Int = instr.targetContext.user.identifier

    @Before
    fun setUp() {
        instr.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Manifest.permission.INSTALL_PACKAGES,
            Manifest.permission.DELETE_PACKAGES)
    }

    @After
    fun tearDown() {
        Uninstall.packages(TestApp.A, TestApp.B, TestApp.S)
        instr.uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun verifyGetters() {
        InstallConstraints.Builder().setAppNotForegroundRequired().build().also {
            assertThat(it.isAppNotForegroundRequired).isTrue()
        }
        InstallConstraints.Builder().setAppNotInteractingRequired().build().also {
            assertThat(it.isAppNotInteractingRequired).isTrue()
        }
        InstallConstraints.Builder().setAppNotTopVisibleRequired().build().also {
            assertThat(it.isAppNotTopVisibleRequired).isTrue()
        }
        InstallConstraints.Builder().setDeviceIdleRequired().build().also {
            assertThat(it.isDeviceIdleRequired).isTrue()
        }
        InstallConstraints.Builder().setNotInCallRequired().build().also {
            assertThat(it.isNotInCallRequired).isTrue()
        }
        InstallConstraints.Builder().build().also {
            assertThat(it.isAppNotForegroundRequired).isFalse()
            assertThat(it.isAppNotInteractingRequired).isFalse()
            assertThat(it.isAppNotTopVisibleRequired).isFalse()
            assertThat(it.isDeviceIdleRequired).isFalse()
            assertThat(it.isNotInCallRequired).isFalse()
        }
    }

    @Test
    fun testCheckInstallConstraints_AppIsInteracting() {
        // Skip this test as the current audio focus detection doesn't work on Auto
        assumeFalse(isAuto())
        Install.single(TestApp.A1).commit()
        // The app will have audio focus and be considered interactive with the user
        InstallUtils.requestAudioFocus(TestApp.A)
        val pi = InstallUtils.getPackageInstaller()
        val constraints = InstallConstraints.Builder().setAppNotInteractingRequired().build()
        val future = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        pi.checkInstallConstraints(
            listOf(TestApp.A),
            constraints,
            { r -> r.run() }
        ) { result -> future.complete(result) }
        assertThat(future.join().areAllConstraintsSatisfied()).isFalse()
    }

    @Test
    fun testCheckInstallConstraints_AppNotInstalled() {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1)
        val pi = InstallUtils.getPackageInstaller()
        try {
            pi.checkInstallConstraints(
                listOf(TestApp.A),
                InstallConstraints.GENTLE_UPDATE,
                { r -> r.run() }
            ) { }
            Assert.fail()
        } catch (e: SecurityException) {
            assertThat(e.message).contains("has no access to package")
        }
    }

    @Test
    fun testCheckInstallConstraints_AppIsTopVisible() {
        Install.single(TestApp.A1).commit()
        Install.single(TestApp.B1).commit()
        // We will have a top-visible app
        startActivity(TestApp.A)

        val pi = InstallUtils.getPackageInstaller()
        val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        val constraints = InstallConstraints.Builder().setAppNotTopVisibleRequired().build()
        pi.checkInstallConstraints(
            listOf(TestApp.A),
            constraints,
            { r -> r.run() }
        ) { result -> f1.complete(result) }
        assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

        // Test app A is no longer top-visible
        startActivity(TestApp.B)
        PollingCheck.waitFor {
            val importance = getPackageImportance(TestApp.A)
            importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        pi.checkInstallConstraints(
            listOf(TestApp.A),
            constraints,
            { r -> r.run() }
        ) { result -> f2.complete(result) }
        assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
    }

    @Test
    fun testCheckInstallConstraints_AppIsForeground() {
        Install.single(TestApp.A1).commit()
        Install.single(TestApp.B1).commit()
        // We will have a foreground app
        startActivity(TestApp.A)

        val pi = InstallUtils.getPackageInstaller()
        val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        val constraints = InstallConstraints.Builder().setAppNotForegroundRequired().build()
        pi.checkInstallConstraints(
            listOf(TestApp.A),
            constraints,
            { r -> r.run() }
        ) { result -> f1.complete(result) }
        assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

        // Test app A is no longer foreground
        startActivity(TestApp.B)
        PollingCheck.waitFor {
            val importance = getPackageImportance(TestApp.A)
            importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        pi.checkInstallConstraints(
            listOf(TestApp.A),
            constraints,
            { r -> r.run() }
        ) { result -> f2.complete(result) }
        assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
    }

    @Test
    fun testCheckInstallConstraints_DeviceIsIdle() {
        val propKey = "debug.pm.gentle_update_test.is_idle"

        Install.single(TestApp.A1).commit()

        try {
            // Device is not idle
            SystemUtil.runShellCommand("setprop $propKey 0")
            val pi = InstallUtils.getPackageInstaller()
            val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            val constraints = InstallConstraints.Builder().setDeviceIdleRequired().build()
            pi.checkInstallConstraints(
                listOf(TestApp.A),
                constraints,
                { r -> r.run() }
            ) { result -> f1.complete(result) }
            assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

            // Device is idle
            SystemUtil.runShellCommand(" setprop $propKey 1")
            val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            pi.checkInstallConstraints(
                listOf(TestApp.A),
                constraints,
                { r -> r.run() }
            ) { result -> f2.complete(result) }
            assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
        } finally {
            SystemUtil.runShellCommand("setprop $propKey 0")
        }
    }

    @Test
    fun testCheckInstallConstraints_DeviceIsInCall() {
        val propKey = "debug.pm.gentle_update_test.is_in_call"
        Install.single(TestApp.A1).commit()

        try {
            // Device is in call
            SystemUtil.runShellCommand("setprop $propKey 1")
            val pi = InstallUtils.getPackageInstaller()
            val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            val constraints = InstallConstraints.Builder().setNotInCallRequired().build()
            pi.checkInstallConstraints(
                listOf(TestApp.A),
                constraints,
                { r -> r.run() }
            ) { result -> f1.complete(result) }
            assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

            // Device is not in call
            SystemUtil.runShellCommand("setprop $propKey 0")
            val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            pi.checkInstallConstraints(
                listOf(TestApp.A),
                constraints,
                { r -> r.run() }
            ) { result -> f2.complete(result) }
            assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
        } finally {
            SystemUtil.runShellCommand("setprop $propKey 0")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCheckInstallConstraints_BoundedService() {
        Install.single(TestApp.A1).commit()
        Install.single(TestApp.B1).commit()
        Install.single(TestApp.S1).commit()
        // Start an activity which will bind a service
        // Test app S is considered foreground as A is foreground
        startActivity(TestApp.A, "com.android.cts.install.lib.testapp.TestServiceActivity")

        val pi = InstallUtils.getPackageInstaller()
        val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        val constraints = InstallConstraints.Builder().setAppNotForegroundRequired().build()
        pi.checkInstallConstraints(
            listOf(TestApp.S),
            constraints,
            { r -> r.run() }
        ) { result -> f1.complete(result) }
        assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

        // Test app A is no longer foreground. So is test app S.
        startActivity(TestApp.B)
        PollingCheck.waitFor {
            val importance = getPackageImportance(TestApp.A)
            importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
        pi.checkInstallConstraints(
            listOf(TestApp.S),
            constraints,
            { r -> r.run() }
        ) { result -> f2.complete(result) }
        assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
    }

    @Test
    fun testCheckInstallConstraints_UsesLibrary() {
        val propKey = "debug.pm.uses_sdk_library_default_cert_digest"

        try {
            Install.single(TestApp.B1).commit()
            Install.single(HelloWorldSdk1).commit()
            // Override the certificate digest so HelloWorldUsingSdk1 can be installed
            SystemUtil.runShellCommand(
                "setprop $propKey ${getPackageCertDigest(HelloWorldSdk1.packageName)}")
            Install.single(HelloWorldUsingSdk1).commit()

            // HelloWorldSdk1 will be considered foreground as HelloWorldUsingSdk1 is foreground
            startActivity(HelloWorldUsingSdk1.packageName,
                "com.example.helloworld.MainActivityNoExit")
            val pi = InstallUtils.getPackageInstaller()
            val f1 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            val constraints = InstallConstraints.Builder().setAppNotForegroundRequired().build()
            pi.checkInstallConstraints(
                listOf(HelloWorldSdk1.packageName),
                constraints,
                { r -> r.run() }
            ) { result -> f1.complete(result) }
            assertThat(f1.join().areAllConstraintsSatisfied()).isFalse()

            // HelloWorldUsingSdk1 is no longer foreground. So is HelloWorldSdk1.
            startActivity(TestApp.B)
            PollingCheck.waitFor {
                val importance = getPackageImportance(HelloWorldUsingSdk1.packageName)
                importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }

            val f2 = CompletableFuture<PackageInstaller.InstallConstraintsResult>()
            pi.checkInstallConstraints(
                listOf(HelloWorldSdk1.packageName),
                constraints,
                { r -> r.run() }
            ) { result -> f2.complete(result) }
            assertThat(f2.join().areAllConstraintsSatisfied()).isTrue()
        } finally {
            SystemUtil.runShellCommand("setprop $propKey invalid")
        }
    }

    @Test
    fun testWaitForInstallConstraints_AppIsForeground() {
        Install.single(TestApp.A1).commit()
        Install.single(TestApp.B1).commit()
        // We will have a foreground app
        startActivity(TestApp.A)
        val pi = InstallUtils.getPackageInstaller()
        val inputConstraints = InstallConstraints.Builder().setAppNotInteractingRequired().build()

        // Timeout == 0, constraints not satisfied
        with(LocalIntentSender()) {
            pi.waitForInstallConstraints(
                listOf(TestApp.A), inputConstraints,
                intentSender, 0
            )
            val intent = this.result
            val packageNames = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES)
            val receivedConstraints = intent.getParcelableExtra(
                PackageInstaller.EXTRA_INSTALL_CONSTRAINTS, InstallConstraints::class.java)
            val result = intent.getParcelableExtra(
                PackageInstaller.EXTRA_INSTALL_CONSTRAINTS_RESULT,
                PackageInstaller.InstallConstraintsResult::class.java
            )
            assertThat(packageNames).asList().containsExactly(TestApp.A)
            assertThat(receivedConstraints).isEqualTo(inputConstraints)
            assertThat(result!!.areAllConstraintsSatisfied()).isFalse()
        }

        // Timeout == one day, constraints not satisfied
        with(LocalIntentSender()) {
            pi.waitForInstallConstraints(
                listOf(TestApp.A), inputConstraints,
                intentSender, TimeUnit.DAYS.toMillis(1)
            )
            // Wait for a while and check the callback is not invoked yet
            assertThat(pollResult(3, TimeUnit.SECONDS)).isNull()

            // Test app A is no longer foreground. The callback will be invoked soon.
            startActivity(TestApp.B)
            val intent = this.result
            val packageNames = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES)
            val receivedConstraints = intent.getParcelableExtra(
                PackageInstaller.EXTRA_INSTALL_CONSTRAINTS, InstallConstraints::class.java)
            val result = intent.getParcelableExtra(
                PackageInstaller.EXTRA_INSTALL_CONSTRAINTS_RESULT,
                PackageInstaller.InstallConstraintsResult::class.java
            )
            assertThat(packageNames).asList().containsExactly(TestApp.A)
            assertThat(receivedConstraints).isEqualTo(inputConstraints)
            assertThat(result!!.areAllConstraintsSatisfied()).isTrue()
        }
    }

    @Test
    fun testCommitAfterInstallConstraintsMet_NoTimeout() {
        Install.single(TestApp.A1).commit()

        // Constraints are satisfied. The session will be committed without timeout.
        val pi = InstallUtils.getPackageInstaller()
        val sessionId = Install.single(TestApp.A2).createSession()
        val constraints = InstallConstraints.Builder().setAppNotForegroundRequired().build()
        val sender = LocalIntentSender()
        pi.commitSessionAfterInstallConstraintsAreMet(
            sessionId, sender.intentSender,
            constraints, TimeUnit.MINUTES.toMillis(1)
        )
        InstallUtils.assertStatusSuccess(sender.result)
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2)
    }

    @Test
    fun testCommitAfterInstallConstraintsMet_RetryOnTimeout() {
        Install.single(TestApp.A1).commit()
        Install.single(TestApp.B1).commit()
        // We will have a foreground app
        startActivity(TestApp.A)

        // Timeout for constraints not satisfied
        val pi = InstallUtils.getPackageInstaller()
        val sessionId = Install.single(TestApp.A2).createSession()
        val constraints = InstallConstraints.Builder().setAppNotForegroundRequired().build()
        val sender = LocalIntentSender()
        pi.commitSessionAfterInstallConstraintsAreMet(
            sessionId, sender.intentSender,
            constraints, TimeUnit.SECONDS.toMillis(3)
        )
        InstallUtils.assertStatusFailure(sender.result)
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1)

        // Test app A is no longer foreground
        startActivity(TestApp.B)
        PollingCheck.waitFor {
            val importance = getPackageImportance(TestApp.A)
            importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        // Commit will succeed for constraints are satisfied
        pi.commitSessionAfterInstallConstraintsAreMet(
            sessionId, sender.intentSender,
            constraints, TimeUnit.MINUTES.toMillis(1)
        )
        InstallUtils.assertStatusSuccess(sender.result)
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2)
    }

    private fun isAuto() =
        instr.context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private fun startActivity(packageName: String) =
        startActivity(packageName, "com.android.cts.install.lib.testapp.MainActivity")

    private fun startActivity(packageName: String, className: String) =
        // The -W option waits for the activity launch to complete
        SystemUtil.runShellCommandOrThrow(
                "am start-activity --user $testUserId -W -n $packageName/$className")

    private fun getPackageImportance(packageName: String) =
        instr.context.getSystemService(ActivityManager::class.java)!!
            .getPackageImportance(packageName)

    private fun computeSha256DigestBytes(data: ByteArray) =
        MessageDigest.getInstance("SHA256").run {
            update(data)
            digest()
        }

    private fun encodeHex(data: ByteArray): String {
        val hexDigits = "0123456789abcdef".toCharArray()
        val len = data.size
        val result = StringBuilder(len * 2)
        for (i in 0 until len) {
            val b = data[i]
            result.append(hexDigits[b.toInt() ushr 4 and 0x0f])
            result.append(hexDigits[b.toInt() and 0x0f])
        }
        return result.toString()
    }

    private fun getPackageCertDigest(packageName: String): String? {
        val pm: PackageManager = instr.context.packageManager
        val flags = GET_SIGNING_CERTIFICATES or MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
        val packageInfo = pm.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )
        val signatures = packageInfo.signingInfo!!.signingCertificateHistory
        val digest = computeSha256DigestBytes(signatures[0].toByteArray())
        return encodeHex(digest)
    }
}
