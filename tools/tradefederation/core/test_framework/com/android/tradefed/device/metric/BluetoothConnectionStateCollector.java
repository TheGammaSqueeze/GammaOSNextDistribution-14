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

import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.BluetoothConnectionStateChanged;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.StatsLog.StatsLogReport.EventMetricDataWrapper;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.metrics.proto.MetricMeasurement.NumericValues;
import com.android.tradefed.result.InputStreamSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This collector will collect BluetoothConnectionStateChanged metrics and record connection state
 * number for each profile.
 */
@OptionClass(alias = "bluetooth-connection-state-collector")
public class BluetoothConnectionStateCollector extends BluetoothConnectionLatencyCollector {
    @Override
    protected void processStatsReport(
            ITestDevice device, InputStreamSource dataStream, DeviceMetricData runData) {
        CLog.d("processStatsReport is called for device %s", device.getSerialNumber());
        HashMap<String, List<Long>> btProfilesStates = new HashMap<>();
        try {
            ConfigMetricsReportList reports =
                    ConfigMetricsReportList.parseFrom(dataStream.createInputStream());
            CLog.d(
                    "Processing %d reports with 0th having %d metrics lists",
                    reports.getReportsCount(), reports.getReports(0).getMetricsCount());
            EventMetricDataWrapper eventData =
                    reports.getReports(0).getMetrics(0).getEventMetrics();
            for (EventMetricData metricData : eventData.getDataList()) {
                processEventMetric(device, metricData, btProfilesStates);
            }
        } catch (IOException e) {
            CLog.e(
                    "Failed to process statsd metric report on device %s, error: %s",
                    device.getSerialNumber(), e);
        }
        addMetricsToRunData(device, runData, btProfilesStates);
    }

    private void addMetricsToRunData(
            ITestDevice device,
            DeviceMetricData runData,
            HashMap<String, List<Long>> btProfilesStates) {
        for (String key : btProfilesStates.keySet()) {
            List<Long> states = btProfilesStates.get(key);
            String metricKey = String.join("_", key, "connection_state_changed");
            NumericValues values = NumericValues.newBuilder().addAllNumericValue(states).build();
            Measurements measurements = Measurements.newBuilder().setNumericValues(values).build();

            CLog.d(
                    "Adding metric on device %s with key %s and values %s",
                    device.getSerialNumber(), metricKey, states.toString());
            runData.addMetricForDevice(
                    device, metricKey, Metric.newBuilder().setMeasurements(measurements));
        }
    }

    private void processEventMetric(
            ITestDevice device,
            EventMetricData metric,
            HashMap<String, List<Long>> btProfilesStates) {
        Atom atom = metric.hasAtom() ? metric.getAtom() : metric.getAggregatedAtomInfo().getAtom();
        if (!atom.hasBluetoothConnectionStateChanged()) {
            CLog.d(
                    "Atom does not have a bluetooth_connection_state_changed info."
                            + " Skipping reporting");
            return;
        }
        BluetoothConnectionStateChanged bluetoothConnectionStateChanged =
                atom.getBluetoothConnectionStateChanged();
        int btState = bluetoothConnectionStateChanged.getState().getNumber();
        int btProfile = bluetoothConnectionStateChanged.getBtProfile();
        CLog.d(
                "Processing connection state changed atom on device %s for profile number %d",
                device.getSerialNumber(), btProfile);
        if (BLUETOOTH_PROFILES_MAP.containsKey(btProfile)) {
            String btProfileName = BLUETOOTH_PROFILES_MAP.get(btProfile);
            List<Long> states = btProfilesStates.getOrDefault(btProfileName, new ArrayList<Long>());
            states.add((long) btState);
            btProfilesStates.put(btProfileName, states);
            CLog.d(
                    "Processed connection state changed atom on device %s profile %s value %d",
                    device.getSerialNumber(), btProfileName, btState);
        }
    }
}
