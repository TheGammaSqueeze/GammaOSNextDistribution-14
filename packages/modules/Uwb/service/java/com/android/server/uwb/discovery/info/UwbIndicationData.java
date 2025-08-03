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
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the UWB Indication Data according to FiRa BLE OOB v1.0 specification.
 */
public class UwbIndicationData {

    private static final String LOG_TAG = UwbIndicationData.class.getSimpleName();

    // Minimum size of the full data
    private static final int UWB_INDICATION_DATA_SIZE = 2;

    // The capabilities field within UWB indication data
    private static final byte FIRA_UWB_SUPPORT_BITMASK = (byte) 0x80;
    private static final byte ISO14443_SUPPORT_BITMASK = 0x40;
    private static final byte UWB_REGULATORY_INFO_AVAILABLE_IN_AD_BITMASK = 0x20;
    private static final byte UWB_REGULATORY_INFO_AVAILABLE_IN_OOB_BITMASK = 0x10;
    private static final byte FIRA_PROFILE_INFO_AVAILABLE_IN_AD_BITMASK = 0x08;
    private static final byte FIRA_PROFILE_INFO_AVAILABLE_IN_OOB_BITMASK = 0x04;
    private static final byte CAPABILITIES_RESERVED_FIELD_BITMASK = 0x02;
    private static final byte CAPABILITIES_RESERVED_FIELD_DATA = 0x0;
    private static final byte DUAL_GAP_ROLE_SUPPORT_BITMASK = 0x01;

    public final boolean firaUwbSupport;
    public final boolean iso14443Support;
    public final boolean uwbRegulartoryInfoAvailableInAd;
    public final boolean uwbRegulartoryInfoAvailableInOob;
    public final boolean firaProfileInfoAvailableInAd;
    public final boolean firaProfileInfoAvailableInOob;
    public final boolean dualGapRoleSupport;
    public final int bluetoothRssiThresholdDbm;
    public final SecureComponentInfo[] secureComponentInfos;

