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

import android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes;
import android.hardware.uwb.fira_android.UwbVendorCapabilityTlvValues;

public class CapabilityParam {
    /**
     * CR 287 params Fira Version 1.0
     */
    public static final int SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_1_0 = 0x0;
    public static final int SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_1_0 = 0x1;
    public static final int SUPPORTED_DEVICE_ROLES_VER_1_0 = 0x2;
    public static final int SUPPORTED_RANGING_METHOD_VER_1_0 = 0x3;
    public static final int SUPPORTED_STS_CONFIG_VER_1_0 = 0x4;
    public static final int SUPPORTED_MULTI_NODE_MODES_VER_1_0 = 0x5;
    public static final int SUPPORTED_RANGING_TIME_STRUCT_VER_1_0 = 0x6;
    public static final int SUPPORTED_SCHEDULED_MODE_VER_1_0 = 0x7;
    public static final int SUPPORTED_HOPPING_MODE_VER_1_0 = 0x8;
    public static final int SUPPORTED_BLOCK_STRIDING_VER_1_0 = 0x9;
    public static final int SUPPORTED_UWB_INITIATION_TIME_VER_1_0 = 0x0A;
    public static final int SUPPORTED_CHANNELS_VER_1_0 = 0x0B;
    public static final int SUPPORTED_RFRAME_CONFIG_VER_1_0 = 0x0C;
    public static final int SUPPORTED_CC_CONSTRAINT_LENGTH_VER_1_0 = 0x0D;
    public static final int SUPPORTED_BPRF_PARAMETER_SETS_VER_1_0 = 0x0E;
    public static final int SUPPORTED_HPRF_PARAMETER_SETS_VER_1_0 = 0x0F;
    public static final int SUPPORTED_AOA_VER_1_0 = 0x10;
    public static final int SUPPORTED_EXTENDED_MAC_ADDRESS_VER_1_0 = 0x11;
    public static final int SUPPORTED_MAX_MESSAGE_SIZE_VER_1_0 = 0x12;
    public static final int SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_1_0 = 0x13;

    /**
     * CR 287 params Fira Version 2.0
     */
    public static final int SUPPORTED_MAX_MESSAGE_SIZE_VER_2_0 = 0x0;
    public static final int SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_2_0  = 0x1;
    public static final int SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_2_0  = 0x2;
    public static final int SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_2_0  = 0x3;
    public static final int SUPPORTED_DEVICE_TYPE_VER_2_0  = 0x4;
    public static final int SUPPORTED_DEVICE_ROLES_VER_2_0  = 0x5;
    public static final int SUPPORTED_RANGING_METHOD_VER_2_0  = 0x6;
    public static final int SUPPORTED_STS_CONFIG_VER_2_0  = 0x7;
    public static final int SUPPORTED_MULTI_NODE_MODES_VER_2_0  = 0x8;
    public static final int SUPPORTED_RANGING_TIME_STRUCT_VER_2_0  = 0x9;
    public static final int SUPPORTED_SCHEDULED_MODE_VER_2_0  = 0x0A;
    public static final int SUPPORTED_HOPPING_MODE_VER_2_0  = 0x0B;
    public static final int SUPPORTED_BLOCK_STRIDING_VER_2_0  = 0x0C;
    public static final int SUPPORTED_UWB_INITIATION_TIME_VER_2_0  = 0x0D;
    public static final int SUPPORTED_CHANNELS_VER_2_0  = 0x0E;
    public static final int SUPPORTED_RFRAME_CONFIG_VER_2_0  = 0x0F;
    public static final int SUPPORTED_CC_CONSTRAINT_LENGTH_VER_2_0  = 0x10;
    public static final int SUPPORTED_BPRF_PARAMETER_SETS_VER_2_0  = 0x11;
    public static final int SUPPORTED_HPRF_PARAMETER_SETS_VER_2_0  = 0x12;
    public static final int SUPPORTED_AOA_VER_2_0  = 0x13;
    public static final int SUPPORTED_EXTENDED_MAC_ADDRESS_VER_2_0  = 0x14;
    public static final int SUPPORTED_SUSPEND_RANGING_VER_2_0  = 0x15;
    public static final int SUPPORTED_SESSION_KEY_LENGTH_VER_2_0  = 0x16;
    public static final int SUPPORTED_DT_TAG_MAX_ACTIVE_RR_2_0 = 0x18;
    public static final int SUPPORTED_DT_TAG_BLOCK_SKIPPING_2_0 = 0x19;
    public static final int SUPPORTED_PSDU_LENGTH_2_0 = 0x1A;

