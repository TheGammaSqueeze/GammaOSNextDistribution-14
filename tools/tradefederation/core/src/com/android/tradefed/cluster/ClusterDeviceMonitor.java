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
package com.android.tradefed.cluster;

import com.google.common.annotations.VisibleForTesting;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.monitoring.LabResourceDeviceMonitor;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An {@link IDeviceMonitor} implementation that reports results to the Tradefed Cluster service.
 */
@OptionClass(alias = "cluster-device-monitor")
public class ClusterDeviceMonitor extends LabResourceDeviceMonitor {

    @Option(
            name = "host-info-cmd",
            description =
                    "A label and command to run periodically, to collect and send host info to the"
                            + " backend. May be repeated. Commands containing spaces should be"
                            + " double-quoted.")
    private Map<String, String> mHostInfoCmds = new HashMap<String, String>();

    private Map<String, String[]> mTokenizedHostInfoCmds = null;

    @Option(
            name = "host-info-cmd-timeout",
            description =
                    "How long to wait for each host-info-cmd to complete, in millis. If the"
                            + " command times out, a (null) value will be passed to the backend for"
                            + " that particular command.")
    private long mHostInfoCmdTimeout = 5 * 1000;

    /** Worker thread to dispatch a cluster host event that includes a snapshot of the devices */
    class EventDispatcher extends Thread {

        private boolean mIsCanceled = false;
        private IClusterEventUploader<ClusterHostEvent> mEventUploader = null;
        private IClusterOptions mClusterOptions = null;

