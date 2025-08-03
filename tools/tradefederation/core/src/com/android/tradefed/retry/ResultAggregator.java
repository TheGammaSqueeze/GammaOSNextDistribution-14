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
package com.android.tradefed.retry;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.EventsLoggerListener;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.MultiFailureDescription;
import com.android.tradefed.result.ResultAndLogForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.retry.ISupportGranularResults;
import com.android.tradefed.result.skipped.SkipReason;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Special forwarder that aggregates the results when needed, based on the retry strategy that was
 * taken.
 */
public class ResultAggregator extends CollectingTestListener {

    /* Forwarder to ALL result reporters */
    private ResultAndLogForwarder mAllForwarder;
    /* Forwarder to result reporters that only support aggregated results */
    private ResultAndLogForwarder mAggregatedForwarder;
    /* Forwarder to result reporters that support the attempt reporting */
    private ResultAndLogForwarder mDetailedForwarder;
    private RetryStrategy mRetryStrategy;
    // Track whether or not a module was started.
    private boolean mModuleInProgress = false;

    // Holders for results in progress
    private TestRunResult mDetailedRunResults = null;
    private boolean mShouldReportFailure = true;
    private List<FailureDescription> mAllDetailedFailures = new ArrayList<>();
    // Since we store some of the module level events, ensure the logs order is maintained.
    private Map<String, LogFile> mDetailedModuleLogs = new LinkedHashMap<>();

    private boolean mUpdatedDetailedReporting = false;

    // In some configuration of non-module retry, all attempts of runs might not be adjacent. We
    // track that a special handling needs to be applied for this case.
    private boolean mUnorderedRetry = true;
    // Track whether run start was called for a module.
    private boolean mRunStartCalled = false;
    // Stores the results from non-module test runs until they are ready to be replayed.
    private final Map<String, List<TestRunResult>> mPureRunResultForAgg = new LinkedHashMap<>();

    private ILogSaver mLogSaver;
    private EventsLoggerListener mAggregatedEventsLogger;
    private EventsLoggerListener mDetailedEventsLogger;

    public ResultAggregator(List<ITestInvocationListener> listeners, RetryStrategy strategy) {

        List<ITestInvocationListener> supportDetails =
                listeners
                        .stream()
                        .filter(
                                i ->
                                        ((i instanceof ISupportGranularResults)
                                                && ((ISupportGranularResults) i)
                                                        .supportGranularResults()))
                        .collect(Collectors.toList());
        List<ITestInvocationListener> noSupportDetails =
                listeners
                        .stream()
                        .filter(
                                i ->
                                        !(i instanceof ISupportGranularResults)
                                                || !((ISupportGranularResults) i)
                                                        .supportGranularResults())
                        .collect(Collectors.toList());

        mDetailedEventsLogger = new EventsLoggerListener("detailed-events");
        supportDetails.add(mDetailedEventsLogger);
        mAggregatedEventsLogger = new EventsLoggerListener("aggregated-events");
        noSupportDetails.add(mAggregatedEventsLogger);

        mAggregatedForwarder = new ResultAndLogForwarder(noSupportDetails);
        mDetailedForwarder = new ResultAndLogForwarder(supportDetails);
        List<ITestInvocationListener> allListeners = new ArrayList<>(listeners);
        allListeners.add(mDetailedEventsLogger);
        allListeners.add(mAggregatedEventsLogger);
        mAllForwarder = new ResultAndLogForwarder(allListeners);

        mRetryStrategy = strategy;
        MergeStrategy mergeStrategy = MergeStrategy.getMergeStrategy(mRetryStrategy);
        setMergeStrategy(mergeStrategy);
    }

