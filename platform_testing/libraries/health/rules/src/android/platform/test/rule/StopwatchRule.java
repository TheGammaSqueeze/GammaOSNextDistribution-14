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

package android.platform.test.rule;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.app.Instrumentation;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;

import org.junit.rules.Stopwatch;
import org.junit.runner.Description;

/**
 * The rule measures the test execution time by extending {@link org.junit.rules.Stopwatch}. It will
 * report the test time as key value pair to the instrumentation. For now, the rule will only report
 * metric when the test succeed.
 *
 * <p>TODO: Consider implementing generic metric reporting library or tight listeners to report
 * metric.
 */
public class StopwatchRule extends Stopwatch {
    /**
     * Metrics will be reported under the "status in progress" for test cases to be associated with
     * the running use cases.
     */
    @VisibleForTesting static final int INST_STATUS_IN_PROGRESS = 2;

    @VisibleForTesting static final String METRIC_BASE = "duration_ms";
    @VisibleForTesting static final String METRIC_FORMAT = METRIC_BASE + "_%s#%s";

    // If true, append the test name to the metric key. Otherwise, don't.
    @VisibleForTesting static final String APPEND_TEST_NAME = "append-test-name";

    private final Bundle mResult = new Bundle();
    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    /**
     * The method will report test time as milliseconds to instrumentation.
     *
     * @param nanos Test time input as nano seconds
     * @param description Description for for the test
     */
    private void reportMetric(long nanos, Description description) {
        mResult.putLong(getMetricKey(description), NANOSECONDS.toMillis(nanos));
        mInstrumentation.sendStatus(INST_STATUS_IN_PROGRESS, mResult);
    }

    /** {@inheritDoc} */
    @Override
    protected void succeeded(long nanos, Description description) {
        reportMetric(nanos, description);
    }

    @VisibleForTesting
    Bundle getMetric() {
        return mResult;
    }

    private String getMetricKey(Description description) {
        if ("true".equals(getArguments().getString(APPEND_TEST_NAME, "true"))) {
            return String.format(
                    METRIC_FORMAT, description.getClassName(), description.getMethodName());
        } else {
            return METRIC_BASE;
        }
    }

    /**
     * Returns the {@link Bundle} containing registered arguments.
     *
     * <p>Override this for unit testing device calls.
     */
    protected Bundle getArguments() {
        return InstrumentationRegistry.getArguments();
    }

    @VisibleForTesting
    void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }
}
