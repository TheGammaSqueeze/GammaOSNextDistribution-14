/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** A Test that runs a native test package on given device. */
@OptionClass(alias = "gtest")
public class GTest extends GTestBase implements IDeviceTest {

    static final String DEFAULT_NATIVETEST_PATH = "/data/nativetest";

    private ITestDevice mDevice = null;

    @Option(name = "native-test-device-path",
            description="The path on the device where native tests are located.")
    private String mNativeTestDevicePath = DEFAULT_NATIVETEST_PATH;

    @Option(
            name = "reboot-before-test",
            description = "Reboot the device before the test suite starts.")
    private boolean mRebootBeforeTest = false;

    @Option(name = "stop-runtime",
            description = "Stops the Java application runtime before test execution.")
    private boolean mStopRuntime = false;

    /** @deprecated Use the --coverage-flush option in CoverageOptions instead. */
    @Deprecated
    @Option(
        name = "coverage-flush",
        description = "Forces coverage data to be flushed at the end of the test."
    )
    private boolean mCoverageFlush = false;

    /** @deprecated Use the --coverage-processes option in CoverageOptions instead. */
    @Deprecated
    @Option(
        name = "coverage-processes",
        description = "Name of processes to collect coverage data from."
    )
    private List<String> mCoverageProcesses = new ArrayList<>();

    /** @deprecated Merged into the --coverage-flush option in CoverageOptions instead. */
    @Deprecated
    @Option(
        name = "coverage-clear-before-test",
        description = "Clears all coverage counters before test execution."
    )
    private boolean mCoverageClearBeforeTest = true;

    @Option(
            name = "filter-non-matching-abi-folders",
            description =
                    "If an abi specific hierarchy seem to exists, only run the parts that "
                            + "match abi under test.")
    private boolean mFilterAbiFolders = true;

    @Option(
            name = "use-updated-shard-retry",
            description = "Whether to use the updated logic for retry with sharding.")
    private boolean mUseUpdatedShardRetry = true;

    /** Whether any incomplete test is found in the current run. */
    private boolean mIncompleteTestFound = false;

    /** List of tests that failed in the current test run when test run was complete. */
    private Set<String> mCurFailedTests = new LinkedHashSet<>();

    // Max characters allowed for executing GTest via command line
    private static final int GTEST_CMD_CHAR_LIMIT = 1000;
    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    protected String loadFilter(String binaryOnDevice) throws DeviceNotAvailableException {
        try {
            String filterKey = getTestFilterKey();
            CLog.i("Loading filter from file for key: '%s'", filterKey);
            String filterFile = String.format("%s%s", binaryOnDevice, FILTER_EXTENSION);
            if (getDevice().doesFileExist(filterFile)) {
                String content =
                        getDevice().executeShellCommand(String.format("cat \"%s\"", filterFile));
                if (content != null && !content.isEmpty()) {
                    JSONObject filter = new JSONObject(content);
                    JSONObject filterObject = filter.getJSONObject(filterKey);
                    return filterObject.getString("filter");
                }
                CLog.e("Error with content of the filter file %s: %s", filterFile, content);
            } else {
                CLog.e("Filter file %s not found", filterFile);
            }
        } catch (JSONException e) {
            CLog.e(e);
        }
        return null;
    }

    /**
     * Gets the path where native tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mNativeTestDevicePath);
        String testModule = getTestModule();
        if (testModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(testModule);
        }
        return testPath.toString();
    }

    public void setNativeTestDevicePath(String path) {
        mNativeTestDevicePath = path;
    }

    /**
     * Executes all native tests in a folder as well as in all subfolders recursively.
     *
     * @param root The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    @VisibleForTesting
    void doRunAllTestsInSubdirectory(
            String root, ITestDevice testDevice, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Set<String> excludeDirectories = new LinkedHashSet<>();
        // Decide to filter out a folder sub-path based on whether we should enforce the
        // current abi under test.
        if (mFilterAbiFolders && getAbi() != null) {
            String currentArch = AbiUtils.getArchForAbi(getAbi().getName());
            // exclude all abi specific folders, that is not the current abi, from the search
            for (String supportedArch : AbiUtils.getArchSupported()) {
                if (!supportedArch.equals(currentArch)) {
                    excludeDirectories.add(supportedArch);
                }
            }
        }
        String[] executableFiles = getExecutableFiles(testDevice, root, excludeDirectories);
        for (String filePath : executableFiles) {
            if (shouldRunFile(filePath)) {
                IShellOutputReceiver resultParser =
                        createResultParser(getFileName(filePath), listener);
                String flags = getAllGTestFlags(filePath);
                CLog.i("Running gtest %s %s on %s", filePath, flags, testDevice.getSerialNumber());
                if (isEnableXmlOutput()) {
                    runTestXml(testDevice, filePath, flags, listener);
                } else {
                    runTest(testDevice, resultParser, filePath, flags);
                }
            }
        }
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

    /**
     * Helper method to determine if we should execute a given file.
     *
     * @param fullPath the full path of the file in question
     * @return true if we should execute the said file.
     */
    protected boolean shouldRunFile(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return false;
        }

