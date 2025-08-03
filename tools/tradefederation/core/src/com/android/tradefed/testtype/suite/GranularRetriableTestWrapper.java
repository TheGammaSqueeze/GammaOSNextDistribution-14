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

package com.android.tradefed.testtype.suite;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.metric.CollectorHelper;
import com.android.tradefed.device.metric.CountTestCasesCollector;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.IsolationGrade;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultAndLogForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.TestStatus;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.retry.IRetryDecision;
import com.android.tradefed.retry.MergeStrategy;
import com.android.tradefed.retry.RetryLogSaverResultForwarder;
import com.android.tradefed.retry.RetryStatistics;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper class works on the {@link IRemoteTest} to granulate the IRemoteTest in testcase level.
 * An IRemoteTest can contain multiple testcases. Previously, these testcases are treated as a
 * whole: When IRemoteTest runs, all testcases will run. Some IRemoteTest (The ones that implements
 * ITestFilterReceiver) can accept an allowlist of testcases and only run those testcases. This
 * class takes advantage of the existing feature and provides a more flexible way to run test suite.
 *
 * <ul>
 *   <li>Single testcase can be retried multiple times (within the same IRemoteTest run) to reduce
 *       the non-test-error failure rates.
 *   <li>The retried testcases are dynamically collected from previous run failures.
 * </ul>
 *
 * <p>Note:
 *
 * <ul>
 *   <li>The prerequisite to run a subset of test cases is that the test type should implement the
 *       interface {@link ITestFilterReceiver}.
 *   <li>X is customized max retry number.
 * </ul>
 */
public class GranularRetriableTestWrapper implements IRemoteTest, ITestCollector {

    private IRetryDecision mRetryDecision;
    private IRemoteTest mTest;
    private ModuleDefinition mModule;
    private List<IMetricCollector> mRunMetricCollectors;
    private TestFailureListener mFailureListener;
    private IInvocationContext mModuleInvocationContext;
    private IConfiguration mModuleConfiguration;
    private ModuleListener mMainGranularRunListener;
    private RetryLogSaverResultForwarder mRetryAttemptForwarder;
    private List<ITestInvocationListener> mModuleLevelListeners;
    private ITestInvocationListener mRemoteTestTimeOutEnforcer;
    private ILogSaver mLogSaver;
    private String mModuleId;
    private int mMaxRunLimit;

    private boolean mCollectTestsOnly = false;

    // Tracking of the metrics
    private RetryStatistics mRetryStats = null;
    private int mCountRetryUsed = 0;

    public GranularRetriableTestWrapper(
            IRemoteTest test,
            ITestInvocationListener mainListener,
            TestFailureListener failureListener,
            List<ITestInvocationListener> moduleLevelListeners,
            int maxRunLimit) {
        this(test, null, mainListener, failureListener, moduleLevelListeners, maxRunLimit);
    }

    public GranularRetriableTestWrapper(
            IRemoteTest test,
            ModuleDefinition module,
            ITestInvocationListener mainListener,
            TestFailureListener failureListener,
            List<ITestInvocationListener> moduleLevelListeners,
            int maxRunLimit) {
        mTest = test;
        mModule = module;
        IInvocationContext context = null;
        if (module != null) {
            context = module.getModuleInvocationContext();
        }
        initializeGranularRunListener(mainListener, context);
        mFailureListener = failureListener;
        mModuleLevelListeners = moduleLevelListeners;
        mMaxRunLimit = maxRunLimit;
    }

    /** Sets the {@link IRetryDecision} to be used. */
    public void setRetryDecision(IRetryDecision decision) {
        mRetryDecision = decision;
    }

    /**
     * Set the {@link ModuleDefinition} name as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param moduleId the name of the moduleDefinition.
     */
    public void setModuleId(String moduleId) {
        mModuleId = moduleId;
    }

    /**
     * Set the {@link ModuleDefinition} RunStrategy as a {@link GranularRetriableTestWrapper}
     * attribute.
     *
     * @param skipTestCases whether the testcases should be skipped.
     */
    public void setMarkTestsSkipped(boolean skipTestCases) {
        mMainGranularRunListener.setMarkTestsSkipped(skipTestCases);
    }

    /**
     * Set the {@link ModuleDefinition}'s runMetricCollector as a {@link
     * GranularRetriableTestWrapper} attribute.
     *
     * @param runMetricCollectors A list of MetricCollector for the module.
     */
    public void setMetricCollectors(List<IMetricCollector> runMetricCollectors) {
        mRunMetricCollectors = runMetricCollectors;
    }

    /**
     * Set the {@link ModuleDefinition}'s ModuleConfig as a {@link GranularRetriableTestWrapper}
     * attribute.
     *
     * @param moduleConfiguration Provide the module metrics.
     */
    public void setModuleConfig(IConfiguration moduleConfiguration) {
        mModuleConfiguration = moduleConfiguration;
    }

