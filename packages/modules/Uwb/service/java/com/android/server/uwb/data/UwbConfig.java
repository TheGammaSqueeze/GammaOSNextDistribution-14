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

package com.android.server.uwb.data;

import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_3;
import static com.google.uwb.support.fira.FiraParams.CONSTRAINT_LENGTH_7;
import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_DISABLE;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_16;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_27M2;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_31M2;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_7M80;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;
import static com.google.uwb.support.fira.FiraParams.TIME_SCHEDULED_RANGING;
import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.UWB_PREAMBLE_CODE_INDEX_10;

import android.annotation.IntDef;
import android.annotation.Nullable;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RangingSessionController;
import com.android.server.uwb.secure.csml.ConfigurationParams;
import com.android.server.uwb.secure.csml.SessionData;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.MacFcsType;
import com.google.uwb.support.fira.FiraParams.MultiNodeMode;
import com.google.uwb.support.fira.FiraParams.PrfMode;
import com.google.uwb.support.fira.FiraParams.RangingRoundUsage;
import com.google.uwb.support.fira.FiraParams.RframeConfig;
import com.google.uwb.support.fira.FiraParams.StsConfig;
import com.google.uwb.support.fira.FiraParams.UwbChannel;
import com.google.uwb.support.fira.FiraParams.UwbPreambleCodeIndex;
import com.google.uwb.support.fira.FiraProtocolVersion;

public class UwbConfig {

    /** UWB OOB types */
    @IntDef(
            value = {
                    OOB_TYPE_NONE,
                    OOB_TYPE_BLE,
            })
    public @interface OobType {
    }

    public static final int OOB_TYPE_NONE = 0;
    public static final int OOB_TYPE_BLE = 1;

    /** UWB OOB roles */
    @IntDef(
            value = {
                    CENTRAL_GATT_CLIENT,
                    PERIPHERAL_GATT_SERVER,
                    PERIPHERAL,
                    CENTRAL,
            })
    public @interface OobBleRole {
    }

    public static final int CENTRAL_GATT_CLIENT = 0;
    public static final int PERIPHERAL_GATT_SERVER = 1;
    public static final int PERIPHERAL = 2;
    public static final int CENTRAL = 3;

    /** UWB device role */
    @IntDef(
            value = {
                    CONTROLEE_AND_RESPONDER,
                    CONTROLEE_AND_INITIATOR,
                    CONTROLLER_AND_RESPONDER,
                    CONTROLLER_AND_INITIATOR,
            })
    public @interface UwbRole {
    }

    public static final int CONTROLEE_AND_RESPONDER = 0;
    public static final int CONTROLEE_AND_INITIATOR = 1;
    public static final int CONTROLLER_AND_RESPONDER = 2;
    public static final int CONTROLLER_AND_INITIATOR = 3;

    public static final int DEVICE_TYPE_BITMASK = 0b00000001; // 1
    public static final int DEVICE_ROLE_BITMASK = 0b00000010; // 2

    @UwbRole
    public final int mUwbRole;
    @RangingRoundUsage
    public final int mRangingRoundUsage;
    @MultiNodeMode
    public final int mMultiNodeMode;
    @RframeConfig
    public final int mRframeConfig;
    @StsConfig
    public final int mStsConfig;
    public final int mRoundHopping;
    @FiraParams.SchedulingMode
    public final int mScheduleMode;
    public final int mMaxContentionPhaseLength;
    public final boolean mTofReport;
    public final boolean mAoaAzimuthReport;
    public final boolean mAoaElevationReport;
    public final boolean mAoaFomReport;
    public final boolean mBlockStriding;
    public final int mSlotDurationRstu;
    public final int mSlotsPerRangingRound;
    public final int mRangingIntervalMs;
    @UwbChannel
    public final int mUwbChannel;
    @UwbPreambleCodeIndex
    public final int mUwbPreambleCodeIndex;
    public final int mSp0PhyParameterSet;
    public final int mSp1PhyParameterSet;
    public final int mSp3PhyParameterSet;
    public final int mMaxRetry;
    @PrfMode
    public final int mPrfMode;
    public final int mConstraintLengthConvolutionalCode;
    public final int mUwbInitiationTimeMs;
    public final int mKeyRotationRate;
    @MacFcsType
    public final int mMacFcsType;
    public final int mRangingRoundControl;
    @Nullable
    public final byte[] mVendorID;
    @Nullable
    public final byte[] mStaticStsIV;
    @OobType
    public final int mOobType;
    @OobBleRole
    public final int mOobBleRole;

