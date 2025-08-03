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
package com.android.server.remoteauth.ranging;

import androidx.annotation.IntDef;

/** Holds ranging report data. */
public class RangingReport {

    /**
     * State of the proximity based on detected distance compared against specified near and far
     * boundaries.
     */
    @IntDef(
            value = {
                PROXIMITY_STATE_UNKNOWN,
                PROXIMITY_STATE_INSIDE,
                PROXIMITY_STATE_OUTSIDE,
            })
    public @interface ProximityState {}

    /** Unknown proximity state. */
    public static final int PROXIMITY_STATE_UNKNOWN = 0x0;

    /**
     * Proximity is inside the lower and upper proximity boundary. lowerProximityBoundaryM <=
     * proximity <= upperProximityBoundaryM
     */
    public static final int PROXIMITY_STATE_INSIDE = 0x1;

    /**
     * Proximity is outside the lower and upper proximity boundary. proximity <
     * lowerProximityBoundaryM OR upperProximityBoundaryM < proximity
     */
    public static final int PROXIMITY_STATE_OUTSIDE = 0x2;

    private final float mDistanceM;
    @ProximityState private final int mProximityState;

    /**
     * Gets the distance measurement in meters.
     *
     * <p>Value may be negative for devices in very close proximity.
     *
     * @return distance in meters
     */
    public float getDistanceM() {
        return mDistanceM;
    }

    /**
     * Gets the {@link ProximityState}.
     *
     * <p>The state is computed based on {@link #getDistanceM} and proximity related session
     * parameters.
     *
     * @return proximity state
     */
    @ProximityState
    public int getProximityState() {
        return mProximityState;
    }

    private RangingReport(float distanceM, @ProximityState int proximityState) {
        mDistanceM = distanceM;
        mProximityState = proximityState;
    }

    /** Builder class for {@link RangingReport}. */
    public static final class Builder {
        private float mDistanceM;
        @ProximityState private int mProximityState;

        /** Sets the distance in meters. */
        public Builder setDistanceM(float distanceM) {
            mDistanceM = distanceM;
            return this;
        }

        /** Sets the proximity state. */
        public Builder setProximityState(@ProximityState int proximityState) {
            mProximityState = proximityState;
            return this;
        }

        /** Builds {@link RangingReport}. */
        public RangingReport build() {
            return new RangingReport(mDistanceM, mProximityState);
        }
    }
}