    /**
     * Set the {@link IInvocationContext} as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param moduleInvocationContext The wrapper uses the InvocationContext to initialize the
     *     MetricCollector when necessary.
     */
    public void setInvocationContext(IInvocationContext moduleInvocationContext) {
        mModuleInvocationContext = moduleInvocationContext;
    }

    /**
     * Set the Module's {@link ILogSaver} as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param logSaver The listeners for each test run should save the logs.
     */
    public void setLogSaver(ILogSaver logSaver) {
        mLogSaver = logSaver;
    }

    /**
     * Initialize granular run listener with {@link RemoteTestTimeOutEnforcer} if timeout is set.
     * And set the test-mapping sources in granular run listener.
     *
     * @param listener The listener for each test run should be wrapped.
     * @param moduleContext the invocation context of the module
     */
    private void initializeGranularRunListener(
            ITestInvocationListener listener, IInvocationContext moduleContext) {
        mMainGranularRunListener = new ModuleListener(listener, moduleContext);
        if (mModule != null) {
            ConfigurationDescriptor configDesc =
                    mModule.getModuleInvocationContext().getConfigurationDescriptor();
            if (configDesc.getMetaData(
                    RemoteTestTimeOutEnforcer.REMOTE_TEST_TIMEOUT_OPTION) != null) {
                Duration duration = Duration.parse(
                        configDesc.getMetaData(
                                RemoteTestTimeOutEnforcer.REMOTE_TEST_TIMEOUT_OPTION).get(0));
                mRemoteTestTimeOutEnforcer = new RemoteTestTimeOutEnforcer(
                        mMainGranularRunListener, mModule, mTest, duration);
            }
            List<String> testMappingSources =
                    configDesc.getMetaData(Integer.toString(mTest.hashCode()));
            if (testMappingSources != null) {
                mMainGranularRunListener.setTestMappingSources(testMappingSources);
            }
        }
    }

    /**
     * Initialize a new {@link ModuleListener} for each test run.
     *
     * @return a {@link ITestInvocationListener} listener which contains the new {@link
     *     ModuleListener}, the main {@link ITestInvocationListener} and main {@link
     *     TestFailureListener}, and wrapped by RunMetricsCollector and Module MetricCollector (if
     *     not initialized).
     */
    private ITestInvocationListener initializeListeners() throws DeviceNotAvailableException {
        List<ITestInvocationListener> currentTestListener = new ArrayList<>();
        // Add all the module level listeners, including TestFailureListener
        if (mModuleLevelListeners != null) {
            currentTestListener.addAll(mModuleLevelListeners);
        }
        currentTestListener.add(mMainGranularRunListener);

        if (mRemoteTestTimeOutEnforcer != null) {
            currentTestListener.add(mRemoteTestTimeOutEnforcer);
        }

        mRetryAttemptForwarder = new RetryLogSaverResultForwarder(mLogSaver, currentTestListener);
        ITestInvocationListener runListener = mRetryAttemptForwarder;
        if (mFailureListener != null) {
            mFailureListener.setLogger(mRetryAttemptForwarder);
            currentTestListener.add(mFailureListener);
        }

        // The module collectors itself are added: this list will be very limited.
        // We clone them since the configuration object is shared across shards.
        for (IMetricCollector collector :
                CollectorHelper.cloneCollectors(mModuleConfiguration.getMetricCollectors())) {
            if (collector.isDisabled()) {
                CLog.d("%s has been disabled. Skipping.", collector);
            } else {
                try (CloseableTraceScope ignored =
                        new CloseableTraceScope(
                                "init_attempt_" + collector.getClass().getSimpleName())) {
                    if (collector instanceof IConfigurationReceiver) {
                        ((IConfigurationReceiver) collector).setConfiguration(mModuleConfiguration);
                    }
                    runListener = collector.init(mModuleInvocationContext, runListener);
                }
            }
        }

        return runListener;
    }

    /**
     * Schedule a series of {@link IRemoteTest#run(TestInformation, ITestInvocationListener)}.
     *
     * @param listener The ResultForwarder listener which contains a new moduleListener for each
     *     run.
     */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mMainGranularRunListener.setCollectTestsOnly(mCollectTestsOnly);
        ITestInvocationListener allListeners = initializeListeners();
        // First do the regular run, not retried.
        DeviceNotAvailableException dnae = intraModuleRun(testInfo, allListeners, 0);

        if (mMaxRunLimit <= 1) {
            // TODO: If module is the last one and there is no retry quota, it won't need to do
            //  device recovery.
            if (dnae == null || !mModule.shouldRecoverVirtualDevice()) {
                if (dnae != null) {
                    throw dnae;
                }
                return;
            }
        }

