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

package com.android.car.settings.qc;

import android.content.Context;
import android.net.Uri;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.io.IOException;

/**
 * Background worker for the {@link PairedBluetoothDevices} QCItem.
 */
public class PairedBluetoothDevicesWorker
        extends SettingsQCBackgroundWorker<PairedBluetoothDevices>
        implements BluetoothCallback {

    private final LocalBluetoothManager mBluetoothManager;

    public PairedBluetoothDevicesWorker(Context context, Uri uri) {
        super(context, uri);
        mBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */ null);
    }

    @Override
    protected void onQCItemSubscribe() {
        mBluetoothManager.getEventManager().registerCallback(this);
    }

    @Override
    protected void onQCItemUnsubscribe() {
        mBluetoothManager.getEventManager().unregisterCallback(this);
    }

    @Override
    public void close() throws IOException {
        mBluetoothManager.getEventManager().unregisterCallback(this);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        notifyQCItemChange();
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        notifyQCItemChange();
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        notifyQCItemChange();
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        notifyQCItemChange();
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        notifyQCItemChange();
    }

    @Override
    public void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        notifyQCItemChange();
    }

    @Override
    public void onProfileConnectionStateChanged(CachedBluetoothDevice cachedDevice,
            int state, int bluetoothProfile) {
        notifyQCItemChange();
    }
}
