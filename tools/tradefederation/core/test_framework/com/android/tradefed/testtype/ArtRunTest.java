/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import difflib.DiffUtils;
import difflib.Patch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A test runner to run ART run-tests. */
public class ArtRunTest implements IRemoteTest, IAbiReceiver, ITestFilterReceiver, ITestCollector {

    private static final String RUNTEST_TAG = "ArtRunTest";

    private static final Path ART_APEX_PATH = Paths.get("/apex", "com.android.art");

    private static final String DALVIKVM_CMD =
            "dalvikvm|#BITNESS#| -Xcompiler-option --compile-art-test -classpath |#CLASSPATH#| "
                    + "|#MAINCLASS#| >|#STDOUT#| 2>|#STDERR#|";

    private static final String STDOUT_FILE_NAME = "stdout.txt";
    private static final String STDERR_FILE_NAME = "stderr.txt";

    // Name of the Checker Python Archive (PAR) file.
    public static final String CHECKER_PAR_FILENAME = "art-run-test-checker";
    private static final long CHECKER_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes.

    @Option(
            name = "test-timeout",
            description =
                    "The max time in ms for an art run-test to "
                            + "run. Test run will be aborted if any test takes longer.",
            isTimeVal = true)
    private long mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "run-test-name", description = "The name to use when reporting results.")
    private String mRunTestName;

    @Option(name = "classpath", description = "Holds the paths to search when loading tests.")
    private List<String> mClasspath = new ArrayList<>();

    private ITestDevice mDevice = null;
    private IAbi mAbi = null;
    private final Set<String> mIncludeFilters = new LinkedHashSet<>();
    private final Set<String> mExcludeFilters = new LinkedHashSet<>();

    @Option(
            name = "collect-tests-only",
            description =
                    "Do a dry-run of the tests in order to collect their "
                            + "names, but do not actually run them.")
    private boolean mCollectTestsOnly = false;

    /** {@inheritDoc} */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mDevice = testInfo.getDevice();
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set.");
        }
        if (mAbi == null) {
            throw new IllegalArgumentException("ABI has not been set.");
        }
        if (mRunTestName == null) {
            throw new IllegalArgumentException("Run-test name has not been set.");
        }
        if (mClasspath.isEmpty()) {
            throw new IllegalArgumentException("Classpath is empty.");
        }

        runArtTest(testInfo, listener);
    }

    /**
     * Run a single ART run-test (on device).
     *
     * @param listener The {@link ITestInvocationListener} object associated to the executed test
     * @throws DeviceNotAvailableException If there was a problem communicating with the device.
     */
    void runArtTest(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        String abi = mAbi.getName();
        String runName = String.format("%s_%s", RUNTEST_TAG, abi);
        TestDescription testId = new TestDescription(runName, mRunTestName);
        if (shouldSkipCurrentTest(testId)) {
            return;
        }

        String deviceSerialNumber = mDevice.getSerialNumber();
        CLog.i("Running ArtRunTest %s on %s", mRunTestName, deviceSerialNumber);

        String testCmd = DALVIKVM_CMD;
        testCmd = testCmd.replace("|#BITNESS#|", AbiUtils.getBitness(abi));
        testCmd = testCmd.replace("|#CLASSPATH#|", ArrayUtil.join(File.pathSeparator, mClasspath));
        // TODO: Turn this into an an option of the `ArtRunTest` class?
        testCmd = testCmd.replace("|#MAINCLASS#|", "Main");

        CLog.d("About to run run-test command: `%s`", testCmd);
        // Note: We only run one test at the moment.
        int testCount = 1;
        listener.testRunStarted(runName, testCount);
        listener.testStarted(testId);

        // Temporary local directory used to store files used in test.
        File tmpTestLocalDir = null;
        // Path to temporary remote directory used to store files used in test.
        String tmpTestRemoteDirPath = null;

        try (CloseableTraceScope ignored = new CloseableTraceScope(testId.toString())) {
            if (mCollectTestsOnly) {
                return;
            }

            // Create remote temporary directory and set redirections in test command.
            String tmpTestRemoteDirPathTemplate =
                    String.format("%s.XXXXXXXXXX", mRunTestName.replaceAll("/", "-"));
            tmpTestRemoteDirPath = createTemporaryDirectoryOnDevice(tmpTestRemoteDirPathTemplate);
            CLog.d("Created temporary remote directory `%s` for test", tmpTestRemoteDirPath);

            String remoteStdoutFilePath =
                    String.format("%s/%s", tmpTestRemoteDirPath, STDOUT_FILE_NAME);
            testCmd = testCmd.replace("|#STDOUT#|", remoteStdoutFilePath);

            String remoteStderrFilePath =
                    String.format("%s/%s", tmpTestRemoteDirPath, STDERR_FILE_NAME);
            testCmd = testCmd.replace("|#STDERR#|", remoteStderrFilePath);

            // TODO: The "run" step should be configurable, as is the case in current ART
            // `run-test` scripts).

            // Execute the test on device.
            CommandResult testResult =
                    mDevice.executeShellV2Command(
                            testCmd, mMaxTestTimeMs, TimeUnit.MILLISECONDS, /* retryAttempts */ 0);
            if (testResult.getStatus() != CommandStatus.SUCCESS) {
                String message =
                        String.format(
                                "Test command execution failed with status %s: %s",
                                testResult.getStatus(), testResult);
                CLog.e(message);
                listener.testFailed(testId, message);
                return;
            }
            Integer exitCode = testResult.getExitCode();
            CLog.v("`%s` on %s returned exit code: %d", testCmd, deviceSerialNumber, exitCode);

            // Create local temporary directory and pull test's outputs from the device.
            tmpTestLocalDir = createTestLocalTempDirectory(testInfo);
            CLog.d("Created temporary local directory `%s` for test", tmpTestLocalDir);

            File localStdoutFile = new File(tmpTestLocalDir, STDOUT_FILE_NAME);
            if (!pullAndCheckFile(remoteStdoutFilePath, localStdoutFile)) {
                throw new IOException(
                        String.format(
                                "Error while pulling remote file `%s` to local file `%s`",
                                remoteStdoutFilePath, localStdoutFile));
            }
            String actualStdoutText = FileUtil.readStringFromFile(localStdoutFile);

            File localStderrFile = new File(tmpTestLocalDir, STDERR_FILE_NAME);
            if (!pullAndCheckFile(remoteStderrFilePath, localStderrFile)) {
                throw new IOException(
                        String.format(
                                "Error while pulling remote file `%s` to local file `%s`",
                                remoteStderrFilePath, localStderrFile));
            }
            String actualStderrText = FileUtil.readStringFromFile(localStderrFile);

            // TODO: The "check" step should be configurable, as is the case in current ART
            // `run-test` scripts).

            // List of encountered errors during the test.
            List<String> errors = new ArrayList<>();

            // Check the test's exit code.
            Optional<String> exitCodeError = checkExitCode(exitCode);
            exitCodeError.ifPresent(e -> errors.add(e));

            // Check the test's standard output.
            Optional<String> stdoutError =
                    checkTestOutput(
                            testInfo,
                            actualStdoutText,
                            /* outputShortName */ "stdout",
                            /* outputPrettyName */ "standard output");
            if (stdoutError.isPresent()) {
                errors.add(stdoutError.get());
                try (FileInputStreamSource source = new FileInputStreamSource(localStdoutFile)) {
                    listener.testLog(STDOUT_FILE_NAME, LogDataType.TEXT, source);
                }
            }

            // Check the test's standard error.
            Optional<String> stderrError =
                    checkTestOutput(
                            testInfo,
                            actualStderrText,
                            /* outputShortName */ "stderr",
                            /* outputPrettyName */ "standard error");
            if (stderrError.isPresent()) {
                errors.add(stderrError.get());
                try (FileInputStreamSource source = new FileInputStreamSource(localStderrFile)) {
                    listener.testLog(STDERR_FILE_NAME, LogDataType.TEXT, source);
                }
            }

            // If the test is a Checker test, run Checker and check its output.
            if (mRunTestName.contains("-checker-")) {
                Optional<String> checkerError = executeCheckerTest(testInfo, listener);
                checkerError.ifPresent(e -> errors.add(e));
            }

            // Process potential errors.
            if (!errors.isEmpty()) {
                String errorMessage = String.join("\n", errors);
                listener.testFailed(testId, errorMessage);
            }
        } catch (AdbShellCommandException | IOException e) {
            listener.testFailed(testId, String.format("Error in `ArtRunTest` test runner: %s", e));
            throw new RuntimeException(e);
        } finally {
            HashMap<String, Metric> emptyTestMetrics = new HashMap<>();
            listener.testEnded(testId, emptyTestMetrics);
            HashMap<String, Metric> emptyTestRunMetrics = new HashMap<>();
            // TODO: Pass an actual value as `elapsedTimeMillis` argument.
            listener.testRunEnded(/* elapsedTimeMillis*/ 0, emptyTestRunMetrics);

            // Clean up temporary directories on host and device.
            FileUtil.recursiveDelete(tmpTestLocalDir);
            if (tmpTestRemoteDirPath != null) {
                mDevice.deleteFile(tmpTestRemoteDirPath);
            }
        }
    }

    /**
     * Create a local temporary directory within the test's dependencies folder, to collect test
     * outputs pulled from the device-under-test.
     *
     * @param testInfo The {@link TestInformation} object associated to the executed test
     * @return The {@link File} object pointing to the created temporary directory.
     * @throws IOException If the creation of the temporary directory failed.
     */
    protected File createTestLocalTempDirectory(TestInformation testInfo) throws IOException {
        return FileUtil.createTempDir(mRunTestName, CurrentInvocation.getWorkFolder());
    }

    /**
     * Check the exit code returned by a test command.
     *
     * @param exitCode The exit code returned by the test command
     * @return An optional error message, empty if the test exit code indicated success
     */
    protected Optional<String> checkExitCode(Integer exitCode) {
        if (exitCode != 0) {
            String errorMessage =
                    String.format("Test `%s` exited with code %d", mRunTestName, exitCode);
            CLog.i(errorMessage);
            return Optional.of(errorMessage);
        }
        return Optional.empty();
    }

    /**
     * Check an output produced by a test command.
     *
     * <p>Used to check the standard output and the standard error of a test.
     *
     * @param testInfo The {@link TestInformation} object associated to the executed test
     * @param actualOutputText The output produced by the test
     * @param outputShortName The short name of the output channel
     * @param outputPrettyName A prettier name for the output channel, used in error messages
     * @return An optional error message, empty if the checked output is valid
     */
    protected Optional<String> checkTestOutput(
            TestInformation testInfo,
            String actualOutputText,
            String outputShortName,
            String outputPrettyName) {
        final String expectedFileName = String.format("expected-%s.txt", outputShortName);
        final String actualFileName = outputShortName;

        if (actualOutputText == null) {
            String errorMessage =
                    String.format(
                            "No %s received to compare to for test `%s`",
                            outputPrettyName, mRunTestName);
            CLog.e(errorMessage);
            return Optional.of(errorMessage);
        }
        try {
            String expectedOutputFileName = String.format("%s-%s", mRunTestName, expectedFileName);
            File expectedOutputFile =
                    testInfo.getDependencyFile(expectedOutputFileName, /* targetFirst */ true);
            CLog.i(
                    "Found expected %s for run-test `%s`: `%s`",
                    outputPrettyName, mRunTestName, expectedOutputFile);
            String expectedOutputText = FileUtil.readStringFromFile(expectedOutputFile);

            if (!actualOutputText.equals(expectedOutputText)) {
                // Produce a unified diff output for the error message.
                String diff =
                        computeDiff(
                                expectedOutputText,
                                actualOutputText,
                                expectedFileName,
                                actualFileName);
                String errorMessage =
                        String.format(
                                "The actual %s does not match the expected %s for test `%s`:\n%s",
                                outputPrettyName, outputPrettyName, mRunTestName, diff);
                CLog.i(errorMessage);
                return Optional.of(errorMessage);
            }
        } catch (IOException ioe) {
            String errorMessage =
                    String.format(
                            "I/O error while accessing expected %s for test `%s`: %s",
                            outputPrettyName, mRunTestName, ioe);
            CLog.e(errorMessage);
            return Optional.of(errorMessage);
        }
        return Optional.empty();
    }

    /**
     * Execute a Checker test and check its output.
     *
     * <p>Checker tests are additional tests included in some ART run-tests, written as annotations
     * in the comments of a test's source files, and used to verify ART's compiler.
     *
     * @param testInfo The {@link TestInformation} object associated to the executed test
     * @param listener The {@link ITestInvocationListener} object associated to the executed test
     * @return An optional error message, empty if the Checker test succeeded
     */
    protected Optional<String> executeCheckerTest(
            TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException, AdbShellCommandException, IOException {

        // Temporary local directory used to store files used in Checker test.
        File tmpCheckerLocalDir = null;
        // Path to temporary remote directory used to store files used in Checker test.
        String tmpCheckerRemoteDirPath = null;

        try {
            String tmpCheckerRemoteDirPathTemplate =
                    String.format("%s.checker.XXXXXXXXXX", mRunTestName.replaceAll("/", "-"));
            tmpCheckerRemoteDirPath =
                    createTemporaryDirectoryOnDevice(tmpCheckerRemoteDirPathTemplate);
            CLog.d(
                    "Created temporary remote directory `%s` for Checker test",
                    tmpCheckerRemoteDirPath);

            String cfgPath = tmpCheckerRemoteDirPath + "/graph.cfg";
            String oatPath = tmpCheckerRemoteDirPath + "/output.oat";
            String abi = mAbi.getName();
            String dex2oatBinary = "dex2oat" + AbiUtils.getBitness(abi);
            Path dex2oatPath = Paths.get(ART_APEX_PATH.toString(), "bin", dex2oatBinary);
            String dex2oatCmd =
                    String.format(
                            "%s --dex-file=%s --oat-file=%s --dump-cfg=%s -j1 --compile-art-test",
                            dex2oatPath, mClasspath.get(0), oatPath, cfgPath);
            CommandResult dex2oatResult = mDevice.executeShellV2Command(dex2oatCmd);
            if (dex2oatResult.getStatus() != CommandStatus.SUCCESS) {
                throw new AdbShellCommandException(
                        "Error while running dex2oat: %s", dex2oatResult.getStderr());
            }

            tmpCheckerLocalDir =
                    FileUtil.createTempDir(mRunTestName, CurrentInvocation.getWorkFolder());
            CLog.d("Created temporary local directory `%s` for Checker test", tmpCheckerLocalDir);

            File localCfgPath = new File(tmpCheckerLocalDir, "graph.cfg");
            if (localCfgPath.isFile()) {
                localCfgPath.delete();
            }

            if (!pullAndCheckFile(cfgPath, localCfgPath)) {
                throw new IOException("Cannot pull CFG file from the device");
            }

            File tempJar = new File(tmpCheckerLocalDir, "temp.jar");
            if (!pullAndCheckFile(mClasspath.get(0), tempJar)) {
                throw new IOException("Cannot pull JAR file from the device");
            }

            extractSourcesFromJar(tmpCheckerLocalDir, tempJar);

            String checkerArch = AbiUtils.getArchForAbi(mAbi.getName()).toUpperCase();

            File checkerBinary = getCheckerBinaryPath(testInfo);

            String[] checkerCommandLine = {
                checkerBinary.getAbsolutePath(),
                "--no-print-cfg",
                "-q",
                "--arch=" + checkerArch,
                localCfgPath.getAbsolutePath(),
                tmpCheckerLocalDir.getAbsolutePath()
            };

            Optional<String> checkerError = runChecker(checkerCommandLine);
            if (checkerError.isPresent()) {
                try (FileInputStreamSource source = new FileInputStreamSource(localCfgPath)) {
                    listener.testLog("graph.cfg", LogDataType.CFG, source);
                }
                CLog.i(checkerError.get());
                return checkerError;
            }
        } catch (AdbShellCommandException | IOException e) {
            CLog.e("Exception while running Checker test: " + e.getMessage());
            throw e;
        } finally {
            // Clean up temporary directories on host and device.
            FileUtil.recursiveDelete(tmpCheckerLocalDir);
            if (tmpCheckerRemoteDirPath != null) {
                mDevice.deleteFile(tmpCheckerRemoteDirPath);
            }
        }
        return Optional.empty();
    }

    /** Find the Checker binary (Python Archive). */
    protected File getCheckerBinaryPath(TestInformation testInfo) {
        File checkerBinary;
        try {
            checkerBinary =
                    testInfo.getDependencyFile(CHECKER_PAR_FILENAME, /* targetFirst */ false);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(
                    String.format("Couldn't find Checker binary file `%s`", CHECKER_PAR_FILENAME));
        }
        checkerBinary.setExecutable(true);
        return checkerBinary;
    }

    /**
     * Run a Checker command and check its result.
     *
     * @param checkerCommandLine The Checker command line to execute
     * @return An optional error message, empty if the Checker invocation was successful
     */
    protected Optional<String> runChecker(String[] checkerCommandLine) {
        String checkerCommandLineString = String.join(" ", checkerCommandLine);
        CLog.d("About to run Checker command: %s", checkerCommandLineString);
        long startTime = System.currentTimeMillis();
        CommandResult result =
                RunUtil.getDefault().runTimedCmd(CHECKER_TIMEOUT_MS, checkerCommandLine);
        long duration = System.currentTimeMillis() - startTime;
        CLog.i("Checker command `%s` executed in %s ms", checkerCommandLineString, duration);
        InvocationMetricLogger.addInvocationMetrics(
            InvocationMetricKey.ART_RUN_TEST_CHECKER_COMMAND_TIME_MS, duration);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            String errorMessage;
            if (result.getStatus() == CommandStatus.TIMED_OUT) {
                errorMessage =
                        String.format("Checker command timed out after %s ms", CHECKER_TIMEOUT_MS);
            } else {
                errorMessage =
                        String.format(
                                "Checker command finished unsuccessfully: status=%s, exit"
                                        + " code=%s,\n"
                                        + "stdout=\n"
                                        + "%s\n"
                                        + "stderr=\n"
                                        + "%s\n",
                                result.getStatus(),
                                result.getExitCode(),
                                result.getStdout(),
                                result.getStderr());
            }
            CLog.i(errorMessage);
            return Optional.of(errorMessage);
        }
        return Optional.empty();
    }

    /** Extract src directory from given jar file to given directory. */
    protected void extractSourcesFromJar(File tmpCheckerLocalDir, File jar) throws IOException {
        try (ZipFile archive = new ZipFile(jar)) {
            File srcFile = new File(tmpCheckerLocalDir, "src");
            if (srcFile.exists()) {
                FileUtil.recursiveDelete(srcFile);
            }

            List<? extends ZipEntry> entries =
                    archive.stream()
                            .sorted(Comparator.comparing(ZipEntry::getName))
                            .collect(Collectors.toList());

            for (ZipEntry entry : entries) {
                if (entry.getName().startsWith("src")) {
                    Path entryDest = tmpCheckerLocalDir.toPath().resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectory(entryDest);
                    } else {
                        Files.copy(archive.getInputStream(entry), entryDest);
                    }
                }
            }
        }
    }

    /**
     * Check if current test should be skipped.
     *
     * @param description The test in progress.
     * @return true if the test should be skipped.
     */
    private boolean shouldSkipCurrentTest(TestDescription description) {
        // Force to skip any test not listed in include filters, or listed in exclude filters.
        // exclude filters have highest priority.
        String testName = description.getTestName();
        String descString = description.toString();
        if (mExcludeFilters.contains(testName) || mExcludeFilters.contains(descString)) {
            return true;
        }
        if (!mIncludeFilters.isEmpty()) {
            return !mIncludeFilters.contains(testName) && !mIncludeFilters.contains(descString);
        }
        return false;
    }

    /**
     * Compute the difference between expected and actual outputs as a unified diff.
     *
     * @param expected The expected output
     * @param actual The actual output
     * @param expectedFileName The name of the expected output file name (used in diff header)
     * @param actualFileName The name of the actual output file name (used in diff header)
     * @return The unified diff between the expected and actual outputs
     */
    private String computeDiff(
            String expected, String actual, String expectedFileName, String actualFileName) {
        List<String> expectedLines = Arrays.asList(expected.split("\\r?\\n"));
        List<String> actualLines = Arrays.asList(actual.split("\\r?\\n"));

        // This try block is necessary to be compatible with a more recent
        // version of diffutil that declares a checked exception on the `diff`
        // method.  This transforms any exceptions in this block into
        // runtime exceptions which as there are no checked exceptions in here
        // at present, doesn't actually change anything.
        // TODO: properly handle DiffException when we can do so.
        try {
            Patch<String> diff = DiffUtils.diff(expectedLines, actualLines);
            List<String> unifiedDiff =
                    DiffUtils.generateUnifiedDiff(
                            expectedFileName, actualFileName, expectedLines, diff, 3);
            StringBuilder diffOutput = new StringBuilder();
            for (String delta : unifiedDiff) {
                diffOutput.append(delta).append('\n');
            }
            return diffOutput.toString();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * An exception class to report an error that occurred during the execution of an ADB shell
     * command.
     */
    public static class AdbShellCommandException extends Exception {
        AdbShellCommandException(String format, Object... args) {
            super(String.format(format, args));
        }
    }

    /**
     * Retrieve a file off device and verify that file was transferred correctly by comparing the
     * sizes and MD5 digests of the original file (on device) and its (local) copy.
     *
     * <p>This method is essentially a wrapper around {@link
     * com.android.tradefed.device.INativeDevice#pullFile}, which has its own way to signal that a
     * file was retrieved successfully or not via its return value -- which is preserved in this
     * method. The additional checks, if they fail, are signaled via exceptions.
     *
     * @see com.android.tradefed.device.INativeDevice#pullFile
     * @param remoteFilePath The absolute path to file on device.
     * @param localFile The local file to store contents in. If non-empty, contents will be
     *     replaced.
     * @return <code>true</code> if file was retrieved successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException If connection with device is lost and cannot be
     *     recovered.
     * @throws AdbShellCommandException If any of the ADB shell commands used to extract information
     *     from the device failed.
     * @throws IOException If the file size check or the MD5 digest check failed.
     */
    private boolean pullAndCheckFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException, AdbShellCommandException, IOException {
        // Get the size of the remote file on device.
        long maxStatCmdTimeInMs = 10 * 1000; // 10 seconds.
        String statCmd = String.format("stat --format %%s %s", remoteFilePath);
        CommandResult statResult = executeAndCheckShellCommand(statCmd, maxStatCmdTimeInMs);
        String remoteFileSizeStr = statResult.getStdout().strip();
        long remoteFileSize = Long.parseLong(remoteFileSizeStr);
        CLog.d("Size of remote file `%s` is %d bytes", remoteFilePath, remoteFileSize);

        // Compute the MD5 digest of the remote file on device.
        long maxMd5sumCmdTimeInMs = 60 * 1000; // 1 minute.
        // Note: On Android, Toybox's implementation of `md5sum` interprets option `-b` as "emit a
        // brief output" ("hash only, no filename") -- which is the behavior we want here -- while
        // the GNU coreutils' implementation interprets option `-b` as "read input in binary mode".
        String md5sumCmd = String.format("md5sum -b %s", remoteFilePath);
        CommandResult md5sumResult = executeAndCheckShellCommand(md5sumCmd, maxMd5sumCmdTimeInMs);
        String remoteMd5Digest = md5sumResult.getStdout().strip();
        CLog.d("MD5 digest of remote file `%s` is %s", remoteFilePath, remoteMd5Digest);

        // Pull the file.
        boolean result = mDevice.pullFile(remoteFilePath, localFile);

        // Get the size of the local file.
        long localFileSize = localFile.length();
        CLog.d("Size of local file `%s` is %d bytes", localFile, localFileSize);

        // Compute the MD5 digest of the local file.
        String localMd5Digest = FileUtil.calculateMd5(localFile);
        CLog.d("MD5 digest of local file `%s` is %s", localFile, localMd5Digest);

        // Check that the size of local file matches the size of the remote file.
        if (localFileSize != remoteFileSize) {
            String message =
                    String.format(
                            "Size of local file `%s` does not match size of remote file `%s` "
                                    + "pulled from device: %d bytes vs %d bytes",
                            localFile, remoteFilePath, localFileSize, remoteFileSize);
            CLog.e(message);
            throw new IOException(message);
        }

        // Check that the MD5 digest of the local file matches the MD5 digest of the remote file.
        if (!localMd5Digest.equals(remoteMd5Digest)) {
            String message =
                    String.format(
                            "MD5 digest of local file `%s` does not match MD5 digest of remote "
                                    + "file `%s` pulled from device: %s vs %s",
                            localFile, remoteFilePath, localMd5Digest, remoteMd5Digest);
            CLog.e(message);
            throw new IOException(message);
        }

        return result;
    }

    /**
     * Helper function to execute an ADB shell command.
     *
     * @see com.android.tradefed.device.INativeDevice#executeShellV2Command
     * @param command The ADB shell command to run
     * @param maxTimeoutForCommandInMs The maximum timeout for the command to complete expressed in
     *     milliseconds.
     * @return The {@link CommandResult} object returned by the {@link #executeShellV2Command}
     *     invocation.
     * @throws DeviceNotAvailableException If connection with device is lost and cannot be
     *     recovered.
     * @throws AdbShellCommandException If the ADB shell command failed.
     */
    private CommandResult executeAndCheckShellCommand(
            String command, final long maxTimeoutForCommandInMs)
            throws DeviceNotAvailableException, AdbShellCommandException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        command,
                        maxTimeoutForCommandInMs,
                        TimeUnit.MILLISECONDS,
                        /* retryAttempts */ 0);
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            String message =
                    String.format(
                            "Command `%s` failed with status %s: %s",
                            command, result.getStatus(), result);
            CLog.e(message);
            throw new AdbShellCommandException(message);
        }
        return result;
    }

    /**
     * Create a temporary directory on device.
     *
     * @param template String from which the name of the directory is created. This template must
     *     include at least three consecutive <code>X</code>s in the last component (e.g.
     *     <code>tmp.XXX</code>).
     * @return The path to the created directory on device.
     * @throws DeviceNotAvailableException If connection with device is lost and cannot be
     *     recovered.
     * @throws AdbShellCommandException If the <code>mktemp</code> ADB shell command failed.
     */
    private String createTemporaryDirectoryOnDevice(String template)
            throws DeviceNotAvailableException, AdbShellCommandException {
        long maxMktempCmdTimeInMs = 10 * 1000; // 10 seconds.
        String mktempCmd = String.format("mktemp -d -p /data/local/tmp %s", template);
        CommandResult mktempResult = executeAndCheckShellCommand(mktempCmd, maxMktempCmdTimeInMs);
        return mktempResult.getStdout().strip();
    }
}