    /**
     * CR 287 params common across versions
     */
    public static final int SUPPORTED_AOA_RESULT_REQ_INTERLEAVING =
            UwbVendorCapabilityTlvTypes.SUPPORTED_AOA_RESULT_REQ_ANTENNA_INTERLEAVING;
    public static final int SUPPORTED_MIN_RANGING_INTERVAL_MS =
            UwbVendorCapabilityTlvTypes.SUPPORTED_MIN_RANGING_INTERVAL_MS;
    public static final int SUPPORTED_RANGE_DATA_NTF_CONFIG =
            UwbVendorCapabilityTlvTypes.SUPPORTED_RANGE_DATA_NTF_CONFIG;
    public static final int SUPPORTED_RSSI_REPORTING =
            UwbVendorCapabilityTlvTypes.SUPPORTED_RSSI_REPORTING;
    public static final int SUPPORTED_DIAGNOSTICS =
            UwbVendorCapabilityTlvTypes.SUPPORTED_DIAGNOSTICS;
    public static final int SUPPORTED_MIN_SLOT_DURATION_RSTU =
            UwbVendorCapabilityTlvTypes.SUPPORTED_MIN_SLOT_DURATION_RSTU;
    public static final int SUPPORTED_MAX_RANGING_SESSION_NUMBER =
            UwbVendorCapabilityTlvTypes.SUPPORTED_MAX_RANGING_SESSION_NUMBER;

    // CCC specific
    public static final int CCC_SUPPORTED_VERSIONS =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_VERSIONS;
    public static final int CCC_SUPPORTED_UWB_CONFIGS =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_UWB_CONFIGS;
    public static final int CCC_SUPPORTED_PULSE_SHAPE_COMBOS =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_PULSE_SHAPE_COMBOS;
    public static final int CCC_SUPPORTED_RAN_MULTIPLIER =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_RAN_MULTIPLIER;
    public static final int CCC_SUPPORTED_MAX_RANGING_SESSION_NUMBER =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_MAX_RANGING_SESSION_NUMBER;
    public static final int CCC_SUPPORTED_CHAPS_PER_SLOT =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHAPS_PER_SLOT;
    public static final int CCC_SUPPORTED_SYNC_CODES =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_SYNC_CODES;
    public static final int CCC_SUPPORTED_CHANNELS =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHANNELS;
    public static final int CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES;
    public static final int CCC_SUPPORTED_MIN_UWB_INITIATION_TIME_MS =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_MIN_UWB_INITIATION_TIME_MS;
    public static final int CCC_PRIORITIZED_CHANNEL_LIST =
            UwbVendorCapabilityTlvTypes.CCC_PRIORITIZED_CHANNEL_LIST;
    public static final int CCC_SUPPORTED_UWBS_MAX_PPM =
            UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_UWBS_MAX_PPM;

    public static final int RESPONDER = 0x01;
    public static final int INITIATOR = 0x02;
    public static final int UT_SYNCHRONIZATION_ANCHOR = 0X04;
    public static final int UT_ANCHOR = 0X08;
    public static final int UT_TAG = 0X10;
    public static final int ADVERTISER = 0X20;
    public static final int OBSERVER = 0X40;
    public static final int DT_ANCHOR = 0X80;
    public static final int DT_TAG = 0X01; // First bit of 2nd byte of Device Role

    public static final int OWR_UL_TDOA = 0x01;
    public static final int SS_TWR_DEFERRED = 0x02;
    public static final int DS_TWR_DEFERRED = 0x04;
    public static final int SS_TWR_NON_DEFERRED = 0x08;
    public static final int DS_TWR_NON_DEFERRED = 0x10;
    public static final int OWR_DL_TDOA = 0x20;
    public static final int OWR_AOA = 0x40;
    public static final int ESS_TWR_NON_DEFERRED = 0x80;
    public static final int ADS_TWR = 0x01; // First bit of 2nd byte of Ranging Method

    public static final int STATIC_STS = 0x1;
    public static final int DYNAMIC_STS = 0x2;
    public static final int DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY = 0x4;
    public static final int PROVISIONED_STS = 0x8;
    public static final int PROVISIONED_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY = 0x10;

    public static final int UNICAST = 0x1;
    public static final int ONE_TO_MANY = 0x2;
    public static final int MANY_TO_MANY = 0x4;

    public static final int INTERVAL_BASED_SCHEDULING = 0x1;
    public static final int BLOCK_BASED_SCHEDULING = 0x2;

    public static final int CONTENTION_BASED_RANGING = 0x1;
    public static final int TIME_SCHEDULED_RANGING = 0x2;

    public static final int CONSTRAINT_LENGTH_3 = 0x1;
    public static final int CONSTRAINT_LENGTH_7 = 0x2;

