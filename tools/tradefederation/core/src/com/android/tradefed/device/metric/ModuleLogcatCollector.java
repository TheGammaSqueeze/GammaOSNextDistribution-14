/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.TestDescription;

import java.util.Map;

/** Version of logcat collector but for module. */
public class ModuleLogcatCollector extends LogcatOnFailureCollector {

    private static final int MAX_LOGAT_SIZE_BYTES = 40 * 1024 * 1024;

    @Override
    public boolean captureModuleLevel() {
        return true;
    }

    @Override
    public void onTestModuleStarted() throws DeviceNotAvailableException {
        super.onTestRunStart(null);
    }

    @Override
    public void onTestModuleEnded() throws DeviceNotAvailableException {
        collectAndLog("module-logcat", MAX_LOGAT_SIZE_BYTES);
        super.onTestRunEnd(null, null);
    }

    // Ignore all the non-module calls.
    @Override
    public void onTestRunStart(DeviceMetricData runData) {
        // Ignore
    }

    @Override
    public void onTestFail(DeviceMetricData testData, TestDescription test)
            throws DeviceNotAvailableException {
        // Ignore
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, Map<String, Metric> currentRunMetrics) {
        // Ignore
    }

    @Override
    public void onTestRunFailed(DeviceMetricData testData, FailureDescription failure)
            throws DeviceNotAvailableException {
        // Ignore
    }
}
