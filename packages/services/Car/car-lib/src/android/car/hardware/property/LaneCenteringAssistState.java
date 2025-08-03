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
 * android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class LaneCenteringAssistState {
    /**
     * This state is used as an alternative for any {@code LaneCenteringAssistState} value that is
     * not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE} should not use this state. The
     * framework can use this field to remain backwards compatible if {@code
     * LaneCenteringAssistState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * LCA is enabled but the ADAS system has not received an activation signal from the driver.
     * Therefore, LCA is not steering the car and waits for the driver to send an {@link
     * LaneCenteringAssistCommand#ACTIVATE} command.
     */
    public static final int ENABLED = 1;

    /**
     * LCA is enabled and the driver has sent an activation command to the LCA system, but the
     * system has not started actively steering the vehicle. This may happen when LCA needs time to
     * detect valid lane lines. The activation command can be sent through the {@link
     * android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_COMMAND} vehicle property or through a
     * system external to Android. Once LCA is actively steering the vehicle, the state will be
     * updated to {@link #ACTIVATED}. If the feature is not able to activate, then the cause can be
     * communicated through the {@link ErrorState} values and then return to the {@link #ENABLED}
     * state.
     */
    public static final int ACTIVATION_REQUESTED = 2;

    /**
     * LCA is enabled and actively steering the car to keep it centered in its lane.
     */
    public static final int ACTIVATED = 3;

    /**
     * Many LCA implementations allow the driver to override LCA. This means that the car has
     * determined it should go a certain direction to keep the car centered in the lane, but a user
     * decides to take over and do something else. This is often done for safety reasons and to
     * ensure that the driver can always take control of the vehicle. This state should be set when
     * the user is actively overriding the LCA system.
     */
    public static final int USER_OVERRIDE = 4;

    /**
     * When LCA is in the {@link #ACTIVATED} state but it will potentially need to deactivate
     * because of external conditions (e.g. roads curvature is too extreme, the driver does not have
     * their hands on the steering wheel for a long period of time, or the driver is not paying
     * attention), then the ADAS system will notify the driver of a potential need to deactivate and
     * give control back to the driver.
     */
    public static final int FORCED_DEACTIVATION_WARNING = 5;

    private LaneCenteringAssistState() {}

    /**
     * Returns a user-friendly representation of a {@code LaneCenteringAssistState}.
     */
    @NonNull
    public static String toString(
            @LaneCenteringAssistStateInt int laneCenteringAssistState) {
        switch (laneCenteringAssistState) {
            case OTHER:
                return "OTHER";
            case ENABLED:
                return "ENABLED";
            case ACTIVATION_REQUESTED:
                return "ACTIVATION_REQUESTED";
            case ACTIVATED:
                return "ACTIVATED";
            case USER_OVERRIDE:
                return "USER_OVERRIDE";
            case FORCED_DEACTIVATION_WARNING:
                return "FORCED_DEACTIVATION_WARNING";
            default:
                return "0x" + Integer.toHexString(laneCenteringAssistState);
        }
    }

    /** @hide */
    @IntDef({OTHER, ENABLED, ACTIVATION_REQUESTED, ACTIVATED, USER_OVERRIDE,
            FORCED_DEACTIVATION_WARNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaneCenteringAssistStateInt {}
}

