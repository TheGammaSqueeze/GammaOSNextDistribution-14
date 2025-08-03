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
package com.android.tradefed.testtype.binary;

import com.google.common.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.observatory.IDiscoverDependencies;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.StreamUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Base class for executable style of tests. For example: binaries, shell scripts. */
public abstract class ExecutableBaseTest
        implements IRemoteTest,
                IRuntimeHintProvider,
                ITestCollector,
                IShardableTest,
                IAbiReceiver,
                ITestFilterReceiver,
                IDiscoverDependencies {

    public static final String NO_BINARY_ERROR = "Binary %s does not exist.";

    @Option(
            name = "per-binary-timeout",
            isTimeVal = true,
            description = "Timeout applied to each binary for their execution.")
    private long mTimeoutPerBinaryMs = 5 * 60 * 1000L;

    @Option(name = "binary", description = "Path to the binary to be run. Can be repeated.")
    private List<String> mBinaryPaths = new ArrayList<>();

    @Option(
            name = "test-command-line",
            description = "The test commands of each test names.",
            requiredForRerun = true)
    private Map<String, String> mTestCommands = new LinkedHashMap<>();

    @Option(
            name = "collect-tests-only",
            description = "Only dry-run through the tests, do not actually run them.")
    private boolean mCollectTestsOnly = false;

    @Option(
        name = "runtime-hint",
        description = "The hint about the test's runtime.",
        isTimeVal = true
    )
    private long mRuntimeHintMs = 60000L; // 1 minute

    enum ShardSplit {
        PER_TEST_CMD,
        PER_SHARD;
    }

    @Option(name = "shard-split", description = "Shard by test command or shard count")
    private ShardSplit mShardSplit = ShardSplit.PER_TEST_CMD;

    private IAbi mAbi;
    private TestInformation mTestInfo;
    private Set<String> mIncludeFilters = new LinkedHashSet<>();
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    /**
     * Get test commands.
     *
     * @return the test commands.
     */
    @VisibleForTesting
    Map<String, String> getTestCommands() {
        return mTestCommands;
    }

    /** @return the timeout applied to each binary for their execution. */
    protected long getTimeoutPerBinaryMs() {
        return mTimeoutPerBinaryMs;
    }

    protected String getModuleId(IInvocationContext context) {
        return context != null
                ? context.getAttributes().getUniqueMap().get(ModuleDefinition.MODULE_ID)
                : getClass().getName();
    }

    protected TestDescription[] getFilterDescriptions(Map<String, String> testCommands) {
        return testCommands.keySet().stream()
                .map(testName -> new TestDescription(testName, testName))
                .filter(description -> !shouldSkipCurrentTest(description))
                .toArray(TestDescription[]::new);
    }

    protected boolean doesRunBinaryGenerateTestResults() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
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
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        setTestInfo(testInfo);
        String moduleId = getModuleId(testInfo.getContext());
        Map<String, String> testCommands = getAllTestCommands();
        TestDescription[] testDescriptions = getFilterDescriptions(testCommands);

        if (testDescriptions.length == 0) {
            return;
        }

        String testRunName =
                testDescriptions.length == 1 ? testDescriptions[0].getTestName() : moduleId;
        long startTimeMs = System.currentTimeMillis();

        try {
            listener.testRunStarted(testRunName, testDescriptions.length);

            for (TestDescription description : testDescriptions) {
                String testName = description.getTestName();
                String cmd = testCommands.get(testName);
                String path = findBinary(cmd);
                try {
                    if (path == null) {
                        listener.testFailed(
                                description,
                                FailureDescription.create(
                                                String.format(NO_BINARY_ERROR, cmd),
                                                FailureStatus.TEST_FAILURE)
                                        .setErrorIdentifier(
                                                InfraErrorIdentifier
                                                        .CONFIGURED_ARTIFACT_NOT_FOUND));
                    } else {
                        try {
                            if (!doesRunBinaryGenerateTestResults()) {
                                listener.testStarted(description);
                            }

                            if (!getCollectTestsOnly()) {
                                // Do not actually run the test if we are dry running it.
                                runBinary(path, listener, description);
                            }
                        } catch (IOException e) {
                            listener.testFailed(
                                    description,
                                    FailureDescription.create(StreamUtil.getStackTrace(e)));
                            if (doesRunBinaryGenerateTestResults()) {
                                // We can't rely on the `testEnded()` call in the finally
                                // clause if `runBinary()` is responsible for generating test
                                // results, therefore we call it here.
                                listener.testEnded(description, new HashMap<String, Metric>());
                            }
                        }
                    }
                } finally {
                    if (!doesRunBinaryGenerateTestResults()) {
                        listener.testEnded(description, new HashMap<String, Metric>());
                    }
                }
            }
        } finally {
            listener.testRunEnded(
                    System.currentTimeMillis() - startTimeMs, new HashMap<String, Metric>());
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
        if (mExcludeFilters.contains(testName)
                || mExcludeFilters.contains(description.toString())) {
            return true;
        }
        if (!mIncludeFilters.isEmpty()) {
            return !mIncludeFilters.contains(testName)
                    && !mIncludeFilters.contains(description.toString());
        }
        return false;
    }

    /**
     * Search for the binary to be able to run it.
     *
     * @param binary the path of the binary or simply the binary name.
     * @return The path to the binary, or null if not found.
     */
    public abstract String findBinary(String binary) throws DeviceNotAvailableException;

    /**
     * Actually run the binary at the given path.
     *
     * @param binaryPath The path of the binary.
     * @param listener The listener where to report the results.
     * @param description The test in progress.
     */
    public abstract void runBinary(
            String binaryPath, ITestInvocationListener listener, TestDescription description)
            throws DeviceNotAvailableException, IOException;

    /** {@inheritDoc} */
    @Override
    public final void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    public boolean getCollectTestsOnly() {
        return mCollectTestsOnly;
    }

    /** {@inheritDoc} */
    @Override
    public final long getRuntimeHint() {
        return mRuntimeHintMs;
    }

    /** {@inheritDoc} */
    @Override
    public final void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /** {@inheritDoc} */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    TestInformation getTestInfo() {
        return mTestInfo;
    }

    void setTestInfo(TestInformation testInfo) {
        mTestInfo = testInfo;
    }

    /** {@inheritDoc} */
    @Override
    public final Collection<IRemoteTest> split(int shardHint) {
        if (shardHint <= 1) {
            return null;
        }
        int testCount = mBinaryPaths.size() + mTestCommands.size();
        if (testCount <= 2) {
            return null;
        }

        if (mShardSplit == ShardSplit.PER_TEST_CMD) {
            return splitByTestCommand();
        } else if (mShardSplit == ShardSplit.PER_SHARD) {
            return splitByShardCount(testCount, shardHint);
        }

        return null;
    }

    private Collection<IRemoteTest> splitByTestCommand() {
        Collection<IRemoteTest> tests = new ArrayList<>();
        for (String path : mBinaryPaths) {
            tests.add(getTestShard(ImmutableList.of(path), null));
        }
        Map<String, String> testCommands = new LinkedHashMap<>(mTestCommands);
        for (String testName : testCommands.keySet()) {
            String cmd = testCommands.get(testName);
            tests.add(getTestShard(null, ImmutableMap.of(testName, cmd)));
        }
        return tests;
    }

    private Collection<IRemoteTest> splitByShardCount(int testCount, int shardCount) {
        int maxTestCntPerShard = (int) Math.ceil((double) testCount / shardCount);
        int numFullSizeShards = testCount % maxTestCntPerShard;
        List<Map.Entry<String, String>> testCommands = new ArrayList<>(mTestCommands.entrySet());

        int runningTestCount = 0;
        int runningTestCountInShard = 0;

        Collection<IRemoteTest> tests = new ArrayList<>();
        List<String> binaryPathsInShard = new ArrayList<String>();
        HashMap<String, String> testCommandsInShard = new HashMap<String, String>();
        while (runningTestCount < testCount) {
            if (runningTestCount < mBinaryPaths.size()) {
                binaryPathsInShard.add(mBinaryPaths.get(runningTestCount));
            } else {
                Map.Entry<String, String> entry =
                        testCommands.get(runningTestCount - mBinaryPaths.size());
                testCommandsInShard.put(entry.getKey(), entry.getValue());
            }
            ++runningTestCountInShard;

            if ((tests.size() < numFullSizeShards && runningTestCountInShard == maxTestCntPerShard)
                    || (tests.size() >= numFullSizeShards
                            && (runningTestCountInShard >= (maxTestCntPerShard - 1)))) {
                tests.add(getTestShard(binaryPathsInShard, testCommandsInShard));
                binaryPathsInShard.clear();
                testCommandsInShard.clear();
                runningTestCountInShard = 0;
            }
            ++runningTestCount;
        }
        return tests;
    }

    /**
     * Get a testShard of ExecutableBaseTest.
     *
     * @param binaryPath the binary path for ExecutableHostTest.
     * @param testName the test name for ExecutableTargetTest.
     * @param cmd the test command for ExecutableTargetTest.
     * @return a shard{@link IRemoteTest} of ExecutableBaseTest{@link ExecutableBaseTest}
     */
    private IRemoteTest getTestShard(List<String> binaryPaths, Map<String, String> testCmds) {
        ExecutableBaseTest shard = null;
        try {
            shard = this.getClass().getDeclaredConstructor().newInstance();
            OptionCopier.copyOptionsNoThrow(this, shard);
            shard.mBinaryPaths.clear();
            shard.mTestCommands.clear();
            if (binaryPaths != null) {
                for (String binaryPath : binaryPaths) {
                    shard.mBinaryPaths.add(binaryPath);
                }
            }
            if (testCmds != null) {
                for (Map.Entry<String, String> entry : testCmds.entrySet()) {
                    shard.mTestCommands.put(entry.getKey(), entry.getValue());
                }
            }
            // Copy the filters to each shard
            shard.mExcludeFilters.addAll(mExcludeFilters);
            shard.mIncludeFilters.addAll(mIncludeFilters);
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            // This cannot happen because the class was already created once at that point.
            throw new RuntimeException(
                    String.format(
                            "%s (%s) when attempting to create shard object",
                            e.getClass().getSimpleName(), e.getMessage()));
        }
        return shard;
    }

    /**
     * Convert mBinaryPaths to mTestCommands for consistency.
     *
     * @return a Map{@link LinkedHashMap}<String, String> of testCommands.
     */
    @VisibleForTesting
    Map<String, String> getAllTestCommands() {
        Map<String, String> testCommands = new LinkedHashMap<>(mTestCommands);
        for (String binary : mBinaryPaths) {
            testCommands.put(new File(binary).getName(), binary);
        }
        return testCommands;
    }

    @Override
    public Set<String> reportDependencies() {
        Set<String> deps = new HashSet<String>();
        deps.addAll(mBinaryPaths);
        return deps;
    }
}
