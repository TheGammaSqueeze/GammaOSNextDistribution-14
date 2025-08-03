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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;

import com.google.common.primitives.Bytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds data of the FiRa UWB Profile support info according to FiRa BLE OOB v1.0 and CSML v1.0
 * specification.
 */
public class FiraProfileSupportInfo {

    private static final String LOG_TAG = FiraProfileSupportInfo.class.getSimpleName();

    /**
     * FiRa defined profiles with ID.
     */
    public enum FiraProfile {
        PACS(1); // Physical Access Control System

        private final int mId;
        private static Map sMap = new HashMap<>();

        FiraProfile(int id) {
            this.mId = id;
        }

        static {
            for (FiraProfile profile : FiraProfile.values()) {
                sMap.put(profile.mId, profile);
            }
        }

        /**
         * Get the FiraProfile based on the given ID.
         *
         * @param id profile ID defined by FiRa.
         * @return {@link FiraProfile} associated with the id, else null if invalid.
         */
        @Nullable
        public static FiraProfile idOf(int id) {
            return (FiraProfile) sMap.get(id);
        }

        public int getId() {
            return mId;
        }
    }

    public final FiraProfile[] supportedFiraProfiles;

    /**
     * Generate the FiraProfileSupportInfo from raw bytes array.
     *
     * @param bytes byte array containing the FiRa UWB Profile support data encoding based on the
     *     FiRa specification. Nth bit represents FiRa Service ID “N+1”. Bit 0 (the least
     *     significant) represents FiRa Service ID 1.
     * @return decode bytes into {@link FiraProfileSupportInfo}, else null if invalid.
     */
    @Nullable
    public static FiraProfileSupportInfo fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            logw("Failed to convert empty into FiRa Profile Support Info.");
            return null;
        }

        List<FiraProfile> supportedProfiles = new ArrayList<>();

        int current_id = 1;
        // Loop through each byte start from the least significant byte.
        for (int i = 1; i <= bytes.length; i++) {
            // Loop through each bit start from the least significant bit.
            byte b = bytes[bytes.length - i];
            for (int j = 0; j < Byte.SIZE; j++) {
                if ((b & (0x1 << j)) != 0) {
                    FiraProfile profile = FiraProfile.idOf(current_id);
                    if (profile != null) {
                        supportedProfiles.add(profile);
                    } else {
                        logw("Invalid Profile ID in FiRa Profile Support Info. ID=" + current_id);
                    }
                }
                current_id++;
            }
        }

        return new FiraProfileSupportInfo(supportedProfiles.toArray(new FiraProfile[0]));
    }

    /**
     * Generate raw bytes array from FiraProfileSupportInfo.
     *
     * @param info the UWB regulatory data.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull FiraProfileSupportInfo info) {
        List<Byte> byteList = new ArrayList<>(); // Little-endian

        for (FiraProfile profile : info.supportedFiraProfiles) {
            int bit_position = profile.getId() - 1;
            int nth_byte = bit_position / Byte.SIZE;
            byte nth_bit = (byte) (bit_position % Byte.SIZE);
            // Extends the byteList will zeros
            if (byteList.size() <= nth_byte) {
                byteList.addAll(Bytes.asList(new byte[1 + nth_byte - byteList.size()]));
            }
            byte b = (byte) (byteList.get(nth_byte).byteValue() | (0x1 << nth_bit));
            byteList.set(nth_byte, Byte.valueOf(b));
        }

        byte[] data = new byte[byteList.size()];
        // Convert to big-endian byte array
        for (int i = 0; i < byteList.size(); i++) {
            data[i] = byteList.get(byteList.size() - i - 1).byteValue();
        }

        return data;
    }

    public FiraProfileSupportInfo(FiraProfile[] supportedFiraProfiles) {
        this.supportedFiraProfiles = supportedFiraProfiles;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FiraProfileSupportInfo: SupportedFiraProfiles=")
                .append(Arrays.toString(supportedFiraProfiles));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
