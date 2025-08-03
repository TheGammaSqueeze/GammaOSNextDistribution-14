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

import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;

import android.uwb.UwbAddress;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.config.ConfigParam;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FiraEncoder extends TlvEncoder {
    private final UwbInjector mUwbInjector;

    public FiraEncoder(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    @Override
    public TlvBuffer getTlvBuffer(Params param, ProtocolVersion protocolVersion) {
        // The "protocolVersion" is always expected to be of type "FiraProtocolVersion" here, but
        // in case it's not, we use a backup value of "PROTOCOL_VERSION_1_1".
        FiraProtocolVersion uwbsFiraProtocolVersion =
                (protocolVersion instanceof FiraProtocolVersion)
                        ? (FiraProtocolVersion) protocolVersion : FiraParams.PROTOCOL_VERSION_1_1;
        if (param instanceof FiraOpenSessionParams) {
            return getTlvBufferFromFiraOpenSessionParams(param, uwbsFiraProtocolVersion);
        }

        if (param instanceof FiraRangingReconfigureParams) {
            return getTlvBufferFromFiraRangingReconfigureParams(param);
        }
        return null;
    }

    private static boolean hasAoaBoundInRangeDataNtfConfig(int rangeDataNtfConfig) {
        return rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG
                || rangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
    }

    private TlvBuffer getTlvBufferFromFiraOpenSessionParams(
            Params baseParam, FiraProtocolVersion uwbsFiraProtocolVersion) {
        FiraOpenSessionParams params = (FiraOpenSessionParams) baseParam;
        int deviceType = params.getDeviceType();
        int resultReportConfig = getResultReportConfig(params);
        int rangingRoundControl = getRangingRoundControl(params);
        int deviceRole = params.getDeviceRole();

        TlvBuffer.Builder tlvBufferBuilder = new TlvBuffer.Builder()
                .putByte(ConfigParam.RANGING_ROUND_USAGE, (byte) params.getRangingRoundUsage())
                .putByte(ConfigParam.STS_CONFIG, (byte) params.getStsConfig())
                .putByte(ConfigParam.MULTI_NODE_MODE, (byte) params.getMultiNodeMode())
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannelNumber())
                .putByteArray(ConfigParam.DEVICE_MAC_ADDRESS, params.getDeviceAddress().size(),
                        getComputedMacAddress(params.getDeviceAddress()))
                .putShort(ConfigParam.SLOT_DURATION, (short) params.getSlotDurationRstu())
                .putByte(ConfigParam.MAC_FCS_TYPE, (byte) params.getFcsType())
                .putByte(ConfigParam.RANGING_ROUND_CONTROL,
                        (byte) rangingRoundControl/* params.getMeasurementReportType()*/)
                .putByte(ConfigParam.AOA_RESULT_REQ, (byte) params.getAoaResultRequest())
                .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG, (byte) params.getRangeDataNtfConfig())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                        (short) params.getRangeDataNtfProximityNear())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                        (short) params.getRangeDataNtfProximityFar())
                .putByte(ConfigParam.DEVICE_ROLE, (byte) params.getDeviceRole())
                .putByte(ConfigParam.RFRAME_CONFIG, (byte) params.getRframeConfig())
                .putByte(ConfigParam.RSSI_REPORTING,
                        (byte) (params.isRssiReportingEnabled() ? 1 : 0))
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX, (byte) params.getPreambleCodeIndex())
                .putByte(ConfigParam.SFD_ID, (byte) params.getSfdId())
                .putByte(ConfigParam.PSDU_DATA_RATE, (byte) params.getPsduDataRate())
                .putByte(ConfigParam.PREAMBLE_DURATION, (byte) params.getPreambleDuration())
                // n.a. for OWR UL-TDoA and 0x01 for all other RangingRoundUsage values.
                .putByte(ConfigParam.RANGING_TIME_STRUCT, (byte) params.getRangingTimeStruct())
                .putByte(ConfigParam.SLOTS_PER_RR, (byte) params.getSlotsPerRangingRound())
                .putByte(ConfigParam.PRF_MODE, (byte) params.getPrfMode())
                .putByte(ConfigParam.SCHEDULED_MODE, (byte) params.getScheduledMode())
                .putByte(ConfigParam.KEY_ROTATION,
                        params.isKeyRotationEnabled() ? (byte) 1 : (byte) 0)
                .putByte(ConfigParam.KEY_ROTATION_RATE, (byte) params.getKeyRotationRate())
                .putByte(ConfigParam.SESSION_PRIORITY, (byte) params.getSessionPriority())
                .putByte(ConfigParam.MAC_ADDRESS_MODE, (byte) params.getMacAddressMode())
                .putByte(ConfigParam.NUMBER_OF_STS_SEGMENTS, (byte) params.getStsSegmentCount())
                .putShort(ConfigParam.MAX_RR_RETRY, (short) params.getMaxRangingRoundRetries())
                .putByte(ConfigParam.HOPPING_MODE,
                        (byte) params.getHoppingMode())
                .putByte(ConfigParam.BLOCK_STRIDE_LENGTH, (byte) params.getBlockStrideLength())
                .putByte(ConfigParam.RESULT_REPORT_CONFIG, (byte) resultReportConfig)
                .putByte(ConfigParam.IN_BAND_TERMINATION_ATTEMPT_COUNT,
                        (byte) params.getInBandTerminationAttemptCount())
                .putByte(ConfigParam.BPRF_PHR_DATA_RATE,
                        (byte) params.getBprfPhrDataRate())
                .putShort(ConfigParam.MAX_NUMBER_OF_MEASUREMENTS,
                        (short) params.getMaxNumberOfMeasurements())
                .putByte(ConfigParam.STS_LENGTH, (byte) params.getStsLength());
        if (params.getDeviceRole() != FiraParams.RANGING_DEVICE_UT_TAG) {
            tlvBufferBuilder.putInt(ConfigParam.RANGING_INTERVAL, params.getRangingIntervalMs());
        }
        if (deviceRole != FiraParams.RANGING_DEVICE_DT_TAG) {
            tlvBufferBuilder.putByte(ConfigParam.DEVICE_TYPE, (byte) params.getDeviceType());
        }

        if (isTimeScheduledTwrSession(
                    params.getScheduledMode(), params.getRangingRoundUsage()))  {
            if (params.getDestAddressList().size() > 0) {
                ByteBuffer dstAddressList = ByteBuffer.allocate(1024);
                for (UwbAddress address : params.getDestAddressList()) {
                    dstAddressList.put(getComputedMacAddress(address));
                }
                tlvBufferBuilder
                        .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                                (byte) params.getDestAddressList().size())
                        .putByteArray(
                                ConfigParam.DST_MAC_ADDRESS, dstAddressList.position(),
                                Arrays.copyOf(dstAddressList.array(), dstAddressList.position()));
            }
        }

        FiraProtocolVersion firaProtocolVersion =
                mUwbInjector.getFeatureFlags().useUwbsUciVersion()
                        ? uwbsFiraProtocolVersion : params.getProtocolVersion();
        if (firaProtocolVersion.getMajor() >= 2) {
            // Initiation time Changed from 4 byte field to 8 byte field in version 2.
            if (deviceRole != FiraParams.RANGING_DEVICE_DT_TAG) {
                // For FiRa 2.0+ device, prefer to set the Absolute UWB Initiation time.
                if (params.getAbsoluteInitiationTime() > 0) {
                    tlvBufferBuilder.putLong(ConfigParam.UWB_INITIATION_TIME,
                            params.getAbsoluteInitiationTime());
                } else {
                    tlvBufferBuilder.putLong(ConfigParam.UWB_INITIATION_TIME,
                            params.getInitiationTime());
                }
            } else {
                tlvBufferBuilder.putByte(ConfigParam.DL_TDOA_BLOCK_STRIDING,
                    (byte) params.getDlTdoaBlockStriding());
            }
            tlvBufferBuilder.putByte(ConfigParam.LINK_LAYER_MODE, (byte) params.getLinkLayerMode())
                    .putByte(ConfigParam.DATA_REPETITION_COUNT,
                            (byte) params.getDataRepetitionCount())
                    .putByte(ConfigParam.SESSION_DATA_TRANSFER_STATUS_NTF_CONFIG,
                            params.getSessionDataTransferStatusNtfConfig() ? (byte) 1 : (byte) 0)
                    .putByte(ConfigParam.APPLICATION_DATA_ENDPOINT,
                            (byte) params.getApplicationDataEndpoint());
            if (deviceType == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER && UwbUtil.isBitSet(
                             params.getReferenceTimeBase(),
                             FiraParams.SESSION_TIME_BASE_REFERENCE_FEATURE_ENABLED)) {
                tlvBufferBuilder.putByteArray(ConfigParam.SESSION_TIME_BASE,
                            getSessionTimeBase(params));
            }
        } else {
            if (deviceRole != FiraParams.RANGING_DEVICE_DT_TAG) {
                tlvBufferBuilder
                        .putInt(ConfigParam.UWB_INITIATION_TIME,
                                Math.toIntExact(params.getInitiationTime()));
            }
            tlvBufferBuilder.putByte(ConfigParam.TX_ADAPTIVE_PAYLOAD_POWER,
                        params.isTxAdaptivePayloadPowerEnabled() ? (byte) 1 : (byte) 0);
        }

        configureStsParameters(tlvBufferBuilder, params);

        if (params.getAoaResultRequest()
                == FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED) {
            tlvBufferBuilder.putByte(ConfigParam.NUM_RANGE_MEASUREMENTS,
                            (byte) params.getNumOfMsrmtFocusOnRange())
                    .putByte(ConfigParam.NUM_AOA_AZIMUTH_MEASUREMENTS,
                            (byte) params.getNumOfMsrmtFocusOnAoaAzimuth())
                    .putByte(ConfigParam.NUM_AOA_ELEVATION_MEASUREMENTS,
                            (byte) params.getNumOfMsrmtFocusOnAoaElevation());
        }
        if (hasAoaBoundInRangeDataNtfConfig(params.getRangeDataNtfConfig())) {
            tlvBufferBuilder.putShortArray(ConfigParam.RANGE_DATA_NTF_AOA_BOUND, new short[]{
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
        if (params.isDiagnosticsEnabled()) {
            tlvBufferBuilder.putByte(ConfigParam.ENABLE_DIAGNOSTICS_RSSI, (byte) 1);
            if (SdkLevel.isAtLeastU()) {
                // Fixed bug to be compliant with HAL interface.
                tlvBufferBuilder.putByte(ConfigParam.ENABLE_DIAGRAMS_FRAME_REPORTS_FIELDS,
                        params.getDiagramsFrameReportsFieldsFlags());
            } else {
                tlvBufferBuilder.putInt(ConfigParam.ENABLE_DIAGRAMS_FRAME_REPORTS_FIELDS,
                        params.getDiagramsFrameReportsFieldsFlags());
            }
        }
        if (params.getScheduledMode() == FiraParams.CONTENTION_BASED_RANGING) {
            tlvBufferBuilder.putByteArray(ConfigParam.CAP_SIZE_RANGE, params.getCapSize());
        }
        if (params.getDeviceRole() == FiraParams.RANGING_DEVICE_UT_TAG) {
            tlvBufferBuilder.putInt(ConfigParam.UL_TDOA_TX_INTERVAL,
                    params.getUlTdoaTxIntervalMs());
            tlvBufferBuilder.putInt(ConfigParam.UL_TDOA_RANDOM_WINDOW,
                    params.getUlTdoaRandomWindowMs());
            tlvBufferBuilder.putByteArray(ConfigParam.UL_TDOA_DEVICE_ID, getUlTdoaDeviceId(
                    params.getUlTdoaDeviceIdType(), params.getUlTdoaDeviceId()));
            tlvBufferBuilder.putByte(ConfigParam.UL_TDOA_TX_TIMESTAMP,
                    (byte) params.getUlTdoaTxTimestampType());
        }
        if (params.getDeviceRole() == FiraParams.RANGING_DEVICE_ROLE_ADVERTISER ||
                params.getDeviceRole() == FiraParams.RANGING_DEVICE_ROLE_OBSERVER) {
            tlvBufferBuilder
                    .putByte(ConfigParam.MIN_FRAMES_PER_RR, (byte) params.getMinFramesPerRr())
                    .putShort(ConfigParam.MTU_SIZE, (short) params.getMtuSize())
                    .putByte(ConfigParam.INTER_FRAME_INTERVAL,
                            (byte) params.getInterFrameInterval());
        }
        return tlvBufferBuilder.build();
    }

    private boolean isTimeScheduledTwrSession(int scheduledMode, int rangingUsage) {
        if (scheduledMode == FiraParams.TIME_SCHEDULED_RANGING) {
            if (rangingUsage == FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE
                    || rangingUsage == FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE
                    || rangingUsage == FiraParams.RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE
                    || rangingUsage == FiraParams.RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE) {
                return true;
            }
        }
        return false;
    }

    private void configureStsParameters(TlvBuffer.Builder tlvBufferBuilder,
        FiraOpenSessionParams params) {
        int stsConfig = params.getStsConfig();

        if (stsConfig == FiraParams.STS_CONFIG_STATIC) {
             tlvBufferBuilder
                    .putByteArray(ConfigParam.VENDOR_ID, params.getVendorId() != null
                            ? getComputedVendorId(params.getVendorId()): null)
                    .putByteArray(ConfigParam.STATIC_STS_IV, params.getStaticStsIV());
        } else if (stsConfig == FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            if (params.getDeviceType() == FiraParams.RANGING_DEVICE_TYPE_CONTROLEE) {
                tlvBufferBuilder.putInt(ConfigParam.SUB_SESSION_ID, params.getSubSessionId());
            }
        } else if (stsConfig == FiraParams.STS_CONFIG_PROVISIONED) {
            if (params.getSessionKey() != null ) {
                tlvBufferBuilder.putByteArray(ConfigParam.SESSION_KEY, params.getSessionKey());
            }
        } else if (stsConfig == FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            if (params.getDeviceType() == FiraParams.RANGING_DEVICE_TYPE_CONTROLEE) {
                tlvBufferBuilder.putInt(ConfigParam.SUB_SESSION_ID, params.getSubSessionId());
                if (params.getSubsessionKey() != null ) {
                    tlvBufferBuilder.
                          putByteArray(ConfigParam.SUBSESSION_KEY, params.getSubsessionKey());
                }
            }
            if (params.getSessionKey() != null ) {
                tlvBufferBuilder.putByteArray(ConfigParam.SESSION_KEY, params.getSessionKey());
            }
        }
    }

    private byte[] getUlTdoaDeviceId(int ulTdoaDeviceIdType, byte[] ulTdoaDeviceId) {
        if (ulTdoaDeviceIdType == FiraParams.UL_TDOA_DEVICE_ID_NONE) {
            // Device ID not included
            return new byte[]{0};
        }
        ByteBuffer buffer = ByteBuffer.allocate(ulTdoaDeviceId.length + 1);
        buffer.put((byte) ulTdoaDeviceIdType);
        buffer.put(ulTdoaDeviceId);
        return buffer.array();
    }

    private TlvBuffer getTlvBufferFromFiraRangingReconfigureParams(Params baseParam) {
        FiraRangingReconfigureParams params = (FiraRangingReconfigureParams) baseParam;
        TlvBuffer.Builder tlvBuilder = new TlvBuffer.Builder();
        Integer blockStrideLength = params.getBlockStrideLength();
        Integer rangeDataNtfConfig = params.getRangeDataNtfConfig();
        Integer rangeDataProximityNear = params.getRangeDataProximityNear();
        Integer rangeDataProximityFar = params.getRangeDataProximityFar();
        Double rangeDataAoaAzimuthLower = params.getRangeDataAoaAzimuthLower();
        Double rangeDataAoaAzimuthUpper = params.getRangeDataAoaAzimuthUpper();
        Double rangeDataAoaElevationLower = params.getRangeDataAoaElevationLower();
        Double rangeDataAoaElevationUpper = params.getRangeDataAoaElevationUpper();
        Integer suspendRangingRounds = params.getSuspendRangingRounds();

        if (blockStrideLength != null) {
            tlvBuilder.putByte(ConfigParam.BLOCK_STRIDE_LENGTH,
                    (byte) blockStrideLength.intValue());
        }

        if (rangeDataNtfConfig != null) {
            tlvBuilder.putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                    (byte) rangeDataNtfConfig.intValue());
        }

        if (rangeDataProximityNear != null) {
            tlvBuilder.putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                    (short) rangeDataProximityNear.intValue());
        }

        if (rangeDataProximityFar != null) {
            tlvBuilder.putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                    (short) rangeDataProximityFar.intValue());
        }

        if (rangeDataNtfConfig != null && hasAoaBoundInRangeDataNtfConfig(rangeDataNtfConfig)) {
            if ((rangeDataAoaAzimuthLower != null && rangeDataAoaAzimuthUpper != null)
                    || (rangeDataAoaElevationLower != null && rangeDataAoaElevationUpper != null)) {
                rangeDataAoaAzimuthLower = rangeDataAoaAzimuthLower != null
                        ? rangeDataAoaAzimuthLower
                        : FiraParams.RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT;
                rangeDataAoaAzimuthUpper = rangeDataAoaAzimuthUpper != null
                        ? rangeDataAoaAzimuthUpper
                        : FiraParams.RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT;
                rangeDataAoaElevationLower = rangeDataAoaElevationLower != null
                        ? rangeDataAoaElevationLower
                        : FiraParams.RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT;
                rangeDataAoaElevationUpper = rangeDataAoaElevationUpper != null
                        ? rangeDataAoaElevationUpper
                        : FiraParams.RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT;
                tlvBuilder.putShortArray(ConfigParam.RANGE_DATA_NTF_AOA_BOUND, new short[]{
                        // TODO (b/235355249): Verify this conversion. This is using AOA value
                        // in UwbTwoWayMeasurement to external RangingMeasurement conversion as
                        // reference.
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        rangeDataAoaAzimuthLower.floatValue()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        rangeDataAoaAzimuthUpper.floatValue()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        rangeDataAoaElevationLower.floatValue()), 9, 7), 16),
                        (short) UwbUtil.twos_compliment(UwbUtil.convertFloatToQFormat(
                                UwbUtil.radianTodegree(
                                        rangeDataAoaElevationUpper.floatValue()), 9, 7), 16),
                });
            }
        }
        if (suspendRangingRounds != null) {
                tlvBuilder.putByte(ConfigParam.SUSPEND_RANGING_ROUNDS,
                        (byte) suspendRangingRounds.intValue());
        }
        return tlvBuilder.build();
    }

    // Merged data from other parameter values
    private int getResultReportConfig(FiraOpenSessionParams params) {
        int resultReportConfig = 0x00;
        resultReportConfig |= params.hasTimeOfFlightReport() ? 0x01 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalAzimuthReport() ? 0x02 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalElevationReport() ? 0x04 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalFigureOfMeritReport() ? 0x08 : 0x00;
        return resultReportConfig;
    }

    private int getRangingRoundControl(FiraOpenSessionParams params) {
        // RANGING_ROUND_CONTROL
        byte rangingRoundControl = 0x00;

        // b0 : Ranging Result Report Message
        rangingRoundControl |= (byte) (params.hasRangingResultReportMessage() ? 0x01 : 0x00);

        // b1 : Control Message
        rangingRoundControl |= (byte) (params.hasControlMessage() ? 0x02 : 0x00);

        // b2 : Ranging Control Phase
        rangingRoundControl |= (byte) (params.hasRangingControlPhase() ? 0x04 : 0x00);

        // b6 : Measurement Report Message
        if (params.getScheduledMode() == FiraParams.CONTENTION_BASED_RANGING) {
            if (params.getMeasurementReportPhase() == FiraParams.MEASUREMENT_REPORT_PHASE_SET) {
                rangingRoundControl |= (byte) 0x40;
            }
        }

        // b7 : Measurement Report Message
        if (params.getMeasurementReportType()
                == FiraParams.MEASUREMENT_REPORT_TYPE_RESPONDER_TO_INITIATOR) {
            rangingRoundControl |= (byte) 0x80;
        }
        return rangingRoundControl;
    }

    private static byte[] getComputedMacAddress(UwbAddress address) {
        if (!SdkLevel.isAtLeastU()) {
            return TlvUtil.getReverseBytes(address.toBytes());
        }
        return address.toBytes();
    }

    private static byte[] getComputedVendorId(byte[] data) {
        if (!SdkLevel.isAtLeastU()) {
            return TlvUtil.getReverseBytes(data);
        }
        return data;
    }

    private byte[] getSessionTimeBase(FiraOpenSessionParams params) {
        byte[] sessionTimeBaseParam = new byte[FiraParams.SESSION_TIME_BASE_PARAM_LEN];
        int offset = 0;
        sessionTimeBaseParam[offset++] = (byte) params.getReferenceTimeBase();
        byte[] sessionHandleValue = TlvUtil.getBytes(params.getReferenceSessionHandle());
        for (int index = FiraParams.SESSION_HANDLE_LEN - 1; index >= 0; index--) {
            sessionTimeBaseParam[offset++] = (byte) sessionHandleValue[index];
        }
        byte[] sessionOffsetInMicroSecondValue =
                TlvUtil.getBytes(params.getSessionOffsetInMicroSeconds());
        for (int index = FiraParams.SESSION_OFFSET_TIME_LEN - 1; index >= 0; index--) {
            sessionTimeBaseParam[offset++] = (byte) sessionOffsetInMicroSecondValue[index];
        }
        return sessionTimeBaseParam;
    }
}
