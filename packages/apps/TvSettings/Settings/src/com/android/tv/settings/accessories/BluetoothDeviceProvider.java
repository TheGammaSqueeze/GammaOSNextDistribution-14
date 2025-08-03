/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothDevice;

/** An interface for interacting with BluetoothDevice. */
public interface BluetoothDeviceProvider {

    /** A Listener that wishes to be notified when devices are updated. */
    interface Listener {
        void onDeviceUpdated(BluetoothDevice device);
    }

    /** Registers a Listener that will be notified when device status is updated. */
    void addListener(Listener listener);

    /** Unregisters the Listener that monitors the device status. */
    void removeListener(Listener listener);

    /** Connects to a "remembered" device that was unpaired. */
    void connectDevice(BluetoothDevice device);

    /** Disconnects with (but not forget) a paired device. */
    void disconnectDevice(BluetoothDevice device);

    /** Renames a device. */
    void renameDevice(BluetoothDevice device, String newName);

    /** Forgets a paired device. */
    void forgetDevice(BluetoothDevice device);
}
