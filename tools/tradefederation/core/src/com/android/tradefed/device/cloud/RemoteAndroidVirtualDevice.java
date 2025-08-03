/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import com.android.ddmlib.IDevice;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.device.TestDeviceOptions.InstanceType;
import com.android.tradefed.device.connection.AdbSshConnection;
import com.android.tradefed.device.connection.DefaultConnection;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;

import java.io.File;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Extends {@link RemoteAndroidDevice} behavior for a full stack android device running in the
 * Google Compute Engine (Gce). Assume the device serial will be in the format
 * <hostname>:<portnumber> in adb.
 */
public class RemoteAndroidVirtualDevice extends RemoteAndroidDevice {

    /**
     * Creates a {@link RemoteAndroidVirtualDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public RemoteAndroidVirtualDevice(
            IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }

    /**
     * Cuttlefish has a special feature that brings the tombstones to the remote host where we can
     * get them directly.
     */
    @Override
    public List<File> getTombstones() throws DeviceNotAvailableException {
        InstanceType type = getOptions().getInstanceType();
        if (InstanceType.CUTTLEFISH.equals(type) || InstanceType.REMOTE_NESTED_AVD.equals(type)) {
            if (getConnection() instanceof AdbSshConnection) {
                return ((AdbSshConnection) getConnection()).getTombstones();
            }
        }
        // If it's not Cuttlefish, use the standard call.
        return super.getTombstones();
    }

    @Override
    public DeviceDescriptor getDeviceDescriptor(boolean shortDescriptor) {
        DeviceDescriptor descriptor = super.getDeviceDescriptor(shortDescriptor);
        if (getConnection() instanceof DefaultConnection) {
            String initialSerial = ((DefaultConnection) getConnection()).getInitialSerial();
            if (!initialSerial.equals(descriptor.getSerial())) {
                // Alter the display for the console.
                descriptor =
                        new DeviceDescriptor(
                                descriptor,
                                initialSerial,
                                initialSerial + "[" + descriptor.getSerial() + "]");
            }
        }
        return descriptor;
    }

    /**
     * Returns the {@link GceAvdInfo} from the created remote VM. Returns null if the bring up was
     * not successful.
     *
     * @deprecated should use the connection API directly
     */
    @Deprecated
    public @Nullable GceAvdInfo getAvdInfo() {
        if (getConnection() instanceof AdbSshConnection) {
            CLog.w("Getting GceAvdInfo via deprecated method.");
            return ((AdbSshConnection) getConnection()).getAvdInfo();
        }
        return null;
    }

    @Deprecated
    public boolean powerwashGce() throws TargetSetupError {
        throw new UnsupportedOperationException(
                "RemoteAndroidVirtualDevice#powerwash should never be called. Only connection"
                        + " one should be invoked.");
    }

    /**
     * Attempt to powerwash a GCE instance
     *
     * @return returns CommandResult of the powerwash attempts
     * @throws TargetSetupError
     */
    @Deprecated
    public CommandResult powerwash() throws TargetSetupError {
        return powerwashGce(null, null);
    }

    /**
     * @deprecated Removed in favor of the connection one
     */
    @Deprecated
    public CommandResult powerwashGce(String user, Integer offset) throws TargetSetupError {
        throw new UnsupportedOperationException(
                "RemoteAndroidVirtualDevice#powerwash should never be called. Only connection"
                        + " one should be invoked.");
    }
}
