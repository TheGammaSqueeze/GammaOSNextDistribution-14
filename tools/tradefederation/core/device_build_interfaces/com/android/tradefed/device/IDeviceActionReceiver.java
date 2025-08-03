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

package com.android.tradefed.device;

/**
 * Provides an API to receive device events.
 *
 * <p>NOTE: This is currently supported for metric collectors only.
 */
public interface IDeviceActionReceiver {
    /**
     * Gets notification when reboot started in device.
     *
     * <p>NOTE: Receivers should avoid rebooting during this callback. Any reboot attempt will be
     * ignored.
     *
     * @param device {@link ITestDevice} where the reboot started.
     */
    public void rebootStarted(ITestDevice device) throws DeviceNotAvailableException;

    /**
     * Gets notification when the reboot ended in device.
     *
     * <p>NOTE: Receivers should avoid rebooting during this callback. Any reboot attempt will be
     * ignored.
     *
     * @param device {@link ITestDevice} where the reboot ended.
     */
    public void rebootEnded(ITestDevice device) throws DeviceNotAvailableException;

    /**
     * Sets whether the {@link IDeviceActionReceiver} should be disabled. Disabling means it will
     * not be registered to the device for receiving device action events.
     */
    public void setDisableReceiver(boolean isDisabled);

    /**
     * Whether the {@link IDeviceActionReceiver} is disabled or not.
     *
     * @return return true if disabled, false otherwise.
     */
    public default boolean isDisabledReceiver() {
        return true;
    }
}
