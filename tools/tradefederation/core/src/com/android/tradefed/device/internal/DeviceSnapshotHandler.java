/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tradefed.device.internal;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.IsolationGrade;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.util.SerializationUtil;

import com.proto.tradefed.feature.FeatureResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility handling Cuttlefish snapshot. This is meant to only be used internally to the test
 * harness. This shouldn't be called during a test.
 */
public class DeviceSnapshotHandler {

    private final TradefedFeatureClient mClient;
    private final IInvocationContext mContext;

    public DeviceSnapshotHandler() {
        this(new TradefedFeatureClient(), CurrentInvocation.getInvocationContext());
    }

    @VisibleForTesting
    DeviceSnapshotHandler(TradefedFeatureClient client, IInvocationContext context) {
        mClient = client;
        mContext = context;
    }

    /**
     * Calls snapshot of the given device.
     *
     * @param device The device to snapshot.
     * @param snapshotId Snapshot ID for the device to be saved to.
     * @return True if snapshot was successful, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean snapshotDevice(ITestDevice device, String snapshotId)
            throws DeviceNotAvailableException {
        if (device.getIDevice() instanceof StubDevice) {
            CLog.d("Device '%s' is a stub device. skipping snapshot.", device.getSerialNumber());
            return true;
        }
        FeatureResponse response;
        try {
            Map<String, String> args = new HashMap<>();
            args.put(DeviceSnapshotFeature.DEVICE_NAME, mContext.getDeviceName(device));
            args.put(DeviceSnapshotFeature.SNAPSHOT_ID, snapshotId);
            response =
                    mClient.triggerFeature(
                            DeviceSnapshotFeature.DEVICE_SNAPSHOT_FEATURE_NAME, args);
            CLog.d("Response from snapshot request: %s", response.getResponse());
        } finally {
            mClient.close();
        }
        if (response.hasErrorInfo()) {
            String trace = response.getErrorInfo().getErrorTrace();
            // Handle if it's an exception error.
            Object o = null;
            try {
                o = SerializationUtil.deserialize(trace);
            } catch (IOException | RuntimeException e) {
                CLog.e(e);
            }
            if (o instanceof DeviceNotAvailableException) {
                throw (DeviceNotAvailableException) o;
            } else if (o instanceof IHarnessException) {
                IHarnessException exception = (IHarnessException) o;
                throw new HarnessRuntimeException(
                        "Exception while snapshotting the device.", exception);
            } else if (o instanceof Exception) {
                throw new HarnessRuntimeException(
                        "Exception while snapshotting the device.",
                        (Exception) o,
                        InfraErrorIdentifier.UNDETERMINED);
            }

            CLog.e("Snapshot failed: %s", response.getErrorInfo().getErrorTrace());
            return false;
        }

        // TODO: parse snapshot ID from response, and save it to mContext.

        // Save snapshot performance data
        Pattern durationPattern = Pattern.compile("Snapshot\\sfinished\\sin (\\d+)\\sms");
        Matcher matcher;
        matcher = durationPattern.matcher(response.getResponse());
        if (matcher.find()) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_SUCCESS_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_DURATIONS, matcher.group(1));
        }
        return true;
    }

    /**
     * Calls restore snapshot of the given device.
     *
     * @param device The device to restore.
     * @param snapshotId Snapshot ID for the device to be restored to.
     * @return True if restore was successful, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean restoreSnapshotDevice(ITestDevice device, String snapshotId)
            throws DeviceNotAvailableException {
        if (device.getIDevice() instanceof StubDevice) {
            CLog.d(
                    "Device '%s' is a stub device. skipping restoring snapshot.",
                    device.getSerialNumber());
            return true;
        }
        FeatureResponse response;
        try {
            Map<String, String> args = new HashMap<>();
            args.put(DeviceSnapshotFeature.SNAPSHOT_ID, snapshotId);
            args.put(DeviceSnapshotFeature.RESTORE_FLAG, "true");
            args.put(DeviceSnapshotFeature.DEVICE_NAME, mContext.getDeviceName(device));
            response =
                    mClient.triggerFeature(
                            DeviceSnapshotFeature.DEVICE_SNAPSHOT_FEATURE_NAME, args);
            CLog.d(
                    "Response from restoring snapshot(%s) request: %s",
                    snapshotId, response.getResponse());
        } finally {
            mClient.close();
        }
        if (response.hasErrorInfo()) {
            String trace = response.getErrorInfo().getErrorTrace();
            // Handle if it's an exception error.
            Object o = null;
            try {
                o = SerializationUtil.deserialize(trace);
            } catch (IOException | RuntimeException e) {
                CLog.e(e);
            }
            if (o instanceof DeviceNotAvailableException) {
                throw (DeviceNotAvailableException) o;
            } else if (o instanceof IHarnessException) {
                IHarnessException exception = (IHarnessException) o;
                throw new HarnessRuntimeException(
                        "Exception while restoring snapshot of the device.", exception);
            } else if (o instanceof Exception) {
                throw new HarnessRuntimeException(
                        "Exception while restoring snapshot of the device.",
                        (Exception) o,
                        InfraErrorIdentifier.UNDETERMINED);
            }

            CLog.e("Restoring snapshot failed: %s", response.getErrorInfo().getErrorTrace());
            return false;
        }
        if (device instanceof NativeDevice) {
            ((NativeDevice) device).resetContentProviderSetup();
        }
        CurrentInvocation.setModuleIsolation(IsolationGrade.FULLY_ISOLATED);
        CurrentInvocation.setRunIsolation(IsolationGrade.FULLY_ISOLATED);

        // Save snapshot performance data
        Pattern durationPattern = Pattern.compile("Restoring snapshot\\sfinished\\sin (\\d+)\\sms");
        Matcher matcher;
        matcher = durationPattern.matcher(response.getResponse());
        if (matcher.find()) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_RESTORE_SUCCESS_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_RESTORE_DURATIONS, matcher.group(1));
        }
        return true;
    }
}
