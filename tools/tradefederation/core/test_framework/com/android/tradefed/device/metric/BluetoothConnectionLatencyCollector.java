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
 * limitations under the License
 */

package com.android.tradefed.device.metric;

import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.DurationBucketInfo;
import com.android.os.StatsLog.DurationMetricData;
import com.android.os.StatsLog.StatsLogReport.DurationMetricDataWrapper;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.Sl4aBluetoothUtil.BluetoothProfile;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;

/**
 * The collector will push a pre-defined statsd duration metric config to devices and collect
 * Bluetooth connection duration for each profile.
 */
@OptionClass(alias = "bluetooth-connection-latency-collector")
public class BluetoothConnectionLatencyCollector extends HostStatsdMetricCollector {

    private static final String BLUETOOTH_CONNECTION_LATENCY_METRIC_KEY =
            "bluetooth_connection_latency";

    /** A map associates Bluetooth profile number to the descriptive name used for metric key. */
    protected static final ImmutableMap<Integer, String> BLUETOOTH_PROFILES_MAP =
            ImmutableMap.<Integer, String>builder()
                    .put(BluetoothProfile.HEADSET.getProfile(), "headset")
                    .put(BluetoothProfile.A2DP.getProfile(), "a2dp")
                    .put(BluetoothProfile.PAN.getProfile(), "pan")
                    .put(BluetoothProfile.MAP.getProfile(), "map")
                    .put(BluetoothProfile.A2DP_SINK.getProfile(), "a2dp_sink")
                    .put(BluetoothProfile.AVRCP_CONTROLLER.getProfile(), "avrcp_controller")
                    .put(BluetoothProfile.HEADSET_CLIENT.getProfile(), "headset_client")
                    .put(BluetoothProfile.PBAP_CLIENT.getProfile(), "pbap_client")
                    .put(BluetoothProfile.MAP_CLIENT.getProfile(), "map_client")
                    .build();

    @Override
    protected void processStatsReport(
            ITestDevice device, InputStreamSource dataStream, DeviceMetricData runData) {
        try {
            ConfigMetricsReportList reports =
                    ConfigMetricsReportList.parseFrom(dataStream.createInputStream());
            if (reports.getReportsCount() == 0) {
                CLog.w("No stats report is collected");
                return;
            }
            ConfigMetricsReport report = reports.getReports(0);
            if (report.getMetricsCount() == 0) {
                CLog.w("No metrics collected in stats report");
                return;
            }
            DurationMetricDataWrapper durationData = report.getMetrics(0).getDurationMetrics();
            if (durationData.getDataCount() == 0) {
                CLog.w("No duration data collected");
                return;
            }
            processBluetoothConnectionLatencyData(device, durationData, runData);
        } catch (IOException e) {
            CLog.e(
                    "Failed to process statsd metric report on device %s, error: %s",
                    device.getSerialNumber(), e);
        }
    }

    /**
     * Process bluetooth connection duration for each bluetooth profile.
     *
     * <p>It is assuming the metric report dumps a lis of duration values for each BT profile. And
     * each duration value is corresponding to a time bucket during the test. It is also assuming
     * the raw data is coming from a single BT connection.
     *
     * @param device to collect and process metrics
     * @param durationData raw duration metrics pulled from device
     * @param runData target object to store processed metrics
     */
    private void processBluetoothConnectionLatencyData(
            ITestDevice device, DurationMetricDataWrapper durationData, DeviceMetricData runData) {
        for (DurationMetricData metric : durationData.getDataList()) {
            long totalDurationNanos = 0;
            for (DurationBucketInfo bucket : metric.getBucketInfoList()) {
                totalDurationNanos += bucket.getDurationNanos();
            }
            int bluetoothProfile = metric.getDimensionLeafValuesInWhat(0).getValueInt();
            String metricKey;
            if (BLUETOOTH_PROFILES_MAP.containsKey(bluetoothProfile)) {
                metricKey =
                        String.join(
                                "_",
                                BLUETOOTH_CONNECTION_LATENCY_METRIC_KEY,
                                BLUETOOTH_PROFILES_MAP.get(bluetoothProfile),
                                "ms");
            } else {
                metricKey =
                        String.join(
                                "_",
                                BLUETOOTH_CONNECTION_LATENCY_METRIC_KEY,
                                "profile",
                                String.valueOf(bluetoothProfile),
                                "ms");
            }
            long durationMs = totalDurationNanos / 1000000;
            CLog.d(
                    "Processed metric on device %s with key: %s, value %d",
                    device.getSerialNumber(), metricKey, durationMs);
            runData.addMetricForDevice(
                    device,
                    metricKey,
                    Metric.newBuilder(
                            TfMetricProtoUtil.stringToMetric(Double.toString(durationMs))));
        }
    }
}
