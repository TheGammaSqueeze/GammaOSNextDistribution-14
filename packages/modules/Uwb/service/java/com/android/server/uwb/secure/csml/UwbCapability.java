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

package com.android.server.uwb.secure.csml;

import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_180;
import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_90;
import static com.android.server.uwb.config.CapabilityParam.AOA_ELEVATION;
import static com.android.server.uwb.config.CapabilityParam.AOA_FOM;
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
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.INITIATOR;
import static com.android.server.uwb.config.CapabilityParam.MANY_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.ONE_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.OWR_UL_TDOA;
import static com.android.server.uwb.config.CapabilityParam.RESPONDER;
import static com.android.server.uwb.config.CapabilityParam.SP0;
import static com.android.server.uwb.config.CapabilityParam.SP1;
import static com.android.server.uwb.config.CapabilityParam.SP3;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.STATIC_STS;
import static com.android.server.uwb.config.CapabilityParam.UNICAST;

import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_3;
import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_7;
import static com.google.uwb.support.fira.FiraParams.CONTENTION_BASED_RANGING;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_2_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DL_TDOA;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP0;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.TIME_SCHEDULED_RANGING;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.config.CapabilityParam;
import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.StsCapabilityFlag;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * UWB_CAPABILITY defined in 8.5.3.2
 */
public class UwbCapability {
    public static final int FIRA_PHY_VERSION_RANGE = 0x80;
    public static final int FIRA_MAC_VERSION_RANGE = 0x81;
    public static final int DEVICE_ROLES = 0x82;
    public static final int RANGING_METHOD = 0x83;
    public static final int STS_CONFIG = 0x84;
    public static final int MULTI_NODE_MODE = 0x85;
    public static final int RANGING_TIME_STRUCT = 0x86;
    public static final int SCHEDULED_MODE = 0x87;
    public static final int HOPPING_MODE = 0x88;
    public static final int BLOCK_STRIDING = 0x89;
    public static final int UWB_INITIATION_TIME = 0x8A;
    public static final int CHANNELS = 0x8B;
    public static final int RFRAME_CONFIG = 0x8C;
    public static final int CC_CONSTRAINT_LENGTH = 0x8D;
    public static final int BPRF_PARAMETER_SETS = 0x8E;
    public static final int HPRF_PARAMETER_SETS = 0x8F;
    public static final int AOA_SUPPORT = 0x90;
    public static final int EXTENDED_MAC_ADDRESS = 0x91;
    public static final int UWB_CAPABILITY_MAX_COUNT = 18;

    public static final int DEFAULT_CHANNEL = 9;

    public final FiraProtocolVersion mMinPhyVersionSupported;
    public final FiraProtocolVersion mMaxPhyVersionSupported;
    public final FiraProtocolVersion mMinMacVersionSupported;
    public final FiraProtocolVersion mMaxMacVersionSupported;
    public final Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> mDeviceRoles;
    public final Optional<Byte> mRangingMethod;
    public final Optional<EnumSet<FiraParams.StsCapabilityFlag>> mStsConfig;
    public final Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> mMultiNodeMode;
    public final Optional<Byte> mRangingTimeStruct;
    public final Optional<Byte> mScheduledMode;
    public final Optional<Boolean> mHoppingMode;
    public final Optional<Boolean> mBlockStriding;
    public final Optional<Boolean> mUwbInitiationTime;
    public final Optional<List<Integer>> mChannels;
    public final Optional<EnumSet<FiraParams.RframeCapabilityFlag>> mRframeConfig;
    public final Optional<Byte> mCcConstraintLength;
    public final Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> mBprfParameterSet;
    public final Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> mHprfParameterSet;
    public final Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mAoaSupport;
    public final Optional<Byte> mExtendedMacSupport;

    private UwbCapability(FiraProtocolVersion minPhyVersionSupported,
            FiraProtocolVersion maxPhyVersionSupported,
            FiraProtocolVersion minMacVersionSupported,
            FiraProtocolVersion maxMacVersionSupported,
            Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> deviceRoles,
            Optional<Byte> rangingMethod,
            Optional<EnumSet<FiraParams.StsCapabilityFlag>> stsConfig,
            Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> multiNodeMode,
            Optional<Byte> rangingTimeStruct,
            Optional<Byte> scheduledMode,
            Optional<Boolean> hoppingMode,
            Optional<Boolean> blockStriding,
            Optional<Boolean> uwbInitiationTime,
            Optional<List<Integer>> channels,
            Optional<EnumSet<FiraParams.RframeCapabilityFlag>> rframeConfig,
            Optional<Byte> ccConstraintLength,
            Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> bprfParameterSet,
            Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> hprfParameterSet,
            Optional<EnumSet<FiraParams.AoaCapabilityFlag>> aoaSupport,
            Optional<Byte> extendedMacSupport) {
        mMinPhyVersionSupported = minPhyVersionSupported;
        mMaxPhyVersionSupported = maxPhyVersionSupported;
        mMinMacVersionSupported = minMacVersionSupported;
        mMaxMacVersionSupported = maxMacVersionSupported;
        mDeviceRoles = deviceRoles;
        mRangingMethod = rangingMethod;
        mStsConfig = stsConfig;
        mMultiNodeMode = multiNodeMode;
        mRangingTimeStruct = rangingTimeStruct;
        mScheduledMode = scheduledMode;
        mHoppingMode = hoppingMode;
        mBlockStriding = blockStriding;
        mUwbInitiationTime = uwbInitiationTime;
        mChannels = channels;
        mRframeConfig = rframeConfig;
        mCcConstraintLength = ccConstraintLength;
        mBprfParameterSet = bprfParameterSet;
        mHprfParameterSet = hprfParameterSet;
        mAoaSupport = aoaSupport;
        mExtendedMacSupport = extendedMacSupport;
    }