    /** Sets the new reporting. */
    public void setUpdatedReporting(boolean updatedReporting) {
        mUpdatedDetailedReporting = updatedReporting;
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        mAllForwarder.invocationStarted(context);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        mAllForwarder.invocationFailed(cause);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationFailed(FailureDescription failure) {
        super.invocationFailed(failure);
        mAllForwarder.invocationFailed(failure);
    }

    @Override
    public void invocationSkipped(SkipReason reason) {
        super.invocationSkipped(reason);
        mAllForwarder.invocationSkipped(reason);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (!mPureRunResultForAgg.isEmpty()) {
            for (String name : mPureRunResultForAgg.keySet()) {
                forwardTestRunResults(mPureRunResultForAgg.get(name), mAggregatedForwarder);
            }
            mPureRunResultForAgg.clear();
        }

        forwardDetailedFailure();
        for (Entry<String, LogFile> assos : mDetailedModuleLogs.entrySet()) {
            mDetailedForwarder.logAssociation(assos.getKey(), assos.getValue());
        }
        mDetailedModuleLogs.clear();
        super.invocationEnded(elapsedTime);
        // Make sure to forward the logs for the invocation.
        forwardAggregatedInvocationLogs();

        // Log the aggregated events for debugging
        mAggregatedEventsLogger.invocationEnded(elapsedTime);
        saveEventsLog(mAggregatedEventsLogger.getLoggedEvents(), "aggregated-events");
        // Log the detailed events for debugging
        mDetailedEventsLogger.invocationEnded(elapsedTime);
        saveEventsLog(mDetailedEventsLogger.getLoggedEvents(), "detailed-events");

        mAllForwarder.invocationEnded(elapsedTime);
    }

    /**
     * Forward all the invocation level logs to the result reporters that don't support the granular
     * results.
     */
    public final void forwardAggregatedInvocationLogs() {
        for (String key : getNonAssociatedLogFiles().keySet()) {
            for (LogFile log : getNonAssociatedLogFiles().get(key)) {
                mAggregatedForwarder.logAssociation(key, log);
            }
        }
    }

    public void cleanEventsFiles() {
        if (mAggregatedEventsLogger != null) {
            FileUtil.deleteFile(mAggregatedEventsLogger.getLoggedEvents());
        }
        if (mDetailedEventsLogger != null) {
            FileUtil.deleteFile(mDetailedEventsLogger.getLoggedEvents());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        mUnorderedRetry = false;
        mRunStartCalled = false;
        if (!mPureRunResultForAgg.isEmpty()) {
            for (String name : mPureRunResultForAgg.keySet()) {
                forwardTestRunResults(mPureRunResultForAgg.get(name), mAggregatedForwarder);
            }
            mPureRunResultForAgg.clear();
        }

        // Reset the reporting since we start a new module
        mShouldReportFailure = true;
        if (mDetailedRunResults != null) {
            forwardDetailedFailure();
        }

        mModuleInProgress = true;
        super.testModuleStarted(moduleContext);
        mAllForwarder.testModuleStarted(moduleContext);
    }

    /** {@inheritDoc} */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        mLogSaver = logSaver;
        super.setLogSaver(logSaver);
        mAllForwarder.setLogSaver(logSaver);
    }

    /** {@inheritDoc} */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        super.testLog(dataName, dataType, dataStream);
        mAllForwarder.testLog(dataName, dataType, dataStream);
    }

    // ====== Forwarders to the detailed result reporters

    @Override
    public void testRunStarted(String name, int testCount, int attemptNumber, long startTime) {
        mRunStartCalled = true;
        // Due to retries happening after several other testRunStart, we need to wait before making
        // the forwarding.
        if (!mUnorderedRetry) {
            if (!mPureRunResultForAgg.isEmpty() && mPureRunResultForAgg.get(name) != null) {
                forwardTestRunResults(mPureRunResultForAgg.get(name), mAggregatedForwarder);
                mPureRunResultForAgg.remove(name);
            }
        }

        if (!mUpdatedDetailedReporting) {
            if (mDetailedRunResults != null) {
                if (mDetailedRunResults.getName().equals(name)) {
                    if (!mDetailedRunResults.isRunFailure()) {
                        if (RetryStrategy.RETRY_ANY_FAILURE.equals(mRetryStrategy)) {
                            mShouldReportFailure = false;
                        }
                    }
                    mDetailedForwarder.testRunEnded(
                            mDetailedRunResults.getElapsedTime(),
                            mDetailedRunResults.getRunProtoMetrics());
                    mDetailedRunResults = null;
                } else {
                    mShouldReportFailure = true;
                    forwardDetailedFailure();
                }
            }
        }
        super.testRunStarted(name, testCount, attemptNumber, startTime);
        mDetailedForwarder.testRunStarted(name, testCount, attemptNumber, startTime);
    }

    @Override
    public void testRunFailed(String errorMessage) {
        super.testRunFailed(errorMessage);
        // Don't forward here to the detailed forwarder in case we need to clear it.
        if (mUpdatedDetailedReporting) {
            mDetailedForwarder.testRunFailed(errorMessage);
        }
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        super.testRunFailed(failure);
        // Don't forward here to the detailed forwarder in case we need to clear it.
        if (mUpdatedDetailedReporting) {
            mDetailedForwarder.testRunFailed(failure);
        }
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        super.testStarted(test, startTime);
        mDetailedForwarder.testStarted(test, startTime);
    }

    @Override
    public void testIgnored(TestDescription test) {
        super.testIgnored(test);
        mDetailedForwarder.testIgnored(test);
    }

