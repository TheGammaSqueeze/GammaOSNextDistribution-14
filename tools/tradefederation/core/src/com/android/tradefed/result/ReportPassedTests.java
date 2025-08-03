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
package com.android.tradefed.result;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.retry.ISupportGranularResults;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

/** Report in a file possible filters to exclude passed test. */
public class ReportPassedTests extends CollectingTestListener
        implements IConfigurationReceiver, ISupportGranularResults {

    private static final String PASSED_TEST_LOG = "passed_tests";
    private boolean mInvocationFailed = false;
    private ITestLogger mLogger;
    private boolean mModuleInProgress;
    private IInvocationContext mContextForEmptyModule;
    private Integer mShardIndex;
    private File mPassedTests;

    public void setLogger(ITestLogger logger) {
        mLogger = logger;
    }

    @Override
    public boolean supportGranularResults() {
        return false;
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        try {
            mPassedTests = FileUtil.createTempFile(PASSED_TEST_LOG, ".txt");
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        if (configuration.getCommandOptions().getShardIndex() != null) {
            mShardIndex = configuration.getCommandOptions().getShardIndex();
        }
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        super.testModuleStarted(moduleContext);
        mModuleInProgress = true;
        mContextForEmptyModule = moduleContext;
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        mContextForEmptyModule = null;
        super.testRunEnded(elapsedTime, runMetrics);
        if (!mModuleInProgress) {
            gatherPassedTests(
                    getCurrentRunResults(), getBaseName(getCurrentRunResults()), mInvocationFailed);
            clearResultsForName(getCurrentRunResults().getName());
            // Clear the failure for aggregation
            getCurrentRunResults().resetRunFailure();
        }
    }

    @Override
    public void testModuleEnded() {
        if (mContextForEmptyModule != null) {
            // If the module was empty
            String moduleId = mContextForEmptyModule.getAttributes()
                    .getUniqueMap().get(ModuleDefinition.MODULE_ID);
            if (moduleId != null) {
                super.testRunStarted(moduleId, 0);
                super.testRunEnded(0L, new HashMap<String, Metric>());
            }
            mContextForEmptyModule = null;
        }
        super.testModuleEnded();
        gatherPassedTests(
                getCurrentRunResults(), getBaseName(getCurrentRunResults()), mInvocationFailed);
        clearResultsForName(getCurrentRunResults().getName());
        // Clear the failure for aggregation
        getCurrentRunResults().resetRunFailure();
        mModuleInProgress = false;
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        super.invocationFailed(failure);
        mInvocationFailed = true;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        try {
            super.invocationEnded(elapsedTime);
            createPassedLog();
        } finally {
            FileUtil.deleteFile(mPassedTests);
        }
    }

    private void createPassedLog() {
        if (mLogger == null || mPassedTests == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TestRunResult result : getMergedTestRunResults()) {
            sb.append(createFilters(result, getBaseName(result), false));
        }
        if (sb.length() > 0) {
            try {
                FileUtil.writeToFile(sb.toString(), mPassedTests, true);
            } catch (IOException e) {
                CLog.e(e);
            }
        }
        if (mPassedTests.length() == 0) {
            CLog.d("No new filter for passed_test");
            return;
        }
        testLog(mPassedTests);
    }

    @VisibleForTesting
    void testLog(File toBeLogged) {
        try (FileInputStreamSource source = new FileInputStreamSource(toBeLogged)) {
            mLogger.testLog(PASSED_TEST_LOG, LogDataType.PASSED_TESTS, source);
        }
    }

    private String getBaseName(TestRunResult runResult) {
        IInvocationContext context = getModuleContextForRunResult(runResult.getName());
        // If it's a test module
        if (context != null) {
            return context.getAttributes().getUniqueMap().get(ModuleDefinition.MODULE_ID);
        } else {
            return runResult.getName();
        }
    }

    private String createFilters(
            TestRunResult runResult, String baseName, boolean invocationFailure) {
        if (mShardIndex != null) {
            baseName = "shard_" + mShardIndex + " " + baseName;
        }
        StringBuilder sb = new StringBuilder();
        if (!runResult.hasFailedTests() && !runResult.isRunFailure() && !invocationFailure) {
            sb.append(baseName);
            sb.append("\n");
            return sb.toString();
        }
        for (Entry<TestDescription, TestResult> res : runResult.getTestResults().entrySet()) {
            if (TestStatus.FAILURE.equals(res.getValue().getResultStatus())) {
                continue;
            }
            // Consider SKIPPED as failure so it can be retried
            if (TestStatus.SKIPPED.equals(res.getValue().getResultStatus())) {
                continue;
            }
            sb.append(baseName + " " + res.getKey().toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void gatherPassedTests(
            TestRunResult runResult, String baseName, boolean invocationFailure) {
        StringBuilder sb = new StringBuilder();
        sb.append(createFilters(runResult, baseName, invocationFailure));
        if (sb.length() == 0L) {
            return;
        }
        try {
            FileUtil.writeToFile(sb.toString(), mPassedTests, true);
        } catch (IOException e) {
            CLog.e(e);
        }
    }
}
