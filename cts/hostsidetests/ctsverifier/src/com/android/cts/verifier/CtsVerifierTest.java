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

package com.android.cts.verifier;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.StreamUtil;

import com.google.common.truth.Truth;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class CtsVerifierTest extends BaseHostJUnit4Test {

    private static final String CTS_VERIFIER_PACKAGE_NAME = "com.android.cts.verifier";

    private static final String[] PERMISSIONS =
            new String[] {
                "android.car.permission.CAR_POWERTRAIN",
                "android.car.permission.READ_CAR_POWER_POLICY",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_LOCATION_EXTRA_COMMAND",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.ACTIVITY_RECOGNITION",
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.BLUETOOTH_ADVERTIS",
                "android.permission.BLUETOOTH_CONNEC",
                "android.permission.BLUETOOTH_SCA",
                "android.permission.BODY_SENSOR",
                "android.permission.CAMERA",
                "android.permission.CHANGE_NETWORK_STATE",
                "android.permission.CHANGE_WIFI_STATE",
                "android.permission.FOREGROUND_SERVIC",
                "android.permission.FULLSCREEN",
                "android.permission.HIGH_SAMPLING_RATE_SENSORS",
                "android.permission.INTERNET",
                "android.permission.NFC",
                "android.permission.NFC_TRANSACTION_EVENT",
                "android.permission.VIBRATE",
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.REQUEST_DELETE_PACKAGES",
                "android.permission.REQUEST_PASSWORD_COMPLEXITY",
                "android.permission.SYSTEM_ALERT_WINDO",
                "android.permission.POST_NOTIFICATION",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.MODIFY_AUDIO_SETTINGS",
                "android.permission.RECORD_AUDIO",
                "android.permission.WAKE_LOCK",
                "com.android.alarm.permission.SET_ALARM",
                "android.permission.CALL_PHONE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_CONTACT",
                "android.permission.WRITE_CONTACT",
                "com.android.providers.tv.permission.WRITE_EPG_DATA",
                "android.permission.USE_FINGERPRIN",
                "android.permission.USE_BIOMETRI",
                "android.permission.ACCESS_NOTIFICATION_POLICY",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_SM",
                "android.permission.READ_PHONE_NUMBER",
                "android.permission.RECEIVE_SMS",
                "android.permission.SEND_SMS",
                "android.permission.MANAGE_OWN_CALLS",
                "android.permission.QUERY_ALL_PACKAGES",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.USE_EXACT_ALARM",
                "android.permission.NEARBY_WIFI_DEVICES",
                "android.permission.READ_LOGS"
            };

    @Option(name = "clear-results")
    public boolean mClearResults;

    /** Indicates if the test should be run in a device of a fold mode. */
    @Option(name = "fold-mode")
    private boolean mFoldMode;

    private static boolean sShouldUninstallCtsVerifier = false;

    enum TestResult {
        NOT_RUN,
        PASSED,
        FAILED,
        ASSUMPTION_FAILED
    }

    @BeforeClassWithInfo
    public static void ensureCtsVerifierInstalled(TestInformation information) throws Exception {
        CompatibilityBuildHelper buildHelper =
                new CompatibilityBuildHelper(information.getBuildInfo());
        if (!information
                .getDevice()
                .getInstalledPackageNames()
                .contains(CTS_VERIFIER_PACKAGE_NAME)) {
            sShouldUninstallCtsVerifier = true;
            information
                    .getDevice()
                    .installPackage(
                            buildHelper.getTestFile("CtsVerifier.apk"),
                            /* reinstall= */ true,
                            "--install-reason 4");
        }

        information
                .getDevice()
                .executeShellCommand(
                        "appops set "
                                + CTS_VERIFIER_PACKAGE_NAME
                                + " android:read_device_identifiers allow");
        information
                .getDevice()
                .executeShellCommand(
                        "appops set " + CTS_VERIFIER_PACKAGE_NAME + " MANAGE_EXTERNAL_STORAGE 0");

        for (String permission : PERMISSIONS) {
            information
                    .getDevice()
                    .executeShellCommand(
                            "pm grant " + CTS_VERIFIER_PACKAGE_NAME + " " + permission);
        }
    }

    @AfterClassWithInfo
    public static void uninstallCtsVerifier(TestInformation information) throws Exception {
        if (sShouldUninstallCtsVerifier) {
            information.getDevice().uninstallPackage(CTS_VERIFIER_PACKAGE_NAME);
        }
    }

    @After
    public void closeCtsVerifier() throws Exception {
        getDevice().executeShellCommand("am force-stop " + CTS_VERIFIER_PACKAGE_NAME);
    }

    void runTest(String testName, String... configRequirements) throws Exception {
        runTest(testName, mFoldMode, configRequirements);
    }

    void runTest(String testName, boolean foldedMode, String... configRequirements)
            throws Exception {
        String testNameToRun = foldedMode ? setFoldedTestNameSuffix(testName) : testName;
        LogUtil.CLog.logAndDisplay(Log.LogLevel.INFO, "Running test " + testNameToRun);

        Truth.assertWithMessage(
                        "A device must be connected to the workstation when starting the test")
                .that(getDevice())
                .isNotNull();

        if (mClearResults) removeTestResult(testNameToRun);

        // Check for existing result
        TestResult currentTestResult = getTestResult(testNameToRun);

        if (currentTestResult == TestResult.PASSED) {
            return; // We passed
        } else if (currentTestResult == TestResult.FAILED) {
            throw new AssertionFailedError("Failed in CtsVerifier");
        }

        getDevice().clearLogcat();

        // Start the activity
        String command =
                "am start -n "
                        + CTS_VERIFIER_PACKAGE_NAME
                        + "/com.android.cts.verifier.CtsInteractiveActivity --es ACTIVITY_NAME "
                        + testName;
        if (foldedMode) {
            command += " --es DISPLAY_MODE folded";
        }
        if (configRequirements.length > 0) {
            command += " --es REQUIREMENTS " + String.join(",", configRequirements);
        }
        getDevice().executeShellCommand(command);

        Instant limit = Instant.now().plus(Duration.ofMinutes(10));

        while (limit.isAfter(Instant.now())) {
            currentTestResult = getTestResult(testNameToRun);
            if (currentTestResult == TestResult.PASSED) {
                return; // We passed
            } else if (currentTestResult == TestResult.FAILED) {
                throw new AssertionFailedError("Failed in CtsVerifier");
            } else if (currentTestResult == TestResult.ASSUMPTION_FAILED) {
                throw new AssumptionViolatedException("Test not valid for device");
            }

            try (InputStreamSource logcatOutput = getDevice().getLogcat()) {
                String logcat = StreamUtil.getStringFromSource(logcatOutput);
                if (logcat.contains("AndroidRuntime: Process: com.android.cts.verifier, PID: ")
                        || logcat.contains("ANR in com.android.cts.verifier")) {
                    // TODO: Collect reason?
                    throw new AssertionFailedError("CTSVerifier crashed. Check logcat for reason");
                }
            }

            Thread.sleep(1000);
        }

        throw new AssertionFailedError("Timed out waiting for result from CtsVerifier");
    }

    private void removeTestResult(String testName) throws Exception {
        getDevice()
                .executeShellCommand(
                        "content delete --uri content://"
                                + CTS_VERIFIER_PACKAGE_NAME
                                + ".testresultsprovider/results --where 'testname=\""
                                + CTS_VERIFIER_PACKAGE_NAME
                                + testName
                                + "\"'");
    }

    private boolean hasWarnedDisconnected = false;

    private TestResult getTestResult(String testName) {
        try {
            String resultString =
                    getDevice()
                            .executeShellCommand(
                                    "content query --uri content://"
                                            + CTS_VERIFIER_PACKAGE_NAME
                                            + ".testresultsprovider/results --where 'testname=\""
                                            + CTS_VERIFIER_PACKAGE_NAME
                                            + testName
                                            + "\"' --projection testresult");
            hasWarnedDisconnected = false;
            if (resultString.contains("testresult=1")) {
                return TestResult.PASSED;
            } else if (resultString.contains("testresult=2")) {
                return TestResult.FAILED;
            } else if (resultString.contains("testresult=3")) {
                return TestResult.ASSUMPTION_FAILED;
            } else if (resultString.contains("testresult=NULL")) {
                return TestResult.NOT_RUN;
            } else if (resultString.contains("No result found")) {
                return TestResult.NOT_RUN;
            } else {
                LogUtil.CLog.logAndDisplay(
                        Log.LogLevel.DEBUG, "Unknown test result " + resultString);
                return TestResult.NOT_RUN;
            }
        } catch (DeviceNotAvailableException e) {
            // Happens when the device is disconnected
            LogUtil.CLog.logAndDisplay(Log.LogLevel.DEBUG, "Error getting test result", e);
            if (!hasWarnedDisconnected) {
                hasWarnedDisconnected = true;
                LogUtil.CLog.logAndDisplay(
                        Log.LogLevel.INFO,
                        "Device is not connected. Connect the device once the test "
                                + "has complete to record the result.");
            }
            return TestResult.NOT_RUN;
        }
    }

    void requireActions(String... actions) throws Exception {
        for (String action : actions) {
            String output =
                    getDevice()
                            .executeShellCommand(
                                    "cmd package query-activities --brief -a " + action);
            if (output.contains("No activities found")) {
                assumeTrue("Test requires handler for action " + action, false);
            }
        }
    }

    void requireFeatures(String... features) throws Exception {
        for (String feature : features) {
            assumeTrue("Test requires feature " + feature, getDevice().hasFeature(feature));
        }
    }

    void applicableFeatures(String... features) throws Exception {
        for (String feature : features) {
            if (getDevice().hasFeature(feature)) {
                return;
            }
        }
        assumeTrue("Test only applicable to features " + Arrays.toString(features), false);
    }

    void excludeFeatures(String... features) throws Exception {
        for (String feature : features) {
            assumeTrue("Test excludes feature " + feature, !getDevice().hasFeature(feature));
        }
    }

    /** Sets test name suffix for the folded mode, which is [folded]. */
    private static String setFoldedTestNameSuffix(String testName) {
        return !testName.endsWith("[folded]") ? testName + "[folded]" : testName;
    }
}
