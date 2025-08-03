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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.device.cloud.GceAvdInfo;
import com.android.tradefed.device.cloud.NestedRemoteDevice;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.device.connection.AbstractConnection;
import com.android.tradefed.device.connection.AdbSshConnection;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.service.IRemoteFeature;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.SerializationUtil;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import java.io.IOException;

/** Server side implementation of device snapshot. */
public class DeviceSnapshotFeature
        implements IRemoteFeature, IConfigurationReceiver, ITestInformationReceiver {

    public static final String DEVICE_SNAPSHOT_FEATURE_NAME = "snapshotDevice";
    public static final String DEVICE_NAME = "device_name";
    public static final String SNAPSHOT_ID = "snapshot_id";
    public static final String RESTORE_FLAG = "restore_flag";

    private IConfiguration mConfig;
    private TestInformation mTestInformation;

    @Override
    public String getName() {
        return DEVICE_SNAPSHOT_FEATURE_NAME;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInformation = testInformation;
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInformation;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        String deviceName = request.getArgsMap().get(DEVICE_NAME);
        if (deviceName == null) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("No device_name args specified."));
            return responseBuilder.build();
        }

        IDeviceConfiguration configHolder = mConfig.getDeviceConfigByName(deviceName);
        int index = 0;
        for (IDeviceConfiguration deviceConfig : mConfig.getDeviceConfig()) {
            if (deviceConfig == configHolder) {
                break;
            }
            index++;
        }

        try {
            mTestInformation.setActiveDeviceIndex(index);
            AbstractConnection connection = mTestInformation.getDevice().getConnection();
            if ((mTestInformation.getDevice() instanceof RemoteAndroidVirtualDevice)
                    || (connection instanceof AdbSshConnection)) {
                GceAvdInfo info = getAvdInfo(mTestInformation.getDevice(), connection);
                if (info == null) {
                    throw new RuntimeException("GceAvdInfo was null. skipping");
                }
                Integer offset = info.getDeviceOffset();
                String user = info.getInstanceUser();

                String snapshotId = request.getArgsMap().get(SNAPSHOT_ID);
                boolean restoreFlag = Boolean.parseBoolean(request.getArgsMap().get(RESTORE_FLAG));
                if (restoreFlag) {
                    restoreSnapshot(responseBuilder, connection, user, offset, snapshotId);
                } else {
                    snapshot(responseBuilder, connection, user, offset, snapshotId);
                }
            } else if (mTestInformation.getDevice() instanceof RemoteAndroidDevice) {
                responseBuilder.setResponse(" RemoteAndroidDevice has no snapshot support.");
            } else if (mTestInformation.getDevice() instanceof NestedRemoteDevice) {
                // TODO: Support NestedRemoteDevice
                responseBuilder.setResponse(" NestedRemoteDevice has no snapshot support.");
            }
        } catch (Exception e) {
            String error = "Failed to snapshot device.";
            try {
                error = SerializationUtil.serializeToString(e);
            } catch (RuntimeException | IOException serializationError) {
                // Ignore
            }
            responseBuilder.setErrorInfo(ErrorInfo.newBuilder().setErrorTrace(error));
        } finally {
            mTestInformation.setActiveDeviceIndex(0);
        }
        return responseBuilder.build();
    }

    private void snapshot(
            FeatureResponse.Builder responseBuilder,
            AbstractConnection connection,
            String user,
            Integer offset,
            String snapshotId)
            throws DeviceNotAvailableException, TargetSetupError {
        String response =
                String.format(
                        "Attempting snapshot device on %s (%s).",
                        mTestInformation.getDevice().getSerialNumber(),
                        mTestInformation.getDevice().getClass().getSimpleName());
        try {
            long startTime = System.currentTimeMillis();
            CommandResult result = snapshotGce(connection, user, offset, snapshotId);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Failed to snapshot device: %s. status:%s\n"
                                        + "stdout: %s\n"
                                        + "stderr:%s",
                                mTestInformation.getDevice().getSerialNumber(),
                                result.getStatus(),
                                result.getStdout(),
                                result.getStderr()),
                        mTestInformation.getDevice().getSerialNumber(),
                        DeviceErrorIdentifier.DEVICE_FAILED_TO_SNAPSHOT);
            }
            response +=
                    String.format(
                            " Snapshot finished in %d ms.", System.currentTimeMillis() - startTime);
        } finally {
            responseBuilder.setResponse(response);
        }
    }

    private void restoreSnapshot(
            FeatureResponse.Builder responseBuilder,
            AbstractConnection connection,
            String user,
            Integer offset,
            String snapshotId)
            throws DeviceNotAvailableException, TargetSetupError {
        String response =
                String.format(
                        "Attempting restore device snapshot on %s (%s) to %s.",
                        mTestInformation.getDevice().getSerialNumber(),
                        mTestInformation.getDevice().getClass().getSimpleName(),
                        snapshotId);
        try {
            long startTime = System.currentTimeMillis();
            CommandResult result = restoreSnapshotGce(connection, user, offset, snapshotId);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Failed to restore snapshot on device: %s. status:%s\n"
                                        + "stdout: %s\n"
                                        + "stderr:%s",
                                mTestInformation.getDevice().getSerialNumber(),
                                result.getStatus(),
                                result.getStdout(),
                                result.getStderr()),
                        mTestInformation.getDevice().getSerialNumber(),
                        DeviceErrorIdentifier.DEVICE_FAILED_TO_RESTORE_SNAPSHOT);
            }
            response +=
                    String.format(
                            " Restoring snapshot finished in %d ms.",
                            System.currentTimeMillis() - startTime);
        } finally {
            responseBuilder.setResponse(response);
        }
    }

    private GceAvdInfo getAvdInfo(ITestDevice device, AbstractConnection connection) {
        if (connection instanceof AdbSshConnection) {
            return ((AdbSshConnection) connection).getAvdInfo();
        }
        if (device instanceof RemoteAndroidVirtualDevice) {
            return ((RemoteAndroidVirtualDevice) device).getAvdInfo();
        }
        return null;
    }

    private CommandResult snapshotGce(
            AbstractConnection connection, String user, Integer offset, String snapshotId)
            throws TargetSetupError {
        if (connection instanceof AdbSshConnection) {
            return ((AdbSshConnection) connection).snapshotGce(user, offset, snapshotId);
        }
        CommandResult res = new CommandResult(CommandStatus.EXCEPTION);
        res.setStderr("Incorrect connection type while attempting device snapshot");
        return res;
    }

    private CommandResult restoreSnapshotGce(
            AbstractConnection connection, String user, Integer offset, String snapshotId)
            throws TargetSetupError {
        if (connection instanceof AdbSshConnection) {
            return ((AdbSshConnection) connection).restoreSnapshotGce(user, offset, snapshotId);
        }
        CommandResult res = new CommandResult(CommandStatus.EXCEPTION);
        res.setStderr("Incorrect connection type while attempting device restore");
        return res;
    }
}
