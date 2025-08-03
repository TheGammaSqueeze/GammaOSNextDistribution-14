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
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;

/**
 * Holds FiRa UWB vendor specific data according to FiRa BLE OOB v1.0 specification.
 */
public class VendorSpecificData {
    private static final String LOG_TAG = VendorSpecificData.class.getSimpleName();

    private static final int VENDOR_ID_FIELD_SIZE = 2;

    // Minimum size of the full info
    private static final int MIN_VENDOR_SPECIFIC_DATA_SIZE = VENDOR_ID_FIELD_SIZE;

    // Vendor ID as assigned by Bluetooth SIG.
    public final int vendorId;
    // Data encoded with vendor specific encoding.
    public final byte[] vendorData;

    /**
     * Generate the VendorSpecificData from raw bytes array.
     *
     * @param bytes byte array containing the UWB vendor specific data.
     * @param vendorId Optionally provide the vendor id if already extracted from the bytes.
     * @return decode bytes into {@link VendorSpecificData}, else null if invalid.
     */
    @Nullable
    public static VendorSpecificData fromBytes(@NonNull byte[] bytes, Optional<Integer> vendorId) {
        if (ArrayUtils.isEmpty(bytes)) {
            logw("Failed to convert empty into UWB vendor specific data.");
            return null;
        }

        if (!vendorId.isPresent() && bytes.length < MIN_VENDOR_SPECIFIC_DATA_SIZE) {
            logw("Failed to convert bytes into UWB vendor specific data due to invalid data size.");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int id;
        if (vendorId.isPresent()) {
            id = vendorId.get().intValue();
        } else {
            id = buffer.order(ByteOrder.LITTLE_ENDIAN).getShort();
        }

        byte[] vendorData = new byte[buffer.remaining()];
        buffer.order(ByteOrder.BIG_ENDIAN).get(vendorData);

        return new VendorSpecificData(id, vendorData);
    }

    /**
     * Generate raw bytes array from VendorSpecificData.
     *
     * @param info the UWB vendor specific data.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull VendorSpecificData info) {
        byte[] id = DataTypeConversionUtil.i32ToLeByteArray(info.vendorId);
        return Bytes.concat(new byte[] {id[0], id[1]}, info.vendorData);
    }

    public VendorSpecificData(@IntRange(from = 0, to = 65535) int vendorId, byte[] vendorData) {
        this.vendorId = vendorId;
        this.vendorData = vendorData;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VendorSpecificData: VendorId=")
                .append(vendorId)
                .append(" VendorSpecificData=")
                .append(Arrays.toString(vendorData));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
