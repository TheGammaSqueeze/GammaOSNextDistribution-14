/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tradefed.testtype.rust;

import static com.android.tradefed.testtype.coverage.CoverageOptions.Toolchain.GCOV;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** A Test that runs a rust binary on given device. */
@OptionClass(alias = "rust-device")
public class RustBinaryTest extends RustTestBase implements IDeviceTest, IConfigurationReceiver {

    static final String DEFAULT_TEST_PATH = "/data/local/tmp";

    @Option(
            name = "test-device-path",
            description = "The path on the device where tests are located.")
    private String mTestDevicePath = DEFAULT_TEST_PATH;

    @Option(name = "module-name", description = "The name of the test module to run.")
    private String mTestModule = null;

    private IConfiguration mConfiguration = null;

    private ITestDevice mDevice = null;

    /** {@inheritDoc} */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    public void setModuleName(String name) {
        mTestModule = name;
    }

    public String getTestModule() {
        return mTestModule;
    }

    /**
     * Gets the path where tests live on the device.
     *
     * @return The path on the device where the tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mTestDevicePath);
        String testModule = getTestModule();
        if (testModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(testModule);
        }
        return testPath.toString();
    }

    String getFileName(String fullPath) {
        int pos = fullPath.lastIndexOf('/');
        if (pos == -1) {
            return fullPath;
        }
        String fileName = fullPath.substring(pos + 1);
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("input should not end with \"/\"");
        }
        return fileName;
    }

    /** Returns true if the given fullPath is not a test we should run. */
    private boolean shouldSkipFile(String fullPath) throws DeviceNotAvailableException {
        if (fullPath == null || fullPath.isEmpty()) {
            return true;
        }

        String moduleName = getTestModule();
        String fileName = getFileName(fullPath);
        if (moduleName != null && !fileName.startsWith(moduleName)) {
            return true;
        }

        return !mDevice.isExecutable(fullPath);
    }

    /**
     * Executes all tests in a folder as well as in all subfolders recursively.
     *
     * @param root The root folder to begin searching for tests
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private boolean doRunAllTestsInSubdirectory(String root, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // returns true iff some test is found
        if (mDevice.isDirectory(root)) {
            // recursively run tests in all subdirectories
            CLog.d("Look into rust directory %s on %s", root, mDevice.getSerialNumber());
            boolean found = false;
            for (String child : mDevice.getChildren(root)) {
                CLog.d("Look into child path %s", (root + "/" + child));
                if (doRunAllTestsInSubdirectory(root + "/" + child, listener)) {
                    found = true;
                }
            }
            return found;
        } else if (shouldSkipFile(root)) {
            CLog.d("Skip rust test %s on %s", root, mDevice.getSerialNumber());
            return false;
        } else {
            CLog.d("To run rust test %s on %s", root, mDevice.getSerialNumber());
            runTest(listener, root);
            return true;
        }
    }

    private void runInvocation(
            final Invocation invocation, IShellOutputReceiver receiver, final String... extraArgs)
            throws DeviceNotAvailableException {
        StringBuilder commandBuilder = new StringBuilder();

        commandBuilder.append("cd ");
        commandBuilder.append(invocation.workingDir);
        commandBuilder.append(" && ");

        for (EnvPair envPair : invocation.env) {
            commandBuilder.append(envPair.key);
            commandBuilder.append("=");
            // TODO quote
            commandBuilder.append(envPair.val);
            commandBuilder.append(" ");
        }

        for (String arg : invocation.command) {
            // TODO quote
            commandBuilder.append(arg);
            commandBuilder.append(" ");
        }

        for (String arg : extraArgs) {
            // TODO quote
            commandBuilder.append(arg);
            commandBuilder.append(" ");
        }

        commandBuilder.deleteCharAt(commandBuilder.length() - 1);

        mDevice.executeShellCommand(
                commandBuilder.toString(),
                receiver,
                mTestTimeout,
                TimeUnit.MILLISECONDS,
                0 /* retryAttempts */);
    }

    /**
     * Run the given Rust binary
     *
     * @param fullPath absolute file system path to rust binary on device
     * @throws DeviceNotAvailableException
     */
    private void runTest(
            final ITestInvocationListener listener,
            final String fullPath)
            throws DeviceNotAvailableException {
        CLog.d("RustBinaryTest runTest: " + fullPath);
        File file = new File(fullPath);
        List<Invocation> invocations = generateInvocations(file);

        // Call with --list once per include filter to add up testCount.
        // Duplicated test cases selected by different include filters should not be counted.
        Set<String> foundTests = new HashSet<>();
        for (Invocation invocation : invocations) {
            try {
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                runInvocation(invocation, receiver, "--list");
                collectTestLines(receiver.getOutput().split("\n"), foundTests);
            } catch (DeviceNotAvailableException e) {
                CLog.e("Could not retrieve tests list from device: %s", e.getMessage());
                throw e;
            }
        }
        int testCount = foundTests.size();
        CLog.d("Total test count: %d", testCount);
        long startTimeMs = System.currentTimeMillis();
        String name = new File(fullPath).getName();
        listener.testRunStarted(name, testCount, 0, startTimeMs);
        for (Invocation invocation : invocations) {
            if (mConfiguration != null
                    && mConfiguration.getCoverageOptions().getCoverageToolchains().contains(GCOV)) {
                invocation.env.add(new EnvPair("GCOV_PREFIX", "/data/misc/trace"));
            }

            IShellOutputReceiver resultParser = createParser(listener, name);
            try {
                runInvocation(invocation, resultParser);
            } catch (DeviceNotAvailableException e) {
                listener.testRunFailed(String.format("Device not available: %s", e.getMessage()));
            } finally {
                resultParser.flush();
            }
        }
        long testTimeMs = System.currentTimeMillis() - startTimeMs;
        listener.testRunEnded(testTimeMs, new HashMap<String, Metric>());
    }

    private void wrongTestPath(String msg, String testPath, ITestInvocationListener listener) {
        CLog.e(msg + testPath);
        // mock a test run start+fail+end
        long startTimeMs = System.currentTimeMillis();
        listener.testRunStarted(testPath, 1, 0, startTimeMs);
        listener.testRunFailed(msg + testPath);
        listener.testRunEnded(0, new HashMap<String, Metric>());
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        if (!mDevice.doesFileExist(testPath)) {
            wrongTestPath("Could not find test directory ", testPath, listener);
            return;
        }

        CLog.d("To run tests in directory " + testPath);

        if (!doRunAllTestsInSubdirectory(testPath, listener)) {
            wrongTestPath("No test found under ", testPath, listener);
        }
    }
}