    private UwbConfig(
            @UwbRole int uwbRole,
            @RangingRoundUsage int rangingRoundUsage,
            @MultiNodeMode int multiNodeMode,
            @RframeConfig int rframeConfig,
            @StsConfig int stsConfig,
            int roundHopping,
            @FiraParams.SchedulingMode int scheduleMode,
            int maxContentionPhaseLength,
            boolean tofReport,
            boolean aoaAzimuthReport,
            boolean aoaElevationReport,
            boolean aoaFomReport,
            boolean blockStriding,
            int slotDurationRstu,
            int slotsPerRangingRound,
            int rangingIntervalMs,
            @UwbChannel int uwbChannel,
            @UwbPreambleCodeIndex int uwbPreambleCodeIndex,
            int sp0PhyParameterSet,
            int sp1PhyParameterSet,
            int sp3PhyParameterSet,
            int maxRetry,
            @PrfMode int prfMode,
            int constraintLengthConvolutionalCode,
            int uwbInitiationTimeMs,
            int keyRotationRate,
            @MacFcsType int macFcsType,
            int rangingRoundControl,
            @Nullable byte[] vendorID,
            @Nullable byte[] staticStsIV,
            @OobType int oobType,
            @OobBleRole int oobBleRole) {
        mUwbRole = uwbRole;
        mRangingRoundUsage = rangingRoundUsage;
        mMultiNodeMode = multiNodeMode;
        mRframeConfig = rframeConfig;
        mStsConfig = stsConfig;
        mRoundHopping = roundHopping;
        mScheduleMode = scheduleMode;
        mMaxContentionPhaseLength = maxContentionPhaseLength;
        mTofReport = tofReport;
        mAoaAzimuthReport = aoaAzimuthReport;
        mAoaElevationReport = aoaElevationReport;
        mAoaFomReport = aoaFomReport;
        mBlockStriding = blockStriding;
        mSlotDurationRstu = slotDurationRstu;
        mSlotsPerRangingRound = slotsPerRangingRound;
        mRangingIntervalMs = rangingIntervalMs;
        mUwbChannel = uwbChannel;
        mUwbPreambleCodeIndex = uwbPreambleCodeIndex;
        mSp0PhyParameterSet = sp0PhyParameterSet;
        mSp1PhyParameterSet = sp1PhyParameterSet;
        mSp3PhyParameterSet = sp3PhyParameterSet;
        mMaxRetry = maxRetry;
        mPrfMode = prfMode;
        mConstraintLengthConvolutionalCode = constraintLengthConvolutionalCode;
        mUwbInitiationTimeMs = uwbInitiationTimeMs;
        mKeyRotationRate = keyRotationRate;
        mMacFcsType = macFcsType;
        mRangingRoundControl = rangingRoundControl;
        this.mVendorID = vendorID;
        this.mStaticStsIV = staticStsIV;
        mOobType = oobType;
        mOobBleRole = oobBleRole;
    }

    public static final class Builder {
        private final RequiredParam<Integer> mUwbRole = new RequiredParam<>();
        private final RequiredParam<Integer> mMultiNodeMode = new RequiredParam<>();
        private final RequiredParam<Integer> mOobType = new RequiredParam<>();
        private final RequiredParam<Integer> mOobBleRole = new RequiredParam<>();

        @StsConfig
        private int mStsConfig = STS_CONFIG_DYNAMIC;

        /** UCI spec default: DS-TWR with deferred mode */
        @RangingRoundUsage
        private int mRangingRoundUsage = RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;

        /** UCI spec default: SP3 */
        @RframeConfig
        private int mRframeConfig = RFRAME_CONFIG_SP3;

        /** Round hopping disabled */
        private int mRoundHopping = HOPPING_MODE_DISABLE;

        /** UCI spec default: Time Based */
        @FiraParams.SchedulingMode
        private int mScheduleMode = TIME_SCHEDULED_RANGING;

        /** Time based used by default */
        private int mMaxContentionPhaseLength = 0;

        /** UCI spec default: time of flight report enabled */
        private boolean mTofReport = true;

        /** UCI spec default: No AoA azimuth report */
        private boolean mAoaAzimuthReport = false;

        /** UCI spec default: No AoA Elevation report */
        private boolean mAoaElevationReport = false;

        /** UCI spec default: No AoA FOM report */
        private boolean mAoaFomReport = false;

        /** UCI spec default: No Block Striding */
        private boolean mBlockStriding = false;

        /** UCI spec default: 2400 RSTU (2 ms). */
        private int mSlotDurationRstu = 2400;

        /** UCI spec default: 30 slots per ranging round. */
        private int mSlotsPerRangingRound = 30;

