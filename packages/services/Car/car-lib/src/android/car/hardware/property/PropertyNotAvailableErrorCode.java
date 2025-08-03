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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Detailed error codes used in vehicle HAL interface.
 *
 * The list of error codes may be extended in future releases to include
 * additional values.
 */
public final class PropertyNotAvailableErrorCode {
    /**
     * General not available error code. Used to support backward compatibility and when other
     * error codes don't cover the not available reason.
     */
    public static final int NOT_AVAILABLE = 0;

    /**
     * For features that are not available because the underlying feature is disabled.
     */
    public static final int NOT_AVAILABLE_DISABLED = 1;
    /**
     * For features that are not available because the vehicle speed is too low.
     */
    public static final int NOT_AVAILABLE_SPEED_LOW = 2;
    /**
     * For features that are not available because the vehicle speed is too high.
     */
    public static final int NOT_AVAILABLE_SPEED_HIGH = 3;
    /**
     * For features that are not available because of bad camera or sensor visibility. Examples
     * might be bird poop blocking the camera or a bumper cover blocking an ultrasonic sensor.
     */
    public static final int NOT_AVAILABLE_POOR_VISIBILITY = 4;
    /**
     * The feature cannot be accessed due to safety reasons. Eg. System could be
     * in a faulty state, an object or person could be blocking the requested
     * operation such as closing a trunk door, etc..
     */
    public static final int NOT_AVAILABLE_SAFETY = 5;

    /**
     * Returns a user-friendly representation of a {@code PropertyNotAvailableErrorCode}.
     */
    @NonNull
    public static String toString(
            @PropertyNotAvailableErrorCodeInt int propertyNotAvailableErrorCode) {
        switch (propertyNotAvailableErrorCode) {
            case NOT_AVAILABLE:
                return "NOT_AVAILABLE";
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
                return Integer.toString(propertyNotAvailableErrorCode);
        }
    }

    /** @hide */
    @IntDef({NOT_AVAILABLE, NOT_AVAILABLE_DISABLED,
        NOT_AVAILABLE_SPEED_LOW, NOT_AVAILABLE_SPEED_HIGH,
        NOT_AVAILABLE_POOR_VISIBILITY, NOT_AVAILABLE_SAFETY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PropertyNotAvailableErrorCodeInt {}

    private PropertyNotAvailableErrorCode() {}
}
