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
import android.bluetooth.BluetoothLeAudioCodecStatus;

import java.util.List;

/**
 * Callback definitions for interacting with LE Audio service
 *
 * @hide
 */
oneway interface IBluetoothLeAudioCallback {
    void onCodecConfigChanged(in int groupId, in BluetoothLeAudioCodecStatus status);
    void onGroupNodeAdded(in BluetoothDevice device, int groupId);
    void onGroupNodeRemoved(in BluetoothDevice device, int groupId);
    void onGroupStatusChanged(int groupId, int groupStatus);
    void onGroupStreamStatusChanged(int groupId, int groupStreamStatus);
}
