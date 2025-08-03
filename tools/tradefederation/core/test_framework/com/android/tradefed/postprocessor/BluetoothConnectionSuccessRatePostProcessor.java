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

package com.android.tradefed.postprocessor;

import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.LogFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of postprocessor which calculates success rate for a bluetooth profile
 *
 * <p>Use "metric-key-match" to specify metrics which contain bluetooth connection states in numeric
 * array Example [0, 1, 2, 3, 0, 1, 2, 3]. Refer to android.bluetooth.ConnectionStateEnum for
 * connection states.
 */
public class BluetoothConnectionSuccessRatePostProcessor extends BasePostProcessor {
    private static final int CONNECTION_STATE_CONNECTED = 2;
    private static final int CONNECTION_STATE_CONNECTING = 1;
    private static final String SUCCESS_RATE_KEY_TAG = "-success-rate";

    @Option(
            name = "metric-key-match",
            description = "Key match for a bluetooth connection change metric")
    private String mMetricKeyMatch = "connection_state_changed";

    @Override
    public Map<String, MetricMeasurement.Metric.Builder> processRunMetricsAndLogs(
            HashMap<String, MetricMeasurement.Metric> runMetrics, Map<String, LogFile> testLogs) {
        Map<String, MetricMeasurement.Metric.Builder> successRateMetrics = new HashMap<>();
        for (String key : runMetrics.keySet()) {
            if (!key.contains(mMetricKeyMatch)) {
                continue;
            }
            MetricMeasurement.Metric metric = runMetrics.get(key);
            List<Long> connectionStates =
                    metric.getMeasurements().getNumericValues().getNumericValueList();
            Long connectionStateConnectingCount =
                    connectionStates.stream()
                            .filter(state -> state == CONNECTION_STATE_CONNECTING)
                            .count();
            Long connectionStateConnectedCount =
                    connectionStates.stream()
                            .filter(state -> state == CONNECTION_STATE_CONNECTED)
                            .count();
            if (connectionStateConnectingCount == 0) {
                LogUtil.CLog.d(
                        "Metric %s does not have connecting state reported. Skipping calculating"
                                + " success rate",
                        key);
                continue;
            }
            Double successRate =
                    connectionStateConnectedCount.doubleValue()
                            / connectionStateConnectingCount.doubleValue();
            successRateMetrics.put(
                    key + SUCCESS_RATE_KEY_TAG,
                    MetricMeasurement.Metric.newBuilder()
                            .setMeasurements(
                                    MetricMeasurement.Measurements.newBuilder()
                                            .setSingleDouble(successRate)
                                            .build()));
        }
        return successRateMetrics;
    }
}
