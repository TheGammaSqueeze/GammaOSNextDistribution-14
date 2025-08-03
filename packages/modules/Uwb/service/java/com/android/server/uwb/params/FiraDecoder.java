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

import static com.android.server.uwb.config.CapabilityParam.ADS_TWR;
import static com.android.server.uwb.config.CapabilityParam.ADVERTISER;
import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_180;
import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_90;
import static com.android.server.uwb.config.CapabilityParam.AOA_ELEVATION;
import static com.android.server.uwb.config.CapabilityParam.AOA_FOM;
import static com.android.server.uwb.config.CapabilityParam.AOA_RESULT_REQ_INTERLEAVING;
import static com.android.server.uwb.config.CapabilityParam.BLOCK_BASED_SCHEDULING;
import static com.android.server.uwb.config.CapabilityParam.BLOCK_STRIDING;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K3;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K7;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_10;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_12;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_13;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_14;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_5;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_6;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_8;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_9;
import static com.android.server.uwb.config.CapabilityParam.CONSTRAINT_LENGTH_3;
import static com.android.server.uwb.config.CapabilityParam.CONSTRAINT_LENGTH_7;
import static com.android.server.uwb.config.CapabilityParam.CONTENTION_BASED_RANGING;
import static com.android.server.uwb.config.CapabilityParam.DIAGNOSTICS;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DT_ANCHOR;
import static com.android.server.uwb.config.CapabilityParam.DT_TAG;
import static com.android.server.uwb.config.CapabilityParam.DT_TAG_BLOCK_SKIPPING;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.ESS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.EXTENDED_MAC_ADDRESS;
import static com.android.server.uwb.config.CapabilityParam.HOPPING_MODE;
import static com.android.server.uwb.config.CapabilityParam.INITIATOR;
import static com.android.server.uwb.config.CapabilityParam.INTERVAL_BASED_SCHEDULING;
import static com.android.server.uwb.config.CapabilityParam.MANY_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.OBSERVER;
import static com.android.server.uwb.config.CapabilityParam.ONE_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.OWR_AOA;
import static com.android.server.uwb.config.CapabilityParam.OWR_DL_TDOA;
import static com.android.server.uwb.config.CapabilityParam.OWR_UL_TDOA;
import static com.android.server.uwb.config.CapabilityParam.PROVISIONED_STS;
import static com.android.server.uwb.config.CapabilityParam.PROVISIONED_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.PSDU_LENGTH_SUPPORT;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_DISABLE;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;
import static com.android.server.uwb.config.CapabilityParam.RESPONDER;
import static com.android.server.uwb.config.CapabilityParam.RSSI_REPORTING;
import static com.android.server.uwb.config.CapabilityParam.SP0;
import static com.android.server.uwb.config.CapabilityParam.SP1;
import static com.android.server.uwb.config.CapabilityParam.SP3;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.STATIC_STS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_AOA_RESULT_REQ_INTERLEAVING;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_AOA_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_AOA_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BLOCK_STRIDING_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BLOCK_STRIDING_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BPRF_PARAMETER_SETS_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BPRF_PARAMETER_SETS_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CC_CONSTRAINT_LENGTH_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CC_CONSTRAINT_LENGTH_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CHANNELS_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CHANNELS_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DEVICE_ROLES_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DEVICE_ROLES_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DEVICE_TYPE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DIAGNOSTICS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DT_TAG_BLOCK_SKIPPING_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DT_TAG_MAX_ACTIVE_RR_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_EXTENDED_MAC_ADDRESS_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_EXTENDED_MAC_ADDRESS_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_HOPPING_MODE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_HOPPING_MODE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_HPRF_PARAMETER_SETS_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_HPRF_PARAMETER_SETS_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MAX_MESSAGE_SIZE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MAX_MESSAGE_SIZE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MAX_RANGING_SESSION_NUMBER;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MIN_RANGING_INTERVAL_MS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MIN_SLOT_DURATION_RSTU;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MULTI_NODE_MODES_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MULTI_NODE_MODES_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_PSDU_LENGTH_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGE_DATA_NTF_CONFIG;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGING_METHOD_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGING_METHOD_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGING_TIME_STRUCT_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGING_TIME_STRUCT_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RFRAME_CONFIG_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RFRAME_CONFIG_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RSSI_REPORTING;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_SCHEDULED_MODE_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_SCHEDULED_MODE_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_SESSION_KEY_LENGTH_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_STS_CONFIG_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_STS_CONFIG_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_SUSPEND_RANGING_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_UWB_INITIATION_TIME_VER_1_0;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_UWB_INITIATION_TIME_VER_2_0;
import static com.android.server.uwb.config.CapabilityParam.TIME_SCHEDULED_RANGING;
import static com.android.server.uwb.config.CapabilityParam.UNICAST;
import static com.android.server.uwb.config.CapabilityParam.UT_ANCHOR;
import static com.android.server.uwb.config.CapabilityParam.UT_SYNCHRONIZATION_ANCHOR;
import static com.android.server.uwb.config.CapabilityParam.UT_TAG;
import static com.android.server.uwb.config.CapabilityParam.UWB_INITIATION_TIME;

