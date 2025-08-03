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

package android.car.hardware.property;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to enumerate the current state of {@link
 * android.car.VehiclePropertyIds#LANE_DEPARTURE_WARNING_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class LaneDepartureWarningState {
    /**
     * This state is used as an alternative for any {@code LaneDepartureWarningState} value that
     * is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#LANE_DEPARTURE_WARNING_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * LaneDepartureWarningState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * LDW is enabled and monitoring, but the vehicle is centered in the lane.
     */
    public static final int NO_WARNING = 1;

    /**
     * LDW is enabled, detects the vehicle is approaching or crossing lane lines on the left side
     * of the vehicle, and is currently warning the user.
     */
    public static final int WARNING_LEFT = 2;

    /**
     * LDW is enabled, detects the vehicle is approaching or crossing lane lines on the right side
     * of the vehicle, and is currently warning the user.
     */
    public static final int WARNING_RIGHT = 3;

    private LaneDepartureWarningState() {}

    /**
     * Returns a user-friendly representation of a {@code LaneDepartureWarningState}.
     */
    @NonNull
    public static String toString(
            @LaneDepartureWarningStateInt int laneDepartureWarningState) {
        switch (laneDepartureWarningState) {
            case OTHER:
                return "OTHER";
            case NO_WARNING:
                return "NO_WARNING";
            case WARNING_LEFT:
                return "WARNING_LEFT";
            case WARNING_RIGHT:
                return "WARNING_RIGHT";
            default:
                return "0x" + Integer.toHexString(laneDepartureWarningState);
        }
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING_LEFT, WARNING_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaneDepartureWarningStateInt {}
}

