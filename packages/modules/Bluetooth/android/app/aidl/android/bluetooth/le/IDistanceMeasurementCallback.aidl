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

package android.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.DistanceMeasurementResult;

/**
 * Callback definitions for interacting with Distance Measurement Manager
 * @hide
 */
oneway interface IDistanceMeasurementCallback {
    void onStarted(in BluetoothDevice device);
    void onStartFail(in BluetoothDevice device, in int reason);
    void onStopped(in BluetoothDevice device, in int reason);
    void onResult(in BluetoothDevice device, in DistanceMeasurementResult result);
}
