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
 * android.car.VehiclePropertyIds#AUTOMATIC_EMERGENCY_BRAKING_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class AutomaticEmergencyBrakingState {
    /**
     * This state is used as an alternative for any {@code AutomaticEmergencyBrakingState} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#AUTOMATIC_EMERGENCY_BRAKING_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * AutomaticEmergencyBrakingState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * AEB is enabled and monitoring safety, but brakes are not activated.
     */
    public static final int ENABLED = 1;

    /**
     * AEB is enabled and currently has the brakes applied for the vehicle.
     */
    public static final int ACTIVATED = 2;

    /**
     * Many AEB implementations allow the driver to override AEB. This means that the car has
     * determined it should brake, but a user decides to take over and do something else. This is
     * often done for safety reasons and to ensure that the driver can always take control of the
     * vehicle. This state should be set when the user is actively overriding the AEB system.
     */
    public static final int USER_OVERRIDE = 3;

    private AutomaticEmergencyBrakingState() {}

    /**
     * Returns a user-friendly representation of an {@code AutomaticEmergencyBrakingState}.
     */
    @NonNull
    public static String toString(
            @AutomaticEmergencyBrakingStateInt int automaticEmergencyBrakingState) {
        switch (automaticEmergencyBrakingState) {
            case OTHER:
                return "OTHER";
            case ENABLED:
                return "ENABLED";
            case ACTIVATED:
                return "ACTIVATED";
            case USER_OVERRIDE:
                return "USER_OVERRIDE";
            default:
                return "0x" + Integer.toHexString(automaticEmergencyBrakingState);
        }
    }

    /** @hide */
    @IntDef({OTHER, ENABLED, ACTIVATED, USER_OVERRIDE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutomaticEmergencyBrakingStateInt {}
}

