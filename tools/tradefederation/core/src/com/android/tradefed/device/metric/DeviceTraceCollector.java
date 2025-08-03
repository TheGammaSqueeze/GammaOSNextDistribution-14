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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.PerfettoTraceRecorder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Collector that will start perfetto trace when a test run starts and log trace file at the end.
 */
public class DeviceTraceCollector extends BaseDeviceMetricCollector {
    // Format of the trace name should be: device-trace_<device-serial>_<trace-count>_<event-name>.
    private static final String NAME_FORMAT = "device-trace_%s_%d_%s";
    private PerfettoTraceRecorder mPerfettoTraceRecorder = new PerfettoTraceRecorder();
    // package name for an instrumentation test, null otherwise.
    private String mInstrumentationPkgName;

    private Map<ITestDevice, Integer> mTraceCountMap = new LinkedHashMap<>();
    // Map of trace files and the proper name it should be logged with
    private Map<File, String> mTraceFilesMap = new LinkedHashMap();

    public DeviceTraceCollector() {
        setDisableReceiver(false);
    }

    @Override
    public void extraInit(IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        super.extraInit(context, listener);
        for (ITestDevice device : getRealDevices()) {
            startTraceOnDevice(device);
        }
    }

    @Override
    public void onTestRunEnd(
            DeviceMetricData runData, Map<String, MetricMeasurement.Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        for (ITestDevice device : getRealDevices()) {
            collectTraceFileFromDevice(device, "testRunEnded");
        }
        logTraceFiles();
    }

    private void startTraceOnDevice(ITestDevice device) {
        // count should be increased even if no trace file collected to make missing traces visible.
        mTraceCountMap.put(device, mTraceCountMap.getOrDefault(device, 0) + 1);
        try {
            Map<String, String> extraConfigs = new LinkedHashMap<>();
            if (mInstrumentationPkgName != null) {
                extraConfigs.put("atrace_apps", String.format("\"%s\"", mInstrumentationPkgName));
            }
            mPerfettoTraceRecorder.startTrace(device, extraConfigs);
        } catch (IOException e) {
            CLog.d(
                    "Failed to start perfetto trace on %s trace-count:%d with error: %s",
                    device.getSerialNumber(), mTraceCountMap.get(device), e.getMessage());
        }
    }

    private void collectTraceFileFromDevice(ITestDevice device, String eventName) {
        File traceFile = mPerfettoTraceRecorder.stopTrace(device);
        if (traceFile == null) {
            CLog.d(
                    "Failed to collect device trace from %s on event:%s trace-count:%d.",
                    device.getSerialNumber(), eventName, mTraceCountMap.get(device));
            return;
        }
        CLog.d(
                "Collected device trace from %s on event:%s. trace-count:%d. size:%d",
                device.getSerialNumber(),
                eventName,
                mTraceCountMap.get(device),
                traceFile.length());
        String name =
                String.format(
                        NAME_FORMAT,
                        device.getSerialNumber(),
                        mTraceCountMap.get(device),
                        eventName);
        mTraceFilesMap.put(traceFile, name);
    }

    private void logTraceFiles() {
        for (Map.Entry<File, String> entry : mTraceFilesMap.entrySet()) {
            try (FileInputStreamSource source = new FileInputStreamSource(entry.getKey(), true)) {
                super.testLog(entry.getValue(), LogDataType.PERFETTO, source);
            }
        }
    }

    public void setInstrumentationPkgName(String packageName) {
        mInstrumentationPkgName = packageName;
    }

    @Override
    public void rebootStarted(ITestDevice device) throws DeviceNotAvailableException {
        super.rebootStarted(device);
        // save previous trace running on this device.
        collectTraceFileFromDevice(device, "rebootStarted");
    }

    @Override
    public void rebootEnded(ITestDevice device) throws DeviceNotAvailableException {
        super.rebootEnded(device);
        // start new trace running on this device
        startTraceOnDevice(device);
    }
}
