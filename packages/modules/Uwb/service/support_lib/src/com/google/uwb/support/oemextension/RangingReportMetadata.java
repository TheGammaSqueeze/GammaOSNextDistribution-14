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

package com.google.uwb.support.oemextension;

import android.os.PersistableBundle;
import android.uwb.RangingReport;

import androidx.annotation.Nullable;

/**
 * Ranging report metadata for post-processing with oem extension
 *
 * <p> This is passed as bundle with RangingReport
 * {@link RangingReport#getRangingReportMetadata()}
 */
public class RangingReportMetadata {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String SESSION_ID = "session_id";
    public static final String RAW_NTF_DATA = "raw_ntf_data";

    private final long mSessionId;
    private final byte[] mRawNtfData;

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public byte[] getRawNtfData() {
        return mRawNtfData;
    }

    private RangingReportMetadata(long sessionId, byte[] rawNtfData) {
        mSessionId = sessionId;
        mRawNtfData = rawNtfData;
    }

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (bytes[i]);
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

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putLong(SESSION_ID, mSessionId);
        bundle.putIntArray(RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        return bundle;
    }

    public static RangingReportMetadata fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static RangingReportMetadata parseVersion1(PersistableBundle bundle) {
        return new RangingReportMetadata.Builder()
                .setSessionId(bundle.getLong(SESSION_ID))
                .setRawNtfData(intArrayToByteArray(bundle.getIntArray(RAW_NTF_DATA)))
                .build();
    }

    /** Builder */
    public static class Builder {
        private long mSessionId;
        private byte[] mRawNtfData;

        public RangingReportMetadata.Builder setSessionId(long sessionId) {
            mSessionId = sessionId;
            return this;
        }

        public RangingReportMetadata.Builder setRawNtfData(byte[] rawNtfData) {
            mRawNtfData = rawNtfData;
            return this;
        }

        public RangingReportMetadata build() {
            return new RangingReportMetadata(mSessionId, mRawNtfData);
        }
    }
}