    /**
     * Generate the UwbIndicationData from raw bytes array.
     *
     * @param bytes byte array containing the UWB Indication Data encoding based on the FiRa
     *     specification.
     * @return decode bytes into {@link UwbIndicationData}, else null if invalid.
     */
    @Nullable
    public static UwbIndicationData fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            logw("Failed to convert empty into UWB Indication Data.");
            return null;
        }

        if (bytes.length < UWB_INDICATION_DATA_SIZE) {
            logw("Failed to convert bytes into UWB Indication Data due to invalid data size.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        byte uwbCapabilities = byteBuffer.get();
        boolean firaUwbSupport = (uwbCapabilities & FIRA_UWB_SUPPORT_BITMASK) != 0;
        boolean iso14443Support = (uwbCapabilities & ISO14443_SUPPORT_BITMASK) != 0;
        boolean uwbRegulartoryInfoAvailableInAd =
                (uwbCapabilities & UWB_REGULATORY_INFO_AVAILABLE_IN_AD_BITMASK) != 0;
        boolean uwbRegulartoryInfoAvailableInOob =
                (uwbCapabilities & UWB_REGULATORY_INFO_AVAILABLE_IN_OOB_BITMASK) != 0;
        boolean firaProfileInfoAvailableInAd =
                (uwbCapabilities & FIRA_PROFILE_INFO_AVAILABLE_IN_AD_BITMASK) != 0;
        boolean firaProfileInfoAvailableInOob =
                (uwbCapabilities & FIRA_PROFILE_INFO_AVAILABLE_IN_OOB_BITMASK) != 0;
        boolean dualGapRoleSupport = (uwbCapabilities & DUAL_GAP_ROLE_SUPPORT_BITMASK) != 0;
        byte capabilitiesReservedField =
                (byte) (uwbCapabilities & CAPABILITIES_RESERVED_FIELD_BITMASK);
        if (capabilitiesReservedField != CAPABILITIES_RESERVED_FIELD_DATA) {
            logw(
                    "Failed to convert bytes into UWB Indication Data due to reserved field in uwb"
                            + " capabilities is unmatched");
            return null;
        }

        int bluetoothRssiThresholdDbm = (int) byteBuffer.get();

        int info_size = (bytes.length - UWB_INDICATION_DATA_SIZE) / SecureComponentInfo.size();
        List<SecureComponentInfo> infos = new ArrayList<>();

        for (int i = 0; i < info_size; i++) {
            byte[] secureComponentInfoBytes = new byte[SecureComponentInfo.size()];
            byteBuffer.get(secureComponentInfoBytes);
            SecureComponentInfo info = SecureComponentInfo.fromBytes(secureComponentInfoBytes);
            if (info != null) {
                infos.add(info);
            }
        }

        return new UwbIndicationData(
                firaUwbSupport,
                iso14443Support,
                uwbRegulartoryInfoAvailableInAd,
                uwbRegulartoryInfoAvailableInOob,
                firaProfileInfoAvailableInAd,
                firaProfileInfoAvailableInOob,
                dualGapRoleSupport,
                bluetoothRssiThresholdDbm,
                infos.toArray(new SecureComponentInfo[0]));
    }

    /**
     * Generate raw bytes array from UwbIndicationData.
     *
     * @param info the UWB Indication Data
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull UwbIndicationData info) {
        byte[] data =
                new byte[] {
                    convertCapabilitiesField(info),
                    DataTypeConversionUtil.i32ToByteArray(info.bluetoothRssiThresholdDbm)[3]
                };
        for (SecureComponentInfo i : info.secureComponentInfos) {
            data = Bytes.concat(data, SecureComponentInfo.toBytes(i));
        }
        return data;
    }

    private static byte convertCapabilitiesField(@NonNull UwbIndicationData info) {
        return (byte)
                ((((info.firaUwbSupport ? 1 : 0) << 7) & FIRA_UWB_SUPPORT_BITMASK)
                        | (((info.iso14443Support ? 1 : 0) << 6) & ISO14443_SUPPORT_BITMASK)
                        | (((info.uwbRegulartoryInfoAvailableInAd ? 1 : 0) << 5)
                                & UWB_REGULATORY_INFO_AVAILABLE_IN_AD_BITMASK)
                        | (((info.uwbRegulartoryInfoAvailableInOob ? 1 : 0) << 4)
                                & UWB_REGULATORY_INFO_AVAILABLE_IN_OOB_BITMASK)
                        | (((info.firaProfileInfoAvailableInAd ? 1 : 0) << 3)
                                & FIRA_PROFILE_INFO_AVAILABLE_IN_AD_BITMASK)
                        | (((info.firaProfileInfoAvailableInOob ? 1 : 0) << 2)
                                & FIRA_PROFILE_INFO_AVAILABLE_IN_OOB_BITMASK)
                        | ((CAPABILITIES_RESERVED_FIELD_DATA << 1)
                                & CAPABILITIES_RESERVED_FIELD_BITMASK)
                        | ((info.dualGapRoleSupport ? 1 : 0) & DUAL_GAP_ROLE_SUPPORT_BITMASK));
    }

    public UwbIndicationData(
            boolean firaUwbSupport,
            boolean iso14443Support,
            boolean uwbRegulartoryInfoAvailableInAd,
            boolean uwbRegulartoryInfoAvailableInOob,
            boolean firaProfileInfoAvailableInAd,
            boolean firaProfileInfoAvailableInOob,
            boolean dualGapRoleSupport,
            int bluetoothRssiThresholdDbm,
            SecureComponentInfo[] secureComponentInfos) {
        this.firaUwbSupport = firaUwbSupport;
        this.iso14443Support = iso14443Support;
        this.uwbRegulartoryInfoAvailableInAd = uwbRegulartoryInfoAvailableInAd;
        this.uwbRegulartoryInfoAvailableInOob = uwbRegulartoryInfoAvailableInOob;
        this.firaProfileInfoAvailableInAd = firaProfileInfoAvailableInAd;
        this.firaProfileInfoAvailableInOob = firaProfileInfoAvailableInOob;
        this.dualGapRoleSupport = dualGapRoleSupport;
        this.bluetoothRssiThresholdDbm = bluetoothRssiThresholdDbm;
        this.secureComponentInfos = secureComponentInfos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UwbIndicationData: firaUwbSupport=")
                .append(firaUwbSupport)
                .append(" iso14443Support=")
                .append(iso14443Support)
                .append(" uwbRegulartoryInfoAvailableInAd=")
                .append(uwbRegulartoryInfoAvailableInAd)
                .append(" uwbRegulartoryInfoAvailableInOob=")
                .append(uwbRegulartoryInfoAvailableInOob)
                .append(" firaProfileInfoAvailableInAd=")
                .append(firaProfileInfoAvailableInAd)
                .append(" firaProfileInfoAvailableInOob=")
                .append(firaProfileInfoAvailableInOob)
                .append(" dualGapRoleSupport=")
                .append(dualGapRoleSupport)
                .append(" bluetoothRssiThresholdDbm=")
                .append(bluetoothRssiThresholdDbm)
                .append(" ")
                .append(Arrays.toString(secureComponentInfos));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
