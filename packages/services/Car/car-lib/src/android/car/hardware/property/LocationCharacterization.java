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
 * Used by {@link android.car.VehiclePropertyIds#LOCATION_CHARACTERIZATION} to enumerate the
 * supported bit flags.
 *
 * <p>These flags are used to indicate what sort of transformations are performed on the GNSS
 * positions before exposed through the {@link android.location.LocationManager} APIs for the
 * {@link android.location.LocationManager#GPS_PROVIDER} location provider.
 *
 * <p>This enum can be extended in future releases to include additional bit flags.
 */
public class LocationCharacterization {
    /**
     * Prior location samples have been used to refine the raw GNSS data (e.g. a Kalman Filter).
     */
    public static final int PRIOR_LOCATIONS = 0x1;
    /**
     * Gyroscope data has been used to refine the raw GNSS data.
     */
    public static final int GYROSCOPE_FUSION = 0x2;
    /**
     * Accelerometer data has been used to refine the raw GNSS data.
     */
    public static final int ACCELEROMETER_FUSION = 0x4;
    /**
     * Compass data has been used to refine the raw GNSS data.
     */
    public static final int COMPASS_FUSION = 0x8;
    /**
     * Wheel speed has been used to refine the raw GNSS data.
     */
    public static final int WHEEL_SPEED_FUSION = 0x10;
    /**
     * Steering angle has been used to refine the raw GNSS data.
     */
    public static final int STEERING_ANGLE_FUSION = 0x20;
    /**
     * Car speed has been used to refine the raw GNSS data.
     */
    public static final int CAR_SPEED_FUSION = 0x40;
    /**
     * Some effort is made to dead-reckon location. In particular, this means that relative changes
     * in location have meaning when no GNSS satellite is available.
     */
    public static final int DEAD_RECKONED = 0x80;
    /**
     * Location is based on GNSS satellite signals without sufficient fusion of other sensors for
     * complete dead reckoning. This flag should be set when relative changes to location cannot be
     * relied on when no GNSS satellite is available.
     */
    public static final int RAW_GNSS_ONLY = 0x100;

    private LocationCharacterization() {}

    /**
     * Returns a user-friendly representation of an {@code LocationCharacterization}.
     */
    @NonNull
    public static String toString(
            @LocationCharacterization.LocationCharacterizationInt int locationCharacterization) {
        switch (locationCharacterization) {
            case PRIOR_LOCATIONS:
                return "PRIOR_LOCATIONS";
            case GYROSCOPE_FUSION:
                return "GYROSCOPE_FUSION";
            case ACCELEROMETER_FUSION:
                return "ACCELEROMETER_FUSION";
            case COMPASS_FUSION:
                return "COMPASS_FUSION";
            case WHEEL_SPEED_FUSION:
                return "WHEEL_SPEED_FUSION";
            case STEERING_ANGLE_FUSION:
                return "STEERING_ANGLE_FUSION";
            case CAR_SPEED_FUSION:
                return "CAR_SPEED_FUSION";
            case DEAD_RECKONED:
                return "DEAD_RECKONED";
            case RAW_GNSS_ONLY:
                return "RAW_GNSS_ONLY";
            default:
                return "0x" + Integer.toHexString(locationCharacterization);
        }
    }

    /** @hide */
    @IntDef({PRIOR_LOCATIONS, GYROSCOPE_FUSION, ACCELEROMETER_FUSION, COMPASS_FUSION,
            WHEEL_SPEED_FUSION, STEERING_ANGLE_FUSION, CAR_SPEED_FUSION, DEAD_RECKONED,
            RAW_GNSS_ONLY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LocationCharacterizationInt {}
}
