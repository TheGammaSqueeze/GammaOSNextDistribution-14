/*
 * Copyright 2022 The Android Open Source Project
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

package android.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;

/**
* Callback definitions for interacting with LE broadcast assistant service
*
* @hide
*/
interface IBluetoothLeBroadcastAssistantCallback {
    void onSearchStarted(in int reason);
    void onSearchStartFailed(in int reason);
    void onSearchStopped(in int reason);
    void onSearchStopFailed(in int reason);
    void onSourceFound(in BluetoothLeBroadcastMetadata source);
    void onSourceAdded(in BluetoothDevice sink, in int sourceId, in int reason);
    void onSourceAddFailed(in BluetoothDevice sink, in BluetoothLeBroadcastMetadata source,
            in int reason);
    void onSourceModified(in BluetoothDevice sink, in int sourceId, in int reason);
    void onSourceModifyFailed(in BluetoothDevice sink, in int sourceId, in int reason);
    void onSourceRemoved(in BluetoothDevice sink, in int sourceId, in int reason);
    void onSourceRemoveFailed(in BluetoothDevice sink, in int sourceId, in int reason);
    void onReceiveStateChanged(in BluetoothDevice sink, in int sourceId,
            in BluetoothLeBroadcastReceiveState state);
    void onSourceLost(in int broadcastId);
}
