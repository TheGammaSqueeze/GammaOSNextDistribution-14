/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.uwb.support.radar;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.PrfMode;
import com.google.uwb.support.fira.FiraParams.RframeConfig;
import com.google.uwb.support.fira.FiraParams.UwbChannel;

/**
 * Defines parameters to open a Radar session.
 *
 * <p>This is passed as a bundle to the service API {@link UwbManager#openRangingSession}.
 */
public class RadarOpenSessionParams extends RadarParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_TYPE = "session_type";
    private static final String KEY_BURST_PERIOD = "burst_period";
    private static final String KEY_SWEEP_PERIOD = "sweep_period";
    private static final String KEY_SWEEPS_PER_BURST = "sweeps_per_burst";
    private static final String KEY_SAMPLES_PER_SWEEP = "samples_per_sweep";
    private static final String KEY_CHANNEL_NUMBER = "channel_number";
    private static final String KEY_SWEEP_OFFSET = "sweep_offset";
    private static final String KEY_RFRAME_CONFIG = "rframe_config";
    private static final String KEY_PREAMBLE_DURATION = "preamble_duration";
    private static final String KEY_PREAMBLE_CODE_INDEX = "preamble_code_index";
    private static final String KEY_SESSION_PRIORITY = "session_priority";
    private static final String KEY_BITS_PER_SAMPLE = "bits_per_samples";
    private static final String KEY_PRF_MODE = "prf_mode";
    private static final String KEY_NUMBER_OF_BURSTS = "number_of_bursts";
    private static final String KEY_RADAR_DATA_TYPE = "radar_data_type";

    private final int mSessionId;
    @SessionType private final int mSessionType;
    @BurstPeriod private final int mBurstPeriod;
    @SweepPeriod private final int mSweepPeriod;
    @SweepsPerBurst private final int mSweepsPerBurst;
    @SamplesPerSweep private final int mSamplesPerSweep;
    @UwbChannel private final int mChannelNumber;
    @SweepOffset private final int mSweepOffset;
    @RframeConfig private final int mRframeConfig;
    @PreambleDuration private final int mPreambleDuration;
    @PreambleCodeIndex private final int mPreambleCodeIndex;
    @SessionPriority private final int mSessionPriority;
    @BitsPerSample private final int mBitsPerSample;
    @PrfMode private final int mPrfMode;
    @NumberOfBursts private final int mNumberOfBursts;
    @RadarDataType private final int mRadarDataType;

    private RadarOpenSessionParams(
            int sessionId,
            @SessionType int sessionType,
            @BurstPeriod int burstPeriod,
            @SweepPeriod int sweepPeriod,
            @SweepsPerBurst int sweepsPerBurst,
            @SamplesPerSweep int samplesPerSweep,
            @UwbChannel int channelNumber,
            @SweepOffset int sweepOffset,
            @RframeConfig int rframeConfig,
            @PreambleDuration int preambleDuration,
            @PreambleCodeIndex int preambleCodeIndex,
            @SessionPriority int sessionPriority,
            @BitsPerSample int bitsPerSample,
            @PrfMode int prfMode,
            @NumberOfBursts int numberOfBursts,
            @RadarDataType int radarDataType) {
        mSessionId = sessionId;
        mSessionType = sessionType;
        mBurstPeriod = burstPeriod;
        mSweepPeriod = sweepPeriod;
        mSweepsPerBurst = sweepsPerBurst;
        mSamplesPerSweep = samplesPerSweep;
        mChannelNumber = channelNumber;
        mSweepOffset = sweepOffset;
        mRframeConfig = rframeConfig;
        mPreambleDuration = preambleDuration;
        mPreambleCodeIndex = preambleCodeIndex;
        mSessionPriority = sessionPriority;
        mBitsPerSample = bitsPerSample;
        mPrfMode = prfMode;
        mNumberOfBursts = numberOfBursts;
        mRadarDataType = radarDataType;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_SESSION_TYPE, mSessionType);
        bundle.putInt(KEY_BURST_PERIOD, mBurstPeriod);
        bundle.putInt(KEY_SWEEP_PERIOD, mSweepPeriod);
        bundle.putInt(KEY_SWEEPS_PER_BURST, mSweepsPerBurst);
        bundle.putInt(KEY_SAMPLES_PER_SWEEP, mSamplesPerSweep);
        bundle.putInt(KEY_CHANNEL_NUMBER, mChannelNumber);
        bundle.putInt(KEY_SWEEP_OFFSET, mSweepOffset);
        bundle.putInt(KEY_RFRAME_CONFIG, mRframeConfig);
        bundle.putInt(KEY_PREAMBLE_DURATION, mPreambleDuration);
        bundle.putInt(KEY_PREAMBLE_CODE_INDEX, mPreambleCodeIndex);
        bundle.putInt(KEY_SESSION_PRIORITY, mSessionPriority);
        bundle.putInt(KEY_BITS_PER_SAMPLE, mBitsPerSample);
        bundle.putInt(KEY_PRF_MODE, mPrfMode);
        bundle.putInt(KEY_NUMBER_OF_BURSTS, mNumberOfBursts);
        bundle.putInt(KEY_RADAR_DATA_TYPE, mRadarDataType);
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RadarOpenSessionParams} */
    public static RadarOpenSessionParams fromBundle(PersistableBundle bundle) {
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

    private static RadarOpenSessionParams parseBundleVersion1(PersistableBundle bundle) {
        return new Builder()
                .setSessionId(bundle.getInt(KEY_SESSION_ID))
                .setBurstPeriod(bundle.getInt(KEY_BURST_PERIOD))
                .setSweepPeriod(bundle.getInt(KEY_SWEEP_PERIOD))
                .setSweepsPerBurst(bundle.getInt(KEY_SWEEPS_PER_BURST))
                .setSamplesPerSweep(bundle.getInt(KEY_SAMPLES_PER_SWEEP))
                .setChannelNumber(bundle.getInt(KEY_CHANNEL_NUMBER))
                .setSweepOffset(bundle.getInt(KEY_SWEEP_OFFSET))
                .setRframeConfig(bundle.getInt(KEY_RFRAME_CONFIG))
                .setPreambleDuration(bundle.getInt(KEY_PREAMBLE_DURATION))
                .setPreambleCodeIndex(bundle.getInt(KEY_PREAMBLE_CODE_INDEX))
                .setSessionPriority(bundle.getInt(KEY_SESSION_PRIORITY))
                .setBitsPerSample(bundle.getInt(KEY_BITS_PER_SAMPLE))
                .setPrfMode(bundle.getInt(KEY_PRF_MODE))
                .setNumberOfBursts(bundle.getInt(KEY_NUMBER_OF_BURSTS))
                .setRadarDataType(bundle.getInt(KEY_RADAR_DATA_TYPE))
                .build();
    }

    public int getSessionId() {
        return mSessionId;
    }

    @SessionType
    public int getSessionType() {
        return mSessionType;
    }

    @BurstPeriod
    public int getBurstPeriod() {
        return mBurstPeriod;
    }

    @SweepPeriod
    public int getSweepPeriod() {
        return mSweepPeriod;
    }

    @SweepsPerBurst
    public int getSweepsPerBurst() {
        return mSweepsPerBurst;
    }

    @SamplesPerSweep
    public int getSamplesPerSweep() {
        return mSamplesPerSweep;
    }

    @UwbChannel
    public int getChannelNumber() {
        return mChannelNumber;
    }

    @SweepOffset
    public int getSweepOffset() {
        return mSweepOffset;
    }

    @RframeConfig
    public int getRframeConfig() {
        return mRframeConfig;
    }

    @PreambleDuration
    public int getPreambleDuration() {
        return mPreambleDuration;
    }

    @PreambleCodeIndex
    public int getPreambleCodeIndex() {
        return mPreambleCodeIndex;
    }

    @SessionPriority
    public int getSessionPriority() {
        return mSessionPriority;
    }

    @BitsPerSample
    public int getBitsPerSample() {
        return mBitsPerSample;
    }

    @PrfMode
    public int getPrfMode() {
        return mPrfMode;
    }

    @NumberOfBursts
    public int getNumberOfBursts() {
        return mNumberOfBursts;
    }

    @RadarDataType
    public int getRadarDataType() {
        return mRadarDataType;
    }

    /** Builder */
    public static final class Builder {
        private RequiredParam<Integer> mSessionId = new RequiredParam<>();
        @SessionType private int mSessionType = RadarParams.SESSION_TYPE_RADAR;
        @BurstPeriod private RequiredParam<Integer> mBurstPeriod = new RequiredParam<>();
        @SweepPeriod private RequiredParam<Integer> mSweepPeriod = new RequiredParam<>();
        @SweepsPerBurst private RequiredParam<Integer> mSweepsPerBurst = new RequiredParam<>();
        @SamplesPerSweep private RequiredParam<Integer> mSamplesPerSweep = new RequiredParam<>();
        @UwbChannel private RequiredParam<Integer> mChannelNumber = new RequiredParam<>();
        @SweepOffset private RequiredParam<Integer> mSweepOffset = new RequiredParam<>();
        @RframeConfig private RequiredParam<Integer> mRframeConfig = new RequiredParam<>();
        @PreambleDuration private RequiredParam<Integer> mPreambleDuration = new RequiredParam<>();

        @PreambleCodeIndex
        private RequiredParam<Integer> mPreambleCodeIndex = new RequiredParam<>();

        @SessionPriority private RequiredParam<Integer> mSessionPriority = new RequiredParam<>();
        @BitsPerSample private RequiredParam<Integer> mBitsPerSample = new RequiredParam<>();
        @PrfMode private RequiredParam<Integer> mPrfMode = new RequiredParam<>();
        @NumberOfBursts private RequiredParam<Integer> mNumberOfBursts = new RequiredParam<>();
        @RadarDataType private RequiredParam<Integer> mRadarDataType = new RequiredParam<>();

        public Builder() {}

        public Builder(@NonNull Builder builder) {
            mSessionId.set(builder.mSessionId.get());
            mSessionType = builder.mSessionType;
            mBurstPeriod.set(builder.mBurstPeriod.get());
            mSweepPeriod.set(builder.mSweepPeriod.get());
            mSweepsPerBurst.set(builder.mSweepsPerBurst.get());
            mSamplesPerSweep.set(builder.mSamplesPerSweep.get());
            mChannelNumber.set(builder.mChannelNumber.get());
            mSweepOffset.set(builder.mSweepOffset.get());
            mRframeConfig.set(builder.mRframeConfig.get());
            mPreambleDuration.set(builder.mPreambleDuration.get());
            mPreambleCodeIndex.set(builder.mPreambleCodeIndex.get());
            mSessionPriority.set(builder.mSessionPriority.get());
            mBitsPerSample.set(builder.mBitsPerSample.get());
            mPrfMode.set(builder.mPrfMode.get());
            mNumberOfBursts.set(builder.mNumberOfBursts.get());
            mRadarDataType.set(builder.mRadarDataType.get());
        }

        public Builder(@NonNull RadarOpenSessionParams params) {
            mSessionId.set(params.mSessionId);
            mSessionType = params.mSessionType;
            mBurstPeriod.set(params.mBurstPeriod);
            mSweepPeriod.set(params.mSweepPeriod);
            mSweepsPerBurst.set(params.mSweepsPerBurst);
            mSamplesPerSweep.set(params.mSamplesPerSweep);
            mChannelNumber.set(params.mChannelNumber);
            mSweepOffset.set(params.mSweepOffset);
            mRframeConfig.set(params.mRframeConfig);
            mPreambleDuration.set(params.mPreambleDuration);
            mPreambleCodeIndex.set(params.mPreambleCodeIndex);
            mSessionPriority.set(params.mSessionPriority);
            mBitsPerSample.set(params.mBitsPerSample);
            mPrfMode.set(params.mPrfMode);
            mNumberOfBursts.set(params.mNumberOfBursts);
            mRadarDataType.set(params.mRadarDataType);
        }

        /** Sets session id */
        public Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        /** Sets burst period */
        public Builder setBurstPeriod(@BurstPeriod int burstPeriod) {
            mBurstPeriod.set(burstPeriod);
            return this;
        }

        /** Sets sweep period */
        public Builder setSweepPeriod(@SweepPeriod int sweepPeriod) {
            mSweepPeriod.set(sweepPeriod);
            return this;
        }

        /** Sets sweeps per burst */
        public Builder setSweepsPerBurst(@SweepsPerBurst int sweepsPerBurst) {
            mSweepsPerBurst.set(sweepsPerBurst);
            return this;
        }

        /** Sets samples per sweep */
        public Builder setSamplesPerSweep(@SamplesPerSweep int samplesPerSweep) {
            mSamplesPerSweep.set(samplesPerSweep);
            return this;
        }

        /** Sets channel number */
        public Builder setChannelNumber(@UwbChannel int channelNumber) {
            mChannelNumber.set(channelNumber);
            return this;
        }

        /** Sets sweep offset */
        public Builder setSweepOffset(@SweepOffset int sweepOffset) {
            mSweepOffset.set(sweepOffset);
            return this;
        }

        /** Sets rframe config */
        public Builder setRframeConfig(@RframeConfig int rframeConfig) {
            mRframeConfig.set(rframeConfig);
            return this;
        }

        /** Sets preamble duration */
        public Builder setPreambleDuration(@PreambleDuration int preambleDuration) {
            mPreambleDuration.set(preambleDuration);
            return this;
        }

        /** Sets preamble code index */
        public Builder setPreambleCodeIndex(@PreambleCodeIndex int preambleCodeIndex) {
            mPreambleCodeIndex.set(preambleCodeIndex);
            return this;
        }

        /** Sets session priority */
        public Builder setSessionPriority(@SessionPriority int sessionPriority) {
            mSessionPriority.set(sessionPriority);
            return this;
        }

        /** Sets bits per sample */
        public Builder setBitsPerSample(@BitsPerSample int bitsPerSample) {
            mBitsPerSample.set(bitsPerSample);
            return this;
        }

        /** Sets PRF mode */
        public Builder setPrfMode(@PrfMode int prfMode) {
            mPrfMode.set(prfMode);
            return this;
        }

        /** Sets number of bursts */
        public Builder setNumberOfBursts(@NumberOfBursts int numberOfBursts) {
            mNumberOfBursts.set(numberOfBursts);
            return this;
        }

        /** Sets radar data type */
        public Builder setRadarDataType(@RadarDataType int radarDataType) {
            mRadarDataType.set(radarDataType);
            return this;
        }

        /** Build {@link RadarOpenSessionParams} */
        public RadarOpenSessionParams build() {
            return new RadarOpenSessionParams(
                    mSessionId.get(),
                    mSessionType,
                    mBurstPeriod.get(),
                    mSweepPeriod.get(),
                    mSweepsPerBurst.get(),
                    mSamplesPerSweep.get(),
                    mChannelNumber.get(),
                    mSweepOffset.get(),
                    mRframeConfig.get(),
                    mPreambleDuration.get(),
                    mPreambleCodeIndex.get(),
                    mSessionPriority.get(),
                    mBitsPerSample.get(),
                    mPrfMode.get(),
                    mNumberOfBursts.get(),
                    mRadarDataType.get());
        }
    }
}