        /** UCI spec default: RANGING_INTERVAL(Fira 2.0: RANGING_DURATION) 200 ms */
        private int mRangingIntervalMs = 200;

        /** UCI spec default: Channel 9, which is the only mandatory channel. */
        @UwbChannel
        private int mUwbChannel = UWB_CHANNEL_9;

        /** UCI spec default: index 10 */
        @UwbPreambleCodeIndex
        private int mUwbPreambleCodeIndex = UWB_PREAMBLE_CODE_INDEX_10;

        /** SP0 PHY parameter set */
        private int mSp0PhyParameterSet = 1;

        /** SP1 PHY parameter set */
        private int mSp1PhyParameterSet = 3;

        /** SP3 PHY parameter set */
        private int mSp3PhyParameterSet = 4;

        /** UCI spec default: Unlimited */
        private int mMaxRetry = 0;

        /** UCI spec default: BPRF */
        @PrfMode
        private int mPrfMode = PRF_MODE_BPRF;
        @FiraParams.CcConstraintLength
        private int mConstraintLengthConvolutionalCode = CONSTRAINT_LENGTH_3;

        /** UCI spec default: 0ms */
        private int mUwbInitiationTimeMs = 0;

        /** UCI spec default: No key rotation */
        private int mKeyRotationRate = 0;

        /** UCI spec default: CRC-16 */
        @MacFcsType
        private int mMacFcsType = MAC_FCS_TYPE_CRC_16;

        private int mRangingRoundControl = 0;

        @Nullable
        private byte[] mVendorID = null;
        @Nullable
        private byte[] mStaticStsIV = null;

        /** Sets UWB role */
        public UwbConfig.Builder setUwbRole(@UwbRole int uwbRole) {
            mUwbRole.set(uwbRole);
            return this;
        }

        /** Sets the multiple node mode, unicast or multicast. */
        public UwbConfig.Builder setMultiNodeMode(@MultiNodeMode int multiNodeMode) {
            mMultiNodeMode.set(multiNodeMode);
            return this;
        }

        /** Sets the STS config mode */
        public UwbConfig.Builder setStsConfig(@StsConfig int stsConfig) {
            mStsConfig = stsConfig;
            return this;
        }

        UwbConfig.Builder setRangingRoundUsage(@RangingRoundUsage int rangingRoundUsage) {
            mRangingRoundUsage = rangingRoundUsage;
            return this;
        }

        /** Sets the RFrame config, sp3, sp1 or sp0 */
        public UwbConfig.Builder setRframeConfig(@RframeConfig int rframeConfig) {
            mRframeConfig = rframeConfig;
            return this;
        }

        UwbConfig.Builder setRoundHopping(int roundHopping) {
            mRoundHopping = roundHopping;
            return this;
        }

        UwbConfig.Builder setScheduleMode(@FiraParams.SchedulingMode int scheduleMode) {
            mScheduleMode = scheduleMode;
            return this;
        }

        UwbConfig.Builder setMaxContentionPhaseLength(int maxContentionPhaseLength) {
            mMaxContentionPhaseLength = maxContentionPhaseLength;
            return this;
        }

        /** Sets TOF report */
        public UwbConfig.Builder setTofReport(boolean tofReport) {
            mTofReport = tofReport;
            return this;
        }

        UwbConfig.Builder setAoaAzimuthReport(boolean aoaAzimuthReport) {
            mAoaAzimuthReport = aoaAzimuthReport;
            return this;
        }

        UwbConfig.Builder setAoaElevationReport(boolean aoaElevationReport) {
            mAoaElevationReport = aoaElevationReport;
            return this;
        }

        UwbConfig.Builder setAoaFomReport(boolean aoaFomReport) {
            mAoaFomReport = aoaFomReport;
            return this;
        }

        UwbConfig.Builder setBlockStriding(boolean blockStriding) {
            mBlockStriding = blockStriding;
            return this;
        }

        UwbConfig.Builder setSlotDurationRstu(int slotDurationRstu) {
            mSlotDurationRstu = slotDurationRstu;
            return this;
        }

        UwbConfig.Builder setSlotsPerRangingRound(int slotsPerRangingRound) {
            mSlotsPerRangingRound = slotsPerRangingRound;
            return this;
        }

        UwbConfig.Builder setRangingIntervalMs(int rangingIntervalMs) {
            mRangingIntervalMs = rangingIntervalMs;
            return this;
        }

        UwbConfig.Builder setUwbChannel(@UwbChannel int uwbChannel) {
            mUwbChannel = uwbChannel;
            return this;
        }

