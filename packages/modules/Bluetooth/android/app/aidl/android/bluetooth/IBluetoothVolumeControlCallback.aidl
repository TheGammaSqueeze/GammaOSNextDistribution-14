/*
 *  Copyright 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package android.bluetooth;

import android.bluetooth.BluetoothDevice;

import java.util.List;

/**
 * Callback definitions for interacting with Volume Control service
 *
 * @hide
 */
oneway interface IBluetoothVolumeControlCallback {
    void onVolumeOffsetChanged(in BluetoothDevice device, in int instanceId, in int volumeOffset);
    void onVolumeOffsetAudioLocationChanged(in BluetoothDevice device, in int instanceId, in int audioLocation);
    void onVolumeOffsetAudioDescriptionChanged(in BluetoothDevice device, in int instanceId, in String audioDescription);
    void onDeviceVolumeChanged(in BluetoothDevice device, in int volume);
}
