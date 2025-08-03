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
 * Used to enumerate the current warning state of {@link
 * android.car.VehiclePropertyIds#HANDS_ON_DETECTION_WARNING}.
 *
 * <p>This enum could be extended in future releases to include additional feature states.
 * @hide
 */
@SystemApi
public class HandsOnDetectionWarning {
    /**
     * This state is used as an alternative for any {@code HandsOnDetectionWarning} value that is
     * not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#HANDS_ON_DETECTION_WARNING} should not use this state. The
     * framework can use this field to remain backwards compatible if {@code
     * HandsOnDetectionWarning} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * HOD is enabled and the driver's current safety does not warrant sending a warning. This state
     * is independent of whether the driver actually has their hands on or off the wheel.
     */
    public static final int NO_WARNING = 1;
    /**
     * HOD is enabled and the driver's hands have been off the wheel for too long a duration, and
     * the vehicle is sending a warning to the driver as a consequence of this.
     */
    public static final int WARNING = 2;

    private HandsOnDetectionWarning() {}

    /**
     * Returns a user-friendly representation of a {@code HandsOnDetectionWarning}.
     */
    @NonNull
    public static String toString(
            @HandsOnDetectionWarning.HandsOnDetectionWarningInt int handsOnDetectionWarning) {
        switch (handsOnDetectionWarning) {
            case OTHER:
                return "OTHER";
            case NO_WARNING:
                return "NO_WARNING";
            case WARNING:
                return "WARNING";
            default:
                return "0x" + Integer.toHexString(handsOnDetectionWarning);
        }
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HandsOnDetectionWarningInt {}
}
