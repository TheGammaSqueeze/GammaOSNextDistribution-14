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
import android.uwb.RangingReport;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.StatusCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Radar data packet
 *
 * <p>This is passed as the mRangingReportMetadata bundle in the RangingReport. {@link
 * RangingReport#getRangingReportMetadata()} This will be passed for Radar sessions only.
 */
public class RadarData extends RadarParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_STATUS_CODE = "status_code";
    private static final String KEY_RADAR_DATA_TYPE = "radar_data_type";
    private static final String KEY_SAMPLES_PER_SWEEP = "samples_per_sweep";
    private static final String KEY_BITS_PER_SAMPLE = "bits_per_samples";
    private static final String KEY_SWEEP_OFFSET = "sweep_offset";
    private static final String KEY_SWEEP_DATA = "sweep_data";

    @StatusCode private final int mStatusCode;
    @RadarDataType private final int mRadarDataType;
    @SamplesPerSweep private final int mSamplesPerSweep;
    @BitsPerSample private final int mBitsPerSample;
    @SweepOffset private final int mSweepOffset;
    private final List<RadarSweepData> mSweepData;

    private RadarData(
            @StatusCode int statusCode,
            @RadarDataType int radarDataType,
            @SamplesPerSweep int samplesPerSweep,
            @BitsPerSample int bitsPerSample,
            @SweepOffset int sweepOffset,
            List<RadarSweepData> sweepData) {
        mStatusCode = statusCode;
        mRadarDataType = radarDataType;
        mSamplesPerSweep = samplesPerSweep;
        mBitsPerSample = bitsPerSample;
        mSweepOffset = sweepOffset;
        mSweepData = sweepData;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_STATUS_CODE, mStatusCode);
        bundle.putInt(KEY_RADAR_DATA_TYPE, mRadarDataType);
        bundle.putInt(KEY_SAMPLES_PER_SWEEP, mSamplesPerSweep);
        bundle.putInt(KEY_BITS_PER_SAMPLE, mBitsPerSample);
        bundle.putInt(KEY_SWEEP_OFFSET, mSweepOffset);
        int sweep_index = 0;
        for (RadarSweepData sweep : mSweepData) {
            bundle.putPersistableBundle(KEY_SWEEP_DATA + sweep_index, sweep.toBundle());
            sweep_index++;
        }
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RadarData} */
    public static RadarData fromBundle(PersistableBundle bundle) {
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

    private static RadarData parseBundleVersion1(PersistableBundle bundle) {
        RadarData.Builder builder =
                new RadarData.Builder()
                        .setStatusCode(bundle.getInt(KEY_STATUS_CODE))
                        .setRadarDataType(bundle.getInt(KEY_RADAR_DATA_TYPE))
                        .setSamplesPerSweep(bundle.getInt(KEY_SAMPLES_PER_SWEEP))
                        .setBitsPerSample(bundle.getInt(KEY_BITS_PER_SAMPLE))
                        .setSweepOffset(bundle.getInt(KEY_SWEEP_OFFSET));

        int sweep_index = 0;
        PersistableBundle sweepBundle = bundle.getPersistableBundle(KEY_SWEEP_DATA + sweep_index);
        while (sweepBundle != null) {
            builder.addSweepData(RadarSweepData.fromBundle(sweepBundle));
            sweep_index++;
            sweepBundle = bundle.getPersistableBundle(KEY_SWEEP_DATA + sweep_index);
        }
        return builder.build();
    }

    @StatusCode
    public int getStatusCode() {
        return mStatusCode;
    }

    @RadarDataType
    public int getRadarDataType() {
        return mRadarDataType;
    }

    @SamplesPerSweep
    public int getSamplesPerSweep() {
        return mSamplesPerSweep;
    }

    @BitsPerSample
    public int getBitsPerSample() {
        return mBitsPerSample;
    }

    @SweepOffset
    public int getSweepOffset() {
        return mSweepOffset;
    }

    public List<RadarSweepData> getSweepData() {
        return mSweepData;
    }

    /** Builder */
    public static final class Builder {
        @StatusCode private RequiredParam<Integer> mStatusCode = new RequiredParam<>();
        @RadarDataType private RequiredParam<Integer> mRadarDataType = new RequiredParam<>();
        @SamplesPerSweep private RequiredParam<Integer> mSamplesPerSweep = new RequiredParam<>();
        @BitsPerSample private RequiredParam<Integer> mBitsPerSample = new RequiredParam<>();
        @SweepOffset private RequiredParam<Integer> mSweepOffset = new RequiredParam<>();
        private List<RadarSweepData> mSweepData = new ArrayList<>();

        /** Sets status code */
        public RadarData.Builder setStatusCode(@StatusCode int statusCode) {
            mStatusCode.set(statusCode);
            return this;
        }

        /** Sets radar data type */
        public RadarData.Builder setRadarDataType(@RadarDataType int radarDataType) {
            mRadarDataType.set(radarDataType);
            return this;
        }

        /** Sets samples per sweep */
        public RadarData.Builder setSamplesPerSweep(@SamplesPerSweep int samplesPerSweep) {
            mSamplesPerSweep.set(samplesPerSweep);
            return this;
        }

        /** Sets bits per sample */
        public RadarData.Builder setBitsPerSample(@BitsPerSample int bitsPerSample) {
            mBitsPerSample.set(bitsPerSample);
            return this;
        }

        /** Sets sweep offset */
        public RadarData.Builder setSweepOffset(@SweepOffset int sweepOffset) {
            mSweepOffset.set(sweepOffset);
            return this;
        }

        /** Adds a list of {@link RadarSweepData} */
        public RadarData.Builder setSweepData(List<RadarSweepData> sweepData) {
            mSweepData.addAll(sweepData);
            return this;
        }

        /** Adds a single {@link RadarSweepData} */
        public RadarData.Builder addSweepData(RadarSweepData sweepData) {
            mSweepData.add(sweepData);
            return this;
        }

        /** Build {@link RadarData} */
        public RadarData build() {
            if (mRadarDataType.get() == RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES
                    && mSweepData.size() == 0) {
                throw new IllegalArgumentException("No radar sweep data");
            }
            return new RadarData(
                    mStatusCode.get(),
                    mRadarDataType.get(),
                    mSamplesPerSweep.get(),
                    mBitsPerSample.get(),
                    mSweepOffset.get(),
                    mSweepData);
        }
    }
}
