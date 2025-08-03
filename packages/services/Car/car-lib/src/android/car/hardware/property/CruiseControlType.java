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
 * Used to enumerate the current type of {@link
 * android.car.VehiclePropertyIds#CRUISE_CONTROL_TYPE}.
 *
 * <p>This enum could be extended in future releases to include additional feature states.
 * @hide
 */
@SystemApi
public class CruiseControlType {
    /**
     * This state is used as an alternative for any {@code CruiseControlType} value that is not
     * defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#CRUISE_CONTROL_TYPE} should not use this state. The framework
     * can use this field to remain backwards compatible if {@code CruiseControlType} is extended to
     * include additional types.
     */
    public static final int OTHER = 0;
    /**
     * Standard cruise control is when a system in the vehicle automatically maintains a set speed
     * without the driver having to keep their foot on the accelerator. This version of cruise
     * control does not include automatic acceleration and deceleration to maintain a set time gap
     * from a vehicle ahead.
     */
    public static final int STANDARD = 1;
    /**
     * Adaptive cruise control is when a system in the vehicle automatically accelerates and
     * decelerates to maintain a set speed and/or a set time gap from a vehicle ahead.
     */
    public static final int ADAPTIVE = 2;
    /**
     * Predictive cruise control is a version of adaptive cruise control that also considers road
     * topography, road curvature, speed limit and traffic signs, etc. to actively adjust braking,
     * acceleration, gear shifting, etc. for the vehicle. This feature is often used to optimize
     * fuel consumption.
     */
    public static final int PREDICTIVE = 3;

    private CruiseControlType() {}

    /**
     * Returns a user-friendly representation of a {@code CruiseControlType}.
     */
    @NonNull
    public static String toString(
            @CruiseControlType.CruiseControlTypeInt int cruiseControlType) {
        switch (cruiseControlType) {
            case OTHER:
                return "OTHER";
            case STANDARD:
                return "STANDARD";
            case ADAPTIVE:
                return "ADAPTIVE";
            case PREDICTIVE:
                return "PREDICTIVE";
            default:
                return "0x" + Integer.toHexString(cruiseControlType);
        }
    }

    /** @hide */
    @IntDef({OTHER, STANDARD, ADAPTIVE, PREDICTIVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CruiseControlTypeInt {}
}