        public EventDispatcher() {
            super("ClusterDeviceMonitor.EventDispatcher");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!mIsCanceled) {
                    dispatch();
                    getRunUtil()
                            .sleep(
                                    ClusterHostUtil.getClusterOptions()
                                            .getDeviceMonitorSnapshotInterval());
                }
            } catch (Exception e) {
                CLog.e(e);
            }
        }

        IClusterEventUploader<ClusterHostEvent> getEventUploader() {
            if (mEventUploader == null) {
                mEventUploader = ClusterHostUtil.getClusterClient().getHostEventUploader();
            }
            return mEventUploader;
        }

        IClusterOptions getClusterOptions() {
            if (mClusterOptions == null) {
                mClusterOptions = ClusterHostUtil.getClusterOptions();
            }
            return mClusterOptions;
        }

        void dispatch() {
            CLog.d("Start device snapshot.");
            final IClusterEventUploader<ClusterHostEvent> eventUploader = getEventUploader();
            final List<DeviceDescriptor> devices = listDevices();
            final ClusterHostEvent.Builder builder =
                    new ClusterHostEvent.Builder()
                            .setHostEventType(ClusterHostEvent.HostEventType.DeviceSnapshot)
                            .setData(getAdditionalHostInfo())
                            .setData(
                                    ClusterHostEvent.LABEL_KEY,
                                    String.join(",", getClusterOptions().getLabels()))
                            .setClusterId(getClusterOptions().getClusterId())
                            .setNextClusterIds(getClusterOptions().getNextClusterIds())
                            .setLabName(getClusterOptions().getLabName());
            Map<String, Map<String, String>> extraInfoByDevice = getExtraInfoByDevice();
            for (DeviceDescriptor device : devices) {
                if (device.isTemporary()) {
                    // Do not report temporary devices, they will go away when the invocation
                    // finish.
                    continue;
                }
                if (ClusterHostUtil.isLocalhostIpPort(device.getSerial())) {
                    continue;
                }
                final ClusterDeviceInfo.Builder deviceBuilder = new ClusterDeviceInfo.Builder();
                String runTargetFormat = getClusterOptions().getRunTargetFormat();
                deviceBuilder.setDeviceDescriptor(device);
                deviceBuilder.setRunTarget(
                        ClusterHostUtil.getRunTarget(
                                device, runTargetFormat, getClusterOptions().getDeviceTag()));
                if (extraInfoByDevice.containsKey(device.getSerial())) {
                    deviceBuilder.addExtraInfo(extraInfoByDevice.get(device.getSerial()));
                }
                builder.addDeviceInfo(deviceBuilder.build());
            }
            // We want to force an upload.
            CLog.d("Dispatched devicesnapshot.");
            eventUploader.postEvent(builder.build());
            eventUploader.flush();
        }

        private Map<String, Map<String, String>> getExtraInfoByDevice() {
            // Get device resource info from cached lab resource.
            return getCachedLabResource().getDeviceList().stream()
                    .collect(
                            Collectors.toMap(
                                    deviceEntity ->
                                            deviceEntity
                                                    .getIdentifierMap()
                                                    .get(
                                                            LabResourceDeviceMonitor
                                                                    .DEVICE_SERIAL_KEY),
                                    this::getExtraInfoFromDeviceEntity));
        }

        private Map<String, String> getExtraInfoFromDeviceEntity(MonitoredEntity deviceEntity) {
            return deviceEntity.getResourceList().stream()
                    .map(this::getExtraInfoFromResource)
                    .reduce(new HashMap<String, String>(), this::mergeMap);
        }

        private Map<String, String> mergeMap(Map<String, String> m1, Map<String, String> m2) {
            m1.putAll(m2);
            return m1;
        }

        private Map<String, String> getExtraInfoFromResource(Resource resource) {
            return resource.getMetricList().stream()
                    .collect(
                            Collectors.toMap(
                                    metric ->
                                            resource.getResourceName().toString()
                                                    + "-"
                                                    + metric.getTag().toString(),
                                    metric -> String.valueOf(metric.getValue())));
        }

        void cancel() {
            mIsCanceled = true;
        }

        boolean isCanceled() {
            return mIsCanceled;
        }
    }

    private EventDispatcher mDispatcher;
    private DeviceLister mDeviceLister;

    /** {@inheritDoc} */
    @Override
    public void run() {
        super.run();
        if (ClusterHostUtil.getClusterOptions().isDeviceMonitorDisabled()) {
            return;
        }
        mDispatcher = getEventDispatcher();
        mDispatcher.start();
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        super.stop();
        if (mDispatcher != null && mDispatcher.isAlive()) {
            mDispatcher.cancel();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        if (lister == null) {
            throw new NullPointerException();
        }
        super.setDeviceLister(lister);
        mDeviceLister = lister;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyDeviceStateChange(
            String serial, DeviceAllocationState oldState, DeviceAllocationState newState) {
        // Nothing happens. We only take snapshots. Maybe we can add state change in the future.
        super.notifyDeviceStateChange(serial, oldState, newState);
    }

    @VisibleForTesting
    EventDispatcher getEventDispatcher() {
        if (mDispatcher == null) {
            mDispatcher = new EventDispatcher();
        }
        return mDispatcher;
    }

    @VisibleForTesting
    List<DeviceDescriptor> listDevices() {
        return mDeviceLister.listDevices();
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /** A helper method to tokenize the host info commands. */
    void tokenizeCommands() {
        if (mTokenizedHostInfoCmds != null && !mTokenizedHostInfoCmds.isEmpty()) {
            // Commands already tokenized and cached. No need to tokenize again.
            return;
        }

        mTokenizedHostInfoCmds = new HashMap<String, String[]>(mHostInfoCmds.size());
        // Tokenize the commands and cache the result
        for (Map.Entry<String, String> entry : mHostInfoCmds.entrySet()) {
            final String key = entry.getKey();
            final String cmd = entry.getValue();

            CLog.d("Tokenized key %s command: %s", key, cmd);
            mTokenizedHostInfoCmds.put(key, QuotationAwareTokenizer.tokenizeLine(cmd));
        }
    }

    /** Queries additional host info from host-info-cmd options in TF configs. */
    Map<String, String> getAdditionalHostInfo() {
        final Map<String, String> info = new HashMap<>();
        this.tokenizeCommands();

        for (Map.Entry<String, String[]> entry : mTokenizedHostInfoCmds.entrySet()) {
            final String key = entry.getKey();
            final String[] cmd = entry.getValue();
            final String cmdString = mHostInfoCmds.get(key);

            final CommandResult result = getRunUtil().runTimedCmdSilently(mHostInfoCmdTimeout, cmd);

            CLog.d("Command %s result: %s", cmdString, result.getStatus().toString());

            if (result.getStatus() == CommandStatus.SUCCESS) {
                info.put(key, result.getStdout());
            } else {
                info.put(key, result.getStderr());
            }
        }

        return info;
    }
}
