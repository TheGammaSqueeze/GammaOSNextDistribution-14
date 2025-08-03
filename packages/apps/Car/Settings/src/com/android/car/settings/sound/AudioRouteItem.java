/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.settings.sound;

import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUS;

import android.annotation.SuppressLint;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;

import androidx.annotation.Nullable;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * A class to encapsulate audio route information.
 */
public class AudioRouteItem {
    private String mName;
    private String mAddress;

    private @AudioDeviceInfo.AudioDeviceType int mAudioRouteType;
    @Nullable
    private CachedBluetoothDevice mBluetoothDevice;
    @Nullable
    private AudioDeviceAttributes mAudioDeviceAttributes;

    @SuppressLint("MissingPermission")
    public AudioRouteItem(CachedBluetoothDevice bluetoothDevice) {
        mName = bluetoothDevice.getName();
        mAddress = bluetoothDevice.getAddress();
        mAudioRouteType = TYPE_BLUETOOTH_A2DP;
        mBluetoothDevice = bluetoothDevice;
    }

    public AudioRouteItem(AudioDeviceAttributes audioDeviceAttributes) {
        mName = audioDeviceAttributes.getName();
        mAddress = audioDeviceAttributes.getAddress();
        mAudioRouteType = TYPE_BUS;
        mAudioDeviceAttributes = audioDeviceAttributes;
    }

    public String getName() {
        return mName;
    }

    public String getAddress() {
        return mAddress;
    }

    public @AudioDeviceInfo.AudioDeviceType int getAudioRouteType() {
        return mAudioRouteType;
    }

    @Nullable
    public CachedBluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    @Nullable
    public AudioDeviceAttributes getAudioDeviceAttributes() {
        return mAudioDeviceAttributes;
    }
}
