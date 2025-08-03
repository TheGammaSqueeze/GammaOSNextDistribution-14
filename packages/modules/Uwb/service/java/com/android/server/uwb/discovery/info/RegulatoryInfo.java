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

import com.android.server.uwb.UwbCountryCode;
import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds data of the UWB Regulatory Information according to FiRa BLE OOB v1.0
 * specification.
 */
public class RegulatoryInfo {

    private static final String LOG_TAG = RegulatoryInfo.class.getSimpleName();

    // Minimum size of the full info
    private static final int MIN_UWB_REGULATORY_INFO_SIZE = 9;

    // The fields within UWB regulatory data
    private static final int SOURCE_OF_INFO_FIELD_MASK = 0xF0;

    private static final byte SOURCE_OF_INFO_USER_DEFINED_BITMASK = 0x8;
    private static final byte SOURCE_OF_INFO_SATELLITE_NAVI_SYS_BITMASK = 0x4;
    private static final byte SOURCE_OF_INFO_CELLULAR_SYS_BITMASK = 0x2;
    private static final byte SOURCE_OF_INFO_ANOTHER_FIRA_DEVICE_BITMASK = 0x1;

    private static final byte UWB_REGULATORY_INFO_RESERVED_FIELD_MASK = 0xE;
    private static final byte UWB_REGULATORY_INFO_RESERVED_FIELD_DATA = 0x0;

    private static final int OUTDOORS_TRANSMISSION_PERMITTED_FIELD_MASK = 0x1;
    private static final int COUNTRY_CODE_FIELD_SIZE = 2;

    private static final int CHANNEL_AND_POWER_FIELD_SIZE = 2;

    /**
     * Source of information of this regulatory info
     */
    public enum SourceOfInfo {
        USER_DEFINED,
        SATELLITE_NAVIGATION_SYSTEM,
        CELLULAR_SYSTEM,
        ANOTHER_FIRA_DEVICE,
    }

    public final SourceOfInfo sourceOfInfo;
    public final boolean outdoorsTransmittionPermitted;
    public final String countryCode;
    public final int timestampSecondsSinceEpoch;
    public final ChannelPowerInfo[] channelPowerInfos;

