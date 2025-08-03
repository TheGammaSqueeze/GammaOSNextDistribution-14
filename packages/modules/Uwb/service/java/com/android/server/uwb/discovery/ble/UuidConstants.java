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
package com.android.server.uwb.discovery.ble;

import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;

/** UUIDs of the UWB BLE OOB. */
public class UuidConstants {

    /* The FiRa service UUID for connector primary and connector secondary as defined in Bluetooth
     * Specification Supplement v10. Little endian encoding.
     */
    public static final byte[] FIRA_CP_UUID = new byte[] {(byte) 0xF3, (byte) 0xFF};
    public static final byte[] FIRA_CS_UUID = new byte[] {(byte) 0xF4, (byte) 0xFF};

    public static final ParcelUuid FIRA_CP_PARCEL_UUID = BluetoothUuid.parseUuidFrom(FIRA_CP_UUID);
    public static final ParcelUuid FIRA_CS_PARCEL_UUID = BluetoothUuid.parseUuidFrom(FIRA_CS_UUID);

    /* FiRa Connector Primary GATT Characteristic UUIDs according to FiRa BLE OOB v1.0
     * specification.
     */
    public static final ParcelUuid CP_IN_CONTROL_POINT_UUID =
            ParcelUuid.fromString("00002A00-0000-1000-8000-D09200000001");
    public static final ParcelUuid CP_OUT_CONTROL_POINT_UUID =
            ParcelUuid.fromString("00002A01-0000-1000-8000-D09200000001");
    public static final ParcelUuid CP_FIRA_CONNECTOR_CAPABILITIES_UUID =
            ParcelUuid.fromString("00002A02-0000-1000-8000-D09200000001");

    /* Client Characteristic Configuration Descriptor UUID defined by Bluetooth specification.
     */
    public static final ParcelUuid CCCD_UUID = BluetoothUuid.parseUuidFrom(new byte[] {0x29, 0x02});

    private UuidConstants() {}
}
