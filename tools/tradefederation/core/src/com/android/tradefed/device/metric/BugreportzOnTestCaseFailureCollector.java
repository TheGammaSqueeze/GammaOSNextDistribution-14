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
package com.android.tradefed.device.metric;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.TestDescription;

import java.util.Map;

/** Collect a bugreportz when a test case in a run fails. */
public class BugreportzOnTestCaseFailureCollector extends BaseDeviceMetricCollector {

    private static final String NAME_FORMAT = "%s-%s-bugreportz-on-test-case-failure";
    private boolean mTestFailed = false;

    @Override
    public void onTestRunStart(DeviceMetricData runData) {
        mTestFailed = false;
    }

    @Override
    public void onTestFail(DeviceMetricData testData, TestDescription test) {
        mTestFailed = true;
    }

    @Override
    public void onTestRunEnd(
            DeviceMetricData runData,
            final Map<String, MetricMeasurement.Metric> currentRunMetrics) {
        if (!mTestFailed) {
            return;
        }
        for (ITestDevice device : getDevices()) {
            String name = String.format(NAME_FORMAT, getRunName(), device.getSerialNumber());
            if (!device.logBugreport(name, getInvocationListener())) {
                CLog.e("Failed to capture bugreportz on '%s'", device.getSerialNumber());
            }
        }
    }
}
