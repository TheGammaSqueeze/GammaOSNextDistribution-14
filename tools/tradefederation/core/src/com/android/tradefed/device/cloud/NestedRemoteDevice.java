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
package com.android.tradefed.device.cloud;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of the device running inside a remote Cuttlefish VM. It will alter the local
 * device {@link TestDevice} behavior in some cases to take advantage of the setup.
 */
public class NestedRemoteDevice extends TestDevice {

    // TODO: Improve the way we associate nested device with their user
    private static final Map<String, String> IP_TO_USER = new HashMap<>();

    static {
        IP_TO_USER.put("0.0.0.0:6520", "1");
        IP_TO_USER.put("0.0.0.0:6521", "2");
        IP_TO_USER.put("0.0.0.0:6522", "3");
        IP_TO_USER.put("0.0.0.0:6523", "4");
        IP_TO_USER.put("0.0.0.0:6524", "5");
        IP_TO_USER.put("0.0.0.0:6525", "6");
        IP_TO_USER.put("0.0.0.0:6526", "7");
        IP_TO_USER.put("0.0.0.0:6527", "8");
        IP_TO_USER.put("0.0.0.0:6528", "9");
        IP_TO_USER.put("0.0.0.0:6529", "10");
        IP_TO_USER.put("0.0.0.0:6530", "11");
        IP_TO_USER.put("0.0.0.0:6531", "12");
        IP_TO_USER.put("0.0.0.0:6532", "13");
        IP_TO_USER.put("0.0.0.0:6533", "14");
        IP_TO_USER.put("0.0.0.0:6534", "15");
    }

    /**
     * Creates a {@link NestedRemoteDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public NestedRemoteDevice(
            IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
        // TODO: Use IDevice directly
        if (stateMonitor instanceof NestedDeviceStateMonitor) {
            ((NestedDeviceStateMonitor) stateMonitor).setDevice(this);
        }
    }

    /** Teardown and restore the virtual device so testing can proceed. */
    public final boolean resetVirtualDevice() throws DeviceNotAvailableException {
        String usernameId = IP_TO_USER.get(getSerialNumber());
        if (usernameId == null) {
            throw new DeviceNotAvailableException(
                    String.format("Cannot reset %s, no mapping in IP_TO_USER", getSerialNumber()),
                    getSerialNumber(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_RESET);
        }
        String powerwashCommand = String.format("powerwash_cvd -instance_num %s", usernameId);
        long timeout = Math.max(300000L, this.getOptions().getGceCmdTimeout());
        CommandResult powerwashRes = getRunUtil().runTimedCmd(timeout, powerwashCommand.split(" "));
        if (!CommandStatus.SUCCESS.equals(powerwashRes.getStatus())) {
            CLog.e("%s", powerwashRes.getStderr());
            // Log 'adb devices' to confirm device is gone
            CommandResult printAdbDevices = getRunUtil().runTimedCmd(60000L, "adb", "devices");
            CLog.e("%s\n%s", printAdbDevices.getStdout(), printAdbDevices.getStderr());
            return false;
        }
        // Wait for the device to start for real.
        getRunUtil().sleep(5000);
        waitForDeviceAvailable();
        resetContentProviderSetup();
        return true;
    }
}