        // look for files that start with the module name if it is set
        String moduleName = getTestModule();
        String fileName = getFileName(fullPath);
        if (moduleName != null && !fileName.startsWith(moduleName)) {
            return false;
        }

        // filter out files excluded by the exclusion regex, for example .so files
        List<String> fileExclusionFilterRegex = getFileExclusionFilterRegex();
        for (String regex : fileExclusionFilterRegex) {
            if (fullPath.matches(regex)) {
                CLog.i("File %s matches exclusion file regex %s, skipping", fullPath, regex);
                return false;
            }
        }

        return true;
    }

    /**
     * Helper method to run a gtest command from a temporary script, in the case that the command
     * is too long to be run directly by adb.
     * @param testDevice the device on which to run the command
     * @param cmd the command string to run
     * @param resultParser the output receiver for reading test results
     */
    protected void executeCommandByScript(final ITestDevice testDevice, final String cmd,
            final IShellOutputReceiver resultParser) throws DeviceNotAvailableException {
        String tmpFileDevice = "/data/local/tmp/gtest_script.sh";
        testDevice.pushString(String.format("#!/bin/bash\n%s", cmd), tmpFileDevice);
        // force file to be executable
        testDevice.executeShellCommand(String.format("chmod 755 %s", tmpFileDevice));
        testDevice.executeShellCommand(
                String.format("sh %s", tmpFileDevice),
                resultParser,
                getMaxTestTimeMs() /* maxTimeToShellOutputResponse */,
                TimeUnit.MILLISECONDS,
                0 /* retry attempts */);
        testDevice.deleteFile(tmpFileDevice);
    }

    @Override
    protected String getGTestCmdLine(String fullPath, String flags) {
        StringBuilder sb = new StringBuilder();
        // When sharding a device GTest, add args to the command line
        if (getShardCount() > 0) {
            if (isCollectTestsOnly()) {
                CLog.w(
                        "--collect-tests-only option ignores sharding parameters, and will cause "
                                + "each shard to collect all tests.");
            }
            sb.append(String.format("GTEST_SHARD_INDEX=%s ", getShardIndex()));
            sb.append(String.format("GTEST_TOTAL_SHARDS=%s ", getShardCount()));
        }
        sb.append(super.getGTestCmdLine(fullPath, flags));
        return sb.toString();
    }

    @Override
    protected String createFlagFile(String filter) throws DeviceNotAvailableException {
        String flagPath = super.createFlagFile(filter);
        if (flagPath == null) {
            // Return null to fall back to base filter
            return null;
        }
        File flagFile = new File(flagPath);
        String devicePath = "/data/local/tmp/" + flagFile.getName();
        try {
            if (!mDevice.pushFile(flagFile, devicePath)) {
                // Failed to push flagfile, return null to fall back to base filter
                return null;
            }
        } finally {
            FileUtil.deleteFile(flagFile);
        }
        return devicePath;
    }

    /**
     * Run the given gtest binary
     *
     * @param testDevice the {@link ITestDevice}
     * @param resultParser the test run output parser
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @throws DeviceNotAvailableException
     */
    private void runTest(final ITestDevice testDevice, final IShellOutputReceiver resultParser,
            final String fullPath, final String flags) throws DeviceNotAvailableException {
        // TODO: add individual test timeout support, and rerun support
        try {
            for (String cmd : getBeforeTestCmd()) {
                testDevice.executeShellCommand(cmd);
            }

            if (mRebootBeforeTest && !isCollectTestsOnly()) {
                CLog.d("Rebooting device before test starts as requested.");
                testDevice.reboot();
            }

            String cmd = getGTestCmdLine(fullPath, flags);
            // ensure that command is not too long for adb
            if (cmd.length() < GTEST_CMD_CHAR_LIMIT) {
                testDevice.executeShellCommand(
                        cmd,
                        resultParser,
                        getMaxTestTimeMs() /* maxTimeToShellOutputResponse */,
                        TimeUnit.MILLISECONDS,
                        0 /* retryAttempts */);
            } else {
                // wrap adb shell command in script if command is too long for direct execution
                executeCommandByScript(testDevice, cmd, resultParser);
            }
        } catch (DeviceNotAvailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } finally {
            // TODO: consider moving the flush of parser data on exceptions to TestDevice or
            // AdbHelper
            resultParser.flush();
            if (resultParser instanceof GTestResultParser) {
                if (((GTestResultParser) resultParser).isTestRunIncomplete()) {
                    mIncompleteTestFound = true;
                } else {
                    // if test run is complete, collect the failed tests so that they can be retried
                    mCurFailedTests.addAll(((GTestResultParser) resultParser).getFailedTests());
                }
            }
            for (String cmd : getAfterTestCmd()) {
                testDevice.executeShellCommand(cmd);
            }
        }
    }

    /**
     * Run the given gtest binary and parse XML results This methods typically requires the filter
     * for .tff and .xml files, otherwise it will post some unwanted results.
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private void runTestXml(
            final ITestDevice testDevice,
            final String fullPath,
            final String flags,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver outputCollector = new CollectingOutputReceiver();
        File tmpOutput = null;
        try {
            String testRunName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            tmpOutput = FileUtil.createTempFile(testRunName, ".xml");
            String tmpResName = fullPath + "_res.xml";
            String extraFlag = String.format(GTEST_XML_OUTPUT, tmpResName);
            String fullFlagCmd =  String.format("%s %s", flags, extraFlag);

            // Run the tests with modified flags
            runTest(testDevice, outputCollector, fullPath, fullFlagCmd);
            // Pull the result file, may not exist if issue with the test.
            testDevice.pullFile(tmpResName, tmpOutput);
            // Clean the file on the device
            testDevice.deleteFile(tmpResName);
            GTestXmlResultParser parser = createXmlParser(testRunName, listener);
            // Attempt to parse the file, doesn't matter if the content is invalid.
            if (tmpOutput.exists()) {
                parser.parseResult(tmpOutput, outputCollector);
                if (parser.isTestRunIncomplete()) {
                    mIncompleteTestFound = true;
                } else {
                    // if test run is complete, collect the failed tests so that they can be retried
                    mCurFailedTests.addAll(parser.getFailedTests());
                }
            }
        } catch (DeviceNotAvailableException | RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            outputCollector.flush();
            for (String cmd : getAfterTestCmd()) {
                testDevice.executeShellCommand(cmd);
            }
            FileUtil.deleteFile(tmpOutput);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // TODO: add support for rerunning tests
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        if (!mDevice.doesFileExist(testPath)) {
            CLog.w("Could not find native test directory %s in %s!", testPath,
                    mDevice.getSerialNumber());
            return;
        }
        // Reset flags that are used to track results of current test run.
        mIncompleteTestFound = false;
        mCurFailedTests = new LinkedHashSet<>();

        if (mStopRuntime) {
            mDevice.executeShellCommand("stop");
        }
        listener = getGTestListener(listener);

        Throwable throwable = null;
        try {
            doRunAllTestsInSubdirectory(testPath, mDevice, listener);
        } catch (Throwable t) {
            throwable = t;
            // if we encounter any errors, count it as test Incomplete so that retry attempts
            // during sharding uses a full retry.
            mIncompleteTestFound = true;
            throw t;
        } finally {
            if (mUseUpdatedShardRetry) {
                // notify of test execution will enable the new sharding retry behavior since Gtest
                // will be aware of retries.
                notifyTestExecution(mIncompleteTestFound, mCurFailedTests);
            }
            if (!(throwable instanceof DeviceNotAvailableException)) {
                if (mStopRuntime) {
                    mDevice.executeShellCommand("start");
                    mDevice.waitForDeviceAvailable();
                }
            }
        }
    }

    public boolean isRebootBeforeTestEnabled() {
        return mRebootBeforeTest;
    }

    /**
     * Searches directories recursively to find all executable files.
     *
     * @param device {@link ITestDevice} where the search will occur.
     * @param deviceFilePath is the path on the device where to do the search.
     * @param excludeDirectories Set of directory names that must be excluded from the search.
     * @return Array of string containing all the executable file paths on the device.
     * @throws DeviceNotAvailableException
     */
    private String[] getExecutableFiles(
            ITestDevice device, String deviceFilePath, Set<String> excludeDirectories)
            throws DeviceNotAvailableException {
        String cmd = String.format("find -L %s -type f -perm -a=x", deviceFilePath);

        if (excludeDirectories != null && !excludeDirectories.isEmpty()) {
            for (String directoryName : excludeDirectories) {
                cmd += String.format(" -not -path \"*/%s/*\"", directoryName);
            }
        }

        String output = device.executeShellCommand(cmd);
        if (output.trim().isEmpty()) {
            return new String[0];
        }
        return output.split("\r?\n");
    }
}
