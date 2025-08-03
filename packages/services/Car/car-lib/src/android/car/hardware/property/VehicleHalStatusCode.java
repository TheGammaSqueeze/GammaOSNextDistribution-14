/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * Error codes used in vehicle HAL interface.
 *
 * The list of status codes may be extended in future releases to include
 * additional values.
 * @hide
 */
public final class VehicleHalStatusCode {

    /** No error detected in HAL.*/
    public static final int STATUS_OK = 0;
    /** Try again. */
    public static final int STATUS_TRY_AGAIN = 1;
    /** Invalid argument provide. */
    public static final int STATUS_INVALID_ARG = 2;
    /**
     * This code must be returned when device that associated with the vehicle
     * property is not available. For example, when client tries to set HVAC
     * temperature when the whole HVAC unit is turned OFF.
     */
    public static final int STATUS_NOT_AVAILABLE = 3;
    /** Access denied */
    public static final int STATUS_ACCESS_DENIED = 4;
    /** Something unexpected has happened in Vehicle HAL */
    public static final int STATUS_INTERNAL_ERROR = 5;

    /**
     * For features that are not available because the underlying feature is disabled.
     *
     * For platform versions before {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, this
     * error will be mapped to {@link #STATUS_NOT_AVAILABLE}.
     */
    public static final int STATUS_NOT_AVAILABLE_DISABLED = 6;
    /**
     * For features that are not available because the vehicle speed is too low.
     *
     * For platform versions before {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, this
     * error will be mapped to {@link #STATUS_NOT_AVAILABLE}.
     */
    public static final int STATUS_NOT_AVAILABLE_SPEED_LOW = 7;
    /**
     * For features that are not available because the vehicle speed is too high.
     *
     * For platform versions before {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, this
     * error will be mapped to {@link #STATUS_NOT_AVAILABLE}.
     */
    public static final int STATUS_NOT_AVAILABLE_SPEED_HIGH = 8;
    /**
     * For features that are not available because of bad camera or sensor visibility. Examples
     * might be bird poop blocking the camera or a bumper cover blocking an ultrasonic sensor.
     *
     * For platform versions before {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, this
     * error will be mapped to {@link #STATUS_NOT_AVAILABLE}.
     */
    public static final int STATUS_NOT_AVAILABLE_POOR_VISIBILITY = 9;
    /**
     * The feature cannot be accessed due to safety reasons. Eg. System could be
     * in a faulty state, an object or person could be blocking the requested
     * operation such as closing a trunk door, etc.
     *
     * For platform versions before {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, this
     * error will be mapped to {@link #STATUS_NOT_AVAILABLE}.
     */
    public static final int STATUS_NOT_AVAILABLE_SAFETY = 10;

    /**
     * Returns a user-friendly representation of a {@code VehicleHalStatusCode}.
     */
    @NonNull
    public static String toString(
            @VehicleHalStatusCodeInt int vehicleHalStatusCode) {
        switch (vehicleHalStatusCode) {
            case STATUS_OK:
                return "STATUS_OK";
            case STATUS_TRY_AGAIN:
                return "STATUS_TRY_AGAIN";
            case STATUS_INVALID_ARG:
                return "STATUS_INVALID_ARG";
            case STATUS_NOT_AVAILABLE:
                return "STATUS_NOT_AVAILABLE";
            case STATUS_ACCESS_DENIED:
                return "STATUS_ACCESS_DENIED";
            case STATUS_INTERNAL_ERROR:
                return "STATUS_INTERNAL_ERROR";
            case STATUS_NOT_AVAILABLE_DISABLED:
                return "STATUS_NOT_AVAILABLE_DISABLED";
            case STATUS_NOT_AVAILABLE_SPEED_LOW:
                return "STATUS_NOT_AVAILABLE_SPEED_LOW";
            case STATUS_NOT_AVAILABLE_SPEED_HIGH:
                return "STATUS_NOT_AVAILABLE_SPEED_HIGH";
            case STATUS_NOT_AVAILABLE_POOR_VISIBILITY:
                return "STATUS_NOT_AVAILABLE_POOR_VISIBILITY";
            case STATUS_NOT_AVAILABLE_SAFETY:
                return "STATUS_NOT_AVAILABLE_SAFETY";
            default:
                return Integer.toString(vehicleHalStatusCode);
        }
    }

    /** @hide */
    @IntDef({STATUS_OK, STATUS_TRY_AGAIN, STATUS_INVALID_ARG, STATUS_NOT_AVAILABLE,
        STATUS_ACCESS_DENIED, STATUS_INTERNAL_ERROR, STATUS_NOT_AVAILABLE_DISABLED,
        STATUS_NOT_AVAILABLE_SPEED_LOW, STATUS_NOT_AVAILABLE_SPEED_HIGH,
        STATUS_NOT_AVAILABLE_POOR_VISIBILITY, STATUS_NOT_AVAILABLE_SAFETY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleHalStatusCodeInt {}

    private VehicleHalStatusCode() {}
}
