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
package com.android.tradefed.device.metric;

import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationGroupMetricKey;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.Map;

/** Count and report the number of test cases for a given {@link IRemoteTest}. */
public class CountTestCasesCollector extends BaseDeviceMetricCollector {

    private long mTestCount = 0L;
    private String mTestType = null;

    public CountTestCasesCollector() {}

    public CountTestCasesCollector(IRemoteTest test) {
        this();
        setTestType(test);
    }

    public void setTestType(IRemoteTest test) {
        mTestType = test.getClass().getSimpleName();
    }

    @Override
    public void onTestEnd(DeviceMetricData testData, Map<String, Metric> currentTestCaseMetrics) {
        if (mTestType == null) {
            return;
        }
        mTestCount++;
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, Map<String, Metric> currentRunMetrics) {
        InvocationMetricLogger.addInvocationMetrics(
                InvocationMetricKey.TOTAL_TEST_COUNT, mTestCount);
        if (mTestType == null) {
            return;
        }
        InvocationMetricLogger.addInvocationMetrics(
                InvocationGroupMetricKey.TEST_TYPE_COUNT, mTestType, mTestCount);
        mTestCount = 0L;
    }
}