    @Override
    public void testSkipped(TestDescription test, SkipReason reason) {
        super.testSkipped(test, reason);
        mDetailedForwarder.testSkipped(test, reason);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        super.testAssumptionFailure(test, trace);
        mDetailedForwarder.testAssumptionFailure(test, trace);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        super.testAssumptionFailure(test, failure);
        mDetailedForwarder.testAssumptionFailure(test, failure);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        super.testFailed(test, trace);
        mDetailedForwarder.testFailed(test, trace);
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        super.testFailed(test, failure);
        mDetailedForwarder.testFailed(test, failure);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        super.testEnded(test, endTime, testMetrics);
        mDetailedForwarder.testEnded(test, endTime, testMetrics);
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        super.logAssociation(dataName, logFile);
        if (mDetailedRunResults != null) {
            mDetailedModuleLogs.put(dataName, logFile);
        } else {
            mDetailedForwarder.logAssociation(dataName, logFile);
        }
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        super.testLogSaved(dataName, dataType, dataStream, logFile);
        mDetailedForwarder.testLogSaved(dataName, dataType, dataStream, logFile);
    }

    // ===== Forwarders to the aggregated reporters.

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        if (mUpdatedDetailedReporting) {
            mDetailedForwarder.testRunEnded(elapsedTime, runMetrics);
        } else {
            mDetailedRunResults = getCurrentRunResults();
            if (mDetailedRunResults.isRunFailure()) {
                FailureDescription currentFailure = mDetailedRunResults.getRunFailureDescription();
                if (currentFailure instanceof MultiFailureDescription) {
                    mAllDetailedFailures.addAll(
                            ((MultiFailureDescription) currentFailure).getFailures());
                } else {
                    mAllDetailedFailures.add(currentFailure);
                }
            }
        }

