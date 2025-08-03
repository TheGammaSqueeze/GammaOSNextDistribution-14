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

package com.android.server.uwb.params;

import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.config.ConfigParam;
import com.android.server.uwb.data.UwbCccConstants;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraParams;

public class CccEncoder extends TlvEncoder {
    private final UwbInjector mUwbInjector;

    public CccEncoder(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    @Override
    public TlvBuffer getTlvBuffer(Params param, ProtocolVersion protocolVersion) {
        if (param instanceof CccOpenRangingParams) {
            return getTlvBufferFromCccOpenRangingParams(param);
        }
        return null;
    }

    private TlvBuffer getTlvBufferFromCccOpenRangingParams(Params baseParam) {
        CccOpenRangingParams params = (CccOpenRangingParams) baseParam;
        int hoppingConfig = params.getHoppingConfigMode();
        int hoppingSequence = params.getHoppingSequence();

        int hoppingMode = CccParams.HOPPING_CONFIG_MODE_NONE;
        byte[] protocolVer = params.getProtocolVersion().toBytes();

        switch (hoppingConfig) {

            case CccParams.HOPPING_CONFIG_MODE_CONTINUOUS:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_AES;
                }
                break;
            case CccParams.HOPPING_CONFIG_MODE_ADAPTIVE:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_AES;
                }
                break;
        }

        TlvBuffer.Builder tlvBufferBuilder = new TlvBuffer.Builder()
                .putByte(ConfigParam.DEVICE_TYPE,
                        (byte) UwbUciConstants.DEVICE_TYPE_CONTROLLER) // DEVICE_TYPE
                .putByte(ConfigParam.STS_CONFIG,
                        (byte) UwbUciConstants.STS_MODE_DYNAMIC) // STS_CONFIG
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannel()) // CHANNEL_ID
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getNumResponderNodes()) // NUMBER_OF_ANCHORS
                .putInt(ConfigParam.RANGING_INTERVAL,
                        params.getRanMultiplier() * 96) //RANGING_INTERVAL = RAN_Multiplier * 96
                .putByte(ConfigParam.DEVICE_ROLE,
                        (byte) UwbUciConstants.RANGING_DEVICE_ROLE_INITIATOR) // DEVICE_ROLE
                .putByte(ConfigParam.MULTI_NODE_MODE,
                        (byte) FiraParams.MULTI_NODE_MODE_ONE_TO_MANY) // MULTI_NODE_MODE
                .putByte(ConfigParam.SLOTS_PER_RR,
                        (byte) params.getNumSlotsPerRound()) // SLOTS_PER_RR
                .putByte(ConfigParam.HOPPING_MODE, (byte) hoppingMode) // HOPPING_MODE
                .putByteArray(ConfigParam.RANGING_PROTOCOL_VER,
                        ConfigParam.RANGING_PROTOCOL_VER_BYTE_COUNT,
                        new byte[] { protocolVer[1], protocolVer[0] }) // RANGING_PROTOCOL_VER
                .putShort(ConfigParam.UWB_CONFIG_ID, (short) params.getUwbConfig()) // UWB_CONFIG_ID
                .putByte(ConfigParam.PULSESHAPE_COMBO,
                        params.getPulseShapeCombo().toBytes()[0]) // PULSESHAPE_COMBO
                .putShort(ConfigParam.URSK_TTL, (short) 0x2D0) // URSK_TTL
                // T(Slotk) =  N(Chap_per_Slot) * T(Chap)
                // T(Chap) = 400RSTU
                // reference : digital key release 3 20.2 MAC Time Grid
                .putShort(ConfigParam.SLOT_DURATION,
                        (short) (params.getNumChapsPerSlot() * 400)) // SLOT_DURATION
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX,
                        (byte) params.getSyncCodeIndex()); // PREAMBLE_CODE_INDEX
        if (params.getStsIndex() != CccParams.STS_INDEX_UNSET) {
              tlvBufferBuilder.putInt(ConfigParam.STS_INDEX, params.getStsIndex());
        }
        if (params.getAbsoluteInitiationTimeUs() > 0) {
            tlvBufferBuilder.putLong(ConfigParam.UWB_INITIATION_TIME,
                    params.getAbsoluteInitiationTimeUs());
        } else if (params.getInitiationTimeMs() != CccParams.UWB_INITIATION_TIME_MS_UNSET) {
            tlvBufferBuilder.putLong(
                    ConfigParam.UWB_INITIATION_TIME, params.getInitiationTimeMs());
        }
        if (mUwbInjector.getDeviceConfigFacade().isCccSupportedRangeDataNtfConfig()) {
            tlvBufferBuilder
                    .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                            (byte) params.getRangeDataNtfConfig())
                    .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                            (short) params.getRangeDataNtfProximityNear())
                    .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                            (short) params.getRangeDataNtfProximityFar());

            if (hasAoaBoundInRangeDataNtfConfig(params.getRangeDataNtfConfig())) {
                tlvBufferBuilder.putShortArray(ConfigParam.RANGE_DATA_NTF_AOA_BOUND, new short[] {
                        // TODO (b/235355249): Verify this conversion. This is using AOA value
                        // in UwbTwoWayMeasurement to external RangingMeasurement conversion as
                        // reference.
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        params.getRangeDataNtfAoaAzimuthLower()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        params.getRangeDataNtfAoaAzimuthUpper()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        params.getRangeDataNtfAoaElevationLower()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        params.getRangeDataNtfAoaElevationUpper()), 9, 7), 16),
                });
            }
        } else {
            tlvBufferBuilder
                    .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                            (byte) UwbUciConstants.RANGE_DATA_NTF_CONFIG_DISABLE); // RNG_DATA_NTF
        }
        return tlvBufferBuilder.build();
    }

    private static boolean hasAoaBoundInRangeDataNtfConfig(int rangeDataNtfConfig) {
        return rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
    }
}
