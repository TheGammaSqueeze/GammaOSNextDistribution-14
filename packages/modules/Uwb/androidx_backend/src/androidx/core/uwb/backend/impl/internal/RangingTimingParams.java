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

package androidx.core.uwb.backend.impl.internal;

/** Timing-related parameters. */
public class RangingTimingParams {

    private final int mRangingIntervalNormal;
    private final int mRangingIntervalFast;
    private final int mRangingIntervalInfrequent;
    private final int mSlotPerRangingRound;
    private final int mSlotDurationRstu;
    private final int mInitiationTimeMs;
    private final boolean mHoppingEnabled;

    RangingTimingParams(
            int rangingIntervalNormal,
            int rangingIntervalFast,
            int rangingIntervalInfrequent,
            int slotPerRangingRound,
            int slotDurationRstu,
            int initiationTimeMs,
            boolean hoppingEnabled) {
        mRangingIntervalNormal = rangingIntervalNormal;
        mRangingIntervalFast = rangingIntervalFast;
        mRangingIntervalInfrequent = rangingIntervalInfrequent;
        mSlotPerRangingRound = slotPerRangingRound;
        mSlotDurationRstu = slotDurationRstu;
        mInitiationTimeMs = initiationTimeMs;
        mHoppingEnabled = hoppingEnabled;
    }

    public int getRangingIntervalNormal() {
        return mRangingIntervalNormal;
    }

    public int getRangingIntervalFast() {
        return mRangingIntervalFast;
    }

    public int getRangingIntervalInfrequent() {
        return mRangingIntervalInfrequent;
    }

    public int getSlotPerRangingRound() {
        return mSlotPerRangingRound;
    }

    public int getSlotDurationRstu() {
        return mSlotDurationRstu;
    }

    public int getInitiationTimeMs() {
        return mInitiationTimeMs;
    }

    public boolean isHoppingEnabled() {
        return mHoppingEnabled;
    }

    /** Converts updateRate to numerical ranging interval value. */
    public int getRangingInterval(@Utils.RangingUpdateRate int updateRate) {
        switch (updateRate) {
            case Utils.NORMAL:
                return mRangingIntervalNormal;
            case Utils.INFREQUENT:
                return mRangingIntervalInfrequent;
            case Utils.FAST:
                return mRangingIntervalFast;
            default:
                throw new IllegalArgumentException("Argument updateRate is invalid.");
        }
    }
}