        // If we are not a module and we reach here. This allows to support non-suite scenarios
        if (!mModuleInProgress) {
            List<TestRunResult> results =
                    mPureRunResultForAgg.getOrDefault(
                            getCurrentRunResults().getName(), new ArrayList<>());
            results.add(getCurrentRunResults());
            mPureRunResultForAgg.put(getCurrentRunResults().getName(), results);
        }
    }

    @Override
    public void testModuleEnded() {
        if (!mUpdatedDetailedReporting) {
            forwardDetailedFailure();
            for (Entry<String, LogFile> assos : mDetailedModuleLogs.entrySet()) {
                mDetailedForwarder.logAssociation(assos.getKey(), assos.getValue());
            }
            mDetailedModuleLogs.clear();
        }
        mModuleInProgress = false;
        super.testModuleEnded();
        // We still forward the testModuleEnd to the detailed reporters
        mDetailedForwarder.testModuleEnded();

        // Only show run results if there was a run.
        if (mRunStartCalled) {
            List<TestRunResult> mergedResults = getMergedTestRunResults();
            Set<String> resultNames = new HashSet<>();
            int expectedTestCount = 0;
            for (TestRunResult result : mergedResults) {
                expectedTestCount += result.getExpectedTestCount();
                resultNames.add(result.getName());
            }
            // Forward all the results aggregated
            mAggregatedForwarder.testRunStarted(
                    getCurrentRunResults().getName(),
                    expectedTestCount,
                    /* Attempt*/ 0,
                    /* Start Time */ getCurrentRunResults().getStartTime());
            for (TestRunResult runResult : mergedResults) {
                forwardTestResults(runResult.getTestResults(), mAggregatedForwarder);
                if (runResult.isRunFailure()) {
                    mAggregatedForwarder.testRunFailed(runResult.getRunFailureDescription());
                }
                // Provide a strong association of the run to its logs.
                for (String key : runResult.getRunLoggedFiles().keySet()) {
                    for (LogFile logFile : runResult.getRunLoggedFiles().get(key)) {
                        mAggregatedForwarder.logAssociation(key, logFile);
                    }
                }
            }
    
            mAggregatedForwarder.testRunEnded(
                    getCurrentRunResults().getElapsedTime(),
                    getCurrentRunResults().getRunProtoMetrics());
            // Ensure we don't carry results from one module to another.
            for (String name : resultNames) {
                clearResultsForName(name);
            }
        }
        // Log all the module only logs
        for (String key : getModuleLogFiles().keySet()) {
            for (LogFile log : getModuleLogFiles().get(key)) {
                mAggregatedForwarder.logAssociation(key, log);
            }
        }
        clearModuleLogFiles();
        mAggregatedForwarder.testModuleEnded();
        mUnorderedRetry = true;
    }

    @VisibleForTesting
    String getInvocationMetricRunError() {
        return InvocationMetricLogger.getInvocationMetrics()
                .get(InvocationMetricKey.CLEARED_RUN_ERROR.toString());
    }

    @VisibleForTesting
    void addInvocationMetricRunError(String errors) {
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.CLEARED_RUN_ERROR, errors);
    }

    private void forwardTestResults(
            Map<TestDescription, TestResult> testResults, ITestInvocationListener listener) {
        for (Map.Entry<TestDescription, TestResult> testEntry : testResults.entrySet()) {
            listener.testStarted(testEntry.getKey(), testEntry.getValue().getStartTime());
            switch (testEntry.getValue().getResultStatus()) {
                case FAILURE:
                    listener.testFailed(testEntry.getKey(), testEntry.getValue().getFailure());
                    break;
                case ASSUMPTION_FAILURE:
                    listener.testAssumptionFailure(
                            testEntry.getKey(), testEntry.getValue().getFailure());
                    break;
                case IGNORED:
                    listener.testIgnored(testEntry.getKey());
                    break;
                case INCOMPLETE:
                    listener.testFailed(
                            testEntry.getKey(),
                            FailureDescription.create("Test did not complete due to exception."));
                    break;
                case SKIPPED:
                    listener.testSkipped(testEntry.getKey(), testEntry.getValue().getSkipReason());
                    break;
                default:
                    break;
            }
            // Provide a strong association of the test to its logs.
            for (Entry<String, LogFile> logFile :
                    testEntry.getValue().getLoggedFiles().entrySet()) {
                if (listener instanceof ILogSaverListener) {
                    ((ILogSaverListener) listener)
                            .logAssociation(logFile.getKey(), logFile.getValue());
                }
            }
            listener.testEnded(
                    testEntry.getKey(),
                    testEntry.getValue().getEndTime(),
                    testEntry.getValue().getProtoMetrics());
        }
    }

    /**
     * Helper method to forward the results from multiple attempts of the same Test Run (same name).
     */
    private void forwardTestRunResults(List<TestRunResult> results, ILogSaverListener listener) {
        TestRunResult result =
                TestRunResult.merge(results, MergeStrategy.getMergeStrategy(mRetryStrategy));

        listener.testRunStarted(
                result.getName(), result.getExpectedTestCount(), 0, result.getStartTime());
        forwardTestResults(result.getTestResults(), listener);
        if (result.isRunFailure()) {
            listener.testRunFailed(result.getRunFailureDescription());
        }
        // Provide a strong association of the run to its logs.
        for (String key : result.getRunLoggedFiles().keySet()) {
            for (LogFile logFile : result.getRunLoggedFiles().get(key)) {
                listener.logAssociation(key, logFile);
            }
        }
        listener.testRunEnded(result.getElapsedTime(), result.getRunProtoMetrics());
        // Ensure we don't keep track of the results we just forwarded
        clearResultsForName(result.getName());
    }

    private void forwardDetailedFailure() {
        if (mDetailedRunResults != null) {
            if (mDetailedRunResults.isRunFailure() && mShouldReportFailure) {
                if (mAllDetailedFailures.size() == 1) {
                    mDetailedForwarder.testRunFailed(mAllDetailedFailures.get(0));
                } else {
                    mDetailedForwarder.testRunFailed(
                            new MultiFailureDescription(mAllDetailedFailures));
                }
            } else {
                // Log the run failure that was cleared
                List<String> invocationFailures = new ArrayList<>();
                String value = getInvocationMetricRunError();
                if (value != null) {
                    invocationFailures.add(value);
                }
                // If there are failure, track them
                if (!mAllDetailedFailures.isEmpty()) {
                    invocationFailures.add(
                            new MultiFailureDescription(mAllDetailedFailures).toString());
                    addInvocationMetricRunError(String.join("\n\n", invocationFailures));
                }
            }
            mAllDetailedFailures.clear();
            mDetailedForwarder.testRunEnded(
                    mDetailedRunResults.getElapsedTime(), mDetailedRunResults.getRunProtoMetrics());
            mDetailedRunResults = null;
        }
    }

    private void saveEventsLog(File eventsLog, String key) {
        if (eventsLog != null && eventsLog.length() > 0 && mLogSaver != null) {
            try {
                LogFile logged =
                        mLogSaver.saveLogFile(
                                eventsLog.getName(), LogDataType.TF_EVENTS, eventsLog);
                if (logged != null) {
                    mAggregatedForwarder.logAssociation(key, logged);
                    mDetailedForwarder.logAssociation(key, logged);
                }
            } catch (IOException e) {
                CLog.e(e);
            }
        }
        FileUtil.deleteFile(eventsLog);
    }

    @VisibleForTesting
    protected File[] getEventsLogs() {
        File[] logs = new File[2];
        logs[0] = mAggregatedEventsLogger.getLoggedEvents();
        logs[1] = mDetailedEventsLogger.getLoggedEvents();
        return logs;
    }
}
