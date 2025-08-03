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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothDevice;

/**  Local provider proxy to customize events. */
abstract class LocalBluetoothDeviceProvider implements BluetoothDeviceProvider {

    abstract BluetoothDeviceProvider getHostBluetoothDeviceProvider();

    @Override
    public void connectDevice(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.connectDevice(device);
        }
    }

    @Override
    public void disconnectDevice(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.disconnectDevice(device);
        }
    }

    @Override
    public void forgetDevice(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.forgetDevice(device);
        }
    }

    @Override
    public void renameDevice(BluetoothDevice device, String newName) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.renameDevice(device, newName);
        }
    }

    @Override
    public void addListener(Listener listener) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.addListener(listener);
        }
    }

    @Override
    public void removeListener(Listener listener) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.removeListener(listener);
        }
    }
}
