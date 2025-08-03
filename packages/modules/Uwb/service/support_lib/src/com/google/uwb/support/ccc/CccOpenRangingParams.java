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

package com.google.uwb.support.ccc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters for CCC open operation
 *
 * <p>This is passed as a bundle to the service API {@link UwbManager#openRangingSession}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccOpenRangingParams extends CccParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_PROTOCOL_VERSION = "protocol_version";
    private static final String KEY_UWB_CONFIG = "uwb_config";
    private static final String KEY_PULSE_SHAPE_COMBO = "pulse_shape_combo";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_TYPE = "session_type";
    private static final String KEY_RAN_MULTIPLIER = "ran_multiplier";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_NUM_CHAPS_PER_SLOT = "num_chaps_per_slot";
    private static final String KEY_NUM_RESPONDER_NODES = "num_responder_nodes";
    private static final String KEY_NUM_SLOTS_PER_ROUND = "num_slots_per_round";
    private static final String KEY_SYNC_CODE_INDEX = "sync_code_index";
    private static final String KEY_HOPPING_CONFIG_MODE = "hopping_config_mode";
    private static final String KEY_HOPPING_SEQUENCE = "hopping_sequence";
    private static final String KEY_STS_INDEX = "sts_index";
    private static final String KEY_INITIATION_TIME_MS = "initiation_time_ms";
    private static final String KEY_ABSOLUTE_INITIATION_TIME_US = "absolute_initiation_time_us";
    private static final String KEY_RANGE_DATA_NTF_CONFIG = "range_data_ntf_config";
    private static final String KEY_RANGE_DATA_NTF_PROXIMITY_NEAR = "range_data_ntf_proximity_near";
    private static final String KEY_RANGE_DATA_NTF_PROXIMITY_FAR = "range_data_ntf_proximity_far";
    private static final String KEY_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER =
            "range_data_ntf_aoa_azimuth_lower";
    private static final String KEY_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER =
            "range_data_ntf_aoa_azimuth_upper";
    private static final String KEY_RANGE_DATA_NTF_AOA_ELEVATION_LOWER =
            "range_data_ntf_aoa_elevation_lower";
    private static final String KEY_RANGE_DATA_NTF_AOA_ELEVATION_UPPER =
            "range_data_ntf_aoa_elevation_upper";

    private final CccProtocolVersion mProtocolVersion;
    @UwbConfig private final int mUwbConfig;
    private final CccPulseShapeCombo mPulseShapeCombo;
    private final int mSessionId;
    @SessionType private final int mSessionType;
    private final int mRanMultiplier;
    @Channel private final int mChannel;
    private final int mNumChapsPerSlot;
    private final int mNumResponderNodes;
    private final int mNumSlotsPerRound;
    @SyncCodeIndex private final int mSyncCodeIndex;
    @HoppingConfigMode private final int mHoppingConfigMode;
    @HoppingSequence private final int mHoppingSequence;
    private final int mStsIndex;
    // FiRa 1.0: Relative time (in milli-seconds).
    // FiRa 2.0: Relative time (in milli-seconds).
    private final long mInitiationTimeMs;

    // FiRa 2.0: Absolute time in UWB time domain, as specified in CR-272 (in micro-seconds).
    private final long mAbsoluteInitiationTimeUs;

    // RANGE_DATA_NTF_CONFIG related fields.
    @RangeDataNtfConfig private final int mRangeDataNtfConfig;
    private final int mRangeDataNtfProximityNear;
    private final int mRangeDataNtfProximityFar;
    private double mRangeDataNtfAoaAzimuthLower;
    private double mRangeDataNtfAoaAzimuthUpper;
    private double mRangeDataNtfAoaElevationLower;
    private double mRangeDataNtfAoaElevationUpper;

    private CccOpenRangingParams(
            CccProtocolVersion protocolVersion,
            @UwbConfig int uwbConfig,
            CccPulseShapeCombo pulseShapeCombo,
            int sessionId,
            @SessionType int sessionType,
            int ranMultiplier,
            @Channel int channel,
            int numChapsPerSlot,
            int numResponderNodes,
            int numSlotsPerRound,
            @SyncCodeIndex int syncCodeIndex,
            @HoppingConfigMode int hoppingConfigMode,
            @HoppingSequence int hoppingSequence,
            int stsIndex,
            long initiationTimeMs,
            long absoluteInitiationTimeUs,
            @RangeDataNtfConfig int rangeDataNtfConfig,
            int rangeDataNtfProximityNear,
            int rangeDataNtfProximityFar,
            double rangeDataNtfAoaAzimuthLower,
            double rangeDataNtfAoaAzimuthUpper,
            double rangeDataNtfAoaElevationLower,
            double rangeDataNtfAoaElevationUpper) {
        mProtocolVersion = protocolVersion;
        mUwbConfig = uwbConfig;
        mPulseShapeCombo = pulseShapeCombo;
        mSessionId = sessionId;
        mSessionType = sessionType;
        mRanMultiplier = ranMultiplier;
        mChannel = channel;
        mNumChapsPerSlot = numChapsPerSlot;
        mNumResponderNodes = numResponderNodes;
        mNumSlotsPerRound = numSlotsPerRound;
        mSyncCodeIndex = syncCodeIndex;
        mHoppingConfigMode = hoppingConfigMode;
        mHoppingSequence = hoppingSequence;
        mStsIndex = stsIndex;
        mInitiationTimeMs = initiationTimeMs;
        mAbsoluteInitiationTimeUs = absoluteInitiationTimeUs;
        mRangeDataNtfConfig = rangeDataNtfConfig;
        mRangeDataNtfProximityNear = rangeDataNtfProximityNear;
        mRangeDataNtfProximityFar = rangeDataNtfProximityFar;
        mRangeDataNtfAoaAzimuthLower = rangeDataNtfAoaAzimuthLower;
        mRangeDataNtfAoaAzimuthUpper = rangeDataNtfAoaAzimuthUpper;
        mRangeDataNtfAoaElevationLower = rangeDataNtfAoaElevationLower;
        mRangeDataNtfAoaElevationUpper = rangeDataNtfAoaElevationUpper;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putString(KEY_PROTOCOL_VERSION, mProtocolVersion.toString());
        bundle.putInt(KEY_UWB_CONFIG, mUwbConfig);
        bundle.putString(KEY_PULSE_SHAPE_COMBO, mPulseShapeCombo.toString());
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_SESSION_TYPE, mSessionType);
        bundle.putInt(KEY_RAN_MULTIPLIER, mRanMultiplier);
        bundle.putInt(KEY_CHANNEL, mChannel);
        bundle.putInt(KEY_NUM_CHAPS_PER_SLOT, mNumChapsPerSlot);
        bundle.putInt(KEY_NUM_RESPONDER_NODES, mNumResponderNodes);
        bundle.putInt(KEY_NUM_SLOTS_PER_ROUND, mNumSlotsPerRound);
        bundle.putInt(KEY_SYNC_CODE_INDEX, mSyncCodeIndex);
        bundle.putInt(KEY_HOPPING_CONFIG_MODE, mHoppingConfigMode);
        bundle.putInt(KEY_HOPPING_SEQUENCE, mHoppingSequence);
        bundle.putInt(KEY_STS_INDEX, mStsIndex);
        bundle.putLong(KEY_INITIATION_TIME_MS, mInitiationTimeMs);
        bundle.putLong(KEY_ABSOLUTE_INITIATION_TIME_US, mAbsoluteInitiationTimeUs);
        bundle.putInt(KEY_RANGE_DATA_NTF_CONFIG, mRangeDataNtfConfig);
        bundle.putInt(KEY_RANGE_DATA_NTF_PROXIMITY_NEAR, mRangeDataNtfProximityNear);
        bundle.putInt(KEY_RANGE_DATA_NTF_PROXIMITY_FAR, mRangeDataNtfProximityFar);
        bundle.putDouble(KEY_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER, mRangeDataNtfAoaAzimuthLower);
        bundle.putDouble(KEY_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER, mRangeDataNtfAoaAzimuthUpper);
        bundle.putDouble(KEY_RANGE_DATA_NTF_AOA_ELEVATION_LOWER, mRangeDataNtfAoaElevationLower);
        bundle.putDouble(KEY_RANGE_DATA_NTF_AOA_ELEVATION_UPPER, mRangeDataNtfAoaElevationUpper);
        return bundle;
    }

    public static CccOpenRangingParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static CccOpenRangingParams parseBundleVersion1(PersistableBundle bundle) {
        return new Builder()
                .setProtocolVersion(
                        CccProtocolVersion.fromString(
                                checkNotNull(bundle.getString(KEY_PROTOCOL_VERSION))))
                .setUwbConfig(bundle.getInt(KEY_UWB_CONFIG))
                .setPulseShapeCombo(
                        CccPulseShapeCombo.fromString(
                                checkNotNull(bundle.getString(KEY_PULSE_SHAPE_COMBO))))
                .setSessionId(bundle.getInt(KEY_SESSION_ID))
                .setRanMultiplier(bundle.getInt(KEY_RAN_MULTIPLIER))
                .setChannel(bundle.getInt(KEY_CHANNEL))
                .setNumChapsPerSlot(bundle.getInt(KEY_NUM_CHAPS_PER_SLOT))
                .setNumResponderNodes(bundle.getInt(KEY_NUM_RESPONDER_NODES))
                .setNumSlotsPerRound(bundle.getInt(KEY_NUM_SLOTS_PER_ROUND))
                .setSyncCodeIndex(bundle.getInt(KEY_SYNC_CODE_INDEX))
                .setHoppingConfigMode(bundle.getInt(KEY_HOPPING_CONFIG_MODE))
                .setHoppingSequence(bundle.getInt(KEY_HOPPING_SEQUENCE))
                .setStsIndex(bundle.getInt(KEY_STS_INDEX))
                .setInitiationTimeMs(bundle.getLong(KEY_INITIATION_TIME_MS))
                .setAbsoluteInitiationTimeUs(bundle.getLong(KEY_ABSOLUTE_INITIATION_TIME_US))
                .setRangeDataNtfConfig(
                        bundle.getInt(KEY_RANGE_DATA_NTF_CONFIG, RANGE_DATA_NTF_CONFIG_DISABLE))
                .setRangeDataNtfProximityNear(
                        bundle.getInt(KEY_RANGE_DATA_NTF_PROXIMITY_NEAR,
                                RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT))
                .setRangeDataNtfProximityFar(
                        bundle.getInt(KEY_RANGE_DATA_NTF_PROXIMITY_FAR,
                                RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT))
                .setRangeDataNtfAoaAzimuthLower(
                        bundle.getDouble(KEY_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER,
                                RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT))
                .setRangeDataNtfAoaAzimuthUpper(
                        bundle.getDouble(KEY_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER,
                                RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT))
                .setRangeDataNtfAoaElevationLower(
                        bundle.getDouble(KEY_RANGE_DATA_NTF_AOA_ELEVATION_LOWER,
                                RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT))
                .setRangeDataNtfAoaElevationUpper(
                        bundle.getDouble(KEY_RANGE_DATA_NTF_AOA_ELEVATION_UPPER,
                                RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT))
                .build();
    }

    public CccProtocolVersion getProtocolVersion() {
        return mProtocolVersion;
    }

    @UwbConfig
    public int getUwbConfig() {
        return mUwbConfig;
    }

    public CccPulseShapeCombo getPulseShapeCombo() {
        return mPulseShapeCombo;
    }

    public int getSessionId() {
        return mSessionId;
    }

    @SessionType
    public int getSessionType() {
        return mSessionType;
    }

    @IntRange(from = 0, to = 255)
    public int getRanMultiplier() {
        return mRanMultiplier;
    }

    @Channel
    public int getChannel() {
        return mChannel;
    }

    public int getNumChapsPerSlot() {
        return mNumChapsPerSlot;
    }

    public int getNumResponderNodes() {
        return mNumResponderNodes;
    }

    public int getNumSlotsPerRound() {
        return mNumSlotsPerRound;
    }

    @SyncCodeIndex
    public int getSyncCodeIndex() {
        return mSyncCodeIndex;
    }

    @HoppingConfigMode
    public int getHoppingConfigMode() {
        return mHoppingConfigMode;
    }

    @HoppingSequence
    public int getHoppingSequence() {
        return mHoppingSequence;
    }

    public int getStsIndex() {
        return mStsIndex;
    }

    public long getInitiationTimeMs() {
        return mInitiationTimeMs;
    }

    public long getAbsoluteInitiationTimeUs() {
        return mAbsoluteInitiationTimeUs;
    }

    @RangeDataNtfConfig
    public int getRangeDataNtfConfig() {
        return mRangeDataNtfConfig;
    }

    public int getRangeDataNtfProximityNear() {
        return mRangeDataNtfProximityNear;
    }

    public int getRangeDataNtfProximityFar() {
        return mRangeDataNtfProximityFar;
    }

    public double getRangeDataNtfAoaAzimuthLower() {
        return mRangeDataNtfAoaAzimuthLower;
    }

    public double getRangeDataNtfAoaAzimuthUpper() {
        return mRangeDataNtfAoaAzimuthUpper;
    }

    public double getRangeDataNtfAoaElevationLower() {
        return mRangeDataNtfAoaElevationLower;
    }

    public double getRangeDataNtfAoaElevationUpper() {
        return mRangeDataNtfAoaElevationUpper;
    }

    /** Returns a builder from the params. */
    public CccOpenRangingParams.Builder toBuilder() {
        return new CccOpenRangingParams.Builder(this);
    }

    /** Builder */
    public static final class Builder {
        private RequiredParam<CccProtocolVersion> mProtocolVersion = new RequiredParam<>();
        @UwbConfig private RequiredParam<Integer> mUwbConfig = new RequiredParam<>();
        private RequiredParam<CccPulseShapeCombo> mPulseShapeCombo = new RequiredParam<>();
        private RequiredParam<Integer> mSessionId = new RequiredParam<>();
        @SessionType private int mSessionType = CccParams.SESSION_TYPE_CCC;
        private RequiredParam<Integer> mRanMultiplier = new RequiredParam<>();
        @Channel private RequiredParam<Integer> mChannel = new RequiredParam<>();
        @ChapsPerSlot private RequiredParam<Integer> mNumChapsPerSlot = new RequiredParam<>();
        private RequiredParam<Integer> mNumResponderNodes = new RequiredParam<>();
        @SlotsPerRound private RequiredParam<Integer> mNumSlotsPerRound = new RequiredParam<>();
        @SyncCodeIndex private RequiredParam<Integer> mSyncCodeIndex = new RequiredParam<>();

        @HoppingConfigMode
        private RequiredParam<Integer> mHoppingConfigMode = new RequiredParam<>();

        @HoppingSequence private RequiredParam<Integer> mHoppingSequence = new RequiredParam<>();

        private int mStsIndex = 0;

        private long mInitiationTimeMs = 0;
        private long mAbsoluteInitiationTimeUs = 0;

        /** CCC default: Ranging notification enabled. */
        @RangeDataNtfConfig private int mRangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_DISABLE;

        /** UCI spec default: 0 (No low-bound filtering) */
        private int mRangeDataNtfProximityNear = RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT;

        /** UCI spec default: 20000 cm (or 200 meters) */
        private int mRangeDataNtfProximityFar = RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT;

        /** UCI spec default: -180 (No low-bound filtering) */
        private double mRangeDataNtfAoaAzimuthLower = RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT;

        /** UCI spec default: +180 (No upper-bound filtering) */
        private double mRangeDataNtfAoaAzimuthUpper = RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT;

        /** UCI spec default: -90 (No low-bound filtering) */
        private double mRangeDataNtfAoaElevationLower = RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT;

        /** UCI spec default: +90 (No upper-bound filtering) */
        private double mRangeDataNtfAoaElevationUpper = RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT;

        public Builder() {}

        public Builder(@NonNull Builder builder) {
            mProtocolVersion.set(builder.mProtocolVersion.get());
            mUwbConfig.set(builder.mUwbConfig.get());
            mPulseShapeCombo.set(builder.mPulseShapeCombo.get());
            mSessionId.set(builder.mSessionId.get());
            mSessionType = builder.mSessionType;
            mRanMultiplier.set(builder.mRanMultiplier.get());
            mChannel.set(builder.mChannel.get());
            mNumChapsPerSlot.set(builder.mNumChapsPerSlot.get());
            mNumResponderNodes.set(builder.mNumResponderNodes.get());
            mNumSlotsPerRound.set(builder.mNumSlotsPerRound.get());
            mSyncCodeIndex.set(builder.mSyncCodeIndex.get());
            mHoppingConfigMode.set(builder.mHoppingConfigMode.get());
            mHoppingSequence.set(builder.mHoppingSequence.get());
            mStsIndex = builder.mStsIndex;
            mInitiationTimeMs = builder.mInitiationTimeMs;
            mAbsoluteInitiationTimeUs = builder.mAbsoluteInitiationTimeUs;
            mRangeDataNtfConfig = builder.mRangeDataNtfConfig;
            mRangeDataNtfProximityNear = builder.mRangeDataNtfProximityNear;
            mRangeDataNtfProximityFar = builder.mRangeDataNtfProximityFar;
            mRangeDataNtfAoaAzimuthLower = builder.mRangeDataNtfAoaAzimuthLower;
            mRangeDataNtfAoaAzimuthUpper = builder.mRangeDataNtfAoaAzimuthUpper;
            mRangeDataNtfAoaElevationLower = builder.mRangeDataNtfAoaElevationLower;
            mRangeDataNtfAoaElevationUpper = builder.mRangeDataNtfAoaElevationUpper;
        }

        public Builder(@NonNull CccOpenRangingParams params) {
            mProtocolVersion.set(params.mProtocolVersion);
            mUwbConfig.set(params.mUwbConfig);
            mPulseShapeCombo.set(params.mPulseShapeCombo);
            mSessionId.set(params.mSessionId);
            mSessionType = params.mSessionType;
            mRanMultiplier.set(params.mRanMultiplier);
            mChannel.set(params.mChannel);
            mNumChapsPerSlot.set(params.mNumChapsPerSlot);
            mNumResponderNodes.set(params.mNumResponderNodes);
            mNumSlotsPerRound.set(params.mNumSlotsPerRound);
            mSyncCodeIndex.set(params.mSyncCodeIndex);
            mHoppingConfigMode.set(params.mHoppingConfigMode);
            mHoppingSequence.set(params.mHoppingSequence);
            mRangeDataNtfConfig = params.mRangeDataNtfConfig;
            mRangeDataNtfProximityNear = params.mRangeDataNtfProximityNear;
            mRangeDataNtfProximityFar = params.mRangeDataNtfProximityFar;
            mRangeDataNtfAoaAzimuthLower = params.mRangeDataNtfAoaAzimuthLower;
            mRangeDataNtfAoaAzimuthUpper = params.mRangeDataNtfAoaAzimuthUpper;
            mRangeDataNtfAoaElevationLower = params.mRangeDataNtfAoaElevationLower;
            mRangeDataNtfAoaElevationUpper = params.mRangeDataNtfAoaElevationUpper;
        }

        public Builder setProtocolVersion(CccProtocolVersion version) {
            mProtocolVersion.set(version);
            return this;
        }

        public Builder setUwbConfig(@UwbConfig int uwbConfig) {
            mUwbConfig.set(uwbConfig);
            return this;
        }

        public Builder setPulseShapeCombo(CccPulseShapeCombo pulseShapeCombo) {
            mPulseShapeCombo.set(pulseShapeCombo);
            return this;
        }

        public Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public Builder setRanMultiplier(int ranMultiplier) {
            mRanMultiplier.set(ranMultiplier);
            return this;
        }

        public Builder setChannel(@Channel int channel) {
            mChannel.set(channel);
            return this;
        }

        public Builder setNumChapsPerSlot(@ChapsPerSlot int numChapsPerSlot) {
            mNumChapsPerSlot.set(numChapsPerSlot);
            return this;
        }

        public Builder setNumResponderNodes(int numResponderNodes) {
            mNumResponderNodes.set(numResponderNodes);
            return this;
        }

        public Builder setNumSlotsPerRound(@SlotsPerRound int numSlotsPerRound) {
            mNumSlotsPerRound.set(numSlotsPerRound);
            return this;
        }

        public Builder setSyncCodeIndex(@SyncCodeIndex int syncCodeIndex) {
            mSyncCodeIndex.set(syncCodeIndex);
            return this;
        }

        public Builder setHoppingConfigMode(@HoppingConfigMode int hoppingConfigMode) {
            mHoppingConfigMode.set(hoppingConfigMode);
            return this;
        }

        public Builder setHoppingSequence(@HoppingSequence int hoppingSequence) {
            mHoppingSequence.set(hoppingSequence);
            return this;
        }

        public Builder setStsIndex(int stsIndex) {
            mStsIndex = stsIndex;
            return this;
        }

        /** Set initiation time in ms */
        public Builder setInitiationTimeMs(long initiationTimeMs) {
            mInitiationTimeMs = initiationTimeMs;
            return this;
        }

        /**
         * Sets the UWB absolute initiation time.
         *
         * @param absoluteInitiationTimeUs Absolute UWB initiation time (in micro-seconds). This is
         *        applicable only for FiRa 2.0+ devices, as specified in CR-272.
         */
        public Builder setAbsoluteInitiationTimeUs(long absoluteInitiationTimeUs) {
            mAbsoluteInitiationTimeUs = absoluteInitiationTimeUs;
            return this;
        }

        public Builder setRangeDataNtfConfig(
                @RangeDataNtfConfig int rangeDataNtfConfig) {
            mRangeDataNtfConfig = rangeDataNtfConfig;
            return this;
        }

        public Builder setRangeDataNtfProximityNear(
                @IntRange(from = RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT,
                        to = RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT)
                        int rangeDataNtfProximityNear) {
            mRangeDataNtfProximityNear = rangeDataNtfProximityNear;
            return this;
        }

        public Builder setRangeDataNtfProximityFar(
                @IntRange(from = RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT,
                        to = RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT)
                        int rangeDataNtfProximityFar) {
            mRangeDataNtfProximityFar = rangeDataNtfProximityFar;
            return this;
        }

        public Builder setRangeDataNtfAoaAzimuthLower(
                @FloatRange(from = RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT)
                        double rangeDataNtfAoaAzimuthLower) {
            mRangeDataNtfAoaAzimuthLower = rangeDataNtfAoaAzimuthLower;
            return this;
        }

        public Builder setRangeDataNtfAoaAzimuthUpper(
                @FloatRange(from = RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT)
                        double rangeDataNtfAoaAzimuthUpper) {
            mRangeDataNtfAoaAzimuthUpper = rangeDataNtfAoaAzimuthUpper;
            return this;
        }

        public Builder setRangeDataNtfAoaElevationLower(
                @FloatRange(from = RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT)
                        double rangeDataNtfAoaElevationLower) {
            mRangeDataNtfAoaElevationLower = rangeDataNtfAoaElevationLower;
            return this;
        }

        public Builder setRangeDataNtfAoaElevationUpper(
                @FloatRange(from = RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT)
                        double rangeDataNtfAoaElevationUpper) {
            mRangeDataNtfAoaElevationUpper = rangeDataNtfAoaElevationUpper;
            return this;
        }

        private void checkRangeDataNtfConfig() {
            if (mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_DISABLE) {
                checkArgument(mRangeDataNtfProximityNear
                        == RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT);
                checkArgument(mRangeDataNtfProximityFar
                        == RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT);
                checkArgument(mRangeDataNtfAoaAzimuthLower
                        == RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT);
                checkArgument(mRangeDataNtfAoaAzimuthUpper
                        == RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT);
                checkArgument(mRangeDataNtfAoaElevationLower
                        == RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT);
                checkArgument(mRangeDataNtfAoaElevationUpper
                        == RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT);
            } else if (mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG
                    || mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG) {
                checkArgument(
                        mRangeDataNtfProximityNear != RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT
                        || mRangeDataNtfProximityFar != RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT);
                checkArgument(mRangeDataNtfAoaAzimuthLower
                        == RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT);
                checkArgument(mRangeDataNtfAoaAzimuthUpper
                        == RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT);
                checkArgument(mRangeDataNtfAoaElevationLower
                        == RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT);
                checkArgument(mRangeDataNtfAoaElevationUpper
                        == RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT);
            } else if (mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG
                    || mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG) {
                checkArgument(mRangeDataNtfProximityNear
                        == RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT);
                checkArgument(mRangeDataNtfProximityFar
                        == RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT);
                checkArgument(mRangeDataNtfAoaAzimuthLower
                            != RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT
                        || mRangeDataNtfAoaAzimuthUpper
                            != RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT
                        || mRangeDataNtfAoaElevationLower
                            != RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT
                        || mRangeDataNtfAoaElevationUpper
                            != RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT);
            } else if (mRangeDataNtfConfig == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG
                    || mRangeDataNtfConfig
                    == RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG) {
                checkArgument(
                        mRangeDataNtfProximityNear != RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT
                        || mRangeDataNtfProximityFar != RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT
                        || mRangeDataNtfAoaAzimuthLower
                            != RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT
                        || mRangeDataNtfAoaAzimuthUpper
                            != RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT
                        || mRangeDataNtfAoaElevationLower
                            != RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT
                        || mRangeDataNtfAoaElevationUpper
                            != RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT);
            }
        }

        public CccOpenRangingParams build() {
            checkRangeDataNtfConfig();
            return new CccOpenRangingParams(
                    mProtocolVersion.get(),
                    mUwbConfig.get(),
                    mPulseShapeCombo.get(),
                    mSessionId.get(),
                    mSessionType,
                    mRanMultiplier.get(),
                    mChannel.get(),
                    mNumChapsPerSlot.get(),
                    mNumResponderNodes.get(),
                    mNumSlotsPerRound.get(),
                    mSyncCodeIndex.get(),
                    mHoppingConfigMode.get(),
                    mHoppingSequence.get(),
                    mStsIndex,
                    mInitiationTimeMs,
                    mAbsoluteInitiationTimeUs,
                    mRangeDataNtfConfig,
                    mRangeDataNtfProximityNear,
                    mRangeDataNtfProximityFar,
                    mRangeDataNtfAoaAzimuthLower,
                    mRangeDataNtfAoaAzimuthUpper,
                    mRangeDataNtfAoaElevationLower,
                    mRangeDataNtfAoaElevationUpper);
        }
    }
}
