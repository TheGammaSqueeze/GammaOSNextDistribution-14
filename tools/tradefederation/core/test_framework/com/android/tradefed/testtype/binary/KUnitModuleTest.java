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
package com.android.tradefed.testtype.binary;

import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/** Test runner for running KUnit test modules on device. */
@OptionClass(alias = "kunit-module-test")
public class KUnitModuleTest extends ExecutableTargetTest {

    public static final String RMMOD_COMMAND_FMT = "rmmod %s";
    public static final String INSMOD_COMMAND_FMT = "insmod %s";
    public static final String KUNIT_DEBUGFS_PATH =
            String.format("%s/kunit", NativeDevice.DEBUGFS_PATH);
    public static final String KUNIT_RESULTS_FMT =
            String.format("%s/%%s/results", KUNIT_DEBUGFS_PATH);

    /**
     * Return module name as it's displayed after loading.
     *
     * <p>For example, see the difference between the file name and that returned by `lsmod`:
     *
     * <pre>{@code
     * $ insmod kunit-example-test.ko
     * $ lsmod | grep kunit
     * kunit_example_test 20480 0
     * }</pre>
     */
    private String getDisplayedModuleName(String fullPath) {

        // Extract filename from full path
        int sepPos = fullPath.lastIndexOf('/');
        String moduleName = sepPos == -1 ? fullPath : fullPath.substring(sepPos + 1);
        if (moduleName.isEmpty()) {
            throw new IllegalArgumentException("input should not end with \"/\"");
        }

        // Remove `.ko` extension if present
        moduleName =
                moduleName.endsWith(".ko")
                        ? moduleName.substring(0, moduleName.length() - 3)
                        : moduleName;

        // Replace all '-' with '_'
        return moduleName.replace('-', '_');
    }

    @Override
    protected boolean doesRunBinaryGenerateTestResults() {
        return true;
    }

    @Override
    public boolean getCollectTestsOnly() {
        if (super.getCollectTestsOnly()) {
            // TODO(b/310965570) Implement collect only mode for KUnit modules
            throw new UnsupportedOperationException("collect-tests-only mode not support");
        }
        return false;
    }

    @Override
    public String findBinary(String binary) throws DeviceNotAvailableException {
        return getSkipBinaryCheck() || getDevice().doesFileExist(binary) ? binary : null;
    }

    @Override
    public void runBinary(
            String modulePath, ITestInvocationListener listener, TestDescription description)
            throws DeviceNotAvailableException, IOException {

        if (getDevice() == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        String kunitModule = getDisplayedModuleName(modulePath);

        // Unload module before hand in case it's already loaded for some reason
        CommandResult result =
                getDevice().executeShellV2Command(String.format(RMMOD_COMMAND_FMT, kunitModule));

        if (CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.w("Module '%s' unexpectedly still loaded, it has been unloaded.", kunitModule);
        }

        boolean debugfsAlreadyMounted = getDevice().isDebugfsMounted();

        try {
            if (!debugfsAlreadyMounted) {
                getDevice().mountDebugfs();
            }

            // Collect list of pre-existing KUnit results under debugfs. So, we can ignore later.
            List<String> kunitTestSuitesBefore =
                    Arrays.asList(getDevice().getChildren(KUNIT_DEBUGFS_PATH));
            if (!kunitTestSuitesBefore.isEmpty()) {
                CLog.w(
                        "Stale KUnit test suite results found [%s]",
                        String.join(",", kunitTestSuitesBefore));
            }

            result =
                    getDevice()
                            .executeShellV2Command(
                                    String.format(INSMOD_COMMAND_FMT, modulePath),
                                    getTimeoutPerBinaryMs(),
                                    TimeUnit.MILLISECONDS);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                String errorMessage =
                        String.format(
                                "binary returned non-zero. Exit code: %d, stderr: %s, stdout: %s",
                                result.getExitCode(), result.getStderr(), result.getStdout());
                listener.testStarted(description);
                listener.testFailed(
                        description,
                        FailureDescription.create(errorMessage)
                                .setFailureStatus(FailureStatus.TEST_FAILURE));
                listener.testEnded(description, new HashMap<String, Metric>());
                return;
            }

            // Parse new KUnit results in debugfs
            List<String> kunitTestSuitesAfter =
                    new ArrayList<String>(
                            Arrays.asList(getDevice().getChildren(KUNIT_DEBUGFS_PATH)));
            kunitTestSuitesAfter.removeAll(kunitTestSuitesBefore);

            if (kunitTestSuitesAfter.isEmpty()) {
                String errorMessage =
                        String.format(
                                "No KTAP results generated in '%s' for module '%s'",
                                KUNIT_DEBUGFS_PATH, kunitModule);
                CLog.e(errorMessage);
                listener.testStarted(description);
                listener.testFailed(
                        description,
                        FailureDescription.create(errorMessage)
                                .setFailureStatus(FailureStatus.TEST_FAILURE));
                listener.testEnded(description, new HashMap<String, Metric>());
            }

            for (String testSuite : kunitTestSuitesAfter) {
                String ktapResults =
                        getDevice().pullFileContents(String.format(KUNIT_RESULTS_FMT, testSuite));
                CLog.i(
                        "KUnit module '%s' KTAP result:\n%s",
                        description.getTestName(), ktapResults);

                try {
                    KTapResultParser.applyKTapResultToListener(
                            listener,
                            description.getTestName(),
                            ktapResults,
                            KTapResultParser.ParseResolution.AGGREGATED_TOP_LEVEL);
                } catch (RuntimeException exception) {
                    CLog.e("KTAP parse error: %s", exception.toString());
                    listener.testStarted(description);
                    listener.testFailed(
                            description,
                            FailureDescription.create(exception.toString())
                                    .setFailureStatus(FailureStatus.TEST_FAILURE));
                    listener.testEnded(description, new HashMap<String, Metric>());
                }
            }

            // Clean up, unload module.
            result =
                    getDevice()
                            .executeShellV2Command(String.format(RMMOD_COMMAND_FMT, kunitModule));

            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                String errorMessage =
                        String.format(
                                "binary returned non-zero. Exit code: %d, stderr: %s, stdout: %s",
                                result.getExitCode(), result.getStderr(), result.getStdout());
                CLog.w("Unable to unload module '%s'. %s", kunitModule, errorMessage);
            }
        } finally {
            if (debugfsAlreadyMounted) {
                // If debugfs was already mounted before this test, then keep it mounted.
                getDevice().unmountDebugfs();
            }
        }
    }
}
