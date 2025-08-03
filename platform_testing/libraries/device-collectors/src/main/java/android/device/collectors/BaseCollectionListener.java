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
package android.device.collectors;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.ICollectorHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.util.Map;
import java.util.function.Function;

/**
 * A {@link BaseCollectionListener} that captures metrics collected during the testing.
 *
 * Metrics can be collected at the test run level or per test method using per_run option.
 *
 * If there are any failure in the metric collection, tests will still proceed to run and
 * not posting the metrics at the end of the test.
 *
 * Do NOT throw exception anywhere in this class. We don't want to halt the test when metrics
 * collection fails.
 */
public class BaseCollectionListener<T> extends BaseMetricListener {

    protected ICollectorHelper mHelper;
    // Collect per run if it is set to true otherwise collect per test.
    public static final String COLLECT_PER_RUN = "per_run";
    // Skip failure metrics collection if this flag is set to true.
    public static final String SKIP_TEST_FAILURE_METRICS = "skip_test_failure_metrics";
    protected boolean mIsCollectPerRun;
    protected boolean mSkipTestFailureMetrics;
    private boolean mIsTestFailed = false;

    public BaseCollectionListener() {
        super();
    }

    @VisibleForTesting
    public BaseCollectionListener(Bundle args, ICollectorHelper helper) {
        super(args);
        mHelper = helper;
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {

        if (mIsCollectPerRun) {
            Function<String, Boolean> filter = getFilter(description);
            testStart(filter, description);
        }
    }

    @Override
    protected void parseArguments() {
        super.parseArguments();
        Bundle args = getArgsBundle();
        mIsCollectPerRun = "true".equals(args.getString(COLLECT_PER_RUN));
        // By default this flag is set to false to collect the metrics on test failure.
        mSkipTestFailureMetrics = "true".equals(args.getString(SKIP_TEST_FAILURE_METRICS));
    }

    protected Function<String, Boolean> getFilter(Description description) {
        return null;
    }

    @Override
    public final void onTestStart(DataRecord testData, Description description) {
        mIsTestFailed = false;
        if (!mIsCollectPerRun) {
            Function<String, Boolean> filter = getFilter(description);
            testStart(filter, description);
        }
    }

    @Override
    public void onTestFail(DataRecord testData, Description description, Failure failure) {
        mIsTestFailed = true;
    }

    @Override
    public void onTestEnd(DataRecord testData, Description description) {
        if (!mIsCollectPerRun) {
            try {
                // Skip adding the metrics collected during the test failure
                // if the skip metrics on test failure flag is enabled and the
                // current test is failed.
                if (shouldSkipFailureTestMetrics()) {
                    Log.i(getTag(), "Skipping the metric collection.");
                } else {
                    // Collect the metrics.
                    collectMetrics(testData);
                }
            } finally {
                mHelper.stopCollecting();
            }
        }
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        if (mIsCollectPerRun) {
            try {
                collectMetrics(runData);
            } finally {
                mHelper.stopCollecting();
            }
        }
    }

    public void testStart(Function<String, Boolean> filter, Description description) {
        if (filter == null) {
            mHelper.startCollecting();
        } else {
            mHelper.startCollecting(filter);
        }
    }

    protected void createHelperInstance(ICollectorHelper helper) {
        mHelper = helper;
    }

    protected void collectMetrics(DataRecord data) {
        Map<String, T> metrics = mHelper.getMetrics();
        for (Map.Entry<String, T> entry : metrics.entrySet()) {
            data.addStringMetric(entry.getKey(), entry.getValue().toString());
        }
    }

    protected boolean shouldSkipFailureTestMetrics() {
        return mSkipTestFailureMetrics && mIsTestFailed;
    }
}
