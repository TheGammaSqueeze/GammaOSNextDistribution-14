/*
 * Copyright 2023 The Android Open Source Project
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

package hardware.google.bluetooth.ccc;

import hardware.google.bluetooth.ccc.IBluetoothCccCallback;
import hardware.google.bluetooth.ccc.LmpEventId;
import hardware.google.bluetooth.ccc.IBluetoothCccCallback;

@VintfStability
interface IBluetoothCcc {
    /**
     * API to monitor Bluetooth and system timestamp for given Bluetooth device when Bluetooth
     * controller send/receive given LMP events.
     *
     * @param callback An instance of the |IBluetoothCccCallback| HIDL interface object.
     * @param address Bluetooth address to monitor.
     * @param lmpEventIds LMP events to monitor.
     */
    void registerForLmpEvents(in IBluetoothCccCallback callback, in byte[6] address,
        in LmpEventId[] lmpEventIds);

    /**
     * API to stop monitoring a given Bluetooth device.
     *
     * @param address Bluetooth device to stop monitoring.
     */
    void unregisterLmpEvents(in byte[6] address);
}
