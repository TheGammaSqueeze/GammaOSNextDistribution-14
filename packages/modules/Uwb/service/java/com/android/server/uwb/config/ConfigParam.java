/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb.config;

import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.BITS_PER_SAMPLE;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.NUMBER_OF_BURSTS;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_CHANNEL_NUMBER;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_DATA_TYPE;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_PREAMBLE_CODE_INDEX;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_PREAMBLE_DURATION;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_PRF_MODE;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_RFRAME_CONFIG;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_SESSION_PRIORITY;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.RADAR_TIMING_PARAMS;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.SAMPLES_PER_SWEEP;
import static android.hardware.uwb.fira_android.UwbVendorRadarAppConfigTlvTypes.SWEEP_OFFSET;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_HOP_MODE_KEY;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_LAST_INDEX_USED;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_PULSESHAPE_COMBO;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_RANGING_PROTOCOL_VER;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_URSK_TTL;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_UWB_CONFIG_ID;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.CCC_UWB_TIME0;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.DIAGRAMS_FRAME_REPORTS_FIELDS;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.ENABLE_DIAGNOSTICS;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.NB_OF_AZIMUTH_MEASUREMENTS;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.NB_OF_ELEVATION_MEASUREMENTS;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvTypes.NB_OF_RANGE_MEASUREMENTS;
import static android.hardware.uwb.fira_android.UwbVendorSessionAppConfigTlvValues.AOA_RESULT_REQ_ANTENNA_INTERLEAVING;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ConfigParam {

    /**
     * App Config Parameter ID's
     **/
    public static final int DEVICE_TYPE = 0x00;
    public static final int RANGING_ROUND_USAGE = 0X01;
    public static final int STS_CONFIG = 0X02;
    public static final int MULTI_NODE_MODE = 0X03;
    public static final int CHANNEL_NUMBER = 0x04;
    public static final int NUMBER_OF_CONTROLEES = 0x05;
    public static final int DEVICE_MAC_ADDRESS = 0x06;
    public static final int DST_MAC_ADDRESS = 0x07;
    public static final int SLOT_DURATION = 0x08;
    public static final int RANGING_INTERVAL = 0x09;
    public static final int STS_INDEX = 0x0A;
    public static final int MAC_FCS_TYPE = 0x0B;
    public static final int RANGING_ROUND_CONTROL = 0x0C;
    public static final int AOA_RESULT_REQ = 0x0D;
    public static final int RANGE_DATA_NTF_CONFIG = 0x0E;
    public static final int RANGE_DATA_NTF_PROXIMITY_NEAR = 0x0F;
    public static final int RANGE_DATA_NTF_PROXIMITY_FAR = 0x10;
    public static final int DEVICE_ROLE = 0x11;
    public static final int RFRAME_CONFIG = 0x12;
    public static final int RSSI_REPORTING = 0x13;
    public static final int PREAMBLE_CODE_INDEX = 0x14;
    public static final int SFD_ID = 0x15;
    public static final int PSDU_DATA_RATE = 0x16;
    public static final int PREAMBLE_DURATION = 0x17;
    public static final int LINK_LAYER_MODE = 0x18;
    public static final int DATA_REPETITION_COUNT = 0x19;
    public static final int RANGING_TIME_STRUCT = 0x1A;
    public static final int SLOTS_PER_RR = 0x1B;
    public static final int TX_ADAPTIVE_PAYLOAD_POWER = 0x1C;
    // TODO: Ensure this value is correct in the final 2.0 specification.
    public static final int RANGE_DATA_NTF_AOA_BOUND = 0x1D;
    public static final int PRF_MODE = 0x1F;
    public static final int CAP_SIZE_RANGE = 0x20;
    public static final int SCHEDULED_MODE = 0x22;
    public static final int KEY_ROTATION = 0x23;
    public static final int KEY_ROTATION_RATE = 0x24;
    public static final int SESSION_PRIORITY = 0x25;
    public static final int MAC_ADDRESS_MODE = 0x26;
    public static final int VENDOR_ID = 0x27;
    public static final int STATIC_STS_IV = 0x28;
    public static final int NUMBER_OF_STS_SEGMENTS = 0x29;
    public static final int MAX_RR_RETRY = 0x2A;
    public static final int UWB_INITIATION_TIME = 0x2B;
    public static final int HOPPING_MODE = 0x2C;
    public static final int BLOCK_STRIDE_LENGTH = 0x2D;
    public static final int RESULT_REPORT_CONFIG = 0x2E;
    public static final int IN_BAND_TERMINATION_ATTEMPT_COUNT = 0x2F;
    public static final int SUB_SESSION_ID = 0x30;
    public static final int BPRF_PHR_DATA_RATE = 0x31;
    public static final int MAX_NUMBER_OF_MEASUREMENTS = 0x32;
    public static final int UL_TDOA_TX_INTERVAL = 0x33;
    public static final int UL_TDOA_RANDOM_WINDOW = 0x34;
    public static final int STS_LENGTH = 0x35;
    public static final int SUSPEND_RANGING_ROUNDS = 0x36;
    public static final int UL_TDOA_DEVICE_ID = 0x38;
    public static final int UL_TDOA_TX_TIMESTAMP = 0x39;
    public static final int MIN_FRAMES_PER_RR = 0x3A;
    public static final int MTU_SIZE = 0x3B;
    public static final int INTER_FRAME_INTERVAL = 0x3C;
    public static final int DL_TDOA_BLOCK_STRIDING = 0x43;
    public static final int SESSION_KEY = 0x45;
    public static final int SUBSESSION_KEY = 0x46;
    public static final int SESSION_DATA_TRANSFER_STATUS_NTF_CONFIG = 0x47;
    public static final int SESSION_TIME_BASE = 0x48;
    public static final int APPLICATION_DATA_ENDPOINT = 0x4C;

    // Android specific params.
    public static final int NUM_RANGE_MEASUREMENTS = NB_OF_RANGE_MEASUREMENTS;
    public static final int NUM_AOA_AZIMUTH_MEASUREMENTS = NB_OF_AZIMUTH_MEASUREMENTS;
    public static final int NUM_AOA_ELEVATION_MEASUREMENTS = NB_OF_ELEVATION_MEASUREMENTS;

    public static final int ENABLE_DIAGNOSTICS_RSSI = ENABLE_DIAGNOSTICS;
    public static final int ENABLE_DIAGRAMS_FRAME_REPORTS_FIELDS = DIAGRAMS_FRAME_REPORTS_FIELDS;

    public static final int VENDOR_ID_BYTE_COUNT = 2;
    public static final int STATIC_STS_IV_BYTE_COUNT = 6;
    public static final int AOA_RESULT_REQ_INTERLEAVING = AOA_RESULT_REQ_ANTENNA_INTERLEAVING;

    // CCC
    //OpenParams
    public static final int RANGING_PROTOCOL_VER = CCC_RANGING_PROTOCOL_VER;
    public static final int UWB_CONFIG_ID = CCC_UWB_CONFIG_ID;
    public static final int PULSESHAPE_COMBO = CCC_PULSESHAPE_COMBO;
    public static final int URSK_TTL = CCC_URSK_TTL;
    public static final int LAST_STS_INDEX_USED = CCC_LAST_INDEX_USED;
    //StartedParams
    public static final int HOP_MODE_KEY = CCC_HOP_MODE_KEY;
    public static final int HOP_MODE_KEY_BYTE = 16;
    public static final int UWB_TIME0 = CCC_UWB_TIME0;

    public static final int RANGING_PROTOCOL_VER_BYTE_COUNT = 2;

    // Radar App Config Parameter IDs
    public static final int RADAR_TIMING_PARAMS_KEY = RADAR_TIMING_PARAMS;
    public static final int SAMPLES_PER_SWEEP_KEY = SAMPLES_PER_SWEEP;
    public static final int CHANNEL_NUMBER_KEY = RADAR_CHANNEL_NUMBER;
    public static final int SWEEP_OFFSET_KEY = SWEEP_OFFSET;
    public static final int RFRAME_CONFIG_KEY = RADAR_RFRAME_CONFIG;
    public static final int PREAMBLE_DURATION_KEY = RADAR_PREAMBLE_DURATION;
    public static final int PREAMBLE_CODE_INDEX_KEY = RADAR_PREAMBLE_CODE_INDEX;
    public static final int SESSION_PRIORITY_KEY = RADAR_SESSION_PRIORITY;
    public static final int BITS_PER_SAMPLE_KEY = BITS_PER_SAMPLE;
    public static final int PRF_MODE_KEY = RADAR_PRF_MODE;
    public static final int NUMBER_OF_BURSTS_KEY = NUMBER_OF_BURSTS;
    public static final int RADAR_DATA_TYPE_KEY = RADAR_DATA_TYPE;

    public static byte[] getTagBytes(int tagType) {
        int tagLength = 1;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).putInt(tagType);
        return Arrays.copyOfRange(buffer.array(), Integer.BYTES - tagLength, Integer.BYTES);
    }
}