    /**
     * Generate the RegulatoryInfo from raw bytes array.
     *
     * @param bytes byte array containing the UWB regulatory data encoding based on the FiRa
     *     specification.
     * @return decode bytes into {@link RegulatoryInfo}, else null if invalid.
     */
    @Nullable
    public static RegulatoryInfo fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            logw("Failed to convert empty into UWB Regulatory Info.");
            return null;
        }

        if (bytes.length < MIN_UWB_REGULATORY_INFO_SIZE) {
            logw("Failed to convert bytes into UWB Regulatory Info due to invalid data size.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byte firstByte = byteBuffer.get();

        byte sourceOfInfoByte = (byte) ((firstByte & SOURCE_OF_INFO_FIELD_MASK) >> 4);
        SourceOfInfo sourceOfInfo = parseSourceOfInfo(sourceOfInfoByte);
        if (sourceOfInfo == null) {
            logw(
                    "Failed to convert bytes into UWB Regulatory Info due to invalid"
                            + " source Of info.");
            return null;
        }

        if ((firstByte & UWB_REGULATORY_INFO_RESERVED_FIELD_MASK)
                != UWB_REGULATORY_INFO_RESERVED_FIELD_DATA) {
            logw(
                    "Failed to convert bytes into UWB Regulatory Info due to invalid"
                            + " reserved field data.");
            return null;
        }

        boolean outdoorsTransmittionPermitted =
                (firstByte & OUTDOORS_TRANSMISSION_PERMITTED_FIELD_MASK) != 0;
        byte[] countryCodeBytes = new byte[COUNTRY_CODE_FIELD_SIZE];
        byteBuffer.get(countryCodeBytes);
        String countryCode = new String(countryCodeBytes, StandardCharsets.UTF_8);

        if (!UwbCountryCode.isValid(countryCode)) {
            logw("Failed to convert bytes into UWB Regulatory Info due to invalid country code");
            return null;
        }

        int timestampSecondsSinceEpoch = byteBuffer.getInt(); // Big-endian

        int info_size =
                1 + (bytes.length - MIN_UWB_REGULATORY_INFO_SIZE) / CHANNEL_AND_POWER_FIELD_SIZE;
        List<ChannelPowerInfo> infos = new ArrayList<>();

        for (int i = 0; i < info_size; i++) {
            byte[] channelPowerInfoBytes = new byte[CHANNEL_AND_POWER_FIELD_SIZE];
            byteBuffer.get(channelPowerInfoBytes);
            ChannelPowerInfo info = ChannelPowerInfo.fromBytes(channelPowerInfoBytes);
            if (info != null) {
                infos.add(info);
            }
        }

        return new RegulatoryInfo(
                sourceOfInfo,
                outdoorsTransmittionPermitted,
                countryCode,
                timestampSecondsSinceEpoch,
                infos.toArray(new ChannelPowerInfo[0]));
    }

    /**
     * Generate raw bytes array from RegulatoryInfo.
     *
     * @param info the UWB regulatory data.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull RegulatoryInfo info) {
        byte[] data =
                new byte[] {
                    (byte)
                            (convertSourceOfInfo(info.sourceOfInfo)
                                    | UWB_REGULATORY_INFO_RESERVED_FIELD_DATA
                                    | convertOutdoorsTransmittionPermitted(
                                            info.outdoorsTransmittionPermitted))
                };
        data =
                Bytes.concat(
                        data,
                        info.countryCode.getBytes(StandardCharsets.UTF_8),
                        DataTypeConversionUtil.i32ToByteArray(info.timestampSecondsSinceEpoch));
        for (ChannelPowerInfo i : info.channelPowerInfos) {
            data = Bytes.concat(data, ChannelPowerInfo.toBytes(i));
        }
        return data;
    }

    @Nullable
    private static SourceOfInfo parseSourceOfInfo(byte sourceOfInfoByte) {
        if (sourceOfInfoByte == 0) {
            logw("Failed to parse 0 into Source Of Info.");
            return null;
        }
        int count = 0;
        SourceOfInfo info = SourceOfInfo.USER_DEFINED;
        if ((sourceOfInfoByte & SOURCE_OF_INFO_USER_DEFINED_BITMASK) != 0) {
            count += 1;
            info = SourceOfInfo.USER_DEFINED;
        }
        if ((sourceOfInfoByte & SOURCE_OF_INFO_SATELLITE_NAVI_SYS_BITMASK) != 0) {
            count += 1;
            info = SourceOfInfo.SATELLITE_NAVIGATION_SYSTEM;
        }
        if ((sourceOfInfoByte & SOURCE_OF_INFO_CELLULAR_SYS_BITMASK) != 0) {
            count += 1;
            info = SourceOfInfo.CELLULAR_SYSTEM;
        }
        if ((sourceOfInfoByte & SOURCE_OF_INFO_ANOTHER_FIRA_DEVICE_BITMASK) != 0) {
            count += 1;
            info = SourceOfInfo.ANOTHER_FIRA_DEVICE;
        }
        if (count > 1) {
            logw("Failed to parse multiple Source Of Info.");
            return null;
        }
        return info;
    }

    private static byte convertSourceOfInfo(SourceOfInfo info) {
        byte result = 0;
        switch (info) {
            case USER_DEFINED:
                result = SOURCE_OF_INFO_USER_DEFINED_BITMASK;
                break;
            case SATELLITE_NAVIGATION_SYSTEM:
                result = SOURCE_OF_INFO_SATELLITE_NAVI_SYS_BITMASK;
                break;
            case CELLULAR_SYSTEM:
                result = SOURCE_OF_INFO_CELLULAR_SYS_BITMASK;
                break;
            case ANOTHER_FIRA_DEVICE:
                result = SOURCE_OF_INFO_ANOTHER_FIRA_DEVICE_BITMASK;
                break;
        }
        return (byte) ((result << 4) & SOURCE_OF_INFO_FIELD_MASK);
    }

    private static byte convertOutdoorsTransmittionPermitted(
            boolean outdoorsTransmittionPermitted) {
        return (byte)
                ((outdoorsTransmittionPermitted ? 1 : 0)
                        & OUTDOORS_TRANSMISSION_PERMITTED_FIELD_MASK);
    }

    public RegulatoryInfo(
            SourceOfInfo sourceOfInfo,
            boolean outdoorsTransmittionPermitted,
            String countryCode,
            int timestampSecondsSinceEpoch,
            ChannelPowerInfo[] channelPowerInfos) {
        this.sourceOfInfo = sourceOfInfo;
        this.outdoorsTransmittionPermitted = outdoorsTransmittionPermitted;
        this.countryCode = countryCode;
        this.timestampSecondsSinceEpoch = timestampSecondsSinceEpoch;
        this.channelPowerInfos = channelPowerInfos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RegulatoryInfo: SourceOfInfo=")
                .append(sourceOfInfo)
                .append(" OutdoorsTransmittionPermitted=")
                .append(outdoorsTransmittionPermitted)
                .append(" CountryCode=")
                .append(countryCode)
                .append(" TimestampSecondsSinceEpoch=")
                .append(timestampSecondsSinceEpoch)
                .append(" ")
                .append(Arrays.toString(channelPowerInfos));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
