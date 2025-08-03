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

package com.android.tradefed.testtype;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ShellOutputReceiverStream;
import com.android.tradefed.util.TestRunnerUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** A Test that runs a native test package. */
@OptionClass(alias = "hostgtest")
public class HostGTest extends GTestBase implements IBuildReceiver {
    private static final long DEFAULT_HOST_COMMAND_TIMEOUT_MS = 2 * 60 * 1000;

    private IBuildInfo mBuildInfo = null;

    @Option(
            name = "use-updated-shard-retry",
            description = "Whether to use the updated logic for retry with sharding.")
    private boolean mUseUpdatedShardRetry = true;

    /** Whether any incomplete test is found in the current run. */
    private boolean mIncompleteTestFound = false;

    /** List of tests that failed in the current test run when test run was complete. */
    private Set<String> mCurFailedTests = new LinkedHashSet<>();

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        this.mBuildInfo = buildInfo;
    }

    /**
     * @param cmd command that want to execute in host
     * @return the {@link CommandResult} of command
     */
    public CommandResult executeHostCommand(String cmd) {
        return executeHostCommand(cmd, DEFAULT_HOST_COMMAND_TIMEOUT_MS);
    }

    /**
     * @param cmd command that want to execute in host
     * @param timeoutMs timeout for command in milliseconds
     * @return the {@link CommandResult} of command
     */
    public CommandResult executeHostCommand(String cmd, long timeoutMs) {
        String[] cmds = cmd.split("\\s+");
        return RunUtil.getDefault().runTimedCmd(timeoutMs, cmds);
    }

    /**
     * @param gtestFile file pointing to the binary to be executed
     * @param cmd command that want to execute in host
     * @param timeoutMs timeout for command in milliseconds
     * @param receiver the result parser
     * @return the {@link CommandResult} of command
     */
    private CommandResult executeHostGTestCommand(
            File gtestFile,
            String cmd,
            long timeoutMs,
            IShellOutputReceiver receiver,
            ITestLogger logger) {
        RunUtil runUtil = new RunUtil();
        String[] cmds = cmd.split("\\s+");

        if (getShardCount() > 0) {
            if (isCollectTestsOnly()) {
                CLog.w(
                        "--collect-tests-only option ignores sharding parameters, and will cause "
                                + "each shard to collect all tests.");
            }
            runUtil.setEnvVariable("GTEST_SHARD_INDEX", Integer.toString(getShardIndex()));
            runUtil.setEnvVariable("GTEST_TOTAL_SHARDS", Integer.toString(getShardCount()));
        }

        // Set the RunUtil to combine stderr with stdout so that they are interleaved correctly.
        runUtil.setRedirectStderrToStdout(true);
        // Set the working dir to the folder containing the binary to execute from the same path.
        runUtil.setWorkingDir(gtestFile.getParentFile());

        String separator = System.getProperty("path.separator");
        List<String> paths = new ArrayList<>();
        paths.add(System.getenv("PATH"));
        paths.add(gtestFile.getParentFile().getAbsolutePath());
        String path = paths.stream().distinct().collect(Collectors.joining(separator));
        CLog.d("Using updated $PATH: %s", path);
        runUtil.setEnvVariablePriority(EnvPriority.SET);
        runUtil.setEnvVariable("PATH", path);

        // Update LD_LIBRARY_PATH
        String ldLibraryPath = TestRunnerUtil.getLdLibraryPath(gtestFile);
        if (ldLibraryPath != null) {
            runUtil.setEnvVariable("LD_LIBRARY_PATH", ldLibraryPath);
        }

        // If there's a shell output receiver to pass results along to, then
        // ShellOutputReceiverStream will write that into the IShellOutputReceiver. If not, the
        // command output will just be ignored.
        CommandResult result = null;
        File stdout = null;
        try {
            stdout =
                    FileUtil.createTempFile(
                            String.format("%s-output", gtestFile.getName()), ".txt");
            try (ShellOutputReceiverStream stream =
                    new ShellOutputReceiverStream(receiver, new FileOutputStream(stdout))) {
                result = runUtil.runTimedCmd(timeoutMs, stream, null, cmds);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Should never happen, ShellOutputReceiverStream.close is a no-op", e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Flush before the log to ensure order of events
            receiver.flush();
            try {
                // Add a small extra log to the output for verification sake.
                FileUtil.writeToFile(
                        String.format(
                                "\nBinary '%s' still exists: %s", gtestFile, gtestFile.exists()),
                        stdout,
                        true);
            } catch (IOException e) {
                // Ignore
            }
            if (stdout != null && stdout.length() > 0L) {

                try (FileInputStreamSource source = new FileInputStreamSource(stdout)) {
                    logger.testLog(
                            String.format("%s-output", gtestFile.getName()),
                            LogDataType.TEXT,
                            source);
                }
            }
            FileUtil.deleteFile(stdout);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String loadFilter(String binaryOnHost) {
        try {
            CLog.i("Loading filter from file for key: '%s'", getTestFilterKey());
            String filterFileName = String.format("%s%s", binaryOnHost, FILTER_EXTENSION);
            File filterFile = new File(filterFileName);
            if (filterFile.exists()) {
                CommandResult cmdResult =
                        executeHostCommand(String.format("cat %s", filterFileName));
                String content = cmdResult.getStdout();
                if (content != null && !content.isEmpty()) {
                    JSONObject filter = new JSONObject(content);
                    String key = getTestFilterKey();
                    JSONObject filterObject = filter.getJSONObject(key);
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
     * Run the given gtest binary
     *
     * @param resultParser the test run output parser
     * @param gtestFile file pointing to gtest binary
     * @param flags gtest execution flags
     */
    private void runTest(
            final IShellOutputReceiver resultParser,
            final File gtestFile,
            final String flags,
            ITestLogger logger) {
        for (String cmd : getBeforeTestCmd()) {
            CommandResult result = executeHostCommand(cmd);
            if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
                throw new RuntimeException("'Before test' command failed: " + result.getStderr());
            }
        }

        long maxTestTimeMs = getMaxTestTimeMs();
        String cmd = getGTestCmdLine(gtestFile.getAbsolutePath(), flags);
        CommandResult testResult =
                executeHostGTestCommand(gtestFile, cmd, maxTestTimeMs, resultParser, logger);
        // TODO: Switch throwing exceptions to use ITestInvocation.testRunFailed
        switch (testResult.getStatus()) {
            case TIMED_OUT:
                throw new HarnessRuntimeException(
                        String.format("Command run timed out after %d ms", maxTestTimeMs),
                        TestErrorIdentifier.TEST_BINARY_TIMED_OUT);
            case EXCEPTION:
                throw new RuntimeException("Command run failed with exception");
            case FAILED:
                // Check the command exit code. If it's 1, then this is just a red herring;
                // gtest returns 1 when a test fails.
                final Integer exitCode = testResult.getExitCode();
                if (exitCode == null || exitCode != 1) {
                    // No need to handle it as the parser would have reported it already.
                    CLog.e("Command run failed with exit code %s", exitCode);
                }
                break;
            default:
                break;
        }
        // Execute the host command if nothing failed badly before.
        for (String afterCmd : getAfterTestCmd()) {
            CommandResult result = executeHostCommand(afterCmd);
            if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
                throw new RuntimeException("'After test' command failed: " + result.getStderr());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException { // DNAE is part of IRemoteTest.
        try {
            // Reset flags that are used to track results of current test run.
            mIncompleteTestFound = false;
            mCurFailedTests = new LinkedHashSet<>();

            // Get testcases directory using the key HOST_LINKED_DIR first.
            // If the directory is null, then get testcase directory from getTestDir() since *TS
            // will invoke setTestDir().
            List<File> scanDirs = new ArrayList<>();
            File hostLinkedDir = mBuildInfo.getFile(BuildInfoFileKey.HOST_LINKED_DIR);
            if (hostLinkedDir != null) {
                scanDirs.add(hostLinkedDir);
            }
            File testsDir = ((DeviceBuildInfo) mBuildInfo).getTestsDir();
            if (testsDir != null) {
                scanDirs.add(testsDir);
            }

            String moduleName = getTestModule();
            Set<File> gTestFiles;
            try {
                gTestFiles =
                        FileUtil.findFiles(
                                moduleName, getAbi(), false, scanDirs.toArray(new File[] {}));
                gTestFiles = applyFileExclusionFilters(gTestFiles);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (gTestFiles == null || gTestFiles.isEmpty()) {
                // If we ended up here we most likely failed to find the proper file as is, so we
                // search for it with a potential suffix (which is allowed).
                try {
                    gTestFiles =
                            FileUtil.findFiles(
                                    moduleName + ".*",
                                    getAbi(),
                                    false,
                                    scanDirs.toArray(new File[] {}));
                    gTestFiles = applyFileExclusionFilters(gTestFiles);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (gTestFiles == null || gTestFiles.isEmpty()) {
                throw new RuntimeException(
                        String.format(
                                "Fail to find native test %s in directory %s.",
                                moduleName, scanDirs));
            }
            // Since we searched files in multiple directories, it is possible that we may have the
            // same file in different source directories. Exclude duplicates.
            gTestFiles = excludeDuplicateFiles(gTestFiles);
            for (File gTestFile : gTestFiles) {
                if (!gTestFile.canExecute()) {
                    CLog.i("%s is not executable! Skipping.", gTestFile.getAbsolutePath());
                    continue;
                }

                listener = getGTestListener(listener);
                // TODO: Need to support XML test output based on isEnableXmlOutput
                IShellOutputReceiver resultParser =
                        createResultParser(gTestFile.getName(), listener);
                String flags = getAllGTestFlags(gTestFile.getName());
                CLog.i("Running gtest %s %s", gTestFile.getName(), flags);
                try {
                    runTest(resultParser, gTestFile, flags, listener);
                } finally {
                    if (resultParser instanceof GTestResultParser) {
                        if (((GTestResultParser) resultParser).isTestRunIncomplete()) {
                            mIncompleteTestFound = true;
                        } else {
                            // if test run is complete, collect the failed tests so that they can be
                            // retried
                            mCurFailedTests.addAll(
                                    ((GTestResultParser) resultParser).getFailedTests());
                        }
                    }
                }
            }
        } catch (Throwable t) {
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
        }
    }

    private void reportFailure(
            ITestInvocationListener listener, String runName, RuntimeException exception) {
        listener.testRunStarted(runName, 0);
        listener.testRunFailed(createFailure(exception));
        listener.testRunEnded(0L, new HashMap<String, Metric>());
    }

    private FailureDescription createFailure(Exception e) {
        return TestInvocation.createFailureFromException(e, FailureStatus.TEST_FAILURE);
    }

    /**
     * Apply exclusion filters and return the remaining files.
     *
     * @param filesToFilterFrom a set of files which need to be filtered.
     * @return a set of files
     */
    private Set<File> applyFileExclusionFilters(Set<File> filesToFilterFrom) {
        Set<File> retFiles = new LinkedHashSet<>();
        List<String> fileExclusionFilterRegex = getFileExclusionFilterRegex();
        for (File file : filesToFilterFrom) {
            boolean matchedRegex = false;
            for (String regex : fileExclusionFilterRegex) {
                if (file.getPath().matches(regex)) {
                    CLog.i(
                            "File %s matches exclusion file regex %s, skipping",
                            file.getPath(), regex);
                    matchedRegex = true;
                    break;
                }
            }
            if (!matchedRegex) {
                retFiles.add(file);
            }
        }
        return retFiles;
    }

    /** exclude files with same names */
    private Set<File> excludeDuplicateFiles(Set<File> files) {
        Map<String, File> seen = new LinkedHashMap<>();
        for (File file : files) {
            if (seen.containsKey(file.getName())) {
                CLog.i(
                        "File %s already exists in location %s. skipping %s.",
                        file.getName(),
                        seen.get(file.getName()).getAbsolutePath(),
                        file.getAbsolutePath());
            } else {
                seen.put(file.getName(), file);
            }
        }
        return new LinkedHashSet(seen.values());
    }
}
