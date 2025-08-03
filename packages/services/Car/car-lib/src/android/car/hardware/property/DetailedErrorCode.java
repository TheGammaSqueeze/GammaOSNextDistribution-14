/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.car.feature.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Detailed error codes used in vehicle HAL interface.
 *
 * The list of error codes may be extended in future releases to include additional values.
 */
@FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
public final class DetailedErrorCode {
    /**
     * General not available error code. Used to support backward compatibility and when other
     * error codes don't cover the not available reason.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NO_DETAILED_ERROR_CODE = 0;

    /**
     * For features that are not available because the underlying feature is disabled.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NOT_AVAILABLE_DISABLED =
            PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED;
    /**
     * For features that are not available because the vehicle speed is too low.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NOT_AVAILABLE_SPEED_LOW =
            PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW;
    /**
     * For features that are not available because the vehicle speed is too high.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NOT_AVAILABLE_SPEED_HIGH =
            PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH;
    /**
     * For features that are not available because of bad camera or sensor visibility. Examples
     * might be bird poop blocking the camera or a bumper cover blocking an ultrasonic sensor.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NOT_AVAILABLE_POOR_VISIBILITY =
            PropertyNotAvailableErrorCode.NOT_AVAILABLE_POOR_VISIBILITY;
    /**
     * The feature cannot be accessed due to safety reasons. Eg. System could be
     * in a faulty state, an object or person could be blocking the requested
     * operation such as closing a trunk door, etc..
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public static final int NOT_AVAILABLE_SAFETY =
            PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY;

    /**
     * Returns a user-friendly representation of a {@code DetailedErrorCode}.
     */
    @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    @NonNull
    public static String toString(
            @DetailedErrorCodeInt int detailedErrorCode) {
        switch (detailedErrorCode) {
            case NO_DETAILED_ERROR_CODE:
                return "NO_DETAILED_ERROR_CODE";
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
                return Integer.toString(detailedErrorCode);
        }
    }

    /** @hide */
    @IntDef({NO_DETAILED_ERROR_CODE, NOT_AVAILABLE_DISABLED,
        NOT_AVAILABLE_SPEED_LOW, NOT_AVAILABLE_SPEED_HIGH,
        NOT_AVAILABLE_POOR_VISIBILITY, NOT_AVAILABLE_SAFETY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DetailedErrorCodeInt {}

    private DetailedErrorCode() {}
}
