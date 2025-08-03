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
package com.android.uicd.tests;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.result.proto.TestRecordProto;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TestRecordInterpreter;
import com.android.tradefed.util.proto.TestRecordProtoUtil;

import com.google.common.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Runs pre-recorded Android UIConductor tests in Tradefed. Each provided JSON file is treated as a
 * test case. Supports automatic retries, including file-based retries across invocations using
 * {@link UiConductorTest.ResultReporter}. See XML configurations in res/config/uicd for examples.
 *
 * <p>See Also: https://github.com/google/android-uiconductor
 * https://console.cloud.google.com/storage/browser/uicd-deps
 */
@OptionClass(alias = "uicd")
public class UiConductorTest implements IRemoteTest, ITestFilterReceiver {

    static final String MODULE_NAME = UiConductorTest.class.getSimpleName();
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30L);
    static final String DEFAULT_OUTPUT_PATH = "uicd_results.pb";

    static final String INPUT_OPTION = "--input";
    static final String OUTPUT_OPTION = "--output";
    static final String DEVICES_OPTION = "--devices";
    static final String MODE_OPTION = "--mode";
    static final String GLOBAL_VARIABLE_OPTION = "--global_variable";

    static final String TEST_RESULT_PATH = "result/action_execution_result";

    /** Testing mode. */
    public enum PlayMode {
        SINGLE,
        MULTIDEVICE,
        PLAYALL,
    }

    /** Test case information, contains the test file and its metadata. */
    private static class UiConductorTestCase {
        private final String mId;
        private final String mKey;
        private final File mFile;
        private final TestDescription mDesc;

        private UiConductorTestCase(String id, String key, File file) {
            mId = id;
            mKey = key;
            mFile = file;
            mDesc = new TestDescription(MODULE_NAME, mId);
        }
    }

    @Option(name = "work-dir", description = "Optional work directory to use")
    private File mWorkDir;

    @Option(
            name = "uicd-cli-jar",
            description = "UICD CLI jar to use when running tests",
            mandatory = true)
    private File mCliJar;

    @Option(
            name = "commandline-action-executable",
            description = "Additional binaries needed by command line actions. Can be repeated.")
    private Collection<File> mBinaries = new ArrayList<>();

    @Option(
            name = "global-variables",
            description = "Global variable (uicd_key1=value1,uicd_key2=value2)")
    private MultiMap<String, String> mGlobalVariables = new MultiMap<>();

    @Option(name = "play-mode", description = "Play mode (SINGLE|MULTIDEVICE|PLAYALL)")
    private PlayMode mPlayMode = PlayMode.SINGLE;

    // Same key can have multiple test files because global-variables can be referenced using the
    // that particular key and shared across different tests.
    // Refer res/config/uicd/uiconductor-globalvariable-sample.xml for more information.
    @Option(
            name = "uicd-test",
            description = "JSON test file or directory of JSON test files to run. Can be repeated.",
            mandatory = true)
    private MultiMap<String, File> mTests = new MultiMap<>();

    @Option(name = "test-timeout", description = "Timeout for each test case")
    private Duration mTestTimeout = DEFAULT_TIMEOUT;

    @Option(name = "include-filter", description = "Regex filters used to find tests to include")
    private Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = "exclude-filter", description = "Regex filters used to find tests to exclude")
    private Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = "previous-results", description = "Previous output file to load when retrying")
    private File mPreviousResults;

    private IRunUtil mRunUtil;
    private Path mOutputDir;

    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (!mCliJar.isFile()) {
            throw new IllegalArgumentException(
                    String.format("UICD CLI jar %s not found", mCliJar.getAbsolutePath()));
        }

        // Load and process previous results
        CollectingTestListener previousResults = this.parsePreviousResults();
        if (previousResults != null) {
            CLog.i("Loading previous results from %s", mPreviousResults);
            this.loadPreviousResults(listener, previousResults);
        }

        // Find test cases to execute
        List<UiConductorTestCase> testCases = new ArrayList<>();
        for (Map.Entry<String, File> entry : mTests.entries()) {
            String key = entry.getKey();
            File file = entry.getValue();
            testCases.addAll(getTestCases(key, file));
        }

        // Create work directory and copy binaries into it
        if (mWorkDir == null) {
            mWorkDir = createWorkDir().toFile();
        }
        mRunUtil = createRunUtil();
        mRunUtil.setWorkingDir(mWorkDir);
        for (File binary : mBinaries) {
            Path copiedBinary = copyFile(binary.toPath(), mWorkDir.toPath());
            copiedBinary.toFile().setExecutable(true);
        }
        mOutputDir = mWorkDir.toPath().resolve("output");

        // Execute test cases
        for (UiConductorTestCase testCase : testCases) {
            if (!shouldRunTestCase(testCase)) {
                CLog.d("Skipping %s", testCase.mDesc);
                continue;
            }
            // TODO(b/186141354): Revert to one module once ATS supports detailed proto results
            long runStartTime = System.currentTimeMillis();
            listener.testRunStarted(testCase.mDesc.toString(), 1);
            runTestCase(listener, testCase, testInfo.getDevices());
            listener.testRunEnded(System.currentTimeMillis() - runStartTime, Map.of());
        }
    }

    /** @return {@link IRunUtil} instance to use */
    @VisibleForTesting
    IRunUtil createRunUtil() {
        return new RunUtil();
    }

    /** @return temporary working directory to use if none is provided */
    private Path createWorkDir() {
        try {
            return FileUtil.createTempDir(MODULE_NAME, CurrentInvocation.getWorkFolder()).toPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return true if the test case should be executed */
    private boolean shouldRunTestCase(UiConductorTestCase testCase) {
        String testId = testCase.mDesc.toString();
        if (mExcludeFilters.stream().anyMatch(testId::matches)) {
            return false;
        }
        return mIncludeFilters.isEmpty() || mIncludeFilters.stream().anyMatch(testId::matches);
    }

    /** Execute a test case using the UICD CLI and parses the result. */
    private void runTestCase(
            ITestInvocationListener listener,
            UiConductorTestCase testCase,
            List<ITestDevice> devices) {
        listener.testStarted(testCase.mDesc, System.currentTimeMillis());

        // Execute the UICD command and handle the result
        String[] command = buildCommand(testCase, devices);
        CLog.i("Running %s (command: %s)", testCase.mDesc, Arrays.asList(command));
        CommandResult result = mRunUtil.runTimedCmd(mTestTimeout.toMillis(), command);
        switch (result.getStatus()) {
            case SUCCESS:
                CLog.i(
                        "Command succeeded, stdout = [%s], stderr = [%s].",
                        result.getStdout(), result.getStderr());
                Path resultFile = mOutputDir.resolve(testCase.mId).resolve(TEST_RESULT_PATH);
                verifyTestResultFile(listener, testCase, resultFile.toFile());
                break;
            case FAILED:
            case EXCEPTION:
                CLog.e(
                        "Command failed, stdout = [%s], stderr = [%s].",
                        result.getStdout(), result.getStderr());
                listener.testFailed(testCase.mDesc, "Command failed");
                break;
            case TIMED_OUT:
                CLog.e(
                        "Command timed out, stdout = [%s], stderr = [%s].",
                        result.getStdout(), result.getStderr());
                listener.testFailed(testCase.mDesc, "Command timed out");
                break;
        }

        listener.testEnded(testCase.mDesc, System.currentTimeMillis(), Map.of());
    }

    /** Parse a test result file and report test failures. */
    private void verifyTestResultFile(
            ITestInvocationListener listener, UiConductorTestCase testCase, File resultFile) {
        if (!resultFile.isFile()) {
            listener.testFailed(
                    testCase.mDesc, String.format("Test result file %s not found", resultFile));
            return;
        }

        try {
            String resultContent = FileUtil.readStringFromFile(resultFile);
            List<String> errors = parseTestResultJson(new JSONObject(resultContent));
            if (!errors.isEmpty()) {
                listener.testFailed(testCase.mDesc, String.join("\n", errors));
            }
        } catch (IOException | JSONException e) {
            CLog.e("Failed to parse test result file", e);
            listener.testFailed(
                    testCase.mDesc,
                    String.format("Failed to parse test result file: %s", e.getMessage()));
        }
        try (FileInputStreamSource inputStream = new FileInputStreamSource(resultFile)) {
            listener.testLog(testCase.mId + "_result", LogDataType.TEXT, inputStream);
        }
    }

    /** Recursively parses the test result JSON, looking for failures. */
    private List<String> parseTestResultJson(JSONObject result) {
        if (result == null) {
            return List.of();
        }

        List<String> errors = new ArrayList<>();
        JSONArray childrenResult = result.optJSONArray("childrenResult");
        if (childrenResult != null) {
            for (int i = 0; i < childrenResult.length(); i++) {
                errors.addAll(parseTestResultJson(childrenResult.optJSONObject(i)));
            }
        }
        if ("FAIL".equalsIgnoreCase(result.optString("playStatus"))) {
            String error =
                    String.format(
                            "%s (%s): %s",
                            result.optString("actionId"),
                            result.optString("content"),
                            result.optString("validationDetails"));
            errors.add(error);
        }
        return errors;
    }

    /**
     * Copy a file into a directory.
     *
     * @param srcFile file to copy
     * @param destDir directory to copy into
     * @return copied file
     */
    private Path copyFile(Path srcFile, Path destDir) {
        try {
            Files.createDirectories(destDir);
            Path destFile = destDir.resolve(srcFile.getFileName());
            return Files.copy(srcFile, destFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Find all test cases in the specified file or directory.
     *
     * @param key test key to associate with test cases
     * @param file file or directory to look in
     * @return list of test cases
     */
    private List<UiConductorTestCase> getTestCases(String key, File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException(
                    String.format("Test file %s not found", file.getAbsolutePath()));
        }
        if (file.isDirectory()) {
            try {
                // Find all nested regular files and use their relative paths as IDs
                Path dirPath = file.toPath().toAbsolutePath();
                try (Stream<Path> stream = Files.walk(dirPath)) {
                    return stream.filter(Files::isRegularFile)
                            .sorted()
                            .map(
                                    filePath -> {
                                        String id =
                                                dirPath.getParent().relativize(filePath).toString();
                                        return new UiConductorTestCase(id, key, filePath.toFile());
                                    })
                            .collect(Collectors.toList());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        // Normal file, use filename as ID
        return List.of(new UiConductorTestCase(file.getName(), key, file));
    }

    /** Constructs the command to execute for a test case. */
    private String[] buildCommand(UiConductorTestCase testCase, List<ITestDevice> devices) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(mCliJar.getAbsolutePath());
        // Add input file path
        command.add(INPUT_OPTION);
        command.add(testCase.mFile.getAbsolutePath());
        // Add output directory path
        command.add(OUTPUT_OPTION);
        command.add(mOutputDir.resolve(testCase.mId).toString());
        // Add play mode
        command.add(MODE_OPTION);
        command.add(mPlayMode.name());
        // Add device serial numbers (comma separated list)
        command.add(DEVICES_OPTION);
        String serials =
                devices.stream().map(ITestDevice::getSerialNumber).collect(Collectors.joining(","));
        command.add(serials);
        // Add global variables if applicable
        if (mGlobalVariables.containsKey(testCase.mKey)) {
            command.add(GLOBAL_VARIABLE_OPTION);
            command.add(String.join(",", mGlobalVariables.get(testCase.mKey)));
        }
        return command.toArray(new String[] {});
    }

    /**
     * Try to locate and parse an existing output file.
     *
     * @return listener containing the results or {@code null} if not found.
     */
    @Nullable
    private CollectingTestListener parsePreviousResults() {
        if (mPreviousResults == null) {
            return null;
        }
        if (!mPreviousResults.isFile()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Previous results %s not found", mPreviousResults.getAbsolutePath()));
        }

        try {
            TestRecordProto.TestRecord record = TestRecordProtoUtil.readFromFile(mPreviousResults);
            return TestRecordInterpreter.interpreteRecord(record);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Iterate over previous results to add them to the current run and exclude passed tests. */
    private void loadPreviousResults(
            ITestInvocationListener listener, CollectingTestListener results) {
        results.getMergedTestRunResults().stream()
                .filter(module -> module.getName().startsWith(MODULE_NAME + '#'))
                .forEach(
                        module -> {
                            // Found a previous result for this module, replay it
                            Map<TestDescription, TestResult> tests = module.getTestResults();
                            listener.testRunStarted(module.getName(), tests.size());
                            tests.forEach(
                                    (test, result) -> {
                                        listener.testStarted(test, result.getStartTime());
                                        if (result.getStatus() == TestStatus.FAILURE) {
                                            listener.testFailed(test, result.getStackTrace());
                                        } else {
                                            // Only the PASSED and FAILURE test statuses are used,
                                            // so exclude all non-FAILURE tests.
                                            this.addExcludeFilter(test.toString());
                                        }
                                        listener.testEnded(test, result.getEndTime(), Map.of());
                                    });
                            listener.testRunEnded(module.getElapsedTime(), Map.of());
                        });
    }

    /** Writes results to a uicd_results.pb file which can be used for file-based retries. */
    @OptionClass(alias = "uicd")
    public static class ResultReporter extends FileProtoResultReporter {

        @Option(name = "output-path", description = "Output file path, can be used for retries")
        private String mOutputPath = DEFAULT_OUTPUT_PATH;

        private File mOutputFile;

        @Override
        public void processStartInvocation(
                TestRecordProto.TestRecord record, IInvocationContext context) {
            mOutputFile = new File(mOutputPath + ".tmp").getAbsoluteFile();
            setFileOutput(mOutputFile);
            super.processStartInvocation(record, context);
        }

        @Override
        public void processFinalProto(TestRecordProto.TestRecord record) {
            super.processFinalProto(record);
            mOutputFile.renameTo(new File(mOutputPath));
        }
    }
}
