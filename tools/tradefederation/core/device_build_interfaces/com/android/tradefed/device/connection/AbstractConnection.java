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
package com.android.tradefed.device.connection;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

/** Abstract connection representation. */
public abstract class AbstractConnection {

    /**
     * Initialize the connection of the device.
     *
     * @throws TargetSetupError
     * @throws DeviceNotAvailableException
     */
    public void initializeConnection() throws TargetSetupError, DeviceNotAvailableException {
        // Empty by default
    }

    /** Notify when doAdbReboot is called. */
    public void notifyAdbRebootCalled() {
        // Empty by default
    }

    /**
     * Reconnect the connection to the device.
     *
     * @param serial The device serial number.
     * @throws DeviceNotAvailableException
     */
    public void reconnect(String serial) throws DeviceNotAvailableException {
        // Empty by default
    }

    /**
     * Reconnect the connection to the device for the recovery routine.
     *
     * @param serial The device serial number.
     * @throws DeviceNotAvailableException
     */
    public void reconnectForRecovery(String serial) throws DeviceNotAvailableException {
        reconnect(serial);
    }

    /** Clean up the connection. */
    public void tearDownConnection() {
        // Empty by default
    }

    /**
     * Recover the given device with device reset.
     *
     * @param device the {@link ITestDevice} is used for device reset handler.
     * @param snapshotId the snapshotId is used for fetching the correct snapshot to restore.
     * @param dnae the {@link DeviceNotAvailableException} is existing device not available
     *     exception.
     * @throws DeviceNotAvailableException if fail on device recovery.
     */
    public void recoverVirtualDevice(
            ITestDevice device, String snapshotId, DeviceNotAvailableException dnae)
            throws DeviceNotAvailableException {
        throw dnae;
    }

    /**
     * Snapshot the given device
     *
     * @param device the {@link ITestDevice} is used for device snapshot handler.
     * @param snapshotId the snapshotId is the name of the snapshot that will be saved.
     * @param dnae the {@link DeviceNotAvailableException} is existing device not available
     *     exception.
     * @throws DeviceNotAvailableException if fail on device recovery.
     */
    public void snapshotDevice(ITestDevice device, String snapshotId)
            throws DeviceNotAvailableException {
        // Empty by default
    }
}
