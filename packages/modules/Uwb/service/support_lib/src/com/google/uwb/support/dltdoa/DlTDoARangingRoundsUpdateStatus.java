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

package com.google.uwb.support.dltdoa;

import android.os.PersistableBundle;

import androidx.annotation.Nullable;

/**
 * Used to send SESSION_UPDATE_ACTIVE_ROUNDS_DT_TAG_RSP
 *
 * <p> This is passed as a bundle for response received for command
 * SESSION_UPDATE_ACTIVE_ROUNDS_DT_TAG_CMD
 */
public class DlTDoARangingRoundsUpdateStatus {
    private final int mStatus;
    private final int mNoOfRangingRounds;
    private final byte[] mRangingRoundIndexes;

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String STATUS = "status";
    public static final String NO_OF_RANGING_ROUNDS = "no_of_ranging_rounds";
    public static final String RANGING_ROUND_INDEXES = "ranging_round_indexes";


    private DlTDoARangingRoundsUpdateStatus(int status, int noOfRangingRounds,
            byte[] rangingRoundIndexes) {
        mStatus = status;
        mNoOfRangingRounds = noOfRangingRounds;
        mRangingRoundIndexes = rangingRoundIndexes;
    }

    public int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getNoOfRangingRounds() {
        return mNoOfRangingRounds;
    }

    public byte[] getRangingRoundIndexes() {
        return mRangingRoundIndexes;
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
        bundle.putInt(KEY_BUNDLE_VERSION, BUNDLE_VERSION_CURRENT);
        bundle.putInt(STATUS, mStatus);
        bundle.putInt(NO_OF_RANGING_ROUNDS, mNoOfRangingRounds);
        bundle.putIntArray(RANGING_ROUND_INDEXES, byteArrayToIntArray(mRangingRoundIndexes));
        return bundle;
    }

    public static DlTDoARangingRoundsUpdateStatus fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static DlTDoARangingRoundsUpdateStatus parseVersion1(PersistableBundle bundle) {
        return new DlTDoARangingRoundsUpdateStatus.Builder()
                .setStatus(bundle.getInt(STATUS))
                .setNoOfRangingRounds(bundle.getInt(NO_OF_RANGING_ROUNDS))
                .setRangingRoundIndexes(
                        intArrayToByteArray(bundle.getIntArray(RANGING_ROUND_INDEXES)))
                .build();
    }

    /** Builder */
    public static class Builder {
        private int mStatus = 0;
        private int mNoOfRangingRounds = 0;
        private byte[] mRangingRoundIndexes = new byte[]{};

        public DlTDoARangingRoundsUpdateStatus.Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public DlTDoARangingRoundsUpdateStatus.Builder setNoOfRangingRounds(
                int rangingRounds) {
            mNoOfRangingRounds = rangingRounds;
            return this;
        }

        public DlTDoARangingRoundsUpdateStatus.Builder setRangingRoundIndexes(
                byte[] rangingRoundIndexes) {
            mRangingRoundIndexes = rangingRoundIndexes;
            return this;
        }

        public DlTDoARangingRoundsUpdateStatus build() {
            return new DlTDoARangingRoundsUpdateStatus(
                    mStatus,
                    mNoOfRangingRounds,
                    mRangingRoundIndexes);
        }
    }
}