    public static final int NO_BLOCK_STRIDING = 0x0;
    public static final int BLOCK_STRIDING = 0x1;

    public static final int NO_HOPPING_MODE = 0x0;
    public static final int HOPPING_MODE = 0x1;

    public static final int NO_EXTENDED_MAC_ADDRESS = 0x0;
    public static final int EXTENDED_MAC_ADDRESS = 0x1;

    public static final int NO_UWB_INITIATION_TIME = 0x0;
    public static final int UWB_INITIATION_TIME = 0x1;

    public static final int CHANNEL_5 = 0x1;
    public static final int CHANNEL_6 = 0x2;
    public static final int CHANNEL_8 = 0x4;
    public static final int CHANNEL_9 = 0x8;
    public static final int CHANNEL_10 = 0x10;
    public static final int CHANNEL_12 = 0x20;
    public static final int CHANNEL_13 = 0x40;
    public static final int CHANNEL_14 = 0x80;

    public static final int SP0 = 0x1;
    public static final int SP1 = 0x2;
    public static final int SP2 = 0x4;
    public static final int SP3 = 0x8;

    public static final int CC_CONSTRAINT_LENGTH_K3 = 0x1;
    public static final int CC_CONSTRAINT_LENGTH_K7 = 0x2;

    public static final int AOA_AZIMUTH_90 = 0x1;
    public static final int AOA_AZIMUTH_180 = 0x2;
    public static final int AOA_ELEVATION = 0x4;
    public static final int AOA_FOM = 0x8;

    public static final int NO_EXTENDED_MAC = 0x0;
    public static final int EXTENDED_MAC = 0x1;

    public static final int NO_AOA_RESULT_REQ_INTERLEAVING = 0x0;
    public static final int AOA_RESULT_REQ_INTERLEAVING = 0x1;

    public static final int NO_RSSI_REPORTING = 0x0;
    public static final int RSSI_REPORTING = 0x1;

    public static final int NO_DIAGNOSTICS = 0x0;
    public static final int DIAGNOSTICS = 0x1;

    public static final int NO_DT_TAG_BLOCK_SKIPPING = 0x0;
    public static final int DT_TAG_BLOCK_SKIPPING = 0x1;

    public static final int NO_PSDU_LENGTH_SUPPORT = 0x0;
    public static final int PSDU_LENGTH_SUPPORT = 0x1;

    public static final int CCC_CHANNEL_5 = (int) UwbVendorCapabilityTlvValues.CCC_CHANNEL_5;
    public static final int CCC_CHANNEL_9 = (int) UwbVendorCapabilityTlvValues.CCC_CHANNEL_9;

    public static final int CCC_CHAPS_PER_SLOT_3 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_3;
    public static final int CCC_CHAPS_PER_SLOT_4 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_4;
    public static final int CCC_CHAPS_PER_SLOT_6 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_6;
    public static final int CCC_CHAPS_PER_SLOT_8 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_8;
    public static final int CCC_CHAPS_PER_SLOT_9 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_9;
    public static final int CCC_CHAPS_PER_SLOT_12 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_12;
    public static final int CCC_CHAPS_PER_SLOT_24 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_24;

    public static final int CCC_HOPPING_CONFIG_MODE_NONE =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_NONE;
    public static final int CCC_HOPPING_CONFIG_MODE_CONTINUOUS =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_CONTINUOUS;
    public static final int CCC_HOPPING_CONFIG_MODE_ADAPTIVE =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_ADAPTIVE;

    public static final int CCC_HOPPING_SEQUENCE_AES =
            (int) UwbVendorCapabilityTlvValues.HOPPING_SEQUENCE_AES;
    public static final int CCC_HOPPING_SEQUENCE_DEFAULT =
            (int) UwbVendorCapabilityTlvValues.HOPPING_SEQUENCE_DEFAULT;

    public static final int SUPPORTED_POWER_STATS_QUERY =
            UwbVendorCapabilityTlvTypes.SUPPORTED_POWER_STATS_QUERY;

    public static final int RANGE_DATA_NTF_CONFIG_ENABLE = 1 << 0;
    public static final int RANGE_DATA_NTF_CONFIG_DISABLE = 1 << 1;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG = 1 << 2;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG = 1 << 3;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG = 1 << 4;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG = 1 << 5;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG = 1 << 6;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG = 1 << 7;

    // Radar specific
    public static final int RADAR_SUPPORT = (int) UwbVendorCapabilityTlvTypes.RADAR_SUPPORT;
    public static final int RADAR_SWEEP_SAMPLES_SUPPORTED =
            (int) UwbVendorCapabilityTlvValues.RADAR_SWEEP_SAMPLES_SUPPORTED;
}
