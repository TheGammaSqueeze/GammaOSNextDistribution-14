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

import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;

/**
 * Radar sweep data packet
 *
 * <p>This is part of {@link RadarData}.
 */
public class RadarSweepData extends RadarParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_SEQUENCE_NUMBER = "sequence_number";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_VENDOR_SPECIFIC_DATA = "vendor_specific_data";
    private static final String KEY_SAMPLE_DATA = "sample_data";

    private final long mSequenceNumber;
    private final long mTimestamp;
    private final byte[] mVendorSpecificData;
    private final byte[] mSampleData;

    private RadarSweepData(
            long sequenceNumber, long timestamp, byte[] vendorSpecificData, byte[] sampleData) {
        mSequenceNumber = sequenceNumber;
        mTimestamp = timestamp;
        mVendorSpecificData = vendorSpecificData;
        mSampleData = sampleData;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytes[i];
        }
        return values;
    }

    @Nullable
    private static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putLong(KEY_SEQUENCE_NUMBER, mSequenceNumber);
        bundle.putLong(KEY_TIMESTAMP, mTimestamp);
        bundle.putIntArray(KEY_VENDOR_SPECIFIC_DATA, byteArrayToIntArray(mVendorSpecificData));
        bundle.putIntArray(KEY_SAMPLE_DATA, byteArrayToIntArray(mSampleData));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RadarSweepData} */
    public static RadarSweepData fromBundle(PersistableBundle bundle) {
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

    private static RadarSweepData parseBundleVersion1(PersistableBundle bundle) {
        return new RadarSweepData.Builder()
                .setSequenceNumber(bundle.getLong(KEY_SEQUENCE_NUMBER))
                .setTimestamp(bundle.getLong(KEY_TIMESTAMP))
                .setVendorSpecificData(
                        intArrayToByteArray(bundle.getIntArray(KEY_VENDOR_SPECIFIC_DATA)))
                .setSampleData(intArrayToByteArray(bundle.getIntArray(KEY_SAMPLE_DATA)))
                .build();
    }

    public long getSequenceNumber() {
        return mSequenceNumber;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public byte[] getVendorSpecificData() {
        return mVendorSpecificData;
    }

    public byte[] getSampleData() {
        return mSampleData;
    }

    /** Builder */
    public static final class Builder {
        private RequiredParam<Long> mSequenceNumber = new RequiredParam<>();
        private RequiredParam<Long> mTimestamp = new RequiredParam<>();
        private byte[] mVendorSpecificData;
        private byte[] mSampleData;

        /** Sets sequence number */
        public RadarSweepData.Builder setSequenceNumber(long sequenceNumber) {
            mSequenceNumber.set(sequenceNumber);
            return this;
        }

        /** Sets timestamp */
        public RadarSweepData.Builder setTimestamp(long timestamp) {
            mTimestamp.set(timestamp);
            return this;
        }

        /** Sets vendor specific data */
        public RadarSweepData.Builder setVendorSpecificData(byte[] vendorSpecificData) {
            mVendorSpecificData = vendorSpecificData;
            return this;
        }

        /** Sets sample data */
        public RadarSweepData.Builder setSampleData(byte[] sampleData) {
            mSampleData = sampleData;
            return this;
        }

        /** Build {@link RadarSweepData} */
        public RadarSweepData build() {
            if (mSequenceNumber.get() < 0) {
                throw new IllegalArgumentException("Invalid sequence number");
            }
            if (mTimestamp.get() < 0) {
                throw new IllegalArgumentException("Invalid timestamp");
            }
            if (mSampleData == null || mSampleData.length == 0) {
                throw new IllegalArgumentException("Empty radar sample data");
            }
            return new RadarSweepData(
                    mSequenceNumber.get(), mTimestamp.get(), mVendorSpecificData, mSampleData);
        }
    }
}
