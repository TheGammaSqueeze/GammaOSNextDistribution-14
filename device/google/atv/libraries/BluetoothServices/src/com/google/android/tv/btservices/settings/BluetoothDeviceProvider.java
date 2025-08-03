/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.settings;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.google.android.tv.btservices.remote.DfuManager;
import com.google.android.tv.btservices.remote.RemoteProxy.DfuResult;
import com.google.android.tv.btservices.remote.Version;

public interface BluetoothDeviceProvider {

    interface Listener {
        void onDeviceUpdated(BluetoothDevice device);
    }

    int getBatteryLevel(BluetoothDevice device);

    String mapBatteryLevel(Context context, BluetoothDevice device, int level);

    Version getVersion(BluetoothDevice device);

    boolean hasUpgrade(BluetoothDevice device);

    boolean isBatteryLow(BluetoothDevice device);

    DfuResult getDfuState(BluetoothDevice device);

    void startDfu(BluetoothDevice device);

    void connectDevice(BluetoothDevice device);

    void disconnectDevice(BluetoothDevice device);

    void forgetDevice(BluetoothDevice device);

    void renameDevice(BluetoothDevice device, String newName);

    void addListener(Listener listener);

    void removeListener(Listener listener);

    void addListener(DfuManager.Listener listener);

    void removeListener(DfuManager.Listener listener);
}
