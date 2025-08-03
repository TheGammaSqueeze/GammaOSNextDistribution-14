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

package com.android.testutils

import com.android.ddmlib.testrunner.TestResult
import com.android.tradefed.config.Option
import com.android.tradefed.invoker.TestInformation
import com.android.tradefed.result.CollectingTestListener
import com.android.tradefed.result.ddmlib.DefaultRemoteAndroidTestRunner
import com.android.tradefed.targetprep.BaseTargetPreparer
import com.android.tradefed.targetprep.TargetSetupError
import com.android.tradefed.targetprep.suite.SuiteApkInstaller

private const val CONNECTIVITY_CHECKER_APK = "ConnectivityTestPreparer.apk"
private const val CONNECTIVITY_PKG_NAME = "com.android.testutils.connectivitypreparer"
private const val CONNECTIVITY_CHECK_CLASS = "$CONNECTIVITY_PKG_NAME.ConnectivityCheckTest"
// As per the <instrumentation> defined in the checker manifest
private const val CONNECTIVITY_CHECK_RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner"
private const val IGNORE_CONN_CHECK_OPTION = "ignore-connectivity-check"

// The default updater package names, which might be updating packages while the CTS
// are running
private val UPDATER_PKGS = arrayOf("com.google.android.gms", "com.android.vending")

/**
 * A target preparer that sets up and verifies a device for connectivity tests.
 *
 * For quick and dirty local testing, the connectivity check can be disabled by running tests with
 * "atest -- \
 * --test-arg com.android.testutils.ConnectivityTestTargetPreparer:ignore-connectivity-check:true".
 */
open class ConnectivityTestTargetPreparer : BaseTargetPreparer() {
    private val installer = SuiteApkInstaller()

    @Option(name = IGNORE_CONN_CHECK_OPTION,
            description = "Disables the check for mobile data and wifi")
    private var ignoreConnectivityCheck = false
    // The default value is never used, but false is a reasonable default
    private var originalTestChainEnabled = false
    private val originalUpdaterPkgsStatus = HashMap<String, Boolean>()

    override fun setUp(testInfo: TestInformation) {
        if (isDisabled) return
        disableGmsUpdate(testInfo)
        originalTestChainEnabled = getTestChainEnabled(testInfo)
        originalUpdaterPkgsStatus.putAll(getUpdaterPkgsStatus(testInfo))
        setUpdaterNetworkingEnabled(testInfo, enableChain = true,
                enablePkgs = UPDATER_PKGS.associateWith { false })
        runPreparerApk(testInfo)
    }

    private fun runPreparerApk(testInfo: TestInformation) {
        installer.setCleanApk(true)
        installer.addTestFileName(CONNECTIVITY_CHECKER_APK)
        installer.setShouldGrantPermission(true)
        installer.setUp(testInfo)

        val runner = DefaultRemoteAndroidTestRunner(
                CONNECTIVITY_PKG_NAME,
                CONNECTIVITY_CHECK_RUNNER_NAME,
                testInfo.device.iDevice)
        runner.runOptions = "--no-hidden-api-checks"

        val receiver = CollectingTestListener()
        if (!testInfo.device.runInstrumentationTests(runner, receiver)) {
            throw TargetSetupError("Device state check failed to complete",
                    testInfo.device.deviceDescriptor)
        }

        val runResult = receiver.currentRunResults
        if (runResult.isRunFailure) {
            throw TargetSetupError("Failed to check device state before the test: " +
                    runResult.runFailureMessage, testInfo.device.deviceDescriptor)
        }

        val ignoredTestClasses = mutableSetOf<String>()
        if (ignoreConnectivityCheck) {
            ignoredTestClasses.add(CONNECTIVITY_CHECK_CLASS)
        }

        val errorMsg = runResult.testResults.mapNotNull { (testDescription, testResult) ->
            if (TestResult.TestStatus.FAILURE != testResult.status ||
                    ignoredTestClasses.contains(testDescription.className)) {
                null
            } else {
                "$testDescription: ${testResult.stackTrace}"
            }
        }.joinToString("\n")
        if (errorMsg.isBlank()) return

        throw TargetSetupError("Device setup checks failed. Check the test bench: \n$errorMsg",
                testInfo.device.deviceDescriptor)
    }

    private fun disableGmsUpdate(testInfo: TestInformation) {
        // This will be a no-op on devices without root (su) or not using gservices, but that's OK.
        testInfo.exec("su 0 am broadcast " +
                "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                "-e finsky.play_services_auto_update_enabled false")
    }

    private fun clearGmsUpdateOverride(testInfo: TestInformation) {
        testInfo.exec("su 0 am broadcast " +
                "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                "--esn finsky.play_services_auto_update_enabled")
    }

    private fun setUpdaterNetworkingEnabled(
            testInfo: TestInformation,
            enableChain: Boolean,
            enablePkgs: Map<String, Boolean>
    ) {
        // Build.VERSION_CODES.S = 31 where this is not available, then do nothing.
        if (testInfo.device.getApiLevel() < 31) return
        testInfo.exec("cmd connectivity set-chain3-enabled $enableChain")
        enablePkgs.forEach { (pkg, allow) ->
            testInfo.exec("cmd connectivity set-package-networking-enabled $allow $pkg")
        }
    }

    private fun getTestChainEnabled(testInfo: TestInformation) =
            testInfo.exec("cmd connectivity get-chain3-enabled").contains("chain:enabled")

    private fun getUpdaterPkgsStatus(testInfo: TestInformation) =
            UPDATER_PKGS.associateWith { pkg ->
                !testInfo.exec("cmd connectivity get-package-networking-enabled $pkg")
                        .contains(":deny")
            }

    override fun tearDown(testInfo: TestInformation, e: Throwable?) {
        if (isTearDownDisabled) return
        installer.tearDown(testInfo, e)
        setUpdaterNetworkingEnabled(testInfo,
                enableChain = originalTestChainEnabled,
                enablePkgs = originalUpdaterPkgsStatus)
        clearGmsUpdateOverride(testInfo)
    }
}
