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

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.RangingSession;

import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters for CCC start reports. The start operation can optionally include a request to
 * reconfigure the RAN multiplier. On a reconfiguration, the CCC spec defines that the selected RAN
 * multiplier shall be equal to or greater than the requested RAN multiplier, and therefore, on a
 * reconfiguration, the selected RAN multiplier shall be populated in the CCC start report.
 *
 * <p>This is passed as a bundle to the client callback {@link RangingSession.Callback#onStarted}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccRangingStartedParams extends CccParams {
    private final int mStartingStsIndex;
    private final long mUwbTime0;
    private final int mHopModeKey;
    @SyncCodeIndex private final int mSyncCodeIndex;
    private final int mRanMultiplier;

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_STARTING_STS_INDEX = "starting_sts_index";
    private static final String KEY_UWB_TIME_0 = "uwb_time_0";
    private static final String KEY_HOP_MODE_KEY = "hop_mode_key";
    private static final String KEY_SYNC_CODE_INDEX = "sync_code_index";
    private static final String KEY_RAN_MULTIPLIER = "ran_multiplier";

    private CccRangingStartedParams(Builder builder) {
        mStartingStsIndex = builder.mStartingStsIndex.get();
        mUwbTime0 = builder.mUwbTime0.get();
        mHopModeKey = builder.mHopModeKey.get();
        mSyncCodeIndex = builder.mSyncCodeIndex.get();
        mRanMultiplier = builder.mRanMultiplier.get();
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_STARTING_STS_INDEX, mStartingStsIndex);
        bundle.putLong(KEY_UWB_TIME_0, mUwbTime0);
        bundle.putInt(KEY_HOP_MODE_KEY, mHopModeKey);
        bundle.putInt(KEY_SYNC_CODE_INDEX, mSyncCodeIndex);
        bundle.putInt(KEY_RAN_MULTIPLIER, mRanMultiplier);
        return bundle;
    }

    public static CccRangingStartedParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static CccRangingStartedParams parseVersion1(PersistableBundle bundle) {
        return new Builder()
                .setStartingStsIndex(bundle.getInt(KEY_STARTING_STS_INDEX))
                .setUwbTime0(bundle.getLong(KEY_UWB_TIME_0))
                .setHopModeKey(bundle.getInt(KEY_HOP_MODE_KEY))
                .setSyncCodeIndex(bundle.getInt(KEY_SYNC_CODE_INDEX))
                .setRanMultiplier(bundle.getInt(KEY_RAN_MULTIPLIER))
                .build();
    }

    public int getStartingStsIndex() {
        return mStartingStsIndex;
    }

    public long getUwbTime0() {
        return mUwbTime0;
    }

    public int getHopModeKey() {
        return mHopModeKey;
    }

    @SyncCodeIndex
    public int getSyncCodeIndex() {
        return mSyncCodeIndex;
    }

    public int getRanMultiplier() {
        return mRanMultiplier;
    }

    /** Builder */
    public static final class Builder {
        private RequiredParam<Integer> mStartingStsIndex = new RequiredParam<>();
        private RequiredParam<Long> mUwbTime0 = new RequiredParam<>();
        private RequiredParam<Integer> mHopModeKey = new RequiredParam<>();
        @SyncCodeIndex private RequiredParam<Integer> mSyncCodeIndex = new RequiredParam<>();
        private RequiredParam<Integer> mRanMultiplier = new RequiredParam<>();

        public Builder setStartingStsIndex(int startingStsIndex) {
            mStartingStsIndex.set(startingStsIndex);
            return this;
        }

        public Builder setUwbTime0(long uwbTime0) {
            mUwbTime0.set(uwbTime0);
            return this;
        }

        public Builder setHopModeKey(int hopModeKey) {
            mHopModeKey.set(hopModeKey);
            return this;
        }

        public Builder setSyncCodeIndex(@SyncCodeIndex int syncCodeIndex) {
            mSyncCodeIndex.set(syncCodeIndex);
            return this;
        }

        public Builder setRanMultiplier(int ranMultiplier) {
            mRanMultiplier.set(ranMultiplier);
            return this;
        }

        public CccRangingStartedParams build() {
            return new CccRangingStartedParams(this);
        }
    }
}
