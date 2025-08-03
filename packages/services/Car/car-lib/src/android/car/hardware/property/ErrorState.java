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
 * Used to enumerate the possible error states. For Android 14, {@code ErrorState} is used by ADAS
 * STATE properties in {@link android.car.VehiclePropertyIds}, but its use may be expanded in future
 * releases.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class ErrorState {
    /**
     * This state is used as an alternative for any {@code ErrorState} value that is not defined in
     * the platform. Ideally, implementations of vehicle properties should not use this state. The
     * framework can use this field to remain backwards compatible if this enum is extended to
     * include additional states.
     */
    public static final int OTHER_ERROR_STATE = -1;

    /**
     * Vehicle property is not available because the feature is disabled.
     */
    public static final int NOT_AVAILABLE_DISABLED = -2;

    /**
     * Vehicle property is not available because the vehicle speed is too low to use this feature.
     */
    public static final int NOT_AVAILABLE_SPEED_LOW = -3;

    /**
     * Vehicle property is not available because the vehicle speed is too high to use this feature.
     */
    public static final int NOT_AVAILABLE_SPEED_HIGH = -4;

    /**
     * Vehicle property is not available because sensor or camera visibility is insufficient to use
     * this feature. For example, this can be caused by bird poop blocking the camera, poor weather
     * conditions such as snow or fog, or by any object obstructing the required sensors.
     */
    public static final int NOT_AVAILABLE_POOR_VISIBILITY = -5;

    /**
     * Vehicle property is not available because there is a safety risk that makes this feature
     * unavailable to use presently. For example, this can be caused by someone blocking the trunk
     * door while it is closing, or by the system being in a faulty state.
     */
    public static final int NOT_AVAILABLE_SAFETY = -6;

    private ErrorState() {}

    /**
     * Returns a user-friendly representation of an {@code ErrorState}.
     */
    @NonNull
    public static String toString(@ErrorStateInt int errorState) {
        switch (errorState) {
            case OTHER_ERROR_STATE:
                return "OTHER_ERROR_STATE";
            case NOT_AVAILABLE_DISABLED:
                return "NOT_AVAILABLE_DISABLED";
            case NOT_AVAILABLE_SPEED_LOW:
                return "NOT_AVAILABLE_SPEED_LOW";
            case NOT_AVAILABLE_SPEED_HIGH:
                return "NOT_AVAILABLE_SPEED_HIGH";
            case NOT_AVAILABLE_POOR_VISIBILITY:
                return "NOT_AVAILABLE_POOR_VISIBILITY";
            case NOT_AVAILABLE_SAFETY:
                return "NOT_AVAILABLE_SAFETY";
            default:
                return "0x" + Integer.toHexString(errorState);
        }
    }

    /** @hide */
    @IntDef({OTHER_ERROR_STATE, NOT_AVAILABLE_DISABLED, NOT_AVAILABLE_SPEED_LOW,
            NOT_AVAILABLE_SPEED_HIGH, NOT_AVAILABLE_POOR_VISIBILITY, NOT_AVAILABLE_SAFETY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorStateInt {}
}

