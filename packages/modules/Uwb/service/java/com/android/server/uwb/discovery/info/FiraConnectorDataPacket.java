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

package com.android.server.uwb.discovery.info;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Holds data of the FiRa UWB Connector Data Packet according to FiRa BLE OOB v1.0 and CSML v1.0
 * specification.
 */
public class FiraConnectorDataPacket {
    private static final String TAG = FiraConnectorDataPacket.class.getSimpleName();

    /** Bit position and mask for the data packet header fields. */
    private static final int LAST_CHAINING_PACKET_BITMASK = 0x80;

    private static final int SECID_BITMASK = 0x7F;

    public static final int HEADER_SIZE = 1;

    /** True if this the last packet in a fragmented session, otherwise it is false. */
    public final boolean lastChainingPacket;

    /**
     * Secure Component ID value (unsigned integer in the range 2..127, values 0 and 1 are reserved)
     */
    @IntRange(from = 2, to = 127)
    public final int secid;

    /** Contains (fragment of) FiRa Connector Message data aligned to bytes. */
    @NonNull public final byte[] payload;

    /**
     * Generate the FiraConnectorDataPacket from raw bytes array.
     *
     * @param bytes byte array containing the FiRa UWB Connector Data Packet encoding based on the
     *     FiRa specification.
     * @return decode bytes into {@link FiraConnectorDataPacket}, else null if invalid.
     */
    @Nullable
    public static FiraConnectorDataPacket fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            Log.w(TAG, "Failed to convert empty into FiRa Connector Data Packet.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byte header = byteBuffer.get();
        boolean lastChainingPacket = (header & LAST_CHAINING_PACKET_BITMASK) != 0;
        int secid = header & SECID_BITMASK;

        byte[] payload = new byte[byteBuffer.remaining()];
        byteBuffer.get(payload);

        return new FiraConnectorDataPacket(lastChainingPacket, secid, payload);
    }

    /**
     * Generate raw bytes array from FiraConnectorDataPacket.
     *
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public byte[] toBytes() {
        byte[] header =
                new byte[] {
                    (byte)
                            ((lastChainingPacket ? LAST_CHAINING_PACKET_BITMASK : 0)
                                    | (DataTypeConversionUtil.i32ToByteArray(secid)[3]
                                            & SECID_BITMASK))
                };
        return Bytes.concat(header, payload);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FiraConnectorDataPacket: lastChainingPacket=")
                .append(lastChainingPacket)
                .append(" secid=")
                .append(secid)
                .append(" payload=")
                .append(Arrays.toString(payload));
        return sb.toString();
    }

    public FiraConnectorDataPacket(boolean lastChainingPacket, int secid, byte[] payload) {
        this.lastChainingPacket = lastChainingPacket;
        this.secid = secid;
        this.payload = payload;
    }
}