    /**
     * Converts the UwbCapabilities to the bytes which are combined per the TLV of CSML 8.5.3.2.
     */
    @NonNull
    public byte[] toBytes() {
        TlvBuffer.Builder uwbCapabilityBuilder = new TlvBuffer.Builder()
                .putByteArray(FIRA_PHY_VERSION_RANGE, new byte[]{
                        (byte) mMinPhyVersionSupported.getMajor(),
                        (byte) mMinPhyVersionSupported.getMinor(),
                        (byte) mMaxPhyVersionSupported.getMajor(),
                        (byte) mMaxPhyVersionSupported.getMinor(),
                })
                .putByteArray(FIRA_MAC_VERSION_RANGE, new byte[]{
                        (byte) mMinMacVersionSupported.getMajor(),
                        (byte) mMinMacVersionSupported.getMinor(),
                        (byte) mMaxMacVersionSupported.getMajor(),
                        (byte) mMaxMacVersionSupported.getMinor(),
                });
        if (mDeviceRoles.isPresent()) {
            byte deviceRoles = 0;
            if (mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT)
                    && mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT)) {
                deviceRoles = (byte) (deviceRoles | RESPONDER);
            }
            if (mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT)
                    && mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT)) {
                deviceRoles = (byte) (deviceRoles | INITIATOR);
            }
            uwbCapabilityBuilder.putByte(DEVICE_ROLES, deviceRoles);
        }
        if (mRangingMethod.isPresent()) {
            uwbCapabilityBuilder.putByte(RANGING_METHOD, mRangingMethod.get());
        }
        if (mStsConfig.isPresent()) {
            byte stsConfig = 0;
            if (mStsConfig.get().contains(FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT)) {
                stsConfig = (byte) (stsConfig | STATIC_STS);
            }
            if (mStsConfig.get().contains(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT)) {
                stsConfig = (byte) (stsConfig | DYNAMIC_STS);
            }
            if (mStsConfig.get().contains(
                    FiraParams.StsCapabilityFlag
                            .HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT)) {
                stsConfig = (byte) (stsConfig | DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY);
            }
            uwbCapabilityBuilder.putByte(STS_CONFIG, stsConfig);
        }
        if (mMultiNodeMode.isPresent()) {
            byte multiMode = 0;
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT)) {
                multiMode = (byte) (multiMode | UNICAST);
            }
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT)) {
                multiMode = (byte) (multiMode | ONE_TO_MANY);
            }
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT)) {
                multiMode = (byte) (multiMode | MANY_TO_MANY);
            }
            uwbCapabilityBuilder.putByte(MULTI_NODE_MODE, multiMode);
        }
        mRangingTimeStruct.ifPresent(
                aByte -> uwbCapabilityBuilder.putByte(RANGING_TIME_STRUCT, aByte));

        mScheduledMode.ifPresent(
                aByte -> uwbCapabilityBuilder.putByte(SCHEDULED_MODE, aByte));

        mHoppingMode.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(HOPPING_MODE,
                (byte) (aBoolean ? 1 : 0)));

        mBlockStriding.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(BLOCK_STRIDING,
                (byte) (aBoolean ? 1 : 0)));

        mUwbInitiationTime.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(UWB_INITIATION_TIME,
                (byte) (aBoolean ? 1 : 0)));
        if (mChannels.isPresent()) {
            byte channels = 0;
            if (mChannels.get().contains(5)) {
                channels = (byte) (channels | CHANNEL_5);
            }
            if (mChannels.get().contains(6)) {
                channels = (byte) (channels | CHANNEL_6);
            }
            if (mChannels.get().contains(8)) {
                channels = (byte) (channels | CHANNEL_8);
            }
            if (mChannels.get().contains(9)) {
                channels = (byte) (channels | CHANNEL_9);
            }
            if (mChannels.get().contains(10)) {
                channels = (byte) (channels | CHANNEL_10);
            }
            if (mChannels.get().contains(12)) {
                channels = (byte) (channels | CHANNEL_12);
            }
            if (mChannels.get().contains(13)) {
                channels = (byte) (channels | CHANNEL_13);
            }
            if (mChannels.get().contains(14)) {
                channels = (byte) (channels | CHANNEL_14);
            }
            uwbCapabilityBuilder.putByte(CHANNELS, channels);
        }
        if (mRframeConfig.isPresent()) {
            byte rFrameConfig = 0;
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP0);
            }
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP1);
            }
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP3);
            }
            uwbCapabilityBuilder.putByte(RFRAME_CONFIG, rFrameConfig);
        }
        if (mCcConstraintLength.isPresent()) {
            uwbCapabilityBuilder.putByte(CC_CONSTRAINT_LENGTH, mCcConstraintLength.get());
        }
        if (mBprfParameterSet.isPresent()) {
            byte bprfParameterSet = (byte) FlagEnum.toInt(mBprfParameterSet.get());
            uwbCapabilityBuilder.putByte(BPRF_PARAMETER_SETS, bprfParameterSet);
        }
        if (mHprfParameterSet.isPresent()) {
            byte hprfParameterSet = (byte) FlagEnum.toInt(mHprfParameterSet.get());
            uwbCapabilityBuilder.putByte(HPRF_PARAMETER_SETS, hprfParameterSet);
        }
        if (mAoaSupport.isPresent()) {
            byte aoaSupport = 0;
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_AZIMUTH_90);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_AZIMUTH_180);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_ELEVATION);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_FOM);
            }
            uwbCapabilityBuilder.putByte(AOA_SUPPORT, aoaSupport);
        }
        mExtendedMacSupport.ifPresent(
                aByte -> uwbCapabilityBuilder.putByte(EXTENDED_MAC_ADDRESS, aByte.byteValue()));

        return uwbCapabilityBuilder.build().getByteArray();
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private static boolean isPresent(TlvDecoderBuffer tlvDecoderBuffer, int tagType) {
        try {
            tlvDecoderBuffer.getByte(tagType);
        } catch (IllegalArgumentException e) {
            try {
                tlvDecoderBuffer.getByteArray(tagType);
            } catch (IllegalArgumentException e1) {
                return false;
            }
        }
        return true;
    }

    private static byte getRangingMethod(@NonNull FiraSpecificationParams firaSpecificationParams) {
        EnumSet<FiraParams.RangingRoundCapabilityFlag>  rangingRoundCapabilityFlags =
                firaSpecificationParams.getRangingRoundCapabilities();
        int rangingMethod = 0;
        if (rangingRoundCapabilityFlags.contains(
                FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT)) {
            rangingMethod |= DS_TWR_DEFERRED;
            if (firaSpecificationParams.hasNonDeferredModeSupport()) {
                rangingMethod |= DS_TWR_NON_DEFERRED;
            }
        }
        if (rangingRoundCapabilityFlags
                .contains(FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT)) {
            rangingMethod |= SS_TWR_DEFERRED;
            if (firaSpecificationParams.hasNonDeferredModeSupport()) {
                rangingMethod |= SS_TWR_NON_DEFERRED;
            }
        }
        return (byte) rangingMethod;
    }

    /** Converts the FiRaSpecificationParam to UwbCapability. */
    @NonNull
    public static UwbCapability fromFiRaSpecificationParam(
            @NonNull FiraSpecificationParams firaSpecificationParams) {
        return new UwbCapability.Builder()
                .setMinPhyVersionSupported(firaSpecificationParams.getMinPhyVersionSupported())
                .setMaxPhyVersionSupported(firaSpecificationParams.getMaxPhyVersionSupported())
                .setMinMacVersionSupported(firaSpecificationParams.getMinMacVersionSupported())
                .setMaxMacVersionSupported(firaSpecificationParams.getMaxMacVersionSupported())
                .setDeviceRoles(firaSpecificationParams.getDeviceRoleCapabilities())
                .setRangingMethod(getRangingMethod(firaSpecificationParams))
                .setStsConfig(firaSpecificationParams.getStsCapabilities())
                .setMultiNodeMode(firaSpecificationParams.getMultiNodeCapabilities())
                .setBlockStriding(firaSpecificationParams.hasBlockStridingSupport())
                .setUwbInitiationTime(firaSpecificationParams.hasInitiationTimeSupport())
                .setChannels(firaSpecificationParams.getSupportedChannels())
                .setRFrameConfig(firaSpecificationParams.getRframeCapabilities())
                .setCcConstraintLength(getCcConstraintLength(
                        firaSpecificationParams.getPsduDataRateCapabilities()))
                .setBprfParameterSet(firaSpecificationParams.getBprfParameterSetCapabilities())
                .setHprfParameterSet(firaSpecificationParams.getHprfParameterSetCapabilities())
                .setAoaSupport(firaSpecificationParams.getAoaCapabilities())
                .build();
    }

    private static byte getCcConstraintLength(
            EnumSet<FiraParams.PsduDataRateCapabilityFlag> psduDataRateCapabilityFlags) {
        byte ccConstraintLength = (byte) 0;
        if (psduDataRateCapabilityFlags.isEmpty()
                || psduDataRateCapabilityFlags.contains(
                        FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT)
                || psduDataRateCapabilityFlags.contains(
                        FiraParams.PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT)) {
            ccConstraintLength |= (byte) CC_CONSTRAINT_LENGTH_K3;
        }
        if (psduDataRateCapabilityFlags.contains(
                        FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT)
                || psduDataRateCapabilityFlags.contains(
                        FiraParams.PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT)) {
            ccConstraintLength |= (byte) CC_CONSTRAINT_LENGTH_K7;
        }

        return ccConstraintLength;
    }

    /** Checks if the capabilities are compatible. */
    boolean isCompatibleTo(@NonNull UwbCapability remoteCap) {
        // mac version
        if (mMinMacVersionSupported.getMajor() > remoteCap.mMaxMacVersionSupported.getMajor()
                || mMaxMacVersionSupported.getMajor()
                        < remoteCap.mMinMacVersionSupported.getMajor()) {
            return false;
        } else if (mMinMacVersionSupported.getMinor() > remoteCap.mMaxMacVersionSupported.getMinor()
                || mMaxMacVersionSupported.getMinor()
                        < remoteCap.mMinMacVersionSupported.getMinor()) {
            return false;
        }

        // phy version
        if (mMinPhyVersionSupported.getMajor() > remoteCap.mMaxPhyVersionSupported.getMajor()
                || mMaxPhyVersionSupported.getMajor()
                        < remoteCap.mMinPhyVersionSupported.getMajor()) {
            return false;
        } else if (mMinPhyVersionSupported.getMinor() > remoteCap.mMaxPhyVersionSupported.getMinor()
                || mMaxPhyVersionSupported.getMinor()
                        < remoteCap.mMinPhyVersionSupported.getMinor()) {
            return false;
        }
        return true;
    }

    /** Gets the minimum phy version supported by both devices. */
    @NonNull
    FiraProtocolVersion getPreferredPhyVersion(FiraProtocolVersion remoteMinPhyVersion) {

        if (mMinPhyVersionSupported.getMajor() < remoteMinPhyVersion.getMajor()) {
            return remoteMinPhyVersion;
        } else if (mMinPhyVersionSupported.getMajor() > remoteMinPhyVersion.getMajor()) {
            return mMinPhyVersionSupported;
        } else if (mMinPhyVersionSupported.getMinor() < remoteMinPhyVersion.getMinor()) {
            return remoteMinPhyVersion;
        }
        return mMinPhyVersionSupported;
    }

    /** Gets the minimum mac version supported by both devices. */
    @NonNull
    FiraProtocolVersion getPreferredMacVersion(FiraProtocolVersion remoteMinMacVersion) {

        if (mMinMacVersionSupported.getMajor() < remoteMinMacVersion.getMajor()) {
            return remoteMinMacVersion;
        } else if (mMinMacVersionSupported.getMajor() > remoteMinMacVersion.getMajor()) {
            return mMinMacVersionSupported;
        } else if (mMinMacVersionSupported.getMinor() < remoteMinMacVersion.getMinor()) {
            return remoteMinMacVersion;
        }
        return mMinMacVersionSupported;
    }

    @FiraParams.MacAddressMode
    int getPreferredMacAddressMode(Optional<Byte> remoteExtendedMacSupport) {
        if (mExtendedMacSupport.isPresent() && mExtendedMacSupport.get() != 0
                && remoteExtendedMacSupport.isPresent() && remoteExtendedMacSupport.get() != 0) {
            return MAC_ADDRESS_MODE_8_BYTES;
        }
        return MAC_ADDRESS_MODE_2_BYTES;
    }

    @FiraParams.SchedulingMode
    int getPreferredScheduleMode(Optional<Byte> remoteScheduleMode) {
        if (mScheduledMode.isPresent() && remoteScheduleMode.isPresent()
                && (mScheduledMode.get() & remoteScheduleMode.get()
                        & (byte) CapabilityParam.CONTENTION_BASED_RANGING) != 0) {
            return CONTENTION_BASED_RANGING;
        }
        return TIME_SCHEDULED_RANGING;
    }

    @FiraParams.RframeConfig
    int getPreferredRframeConfig(
            Optional<EnumSet<FiraParams.RframeCapabilityFlag>> remoteRframeConfig) {
        if (mRframeConfig.isEmpty() || remoteRframeConfig.isEmpty()) {
            return RFRAME_CONFIG_SP3;
        }
        if (mRframeConfig.get().contains(FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT)
                && remoteRframeConfig.get().contains(
                        FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT)) {
            return RFRAME_CONFIG_SP3;
        }
        if (mRframeConfig.get().contains(FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT)
                && remoteRframeConfig.get().contains(
                        FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT)) {
            return RFRAME_CONFIG_SP1;
        }
        if (mRframeConfig.get().contains(FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT)
                && remoteRframeConfig.get().contains(
                        FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT)) {
            return RFRAME_CONFIG_SP0;
        }
        return RFRAME_CONFIG_SP3;
    }

    @FiraParams.StsConfig
    int getPreferredStsConfig(
            Optional<EnumSet<StsCapabilityFlag>> remoteStsCapFlags,
            boolean isMultiCast) {
        if (!isMultiCast) {
            return STS_CONFIG_DYNAMIC;
        }
        if (mStsConfig.isEmpty() && remoteStsCapFlags.isEmpty()) {
            return STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
        }

        if ((remoteStsCapFlags.isEmpty() && mStsConfig.get().contains(
                    StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT))
                    || (mStsConfig.isEmpty() && remoteStsCapFlags.get().contains(
                            StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT))
                    || (mStsConfig.get().contains(
                            StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT)
                        && remoteStsCapFlags.get().contains(
                                StsCapabilityFlag
                                        .HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT))) {
            return STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
        }

        return STS_CONFIG_DYNAMIC;
    }

    @NonNull
    Optional<Integer> getPreferredChannel(Optional<List<Integer>> remoteChannels) {
        if ((mChannels.isEmpty() && remoteChannels.isEmpty())
                || (mChannels.isEmpty() && remoteChannels.get().contains(DEFAULT_CHANNEL))
                || (remoteChannels.isEmpty() && mChannels.get().contains(DEFAULT_CHANNEL))) {
            return Optional.of(DEFAULT_CHANNEL);
        }
        List<Integer> commonChannels = mChannels.get().stream()
                .distinct().filter(remoteChannels.get()::contains)
                .collect(Collectors.toList());

        return commonChannels.stream().findAny();
    }

    boolean getPreferredHoppingMode(Optional<Boolean> remoteHoppingMode) {
        if (mHoppingMode.isEmpty() || remoteHoppingMode.isEmpty()) {
            return false;
        }
        return mHoppingMode.get() && remoteHoppingMode.get();
    }

    @FiraParams.CcConstraintLength
    int getPreferredConstrainLengthOfConvolutionalCode(
            Optional<Byte> remoteCcConstrainLength) {
        if (mCcConstraintLength.isEmpty() || remoteCcConstrainLength.isEmpty()) {
            return CONSTRAINT_LENGTH_3;
        }
        if ((mCcConstraintLength.get() & remoteCcConstrainLength.get()
                & CC_CONSTRAINT_LENGTH_K7) != 0) {
            return CONSTRAINT_LENGTH_7;
        }
        return CONSTRAINT_LENGTH_3;
    }

    @FiraParams.RangingRoundUsage
    int getPreferredRangingMethod(Optional<Byte> remoteRangingMethod) {
        if (mRangingMethod.isEmpty() || remoteRangingMethod.isEmpty()) {
            return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
        }

        byte rangingMethodMask = (byte) (mRangingMethod.get() & remoteRangingMethod.get());

        if ((rangingMethodMask & DS_TWR_DEFERRED) != 0) {
            return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
        }
        if ((rangingMethodMask & DS_TWR_NON_DEFERRED) != 0) {
            return RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;
        }
        if ((rangingMethodMask & SS_TWR_DEFERRED) != 0) {
            return RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
        }
        if ((rangingMethodMask & SS_TWR_NON_DEFERRED) != 0) {
            return RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE;
        }
        if (((rangingMethodMask & OWR_UL_TDOA) != 0)) {
            return RANGING_ROUND_USAGE_DL_TDOA;
        }
        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
    }

    boolean getPreferredBlockStriding(Optional<Boolean> remoteBlockStriding) {
        if (mBlockStriding.isEmpty() || remoteBlockStriding.isEmpty()) {
            return false;
        }
        return mBlockStriding.get() && remoteBlockStriding.get();
    }

    /**
     * Converts the UwbCapabilities from the data stream, which is encoded per the CSML 8.5.3.2.
     *
     * @return null if the data cannot be decoded per spec.
     */
    @Nullable
    public static UwbCapability fromBytes(@NonNull byte[] data) {
        TlvDecoderBuffer uwbCapabilityTlv = new TlvDecoderBuffer(data, UWB_CAPABILITY_MAX_COUNT);
        uwbCapabilityTlv.parse();
        UwbCapability.Builder uwbCapabilityBuilder = new UwbCapability.Builder();

        if (isPresent(uwbCapabilityTlv, FIRA_PHY_VERSION_RANGE)) {
            byte[] firaPhyVersionRange = uwbCapabilityTlv.getByteArray(FIRA_PHY_VERSION_RANGE);
            if (firaPhyVersionRange.length == 4) {
                FiraProtocolVersion minVersion = new FiraProtocolVersion(firaPhyVersionRange[0],
                        firaPhyVersionRange[1]);
                FiraProtocolVersion maxVersion = new FiraProtocolVersion(firaPhyVersionRange[2],
                        firaPhyVersionRange[3]);
                uwbCapabilityBuilder.setMinPhyVersionSupported(minVersion);
                uwbCapabilityBuilder.setMaxPhyVersionSupported(maxVersion);
            }
        }
        if (isPresent(uwbCapabilityTlv, FIRA_MAC_VERSION_RANGE)) {
            byte[] firaMacVersionRange = uwbCapabilityTlv.getByteArray(FIRA_MAC_VERSION_RANGE);
            if (firaMacVersionRange.length == 4) {
                FiraProtocolVersion minVersion = new FiraProtocolVersion(firaMacVersionRange[0],
                        firaMacVersionRange[1]);
                FiraProtocolVersion maxVersion = new FiraProtocolVersion(firaMacVersionRange[2],
                        firaMacVersionRange[3]);
                uwbCapabilityBuilder.setMinMacVersionSupported(minVersion);
                uwbCapabilityBuilder.setMaxMacVersionSupported(maxVersion);
            }
        }
        if (isPresent(uwbCapabilityTlv, DEVICE_ROLES)) {
            EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoles = EnumSet.noneOf(
                    FiraParams.DeviceRoleCapabilityFlag.class);
            byte deviceRolesRaw = uwbCapabilityTlv.getByte(DEVICE_ROLES);
            if (isBitSet(deviceRolesRaw, INITIATOR)) {
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT);
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT);
            }
            if (isBitSet(deviceRolesRaw, RESPONDER)) {
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);
            }
            uwbCapabilityBuilder.setDeviceRoles(deviceRoles);
        }
        if (isPresent(uwbCapabilityTlv, RANGING_METHOD)) {
            uwbCapabilityBuilder.setRangingMethod(uwbCapabilityTlv.getByte(RANGING_METHOD));
        }
        if (isPresent(uwbCapabilityTlv, STS_CONFIG)) {
            EnumSet<FiraParams.StsCapabilityFlag> stsConfig = EnumSet.noneOf(
                    FiraParams.StsCapabilityFlag.class);
            byte stsConfigRaw = uwbCapabilityTlv.getByte(STS_CONFIG);
            if (isBitSet(stsConfigRaw, STATIC_STS)) {
                stsConfig.add(FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigRaw, DYNAMIC_STS)) {
                stsConfig.add(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigRaw, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsConfig.add(
                        FiraParams.StsCapabilityFlag
                                .HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            uwbCapabilityBuilder.setStsConfig(stsConfig);
        }
        if (isPresent(uwbCapabilityTlv, MULTI_NODE_MODE)) {
            EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeMode = EnumSet.noneOf(
                    FiraParams.MultiNodeCapabilityFlag.class);
            byte multiNodeRaw = uwbCapabilityTlv.getByte(MULTI_NODE_MODE);
            if (isBitSet(multiNodeRaw, UNICAST)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);
            }
            if (isBitSet(multiNodeRaw, ONE_TO_MANY)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT);
            }
            if (isBitSet(multiNodeRaw, MANY_TO_MANY)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT);
            }
            uwbCapabilityBuilder.setMultiMode(multiNodeMode);
        }
        if (isPresent(uwbCapabilityTlv, RANGING_TIME_STRUCT)) {
            uwbCapabilityBuilder.setRangingTimeStruct(
                    uwbCapabilityTlv.getByte(RANGING_TIME_STRUCT));
        }
        if (isPresent(uwbCapabilityTlv, SCHEDULED_MODE)) {
            uwbCapabilityBuilder.setScheduledMode(uwbCapabilityTlv.getByte(SCHEDULED_MODE));
        }
        if (isPresent(uwbCapabilityTlv, HOPPING_MODE)) {
            uwbCapabilityBuilder.setHoppingMode(uwbCapabilityTlv.getByte(HOPPING_MODE) == 1);
        }
        if (isPresent(uwbCapabilityTlv, BLOCK_STRIDING)) {
            uwbCapabilityBuilder.setBlockStriding(uwbCapabilityTlv.getByte(BLOCK_STRIDING) == 1);
        }
        if (isPresent(uwbCapabilityTlv, UWB_INITIATION_TIME)) {
            uwbCapabilityBuilder.setUwbInitiationTime(
                    uwbCapabilityTlv.getByte(UWB_INITIATION_TIME) == 1);
        }
        if (isPresent(uwbCapabilityTlv, CHANNELS)) {
            List<Integer> channels = new ArrayList<>();
            byte channelsRaw = uwbCapabilityTlv.getByte(CHANNELS);
            if (isBitSet(channelsRaw, CHANNEL_5)) {
                channels.add(5);
            }
            if (isBitSet(channelsRaw, CHANNEL_6)) {
                channels.add(6);
            }
            if (isBitSet(channelsRaw, CHANNEL_8)) {
                channels.add(8);
            }
            if (isBitSet(channelsRaw, CHANNEL_9)) {
                channels.add(9);
            }
            if (isBitSet(channelsRaw, CHANNEL_10)) {
                channels.add(10);
            }
            if (isBitSet(channelsRaw, CHANNEL_12)) {
                channels.add(12);
            }
            if (isBitSet(channelsRaw, CHANNEL_13)) {
                channels.add(13);
            }
            if (isBitSet(channelsRaw, CHANNEL_14)) {
                channels.add(14);
            }
            uwbCapabilityBuilder.setChannels(channels);
        }
        if (isPresent(uwbCapabilityTlv, RFRAME_CONFIG)) {
            EnumSet<FiraParams.RframeCapabilityFlag> rFrameConfig = EnumSet.noneOf(
                    FiraParams.RframeCapabilityFlag.class);
            byte rFrameConfigRaw = uwbCapabilityTlv.getByte(RFRAME_CONFIG);
            if (isBitSet(rFrameConfigRaw, SP0)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
            }
            if (isBitSet(rFrameConfigRaw, SP1)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
            }
            if (isBitSet(rFrameConfigRaw, SP3)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
            }
            uwbCapabilityBuilder.setRFrameConfig(rFrameConfig);
        }
        if (isPresent(uwbCapabilityTlv, CC_CONSTRAINT_LENGTH)) {
            byte ccConstraintLength = uwbCapabilityTlv.getByte(CC_CONSTRAINT_LENGTH);
            uwbCapabilityBuilder.setCcConstraintLength(ccConstraintLength);
        }
        if (isPresent(uwbCapabilityTlv, AOA_SUPPORT)) {
            EnumSet<FiraParams.AoaCapabilityFlag> aoaSupport = EnumSet.noneOf(
                    FiraParams.AoaCapabilityFlag.class);
            byte aoaSupportRaw = uwbCapabilityTlv.getByte(AOA_SUPPORT);
            if (isBitSet(aoaSupportRaw, AOA_AZIMUTH_90)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_AZIMUTH_180)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_ELEVATION)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_FOM)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
            }
            uwbCapabilityBuilder.setAoaSupport(aoaSupport);
        }
        if (isPresent(uwbCapabilityTlv, BPRF_PARAMETER_SETS)) {
            byte bprfSets = uwbCapabilityTlv.getByte(BPRF_PARAMETER_SETS);
            int bprfSetsValue = Integer.valueOf(bprfSets);
            EnumSet<FiraParams.BprfParameterSetCapabilityFlag> bprfFlag;
            bprfFlag = FlagEnum.toEnumSet(bprfSetsValue,
                    FiraParams.BprfParameterSetCapabilityFlag.values());
            uwbCapabilityBuilder.setBprfParameterSet(bprfFlag);
        }
        if (isPresent(uwbCapabilityTlv, HPRF_PARAMETER_SETS)) {
            byte hprfSets = uwbCapabilityTlv.getByte(HPRF_PARAMETER_SETS);
            int hprfSetsValue = Integer.valueOf(hprfSets);
            EnumSet<FiraParams.HprfParameterSetCapabilityFlag> hprfFlag;
            hprfFlag = FlagEnum.toEnumSet(hprfSetsValue,
                    FiraParams.HprfParameterSetCapabilityFlag.values());
            uwbCapabilityBuilder.setHprfParameterSet(hprfFlag);
        }
        if (isPresent(uwbCapabilityTlv, EXTENDED_MAC_ADDRESS)) {
            uwbCapabilityBuilder.setExtendedMacSupport(
                    uwbCapabilityTlv.getByte(EXTENDED_MAC_ADDRESS));
        }
        return uwbCapabilityBuilder.build();
    }

    /** Builder for UwbCapabilities */
    public static class Builder {
        // Set all default protocol version to FiRa 1.1
        private FiraProtocolVersion mMinPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMinMacVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxMacVersionSupported = new FiraProtocolVersion(1, 1);
        private Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> mDeviceRoles =
                Optional.empty();
        private Optional<Byte> mRangingMethod = Optional.empty();
        private Optional<EnumSet<FiraParams.StsCapabilityFlag>> mStsConfig = Optional.empty();
        private Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> mMultiNodeMode =
                Optional.empty();
        private Optional<Byte> mRangingTimeStruct = Optional.empty();
        private Optional<Byte> mScheduledMode = Optional.empty();
        private Optional<Boolean> mHoppingMode = Optional.empty();
        private Optional<Boolean> mBlockStriding = Optional.empty();
        private Optional<Boolean> mUwbInitiationTime = Optional.empty();
        private Optional<List<Integer>> mChannels = Optional.empty();
        private Optional<EnumSet<FiraParams.RframeCapabilityFlag>> mRframeConfig = Optional.empty();
        private Optional<Byte> mCcConstraintLength =
                Optional.empty();
        private Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> mBprfParameterSet =
                Optional.empty();
        private Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> mHprfParameterSet =
                Optional.empty();
        private Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mAoaSupport = Optional.empty();
        private Optional<Byte> mExtendedMacSupport = Optional.empty();

        UwbCapability.Builder setMinPhyVersionSupported(
                FiraProtocolVersion minPhyVersionSupported) {
            mMinPhyVersionSupported = minPhyVersionSupported;
            return this;
        }

        UwbCapability.Builder setMaxPhyVersionSupported(
                FiraProtocolVersion maxPhyVersionSupported) {
            mMaxPhyVersionSupported = maxPhyVersionSupported;
            return this;
        }

        UwbCapability.Builder setMinMacVersionSupported(
                FiraProtocolVersion minMacVersionSupported) {
            mMinMacVersionSupported = minMacVersionSupported;
            return this;
        }

        UwbCapability.Builder setMaxMacVersionSupported(
                FiraProtocolVersion maxMacVersionSupported) {
            mMaxMacVersionSupported = maxMacVersionSupported;
            return this;
        }

        UwbCapability.Builder setDeviceRoles(
                EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoles) {
            mDeviceRoles = Optional.of(deviceRoles);
            return this;
        }

        UwbCapability.Builder setRangingMethod(
                byte rangingMethod) {
            mRangingMethod = Optional.of(rangingMethod);
            return this;
        }

        UwbCapability.Builder setStsConfig(
                EnumSet<FiraParams.StsCapabilityFlag> stsConfig) {
            mStsConfig = Optional.of(stsConfig);
            return this;
        }

        UwbCapability.Builder setMultiMode(
                EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeMode) {
            mMultiNodeMode = Optional.of(multiNodeMode);
            return this;
        }

        UwbCapability.Builder setRangingTimeStruct(Byte rangingTimeStruct) {
            mRangingTimeStruct = Optional.of(rangingTimeStruct);
            return this;
        }

        UwbCapability.Builder setScheduledMode(Byte scheduledMode) {
            mScheduledMode = Optional.of(scheduledMode);
            return this;
        }

        UwbCapability.Builder setHoppingMode(Boolean hoppingMode) {
            mHoppingMode = Optional.of(hoppingMode);
            return this;
        }

        UwbCapability.Builder setBlockStriding(Boolean blockStriding) {
            mBlockStriding = Optional.of(blockStriding);
            return this;
        }

        UwbCapability.Builder setUwbInitiationTime(Boolean uwbInitiationTime) {
            mUwbInitiationTime = Optional.of(uwbInitiationTime);
            return this;
        }

        UwbCapability.Builder setChannels(List<Integer> channels) {
            mChannels = Optional.of(channels);
            return this;
        }

        UwbCapability.Builder setMultiNodeMode(
                EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeMode) {
            mMultiNodeMode = Optional.of(multiNodeMode);
            return this;
        }

        UwbCapability.Builder setRFrameConfig(
                EnumSet<FiraParams.RframeCapabilityFlag> rFrameConfig) {
            mRframeConfig = Optional.of(rFrameConfig);
            return this;
        }

        UwbCapability.Builder setCcConstraintLength(byte ccConstraintLength) {
            mCcConstraintLength = Optional.of(ccConstraintLength);
            return this;
        }

        UwbCapability.Builder setBprfParameterSet(
                EnumSet<FiraParams.BprfParameterSetCapabilityFlag> bprfParameterSet) {
            mBprfParameterSet = Optional.of(bprfParameterSet);
            return this;
        }

        UwbCapability.Builder setHprfParameterSet(
                EnumSet<FiraParams.HprfParameterSetCapabilityFlag> hprfParameterSet) {
            mHprfParameterSet = Optional.of(hprfParameterSet);
            return this;
        }

        UwbCapability.Builder setAoaSupport(
                EnumSet<FiraParams.AoaCapabilityFlag> aoaSupport) {
            mAoaSupport = Optional.of(aoaSupport);
            return this;
        }

        UwbCapability.Builder setExtendedMacSupport(Byte extendedMacSupport) {
            mExtendedMacSupport = Optional.of(extendedMacSupport);
            return this;
        }

        UwbCapability build() {
            return new UwbCapability(
                    mMinPhyVersionSupported,
                    mMaxPhyVersionSupported,
                    mMinMacVersionSupported,
                    mMaxMacVersionSupported,
                    mDeviceRoles,
                    mRangingMethod,
                    mStsConfig,
                    mMultiNodeMode,
                    mRangingTimeStruct,
                    mScheduledMode,
                    mHoppingMode,
                    mBlockStriding,
                    mUwbInitiationTime,
                    mChannels,
                    mRframeConfig,
                    mCcConstraintLength,
                    mBprfParameterSet,
                    mHprfParameterSet,
                    mAoaSupport,
                    mExtendedMacSupport
            );
        }
    }
}
