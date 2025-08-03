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

package com.android.cts.packagemanager.stats.host;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class Utils {
    public static final String SIGNATURE_FILE_SUFFIX = ".idsig";

    public static String pushApkToRemote(String apkName, String remoteDirPath, IBuildInfo ctsBuild,
            ITestDevice device) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(ctsBuild);
        final File apk = buildHelper.getTestFile(apkName);
        final File signature = buildHelper.getTestFile(apkName + SIGNATURE_FILE_SUFFIX);
        assertNotNull(apk);
        assertNotNull(signature);
        final String remoteApkPath = remoteDirPath + "/" + apk.getName();
        final String remoteSignaturePath = remoteApkPath + SIGNATURE_FILE_SUFFIX;
        assertTrue(device.pushFile(apk, remoteApkPath));
        assertTrue(device.pushFile(signature, remoteSignaturePath));
        return remoteApkPath;
    }

    public static Map<String, String> runDeviceTests(ITestDevice device, String packageName,
            String testClassName, String testMethodName, Map<String, String> testArgs)
            throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = packageName + testClassName;
        }
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                "androidx.test.runner.AndroidJUnitRunner", device.getIDevice());

        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        if (testArgs != null && testArgs.size() > 0) {
            for (String name : testArgs.keySet()) {
                final String value = testArgs.get(name);
                testRunner.addInstrumentationArg(name, value);
            }
        }
        final TestResultListener listener = new TestResultListener();
        assertTrue(device.runInstrumentationTests(testRunner, listener));

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new Error("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new Error("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(
                        com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }

        if (listener.mFailureStackTrace != null) {
            throw new AssertionError("Failed to successfully run device tests: "
                    + listener.mFailureStackTrace);
        }
        return listener.mMetrics;
    }

    /* Special listener for collecting data from the test result */
    private static class TestResultListener extends CollectingTestListener {
        private final Map<String, String> mMetrics = new HashMap<>();
        private String mFailureStackTrace = null;

        @Override
        public void testEnded(TestDescription test, Map<String, String> metrics) {
            mMetrics.putAll(metrics);
        }

        @Override
        public void testEnded(TestDescription test, HashMap<String, Metric> metrics) {
            for (Map.Entry<String, Metric> e: metrics.entrySet()) {
                mMetrics.put(e.getKey(), e.getValue().getMeasurements().getSingleString());
            }
        }

        @Override
        public void testFailed(TestDescription test, String trace) {
            mFailureStackTrace = trace;
        }
    }

    public static boolean hasIncrementalFeature(ITestDevice device) throws Exception {
        return "true\n".equals(device.executeShellCommand(
                "pm has-feature android.software.incremental_delivery"));
    }
}
