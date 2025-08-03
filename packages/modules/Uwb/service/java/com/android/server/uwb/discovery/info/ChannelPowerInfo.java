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
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

/**
 * UWB channel power information according to FiRa BLE OOB v1.0
 * specification.
 */
public class ChannelPowerInfo {
    private static final String LOG_TAG = ChannelPowerInfo.class.getSimpleName();

    // Minimum size of the full info
    private static final int MIN_CHANNEL_POWER_INFO_SIZE = 2;

    private static final int ENCODE_1ST_CHANNEL_BITMASK = 0xF0;
    private static final byte ENCODE_NUM_OF_CHANNEL_BITMASK = 0x0E;
    private static final byte ENCODE_OUTDOOR_OR_INDOOR_BITMASK = 0x01;

    public final int firstChannel;
    public final int numOfChannels;
    public final boolean isIndoor;
    public final int averagePowerLimitDbm;

    /**
     * Generate the ChannelPowerInfo from raw bytes array.
     *
     * @param bytes byte array containing the channel power info as part of the UWB regulatory,
     *     data encoding based on the FiRa specification.
     * @return decode bytes into {@link ChannelPowerInfo}.
     */
    public static ChannelPowerInfo fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            Log.w(LOG_TAG, "Failed to convert empty into UWB channel power info.");
            return null;
        }

        if (bytes.length < MIN_CHANNEL_POWER_INFO_SIZE) {
            Log.w(
                    LOG_TAG,
                    "Failed to convert bytes into UWB channel power info due to invalid data"
                            + " size.");
            return null;
        }

        int firstChannel = (int) (((bytes[0] & ENCODE_1ST_CHANNEL_BITMASK) >> 4) & 0x000F);
        int numOfChannels = (int) (((bytes[0] & ENCODE_NUM_OF_CHANNEL_BITMASK) >> 1) & 0x0007);
        boolean isIndoor = (bytes[0] & ENCODE_OUTDOOR_OR_INDOOR_BITMASK) != 0;
        int averagePowerLimitDbm = (int) bytes[1];
        return new ChannelPowerInfo(firstChannel, numOfChannels, isIndoor, averagePowerLimitDbm);
    }

    /**
     * Generate raw bytes array from ChannelPowerInfo.
     *
     * @param info the channel power data.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(@NonNull ChannelPowerInfo info) {
        return new byte[] {
            (byte)
                    (convertFirstChannel(info.firstChannel)
                            | convertNumOfChannels(info.numOfChannels)
                            | convertIsIndoor(info.isIndoor)),
            DataTypeConversionUtil.i32ToByteArray(info.averagePowerLimitDbm)[3]
        };
    }

    private static byte convertFirstChannel(int firstChannel) {
        return (byte) ((firstChannel << 4) & ENCODE_1ST_CHANNEL_BITMASK);
    }

    private static byte convertNumOfChannels(int numOfChannels) {
        return (byte) ((numOfChannels << 1) & ENCODE_NUM_OF_CHANNEL_BITMASK);
    }

    private static byte convertIsIndoor(boolean isIndoor) {
        return (byte) ((isIndoor ? 1 : 0) & ENCODE_OUTDOOR_OR_INDOOR_BITMASK);
    }

    public ChannelPowerInfo(
            int firstChannel, int numOfChannels, boolean isIndoor, int averagePowerLimitDbm) {
        this.firstChannel = firstChannel;
        this.isIndoor = isIndoor;
        this.numOfChannels = numOfChannels;
        this.averagePowerLimitDbm = averagePowerLimitDbm;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChannelPowerInfo: mFirstChannel=")
                .append(firstChannel)
                .append(" mNumOfChannels=")
                .append(numOfChannels)
                .append(" mIsIndoor=")
                .append(isIndoor)
                .append(" mAveragePowerLimitDbm=")
                .append(averagePowerLimitDbm);
        return sb.toString();
    }
}