        UwbConfig.Builder setUwbPreambleCodeIndex(@UwbPreambleCodeIndex
                int uwbPreambleCodeIndex) {
            mUwbPreambleCodeIndex = uwbPreambleCodeIndex;
            return this;
        }

        UwbConfig.Builder setSp0PhyParameterSet(int sp0PhyParameterSet) {
            mSp0PhyParameterSet = sp0PhyParameterSet;
            return this;
        }

        UwbConfig.Builder setSp1PhyParameterSet(int sp1PhyParameterSet) {
            mSp1PhyParameterSet = sp1PhyParameterSet;
            return this;
        }

        UwbConfig.Builder setSp3PhyParameterSet(int sp3PhyParameterSet) {
            mSp3PhyParameterSet = sp3PhyParameterSet;
            return this;
        }

        UwbConfig.Builder setMaxRetry(int maxRetry) {
            mMaxRetry = maxRetry;
            return this;
        }

        UwbConfig.Builder setPrfMode(@PrfMode int prfMode) {
            mPrfMode = prfMode;
            return this;
        }

        UwbConfig.Builder setConstraintLengthConvolutionalCode(
                int constraintLengthConvolutionalCode) {
            mConstraintLengthConvolutionalCode = constraintLengthConvolutionalCode;
            return this;
        }

        UwbConfig.Builder setUwbInitiationTimeMs(int uwbInitiationTimeMs) {
            mUwbInitiationTimeMs = uwbInitiationTimeMs;
            return this;
        }

        UwbConfig.Builder setKeyRotationRate(int keyRotationRate) {
            mKeyRotationRate = keyRotationRate;
            return this;
        }

        UwbConfig.Builder setKMacFcsType(@MacFcsType int macFcsType) {
            mMacFcsType = macFcsType;
            return this;
        }

        UwbConfig.Builder setRangingRoundControl(int rangingRoundControl) {
            mRangingRoundControl = rangingRoundControl;
            return this;
        }

        UwbConfig.Builder setVendorID(byte[] vendorID) {
            mVendorID = vendorID;
            return this;
        }

        UwbConfig.Builder setStaticStsIV(byte[] staticStsIV) {
            mStaticStsIV = staticStsIV;
            return this;
        }

        /** Sets the OOB type */
        public UwbConfig.Builder setOobType(@OobType int uwbRole) {
            mOobType.set(uwbRole);
            return this;
        }

        /** Sets BLE role */
        public UwbConfig.Builder setOobBleRole(@OobBleRole int uwbRole) {
            mOobBleRole.set(uwbRole);
            return this;
        }

        /** build the instance of {@link UwbConfig}. */
        public UwbConfig build() {
            return new UwbConfig(
                    mUwbRole.get(),
                    mRangingRoundUsage,
                    mMultiNodeMode.get(),
                    mRframeConfig,
                    mStsConfig,
                    mRoundHopping,
                    mScheduleMode,
                    mMaxContentionPhaseLength,
                    mTofReport,
                    mAoaAzimuthReport,
                    mAoaElevationReport,
                    mAoaFomReport,
                    mBlockStriding,
                    mSlotDurationRstu,
                    mSlotsPerRangingRound,
                    mRangingIntervalMs,
                    mUwbChannel,
                    mUwbPreambleCodeIndex,
                    mSp0PhyParameterSet,
                    mSp1PhyParameterSet,
                    mSp3PhyParameterSet,
                    mMaxRetry,
                    mPrfMode,
                    mConstraintLengthConvolutionalCode,
                    mUwbInitiationTimeMs,
                    mKeyRotationRate,
                    mMacFcsType,
                    mRangingRoundControl,
                    mVendorID,
                    mStaticStsIV,
                    mOobType.get(),
                    mOobBleRole.get());
        }
    }

    @FiraParams.PsduDataRate
    int getPsduDataRate() {
        if (mPrfMode == PRF_MODE_BPRF) {
            if (mConstraintLengthConvolutionalCode == CONSTRAINT_LENGTH_3) {
                return PSDU_DATA_RATE_27M2;
            } else {
                return PSDU_DATA_RATE_6M81;
            }
        } else { // PRF_MODE_HPRF
            if (mConstraintLengthConvolutionalCode == CONSTRAINT_LENGTH_7) {
                return PSDU_DATA_RATE_31M2;
            } else {
                return PSDU_DATA_RATE_7M80;
            }
        }
    }

