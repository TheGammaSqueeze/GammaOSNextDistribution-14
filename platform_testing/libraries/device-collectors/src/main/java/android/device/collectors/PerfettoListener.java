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

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.PerfettoHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * A {@link PerfettoListener} that captures the perfetto trace during each test method and save the
 * perfetto trace files under
 * <root>/<test_name>/PerfettoTracingStrategy/<test_name>-<invocation_count>.perfetto-trace
 */
@OptionClass(alias = "perfetto-collector")
public class PerfettoListener extends BaseMetricListener {
    public static final String COLLECT_PER_RUN = "per_run";
    public static final String COLLECT_PER_CLASS = "per_class";

    private PerfettoTracingStrategy mTracingStrategy;

    public PerfettoListener() {
        super();
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    public PerfettoListener(Bundle args, PerfettoTracingStrategy strategy) {
        super(args);
        mTracingStrategy = strategy;
    }

    protected PerfettoHelper getPerfettoHelper() {
        return mTracingStrategy.getPerfettoHelper();
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        mTracingStrategy.testRunStart(runData, description);
    }

    @Override
    public void onTestStart(DataRecord testData, Description description) {
        mTracingStrategy.testStart(testData, description);
    }

    @Override
    public void onTestFail(DataRecord testData, Description description, Failure failure) {
        mTracingStrategy.testFail(testData, description, failure);
    }

    @Override
    public void onTestEnd(DataRecord testData, Description description) {
        mTracingStrategy.testEnd(testData, description);
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        mTracingStrategy.testRunEnd(runData, result);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();

        if (mTracingStrategy == null) {
            initTracingStrategy(args);
        }

        mTracingStrategy.setup(args);
    }

    private void initTracingStrategy(Bundle args) {
        // Whether to collect the for the entire test run, per test, or per class.
        if (Boolean.parseBoolean(args.getString(COLLECT_PER_RUN))) {
            mTracingStrategy = new PerfettoTracingPerRunStrategy(getInstrumentation());
        } else if (Boolean.parseBoolean(args.getString(COLLECT_PER_CLASS))) {
            mTracingStrategy = new PerfettoTracingPerClassStrategy(getInstrumentation());
        } else {
            mTracingStrategy = new PerfettoTracingPerTestStrategy(getInstrumentation());
        }
    }

    @VisibleForTesting
    String getTestFileName(Description description) {
        return PerfettoTracingStrategy.getTestFileName(description);
    }

    @VisibleForTesting
    void runWithWakeLock(Runnable runnable) {
        mTracingStrategy.runWithWakeLock(runnable);
    }
}
