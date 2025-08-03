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
 * Defines optional parameters for CCC start operation. These parameters are only required if a
 * reconfiguration of the RAN multiplier is required. These parameters are used to support the
 * Configurable_Ranging_Recovery_RQ message in the CCC specification. Start, or start with RAN
 * multiplier reconfiguration can only be called on a stopped session.
 *
 * <p>This is passed as a bundle to the service API {@link RangingSession#start}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccStartRangingParams extends CccParams {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_RAN_MULTIPLIER = "ran_multiplier";
    private static final String KEY_INITIATION_TIME_MS = "initiation_time_ms";
    private static final String KEY_ABSOLUTE_INITIATION_TIME_US = "absolute_initiation_time_us";
    private static final String KEY_STS_INDEX = "sts_index";

    private final int mSessionId;
    private final int mRanMultiplier;

    // FiRa 1.0: Relative time (in milli-seconds).
    // FiRa 2.0: Relative time (in milli-seconds).
    private final long mInitiationTimeMs;

    // FiRa 2.0: Absolute time in UWB time domain, as specified in CR-272 (in micro-seconds).
    private final long mAbsoluteInitiationTimeUs;
    private final int mStsIndex;


    private CccStartRangingParams(Builder builder) {
        this.mSessionId = builder.mSessionId.get();
        this.mRanMultiplier = builder.mRanMultiplier.get();
        this.mInitiationTimeMs = builder.mInitiationTimeMs;
        this.mAbsoluteInitiationTimeUs = builder.mAbsoluteInitiationTimeUs;
        this.mStsIndex = builder.mStsIndex;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_RAN_MULTIPLIER, mRanMultiplier);
        bundle.putLong(KEY_INITIATION_TIME_MS, mInitiationTimeMs);
        bundle.putLong(KEY_ABSOLUTE_INITIATION_TIME_US, mAbsoluteInitiationTimeUs);
        bundle.putInt(KEY_STS_INDEX, mStsIndex);
        return bundle;
    }

    public static CccStartRangingParams fromBundle(PersistableBundle bundle) {
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

    public int getSessionId() {
        return mSessionId;
    }

    public int getRanMultiplier() {
        return mRanMultiplier;
    }

    public long getInitiationTimeMs() {
        return mInitiationTimeMs;
    }

    public long getAbsoluteInitiationTimeUs() {
        return mAbsoluteInitiationTimeUs;
    }

    public int getStsIndex() {
        return mStsIndex;
    }

    private static CccStartRangingParams parseVersion1(PersistableBundle bundle) {
        return new Builder()
            .setSessionId(bundle.getInt(KEY_SESSION_ID))
            .setRanMultiplier(bundle.getInt(KEY_RAN_MULTIPLIER))
            .setInitiationTimeMs(bundle.getLong(KEY_INITIATION_TIME_MS))
            .setAbsoluteInitiationTimeUs(bundle.getLong(KEY_ABSOLUTE_INITIATION_TIME_US))
            .setStsIndex(bundle.getInt(KEY_STS_INDEX, 0))
            .build();
    }

    /** Builder */
    public static class Builder {
        private RequiredParam<Integer> mSessionId = new RequiredParam<>();
        private RequiredParam<Integer> mRanMultiplier = new RequiredParam<>();
        private long mInitiationTimeMs = 0;
        private long mAbsoluteInitiationTimeUs = 0;
        private int mStsIndex = 0;

        public Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public Builder setRanMultiplier(int ranMultiplier) {
            mRanMultiplier.set(ranMultiplier);
            return this;
        }

        /** Set initiation time in ms */
        public Builder setInitiationTimeMs(long initiationTimeMs) {
            mInitiationTimeMs = initiationTimeMs;
            return this;
        }

        public Builder setStsIndex(int stsIndex) {
            mStsIndex = stsIndex;
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

        public CccStartRangingParams build() {
            return new CccStartRangingParams(this);
        }
    }
}
