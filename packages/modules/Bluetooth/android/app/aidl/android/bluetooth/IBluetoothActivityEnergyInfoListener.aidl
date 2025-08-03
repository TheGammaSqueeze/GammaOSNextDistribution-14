/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.bluetooth.BluetoothActivityEnergyInfo;

/**
 * Interface for Bluetooth activity energy info listener.
 *
 * {@hide}
 */
oneway interface IBluetoothActivityEnergyInfoListener
{
    /**
     * AdapterService to BluetoothAdapter callback providing current Bluetooth
     * activity energy info.
     * @param info the Bluetooth activity energy info
     */
    void onBluetoothActivityEnergyInfoAvailable(in BluetoothActivityEnergyInfo info);
}