    /**
     * Convert UwbConfig to FiraOpenSessionParams
     */
    public static FiraOpenSessionParams getOpenSessionParams(
            RangingSessionController.SessionInfo sessionInfo, UwbConfig uwbConfig) {

        FiraProtocolVersion protocolVersion = FiraParams.PROTOCOL_VERSION_1_1;
        FiraOpenSessionParams.Builder firaOpenSessionBuilder = new FiraOpenSessionParams.Builder()
                .setSessionId(sessionInfo.getSessionId())
                .setDeviceAddress(sessionInfo.getDeviceAddress())
                .setDestAddressList(sessionInfo.mDestAddressList)
                .setProtocolVersion(protocolVersion)
                .setDeviceType(uwbConfig.mUwbRole & DEVICE_TYPE_BITMASK)
                .setDeviceRole(uwbConfig.mUwbRole & DEVICE_ROLE_BITMASK)
                .setRangingRoundUsage(uwbConfig.mRangingRoundUsage)
                .setMultiNodeMode(uwbConfig.mMultiNodeMode)
                .setRframeConfig(uwbConfig.mRframeConfig)
                .setStsConfig(uwbConfig.mStsConfig)
                .setHoppingMode(uwbConfig.mRoundHopping)
                .setHasTimeOfFlightReport(uwbConfig.mTofReport)
                .setHasAngleOfArrivalAzimuthReport(uwbConfig.mAoaAzimuthReport)
                .setHasAngleOfArrivalElevationReport(uwbConfig.mAoaElevationReport)
                .setRangingIntervalMs(uwbConfig.mRangingIntervalMs)
                .setPsduDataRate(uwbConfig.getPsduDataRate())
                .setPrfMode(uwbConfig.mPrfMode)
                .setScheduledMode(uwbConfig.mScheduleMode);

        sessionInfo.subSessionId.ifPresent(firaOpenSessionBuilder::setSubSessionId);
        sessionInfo.mSubSessionKey.ifPresent(firaOpenSessionBuilder::setSubsessionKey);
        sessionInfo.mSessionKey.ifPresent(firaOpenSessionBuilder::setSessionKey);

        return firaOpenSessionBuilder.build();
    }

    /** Converts the fields of {@link SessionData} to those fields of UwbConfig. */
    public static UwbConfig fromSessionData(
            @NonNull UwbConfig.Builder uwbConfigBuilder, @NonNull SessionData sessionData) {
        if (sessionData.mConfigurationParams.isPresent()) {
            ConfigurationParams configurationParams =
                    sessionData.mConfigurationParams.get();
            configurationParams.mScheduleMode.ifPresent(uwbConfigBuilder::setScheduleMode);
            configurationParams.mBlockStriding.ifPresent(uwbConfigBuilder::setBlockStriding);
            configurationParams.mStsConfig.ifPresent(uwbConfigBuilder::setStsConfig);
            configurationParams.mChannel.ifPresent(uwbConfigBuilder::setUwbChannel);
            configurationParams.mSp0PhyParameterSet.ifPresent(
                    uwbConfigBuilder::setSp0PhyParameterSet);
            configurationParams.mSp1PhyParameterSet.ifPresent(
                    uwbConfigBuilder::setSp1PhyParameterSet);
            configurationParams.mSp3PhyParameterSet.ifPresent(
                    uwbConfigBuilder::setSp3PhyParameterSet);
            configurationParams.mPreambleCodeIndex.ifPresent(
                    uwbConfigBuilder::setUwbPreambleCodeIndex);
            configurationParams.mSlotsPerRangingRound.ifPresent(
                    uwbConfigBuilder::setSlotsPerRangingRound);
            configurationParams.mMaxContentionPhaseLength.ifPresent(
                    uwbConfigBuilder::setMaxContentionPhaseLength);
            configurationParams.mSlotDuration.ifPresent(
                    uwbConfigBuilder::setSlotDurationRstu);
            configurationParams.mRangingIntervalMs.ifPresent(
                    uwbConfigBuilder::setRangingIntervalMs);
            configurationParams.mKeyRotationRate.ifPresent(
                    uwbConfigBuilder::setKeyRotationRate);
            configurationParams.mMacFcsType.ifPresent(uwbConfigBuilder::setKMacFcsType);
            configurationParams.mRangingMethod.ifPresent(
                    uwbConfigBuilder::setRangingRoundUsage);
            configurationParams.mPrfMode.ifPresent(uwbConfigBuilder::setPrfMode);
            configurationParams.mCcConstraintLength.ifPresent(
                    uwbConfigBuilder::setConstraintLengthConvolutionalCode);
            configurationParams.mRframeConfig.ifPresent(uwbConfigBuilder::setRframeConfig);
        }

        return uwbConfigBuilder.build();
    }
}
