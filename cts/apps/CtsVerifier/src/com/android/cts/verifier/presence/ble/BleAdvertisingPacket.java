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

package com.android.cts.verifier.presence.ble;

import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BleAdvertisingPacket {
    private static final String TAG = BleAdvertisingPacket.class.getName();

    public static final int ADVERTISING_PACKET_LENGTH = 12;
    public static final int MAX_REFERENCE_DEVICE_NAME_LENGTH = 10;

    private final String referenceDeviceName;

    private final byte randomDeviceId;
    private final byte rssiMedianFromReferenceDevice;

    public BleAdvertisingPacket(String referenceDeviceName, byte randomDeviceId,
            byte rssiMedianFromReferenceDevice) {
        this.referenceDeviceName = referenceDeviceName;
        this.randomDeviceId = randomDeviceId;
        this.rssiMedianFromReferenceDevice = rssiMedianFromReferenceDevice;
    }

    public byte[] toBytes() {
        return ByteBuffer.allocate(ADVERTISING_PACKET_LENGTH)
                .put(referenceDeviceName.getBytes(StandardCharsets.UTF_8))
                .put(MAX_REFERENCE_DEVICE_NAME_LENGTH, randomDeviceId)
                .put(MAX_REFERENCE_DEVICE_NAME_LENGTH + 1, rssiMedianFromReferenceDevice)
                .array();
    }

    public String getReferenceDeviceName() {
        return referenceDeviceName;
    }

    public byte getRandomDeviceId() {
        return randomDeviceId;
    }

    public byte getRssiMedianFromReferenceDevice() {
        return rssiMedianFromReferenceDevice;
    }

    @Nullable
    public static BleAdvertisingPacket fromBytes(byte[] packet) {
        if (packet == null || packet.length != ADVERTISING_PACKET_LENGTH) {
            Log.e(TAG, "Advertising packet is null or not the right size");
            return null;
        }
        String referenceDeviceName = new String(
                ByteBuffer.wrap(packet, 0, MAX_REFERENCE_DEVICE_NAME_LENGTH - 1).array(),
                StandardCharsets.UTF_8);
        byte randomDeviceId = ByteBuffer.wrap(packet, MAX_REFERENCE_DEVICE_NAME_LENGTH, 1).get();
        byte rssiMedianFromReferenceDevice = ByteBuffer.wrap(packet,
                MAX_REFERENCE_DEVICE_NAME_LENGTH + 1, 1).get();
        return new BleAdvertisingPacket(referenceDeviceName, randomDeviceId,
                rssiMedianFromReferenceDevice);
    }
}