        if (mRetryDecision == null) {
            CLog.e("RetryDecision is null. Something is misconfigured this shouldn't happen");
            return;
        }

        // Bail out early if there is no need to retry at all.
        if (!mRetryDecision.shouldRetry(
                mTest, mModule, 0, mMainGranularRunListener.getTestRunForAttempts(0), dnae)) {
            return;
        }

        // Avoid rechecking the shouldRetry below the first time as it could retrigger reboot.
        boolean firstCheck = true;

        // Deal with retried attempted
        long startTime = System.currentTimeMillis();
        try {
            CLog.d("Starting intra-module retry.");
            for (int attemptNumber = 1; attemptNumber < mMaxRunLimit; attemptNumber++) {
                if (!firstCheck) {
                    boolean retry =
                            mRetryDecision.shouldRetry(
                                    mTest,
                                    mModule,
                                    attemptNumber - 1,
                                    mMainGranularRunListener.getTestRunForAttempts(
                                            attemptNumber - 1),
                                    dnae);
                    if (!retry) {
                        return;
                    }
                }
                firstCheck = false;
                mCountRetryUsed++;
                CLog.d("Intra-module retry attempt number %s", attemptNumber);
                // Run the tests again
                dnae = intraModuleRun(testInfo, allListeners, attemptNumber);
            }
            // Feed the last attempt if we reached here.
            mRetryDecision.addLastAttempt(
                    mMainGranularRunListener.getTestRunForAttempts(mMaxRunLimit - 1));
        } finally {
            mRetryStats = mRetryDecision.getRetryStatistics();
            // Track how long we spend in retry
            mRetryStats.mRetryTime = System.currentTimeMillis() - startTime;
        }
    }

    /**
     * The workflow for each individual {@link IRemoteTest} run.
     *
     * @return DeviceNotAvailableException while DNAE happened, null otherwise.
     */
    private final DeviceNotAvailableException intraModuleRun(
            TestInformation testInfo, ITestInvocationListener runListener, int attempt) {
        DeviceNotAvailableException exception = null;
        mMainGranularRunListener.setAttemptIsolation(CurrentInvocation.runCurrentIsolation());
        StartEndCollector startEndCollector = new StartEndCollector(runListener);
        runListener = startEndCollector;
        try (CloseableTraceScope ignored =
                new CloseableTraceScope(
                        "attempt " + attempt + " " + mTest.getClass().getCanonicalName())) {
            List<IMetricCollector> clonedCollectors = cloneCollectors(mRunMetricCollectors);
            if (mTest instanceof IMetricCollectorReceiver) {
                ((IMetricCollectorReceiver) mTest).setMetricCollectors(clonedCollectors);
                // If test can receive collectors then let it handle how to set them up
                mTest.run(testInfo, runListener);
            } else {
                if (mModuleConfiguration.getCommandOptions().reportTestCaseCount()) {
                    CountTestCasesCollector counter = new CountTestCasesCollector(mTest);
                    clonedCollectors.add(counter);
                }
                // Module only init the collectors here to avoid triggering the collectors when
                // replaying the cached events at the end. This ensures metrics are capture at
                // the proper time in the invocation.
                for (IMetricCollector collector : clonedCollectors) {
                    if (collector.isDisabled()) {
                        CLog.d("%s has been disabled. Skipping.", collector);
                    } else {
                        try (CloseableTraceScope ignoreCollector =
                                new CloseableTraceScope(
                                        "init_run_" + collector.getClass().getSimpleName())) {
                            if (collector instanceof IConfigurationReceiver) {
                                ((IConfigurationReceiver) collector)
                                        .setConfiguration(mModuleConfiguration);
                            }
                            runListener = collector.init(mModuleInvocationContext, runListener);
                        }
                    }
                }
                mTest.run(testInfo, runListener);
            }
        } catch (RuntimeException | AssertionError re) {
            CLog.e("Module '%s' - test '%s' threw exception:", mModuleId, mTest.getClass());
            CLog.e(re);
            CLog.e("Proceeding to the next test.");
            if (!startEndCollector.mRunStartReported) {
                CLog.e("Event mismatch ! the test runner didn't report any testRunStart.");
                runListener.testRunStarted(mModule.getId(), 0);
            }
            runListener.testRunFailed(createFromException(re));
            if (!startEndCollector.mRunEndedReported) {
                CLog.e("Event mismatch ! the test runner didn't report any testRunEnded.");
                runListener.testRunEnded(0L, new HashMap<String, Metric>());
            }
        } catch (DeviceUnresponsiveException due) {
            // being able to catch a DeviceUnresponsiveException here implies that recovery was
            // successful, and test execution should proceed to next module.
            CLog.w(
                    "Ignored DeviceUnresponsiveException because recovery was "
                            + "successful, proceeding with next module. Stack trace:");
            CLog.w(due);
            CLog.w("Proceeding to the next test.");
            // If it already was marked as failure do not remark it.
            if (!mMainGranularRunListener.hasLastAttemptFailed()) {
                runListener.testRunFailed(createFromException(due));
            }
        } catch (DeviceNotAvailableException dnae) {
            // TODO: See if it's possible to report IReportNotExecuted
            CLog.e("Run in progress was not completed due to:");
            CLog.e(dnae);
            // If it already was marked as failure do not remark it.
            if (!mMainGranularRunListener.hasLastAttemptFailed()) {
                runListener.testRunFailed(createFromException(dnae));
            }
            exception = dnae;
        } finally {
            mRetryAttemptForwarder.incrementAttempt();
            // After one run, do not consider follow up isolated without action.
            CurrentInvocation.setRunIsolation(IsolationGrade.NOT_ISOLATED);
        }
        return exception;
    }

    /** Get the merged TestRunResults from each {@link IRemoteTest} run. */
    public final List<TestRunResult> getFinalTestRunResults() {
        MergeStrategy strategy = MergeStrategy.getMergeStrategy(mRetryDecision.getRetryStrategy());
        mMainGranularRunListener.setMergeStrategy(strategy);
        return mMainGranularRunListener.getMergedTestRunResults();
    }

    @VisibleForTesting
    Map<String, List<TestRunResult>> getTestRunResultCollected() {
        Map<String, List<TestRunResult>> runResultMap = new LinkedHashMap<>();
        for (String runName : mMainGranularRunListener.getTestRunNames()) {
            runResultMap.put(runName, mMainGranularRunListener.getTestRunAttempts(runName));
        }
        return runResultMap;
    }

    @VisibleForTesting
    List<IMetricCollector> cloneCollectors(List<IMetricCollector> originalCollectors) {
        return CollectorHelper.cloneCollectors(originalCollectors);
    }

    /**
     * Calculate the number of testcases in the {@link IRemoteTest}. This value distincts the same
     * testcases that are rescheduled multiple times.
     */
    public final int getExpectedTestsCount() {
        return mMainGranularRunListener.getExpectedTests();
    }

    public final Set<TestDescription> getPassedTests() {
        Set<TestDescription> nonFailedTests = new LinkedHashSet<>();
        for (TestRunResult runResult : mMainGranularRunListener.getMergedTestRunResults()) {
            nonFailedTests.addAll(
                    runResult.getTestsInState(
                            Arrays.asList(
                                    TestStatus.PASSED,
                                    TestStatus.IGNORED,
                                    TestStatus.ASSUMPTION_FAILURE)));
        }
        return nonFailedTests;
    }

    /** Returns the listener containing all the results. */
    public ModuleListener getResultListener() {
        return mMainGranularRunListener;
    }

    public int getRetryCount() {
        return mCountRetryUsed;
    }

    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    private FailureDescription createFromException(Throwable exception) {
        String message =
                (exception.getMessage() == null)
                        ? String.format(
                                "No error message reported for: %s",
                                StreamUtil.getStackTrace(exception))
                        : exception.getMessage();
        FailureDescription failure =
                CurrentInvocation.createFailure(message, null).setCause(exception);
        if (exception instanceof IHarnessException) {
            ErrorIdentifier id = ((IHarnessException) exception).getErrorId();
            failure.setErrorIdentifier(id);
            if (id != null) {
                failure.setFailureStatus(id.status());
            }
            failure.setOrigin(((IHarnessException) exception).getOrigin());
        }
        return failure;
    }

    /** Class helper to catch missing run start and end. */
    public class StartEndCollector extends ResultAndLogForwarder {

        public boolean mRunStartReported = false;
        public boolean mRunEndedReported = false;

        StartEndCollector(ITestInvocationListener listener) {
            super(listener);
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            super.testRunStarted(runName, testCount);
            mRunStartReported = true;
        }

        @Override
        public void testRunStarted(String runName, int testCount, int attemptNumber) {
            super.testRunStarted(runName, testCount, attemptNumber);
            mRunStartReported = true;
        }

        @Override
        public void testRunStarted(
                String runName, int testCount, int attemptNumber, long startTime) {
            super.testRunStarted(runName, testCount, attemptNumber, startTime);
            mRunStartReported = true;
        }

        @Override
        public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
            super.testRunEnded(elapsedTime, runMetrics);
            mRunEndedReported = true;
        }

        @Override
        public void testRunEnded(long elapsedTimeMillis, Map<String, String> runMetrics) {
            super.testRunEnded(elapsedTimeMillis, runMetrics);
            mRunEndedReported = true;
        }
    }
}