import android.util.Log;

import com.android.server.uwb.UwbInjector;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.BprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.CcConstraintLengthCapabilitiesFlag;
import com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.HprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.RangingTimeStructCapabilitiesFlag;
import com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.SchedulingModeCapabilitiesFlag;
import com.google.uwb.support.fira.FiraParams.StsCapabilityFlag;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

public class FiraDecoder extends TlvDecoder {
    private static final String TAG = "FiraDecoder";

    private final UwbInjector mUwbInjector;

    public FiraDecoder(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType,
            ProtocolVersion protocolVersion) {
        if (FiraSpecificationParams.class.equals(paramType)) {
            // The "protocolVersion" is always expected to be of type "FiraProtocolVersion" here,
            // but in case it's not, we use a backup value of "PROTOCOL_VERSION_1_1".
            FiraProtocolVersion uwbsFiraProtocolVersion =
                    (protocolVersion instanceof FiraProtocolVersion)
                        ? (FiraProtocolVersion) protocolVersion : FiraParams.PROTOCOL_VERSION_1_1;
            return (T) getFiraSpecificationParamsFromTlvBuffer(tlvs, uwbsFiraProtocolVersion);
        }
        return null;
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private FiraSpecificationParams getFiraSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs,
                    ProtocolVersion protocolVersion) {
        FiraSpecificationParams.Builder builder = new FiraSpecificationParams.Builder();
        byte[] versionCheck = tlvs.getByteArray(SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_2_0);
        if (versionCheck.length == 1) {
            // FiRa Version 1.0
            byte[] phyVersions = tlvs.getByteArray(SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_1_0);
            builder.setMinPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 0));
            builder.setMaxPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 2));
            byte[] macVersions = tlvs.getByteArray(SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_1_0);
            builder.setMinMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 0));
            builder.setMaxMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 2));

            byte deviceRolesUci = tlvs.getByte(SUPPORTED_DEVICE_ROLES_VER_1_0);
            EnumSet<DeviceRoleCapabilityFlag> deviceRoles =
                    EnumSet.noneOf(DeviceRoleCapabilityFlag.class);
            if (isBitSet(deviceRolesUci, INITIATOR)) {
                // This assumes both controller + controlee is supported.
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT);
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT);
            }
            if (isBitSet(deviceRolesUci, RESPONDER)) {
                // This assumes both controller + controlee is supported.
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
            }
            builder.setDeviceRoleCapabilities(deviceRoles);

            byte rangingMethodUci = tlvs.getByte(SUPPORTED_RANGING_METHOD_VER_1_0);
            EnumSet<RangingRoundCapabilityFlag> rangingRoundFlag = EnumSet.noneOf(
                    RangingRoundCapabilityFlag.class);
            if (isBitSet(rangingMethodUci, DS_TWR_DEFERRED)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);
            }
            if (isBitSet(rangingMethodUci, SS_TWR_DEFERRED)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT);
            }
            builder.setRangingRoundCapabilities(rangingRoundFlag);

            // TODO(b/209053358): This does not align with UCI spec.
            if (isBitSet(rangingMethodUci, DS_TWR_NON_DEFERRED)
                    || isBitSet(rangingMethodUci, SS_TWR_NON_DEFERRED)) {
                builder.hasNonDeferredModeSupport(true);
            }

            byte stsConfigUci = tlvs.getByte(SUPPORTED_STS_CONFIG_VER_1_0);
            EnumSet<StsCapabilityFlag> stsCapabilityFlag = EnumSet.noneOf(StsCapabilityFlag.class);
            if (isBitSet(stsConfigUci, STATIC_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, DYNAMIC_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsCapabilityFlag.add(
                        StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            if (isBitSet(stsConfigUci, PROVISIONED_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_PROVISIONED_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, PROVISIONED_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsCapabilityFlag.add(
                        StsCapabilityFlag.HAS_PROVISIONED_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            builder.setStsCapabilities(stsCapabilityFlag);

            byte multiNodeUci = tlvs.getByte(SUPPORTED_MULTI_NODE_MODES_VER_1_0);
            EnumSet<MultiNodeCapabilityFlag> multiNodeFlag =
                    EnumSet.noneOf(MultiNodeCapabilityFlag.class);
            if (isBitSet(multiNodeUci, UNICAST)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);
            }
            if (isBitSet(multiNodeUci, ONE_TO_MANY)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT);
            }
            if (isBitSet(multiNodeUci, MANY_TO_MANY)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT);
            }
            builder.setMultiNodeCapabilities(multiNodeFlag);

            byte rangingTimeStructUci = tlvs.getByte(SUPPORTED_RANGING_TIME_STRUCT_VER_1_0);
            EnumSet<RangingTimeStructCapabilitiesFlag> rangingTimeStructFlag =
                    EnumSet.noneOf(RangingTimeStructCapabilitiesFlag.class);
            if (protocolVersion.getMajor() <= 2
                    && isBitSet(rangingTimeStructUci, INTERVAL_BASED_SCHEDULING)) {
                rangingTimeStructFlag.add(
                        RangingTimeStructCapabilitiesFlag.HAS_INTERVAL_BASED_SCHEDULING_SUPPORT);
            }
            if (isBitSet(rangingTimeStructUci, BLOCK_BASED_SCHEDULING)) {
                rangingTimeStructFlag.add(
                        RangingTimeStructCapabilitiesFlag.HAS_BLOCK_BASED_SCHEDULING_SUPPORT);
            }
            builder.setRangingTimeStructCapabilities(rangingTimeStructFlag);

            byte schedulingModeUci = tlvs.getByte(SUPPORTED_SCHEDULED_MODE_VER_1_0);
            EnumSet<SchedulingModeCapabilitiesFlag> schedulingModeFlag =
                    EnumSet.noneOf(SchedulingModeCapabilitiesFlag.class);
            if (isBitSet(schedulingModeUci, CONTENTION_BASED_RANGING)) {
                schedulingModeFlag.add(
                        SchedulingModeCapabilitiesFlag.HAS_CONTENTION_BASED_RANGING_SUPPORT);
            }
            if (isBitSet(schedulingModeUci, TIME_SCHEDULED_RANGING)) {
                schedulingModeFlag.add(
                        SchedulingModeCapabilitiesFlag.HAS_TIME_SCHEDULED_RANGING_SUPPORT);
            }
            builder.setSchedulingModeCapabilities(schedulingModeFlag);

            byte ccConstraintLengthUci = tlvs.getByte(SUPPORTED_CC_CONSTRAINT_LENGTH_VER_1_0);
            EnumSet<CcConstraintLengthCapabilitiesFlag> ccConstraintLengthFlag =
                    EnumSet.noneOf(CcConstraintLengthCapabilitiesFlag.class);
            if (isBitSet(ccConstraintLengthUci, CONSTRAINT_LENGTH_3)) {
                ccConstraintLengthFlag.add(
                        CcConstraintLengthCapabilitiesFlag.HAS_CONSTRAINT_LENGTH_3_SUPPORT);
            }
            if (isBitSet(ccConstraintLengthUci, CONSTRAINT_LENGTH_7)) {
                ccConstraintLengthFlag.add(
                        CcConstraintLengthCapabilitiesFlag.HAS_CONSTRAINT_LENGTH_7_SUPPORT);
            }
            builder.setCcConstraintLengthCapabilities(ccConstraintLengthFlag);

            byte blockStridingUci = tlvs.getByte(SUPPORTED_BLOCK_STRIDING_VER_1_0);
            if (isBitSet(blockStridingUci, BLOCK_STRIDING)) {
                builder.hasBlockStridingSupport(true);
            }

            byte hoppingPreferenceUci = tlvs.getByte(SUPPORTED_HOPPING_MODE_VER_1_0);
            if (isBitSet(hoppingPreferenceUci, HOPPING_MODE)) {
                builder.hasHoppingPreferenceSupport(true);
            }

            byte extendedMacAddressUci = tlvs.getByte(SUPPORTED_EXTENDED_MAC_ADDRESS_VER_1_0);
            if (isBitSet(extendedMacAddressUci, EXTENDED_MAC_ADDRESS)) {
                builder.hasExtendedMacAddressSupport(true);
            }

            byte initiationTimeUci = tlvs.getByte(SUPPORTED_UWB_INITIATION_TIME_VER_1_0);
            if (isBitSet(initiationTimeUci, UWB_INITIATION_TIME)) {
                builder.hasInitiationTimeSupport(true);
            }

            byte channelsUci = tlvs.getByte(SUPPORTED_CHANNELS_VER_1_0);
            List<Integer> channels = new ArrayList<>();
            if (isBitSet(channelsUci, CHANNEL_5)) {
                channels.add(5);
            }
            if (isBitSet(channelsUci, CHANNEL_6)) {
                channels.add(6);
            }
            if (isBitSet(channelsUci, CHANNEL_8)) {
                channels.add(8);
            }
            if (isBitSet(channelsUci, CHANNEL_9)) {
                channels.add(9);
            }
            if (isBitSet(channelsUci, CHANNEL_10)) {
                channels.add(10);
            }
            if (isBitSet(channelsUci, CHANNEL_12)) {
                channels.add(12);
            }
            if (isBitSet(channelsUci, CHANNEL_13)) {
                channels.add(13);
            }
            if (isBitSet(channelsUci, CHANNEL_14)) {
                channels.add(14);
            }
            builder.setSupportedChannels(channels);

            byte rframeConfigUci = tlvs.getByte(SUPPORTED_RFRAME_CONFIG_VER_1_0);
            EnumSet<RframeCapabilityFlag> rframeConfigFlag =
                    EnumSet.noneOf(RframeCapabilityFlag.class);
            if (isBitSet(rframeConfigUci, SP0)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
            }
            if (isBitSet(rframeConfigUci, SP1)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
            }
            if (isBitSet(rframeConfigUci, SP3)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
            }
            builder.setRframeCapabilities(rframeConfigFlag);

            byte bprfSets = tlvs.getByte(SUPPORTED_BPRF_PARAMETER_SETS_VER_1_0);
            int bprfSetsValue = Integer.valueOf(bprfSets);
            EnumSet<BprfParameterSetCapabilityFlag> bprfFlag;
            bprfFlag = FlagEnum.toEnumSet(bprfSetsValue, BprfParameterSetCapabilityFlag.values());
            builder.setBprfParameterSetCapabilities(bprfFlag);

            byte[] hprfSets = tlvs.getByteArray(SUPPORTED_HPRF_PARAMETER_SETS_VER_1_0);
            // Extend the 5 bytes from HAL to 8 bytes for long.
            long hprfSetsValue = new BigInteger(TlvUtil.getReverseBytes(hprfSets)).longValue();
            EnumSet<HprfParameterSetCapabilityFlag> hprfFlag;
            hprfFlag = FlagEnum.longToEnumSet(
                    hprfSetsValue, HprfParameterSetCapabilityFlag.values());
            builder.setHprfParameterSetCapabilities(hprfFlag);

            EnumSet<FiraParams.PrfCapabilityFlag> prfFlag =
                    EnumSet.noneOf(FiraParams.PrfCapabilityFlag.class);
            boolean hasBprfSupport = bprfSets != 0;
            if (hasBprfSupport) {
                prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT);
            }
            boolean hasHprfSupport =
                    IntStream.range(0, hprfSets.length).parallel().anyMatch(i -> hprfSets[i] != 0);
            if (hasHprfSupport) {
                prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT);
            }
            builder.setPrfCapabilities(prfFlag);

            byte ccConstraintUci = tlvs.getByte(SUPPORTED_CC_CONSTRAINT_LENGTH_VER_1_0);
            EnumSet<PsduDataRateCapabilityFlag> psduRateFlag =
                    EnumSet.noneOf(PsduDataRateCapabilityFlag.class);
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasBprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasBprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasHprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasHprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT);
            }
            builder.setPsduDataRateCapabilities(psduRateFlag);

            byte aoaUci = tlvs.getByte(SUPPORTED_AOA_VER_1_0);
            EnumSet<FiraParams.AoaCapabilityFlag> aoaFlag =
                    EnumSet.noneOf(FiraParams.AoaCapabilityFlag.class);
            if (isBitSet(aoaUci, AOA_AZIMUTH_90)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_AZIMUTH_180)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_ELEVATION)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_FOM)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
            }
            byte aoaInterleavingUci = tlvs.getByte(SUPPORTED_AOA_RESULT_REQ_INTERLEAVING);
            if (isBitSet(aoaInterleavingUci, AOA_RESULT_REQ_INTERLEAVING)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_INTERLEAVING_SUPPORT);
            }
            builder.setAoaCapabilities(aoaFlag);

            try {
                int maxMessageSizeUci = tlvs.getShort(SUPPORTED_MAX_MESSAGE_SIZE_VER_1_0);
                builder.setMaxMessageSize(Integer.valueOf(maxMessageSizeUci));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_MAX_MESSAGE_SIZE not found.");
            }
            try {
                int maxDataPacketPayloadSizeUci = tlvs.getShort(
                        SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_1_0);
                builder.setMaxDataPacketPayloadSize(Integer.valueOf(maxDataPacketPayloadSizeUci));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE not found.");
            }
        } else if (versionCheck.length == 4) {
            // FiRa Version 2.0
            byte[] phyVersions = tlvs.getByteArray(SUPPORTED_FIRA_PHY_VERSION_RANGE_VER_2_0);
            builder.setMinPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 0));
            builder.setMaxPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 2));
            byte[] macVersions = tlvs.getByteArray(SUPPORTED_FIRA_MAC_VERSION_RANGE_VER_2_0);
            builder.setMinMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 0));
            builder.setMaxMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 2));

            byte[] deviceRolesUci = tlvs.getByteArray(SUPPORTED_DEVICE_ROLES_VER_2_0);
            EnumSet<DeviceRoleCapabilityFlag> deviceRoles =
                    EnumSet.noneOf(DeviceRoleCapabilityFlag.class);
            if (isBitSet(deviceRolesUci[0], INITIATOR)) {
                // This assumes both controller + controlee is supported.
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT);
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], RESPONDER)) {
                // This assumes both controller + controlee is supported.
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], UT_SYNCHRONIZATION_ANCHOR)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_UT_SYNCHRONIZATION_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], UT_ANCHOR)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_UT_ANCHOR_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], UT_TAG)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_UT_TAG_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], ADVERTISER)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_ADVERTISER_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], OBSERVER)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_OBSERVER_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[0], DT_ANCHOR)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_DT_ANCHOR_SUPPORT);
            }
            if (isBitSet(deviceRolesUci[1], DT_TAG)) {
                deviceRoles.add(DeviceRoleCapabilityFlag.HAS_DT_TAG_SUPPORT);
            }
            builder.setDeviceRoleCapabilities(deviceRoles);

            byte[] rangingMethodUci = tlvs.getByteArray(SUPPORTED_RANGING_METHOD_VER_2_0);
            EnumSet<RangingRoundCapabilityFlag> rangingRoundFlag = EnumSet.noneOf(
                    RangingRoundCapabilityFlag.class);
            if (isBitSet(rangingMethodUci[0], DS_TWR_DEFERRED)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[0], SS_TWR_DEFERRED)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[0], OWR_UL_TDOA)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_OWR_UL_TDOA_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[0], OWR_DL_TDOA)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_OWR_DL_TDOA_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[0], OWR_AOA)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_OWR_AOA_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[0], ESS_TWR_NON_DEFERRED)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_ESS_TWR_SUPPORT);
            }
            if (isBitSet(rangingMethodUci[1], ADS_TWR)) {
                rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_ADS_TWR_SUPPORT);
            }
            builder.setRangingRoundCapabilities(rangingRoundFlag);

            // TODO(b/209053358): This does not align with UCI spec.
            if (isBitSet(rangingMethodUci[0], DS_TWR_NON_DEFERRED)
                    || isBitSet(rangingMethodUci[0], SS_TWR_NON_DEFERRED)) {
                builder.hasNonDeferredModeSupport(true);
            }

            byte stsConfigUci = tlvs.getByte(SUPPORTED_STS_CONFIG_VER_2_0);
            EnumSet<StsCapabilityFlag> stsCapabilityFlag = EnumSet.noneOf(StsCapabilityFlag.class);
            if (isBitSet(stsConfigUci, STATIC_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, DYNAMIC_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsCapabilityFlag.add(
                        StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            if (isBitSet(stsConfigUci, PROVISIONED_STS)) {
                stsCapabilityFlag.add(StsCapabilityFlag.HAS_PROVISIONED_STS_SUPPORT);
            }
            if (isBitSet(stsConfigUci, PROVISIONED_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsCapabilityFlag.add(
                        StsCapabilityFlag.HAS_PROVISIONED_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            builder.setStsCapabilities(stsCapabilityFlag);

            byte multiNodeUci = tlvs.getByte(SUPPORTED_MULTI_NODE_MODES_VER_2_0);
            EnumSet<MultiNodeCapabilityFlag> multiNodeFlag =
                    EnumSet.noneOf(MultiNodeCapabilityFlag.class);
            if (isBitSet(multiNodeUci, UNICAST)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);
            }
            if (isBitSet(multiNodeUci, ONE_TO_MANY)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT);
            }
            if (isBitSet(multiNodeUci, MANY_TO_MANY)) {
                multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT);
            }
            builder.setMultiNodeCapabilities(multiNodeFlag);

            byte rangingTimeStructUci = tlvs.getByte(SUPPORTED_RANGING_TIME_STRUCT_VER_2_0);
            EnumSet<RangingTimeStructCapabilitiesFlag> rangingTimeStructFlag =
                    EnumSet.noneOf(RangingTimeStructCapabilitiesFlag.class);
            // When CR-423 is implemented, do not parse for the "INTERVAL_BASED_SCHEDULING" bit.
            if (!mUwbInjector.getFeatureFlags().cr423CleanupIntervalScheduling()) {
                if (isBitSet(rangingTimeStructUci, INTERVAL_BASED_SCHEDULING)) {
                    rangingTimeStructFlag.add(
                            RangingTimeStructCapabilitiesFlag.HAS_INTERVAL_BASED_SCHEDULING_SUPPORT);
                }
            }
            if (isBitSet(rangingTimeStructUci, BLOCK_BASED_SCHEDULING)) {
                rangingTimeStructFlag.add(
                        RangingTimeStructCapabilitiesFlag.HAS_BLOCK_BASED_SCHEDULING_SUPPORT);
            }
            builder.setRangingTimeStructCapabilities(rangingTimeStructFlag);

            byte schedulingModeUci = tlvs.getByte(SUPPORTED_SCHEDULED_MODE_VER_2_0);
            EnumSet<SchedulingModeCapabilitiesFlag> schedulingModeFlag =
                    EnumSet.noneOf(SchedulingModeCapabilitiesFlag.class);
            if (isBitSet(schedulingModeUci, CONTENTION_BASED_RANGING)) {
                schedulingModeFlag.add(
                        SchedulingModeCapabilitiesFlag.HAS_CONTENTION_BASED_RANGING_SUPPORT);
            }
            if (isBitSet(schedulingModeUci, TIME_SCHEDULED_RANGING)) {
                schedulingModeFlag.add(
                        SchedulingModeCapabilitiesFlag.HAS_TIME_SCHEDULED_RANGING_SUPPORT);
            }
            builder.setSchedulingModeCapabilities(schedulingModeFlag);

            byte ccConstraintLengthUci = tlvs.getByte(SUPPORTED_CC_CONSTRAINT_LENGTH_VER_2_0);
            EnumSet<CcConstraintLengthCapabilitiesFlag> ccConstraintLengthFlag =
                    EnumSet.noneOf(CcConstraintLengthCapabilitiesFlag.class);
            if (isBitSet(ccConstraintLengthUci, CONSTRAINT_LENGTH_3)) {
                ccConstraintLengthFlag.add(
                        CcConstraintLengthCapabilitiesFlag.HAS_CONSTRAINT_LENGTH_3_SUPPORT);
            }
            if (isBitSet(ccConstraintLengthUci, CONSTRAINT_LENGTH_7)) {
                ccConstraintLengthFlag.add(
                        CcConstraintLengthCapabilitiesFlag.HAS_CONSTRAINT_LENGTH_7_SUPPORT);
            }
            builder.setCcConstraintLengthCapabilities(ccConstraintLengthFlag);

            byte blockStridingUci = tlvs.getByte(SUPPORTED_BLOCK_STRIDING_VER_2_0);
            if (isBitSet(blockStridingUci, BLOCK_STRIDING)) {
                builder.hasBlockStridingSupport(true);
            }

            byte hoppingPreferenceUci = tlvs.getByte(SUPPORTED_HOPPING_MODE_VER_2_0);
            if (isBitSet(hoppingPreferenceUci, HOPPING_MODE)) {
                builder.hasHoppingPreferenceSupport(true);
            }

            byte extendedMacAddressUci = tlvs.getByte(SUPPORTED_EXTENDED_MAC_ADDRESS_VER_2_0);
            if (isBitSet(extendedMacAddressUci, EXTENDED_MAC_ADDRESS)) {
                builder.hasExtendedMacAddressSupport(true);
            }

            byte initiationTimeUci = tlvs.getByte(SUPPORTED_UWB_INITIATION_TIME_VER_2_0);
            if (isBitSet(initiationTimeUci, UWB_INITIATION_TIME)) {
                builder.hasInitiationTimeSupport(true);
            }

            byte channelsUci = tlvs.getByte(SUPPORTED_CHANNELS_VER_2_0);
            List<Integer> channels = new ArrayList<>();
            if (isBitSet(channelsUci, CHANNEL_5)) {
                channels.add(5);
            }
            if (isBitSet(channelsUci, CHANNEL_6)) {
                channels.add(6);
            }
            if (isBitSet(channelsUci, CHANNEL_8)) {
                channels.add(8);
            }
            if (isBitSet(channelsUci, CHANNEL_9)) {
                channels.add(9);
            }
            if (isBitSet(channelsUci, CHANNEL_10)) {
                channels.add(10);
            }
            if (isBitSet(channelsUci, CHANNEL_12)) {
                channels.add(12);
            }
            if (isBitSet(channelsUci, CHANNEL_13)) {
                channels.add(13);
            }
            if (isBitSet(channelsUci, CHANNEL_14)) {
                channels.add(14);
            }
            builder.setSupportedChannels(channels);

            byte rframeConfigUci = tlvs.getByte(SUPPORTED_RFRAME_CONFIG_VER_2_0);
            EnumSet<RframeCapabilityFlag> rframeConfigFlag =
                    EnumSet.noneOf(RframeCapabilityFlag.class);
            if (isBitSet(rframeConfigUci, SP0)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
            }
            if (isBitSet(rframeConfigUci, SP1)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
            }
            if (isBitSet(rframeConfigUci, SP3)) {
                rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
            }
            builder.setRframeCapabilities(rframeConfigFlag);

            byte bprfSets = tlvs.getByte(SUPPORTED_BPRF_PARAMETER_SETS_VER_2_0);
            int bprfSetsValue = Integer.valueOf(bprfSets);
            EnumSet<BprfParameterSetCapabilityFlag> bprfFlag;
            bprfFlag = FlagEnum.toEnumSet(bprfSetsValue, BprfParameterSetCapabilityFlag.values());
            builder.setBprfParameterSetCapabilities(bprfFlag);

            byte[] hprfSets = tlvs.getByteArray(SUPPORTED_HPRF_PARAMETER_SETS_VER_2_0);
            // Extend the 5 bytes from HAL to 8 bytes for long.
            long hprfSetsValue = new BigInteger(TlvUtil.getReverseBytes(hprfSets)).longValue();
            EnumSet<HprfParameterSetCapabilityFlag> hprfFlag;
            hprfFlag = FlagEnum.longToEnumSet(
                    hprfSetsValue, HprfParameterSetCapabilityFlag.values());
            builder.setHprfParameterSetCapabilities(hprfFlag);

            EnumSet<FiraParams.PrfCapabilityFlag> prfFlag =
                    EnumSet.noneOf(FiraParams.PrfCapabilityFlag.class);
            boolean hasBprfSupport = bprfSets != 0;
            if (hasBprfSupport) {
                prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT);
            }
            boolean hasHprfSupport =
                    IntStream.range(0, hprfSets.length).parallel().anyMatch(i -> hprfSets[i] != 0);
            if (hasHprfSupport) {
                prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT);
            }
            builder.setPrfCapabilities(prfFlag);

            byte ccConstraintUci = tlvs.getByte(SUPPORTED_CC_CONSTRAINT_LENGTH_VER_2_0);
            EnumSet<PsduDataRateCapabilityFlag> psduRateFlag =
                    EnumSet.noneOf(PsduDataRateCapabilityFlag.class);
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasBprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasBprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasHprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT);
            }
            if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasHprfSupport) {
                psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT);
            }
            builder.setPsduDataRateCapabilities(psduRateFlag);

            byte aoaUci = tlvs.getByte(SUPPORTED_AOA_VER_2_0);
            EnumSet<FiraParams.AoaCapabilityFlag> aoaFlag =
                    EnumSet.noneOf(FiraParams.AoaCapabilityFlag.class);
            if (isBitSet(aoaUci, AOA_AZIMUTH_90)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_AZIMUTH_180)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_ELEVATION)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
            }
            if (isBitSet(aoaUci, AOA_FOM)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
            }
            byte aoaInterleavingUci = tlvs.getByte(SUPPORTED_AOA_RESULT_REQ_INTERLEAVING);
            if (isBitSet(aoaInterleavingUci, AOA_RESULT_REQ_INTERLEAVING)) {
                aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_INTERLEAVING_SUPPORT);
            }
            builder.setAoaCapabilities(aoaFlag);

            try {
                int maxMessageSizeUci = tlvs.getShort(SUPPORTED_MAX_MESSAGE_SIZE_VER_2_0);
                builder.setMaxMessageSize(Integer.valueOf(maxMessageSizeUci));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_MAX_MESSAGE_SIZE not found.");
            }
            try {
                int maxDataPacketPayloadSizeUci = tlvs.getShort(
                        SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE_VER_2_0);
                builder.setMaxDataPacketPayloadSize(Integer.valueOf(maxDataPacketPayloadSizeUci));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_MAX_DATA_PACKET_PAYLOAD_SIZE not found.");
            }

            int deviceType = tlvs.getByte(SUPPORTED_DEVICE_TYPE_VER_2_0);
            builder.setDeviceType(deviceType);

            int supportedSuspendSupport = tlvs.getByte(SUPPORTED_SUSPEND_RANGING_VER_2_0);
            builder.setSuspendRangingSupport(supportedSuspendSupport != 0);

            int sessionKeyLength = tlvs.getByte(SUPPORTED_SESSION_KEY_LENGTH_VER_2_0);
            builder.setSessionKeyLength(sessionKeyLength);

            try {
                int dtTagMaxActiveRr = tlvs.getByte(SUPPORTED_DT_TAG_MAX_ACTIVE_RR_2_0);
                builder.setDtTagMaxActiveRr(dtTagMaxActiveRr);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_DT_TAG_MAX_ACTIVE_RR not found.");
            }

            try {
                byte dtTagBlockSkippingUci = tlvs.getByte(SUPPORTED_DT_TAG_BLOCK_SKIPPING_2_0);
                if (isBitSet(dtTagBlockSkippingUci, DT_TAG_BLOCK_SKIPPING)) {
                    builder.setDtTagBlockSkippingSupport(true);
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_DT_TAG_BLOCK_SKIPPING_2_0 not found.");
            }

            try {
                byte psduLengthUci = tlvs.getByte(SUPPORTED_PSDU_LENGTH_2_0);
                if (isBitSet(psduLengthUci, PSDU_LENGTH_SUPPORT)) {
                    builder.setPsduLengthSupport(true);
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "SUPPORTED_PSDU_LENGTH_2_0 not found.");
            }
        } else {
            // This FiRa version is not supported yet.
            return null;
        }
        try {
            int minRangingInterval = tlvs.getInt(SUPPORTED_MIN_RANGING_INTERVAL_MS);
            builder.setMinRangingIntervalSupported(minRangingInterval);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_MIN_RANGING_INTERVAL_MS not found.");
        }

        try {
            int minSlotDurationUs = TlvUtil.rstuToUs(tlvs.getInt(SUPPORTED_MIN_SLOT_DURATION_RSTU));
            builder.setMinSlotDurationSupportedUs(minSlotDurationUs);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_MIN_SLOT_DURATION not found.");
        }

        try {
            int maxRangingSessionNumber = tlvs.getInt(SUPPORTED_MAX_RANGING_SESSION_NUMBER);
            builder.setMaxRangingSessionNumberSupported(maxRangingSessionNumber);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_MAX_RANGING_SESSION_NUMBER not found");
        }

        try {
            byte rssiReporting = tlvs.getByte(SUPPORTED_RSSI_REPORTING);
            if (isBitSet(rssiReporting, RSSI_REPORTING)) {
                builder.hasRssiReportingSupport(true);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_RSSI_REPORTING not found.");
        }

        try {
            byte diagnostics = tlvs.getByte(SUPPORTED_DIAGNOSTICS);
            if (isBitSet(diagnostics, DIAGNOSTICS)) {
                builder.hasDiagnosticsSupport(true);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_DIAGNOSTICS not found.");
        }

        try {
            byte[] rangeDataNtfConfigUciBytes = tlvs.getByteArray(SUPPORTED_RANGE_DATA_NTF_CONFIG);
            int rangeDataNtfConfigUci =
                    new BigInteger(TlvUtil.getReverseBytes(rangeDataNtfConfigUciBytes)).intValue();
            EnumSet<FiraParams.RangeDataNtfConfigCapabilityFlag> rangeDataNtfConfigCapabilityFlag =
                    EnumSet.noneOf(FiraParams.RangeDataNtfConfigCapabilityFlag.class);
            if (isBitSet(rangeDataNtfConfigUci, RANGE_DATA_NTF_CONFIG_ENABLE)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE);
            }
            if (isBitSet(rangeDataNtfConfigUci, RANGE_DATA_NTF_CONFIG_DISABLE)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_DISABLE);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG);
            }
            if (isBitSet(rangeDataNtfConfigUci,
                    RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG)) {
                rangeDataNtfConfigCapabilityFlag.add(
                        FiraParams.RangeDataNtfConfigCapabilityFlag
                                .HAS_RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG);
            }
            builder.setRangeDataNtfConfigCapabilities(rangeDataNtfConfigCapabilityFlag);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SUPPORTED_RANGE_DATA_NTF_CONFIG not found.");
        }
        return builder.build();
    }
}
