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
import com.google.android.tv.btservices.remote.RemoteProxy;
import com.google.android.tv.btservices.remote.Version;
import com.google.android.tv.btservices.R;

/**
 * Local provider proxy to customize events.
 */
abstract class LocalBluetoothDeviceProvider implements BluetoothDeviceProvider {

    abstract BluetoothDeviceProvider getHostBluetoothDeviceProvider();

    @Override
    public int getBatteryLevel(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.getBatteryLevel(device);
        }
        return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
    }

    @Override
    public String mapBatteryLevel(Context context, BluetoothDevice device, int level) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.mapBatteryLevel(context, device, level);
        }
        return context.getString(R.string.settings_remote_battery_level_percentage_label, level);
    }

    @Override
    public Version getVersion(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.getVersion(device);
        }
        return Version.BAD_VERSION;
    }

    @Override
    public boolean hasUpgrade(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.hasUpgrade(device);
        }
        return false;
    }

    @Override
    public boolean isBatteryLow(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.isBatteryLow(device);
        }
        return false;
    }

    @Override
    public RemoteProxy.DfuResult getDfuState(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            return provider.getDfuState(device);
        }
        return null;
    }

    @Override
    public void startDfu(BluetoothDevice device) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.startDfu(device);
        }
    }

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

    @Override
    public void addListener(DfuManager.Listener listener) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.addListener(listener);
        }
    }

    @Override
    public void removeListener(DfuManager.Listener listener) {
        BluetoothDeviceProvider provider = getHostBluetoothDeviceProvider();
        if (provider != null) {
            provider.removeListener(listener);
        }
    }
}
