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
 * android.car.VehiclePropertyIds#LANE_KEEP_ASSIST_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class LaneKeepAssistState {
    /**
     * This state is used as an alternative for any {@code LaneKeepAssistState} value that is not
     * defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#LANE_KEEP_ASSIST_STATE} should not use this state. The
     * framework can use this field to remain backwards compatible if {@code LaneKeepAssistState}
     * is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * LKA is enabled and monitoring, but steering assist is not activated.
     */
    public static final int ENABLED = 1;

    /**
     * LKA is enabled and currently has steering assist applied for the vehicle. Steering assist is
     * steering toward the left direction, which generally means the steering wheel turns counter
     * clockwise. This is usually in response to the vehicle drifting to the right. Once steering
     * assist is completed, LKA must return to the {@link #ENABLED} state.
     */
    public static final int ACTIVATED_STEER_LEFT = 2;

    /**
     * LKA is enabled and currently has steering assist applied for the vehicle. Steering assist is
     * steering toward the right direction, which generally means the steering wheel turns
     * clockwise. This is usually in response to the vehicle drifting to the left. Once steering
     * assist is completed, LKA must return to the {@link #ENABLED} state.
     */
    public static final int ACTIVATED_STEER_RIGHT = 3;

    /**
     * Many LKA implementations allow the driver to override LKA. This means that the car has
     * determined it should take some action, but a user decides to take over and do something else.
     * This is often done for safety reasons and to ensure that the driver can always take control
     * of the vehicle. This state should be set when the user is actively overriding the LKA system.
     */
    public static final int USER_OVERRIDE = 4;

    private LaneKeepAssistState() {}

    /**
     * Returns a user-friendly representation of a {@code LaneKeepAssistState}.
     */
    @NonNull
    public static String toString(
            @LaneKeepAssistStateInt int laneKeepAssistState) {
        switch (laneKeepAssistState) {
            case OTHER:
                return "OTHER";
            case ENABLED:
                return "ENABLED";
            case ACTIVATED_STEER_LEFT:
                return "ACTIVATED_STEER_LEFT";
            case ACTIVATED_STEER_RIGHT:
                return "ACTIVATED_STEER_RIGHT";
            case USER_OVERRIDE:
                return "USER_OVERRIDE";
            default:
                return "0x" + Integer.toHexString(laneKeepAssistState);
        }
    }

    /** @hide */
    @IntDef({OTHER, ENABLED, ACTIVATED_STEER_LEFT, ACTIVATED_STEER_RIGHT, USER_OVERRIDE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaneKeepAssistStateInt {}
}

