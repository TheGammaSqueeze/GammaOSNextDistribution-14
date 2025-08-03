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
 * Used to enumerate the current driver state of {@link
 * android.car.VehiclePropertyIds#HANDS_ON_DETECTION_DRIVER_STATE}.
 *
 * <p>This enum could be extended in future releases to include additional feature states.
 * @hide
 */
@SystemApi
public class HandsOnDetectionDriverState {
    /**
     * This state is used as an alternative for any {@code HandsOnDetectionDriverState} value that
     * is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#HANDS_ON_DETECTION_DRIVER_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * HandsOnDetectionDriverState} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * The system detects that the driver has their hands on the steering wheel.
     */
    public static final int HANDS_ON = 1;
    /**
     * The system detects that the driver has their hands off the steering wheel.
     */
    public static final int HANDS_OFF = 2;

    private HandsOnDetectionDriverState() {}

    /**
     * Returns a user-friendly representation of a {@code HandsOnDetectionDriverState}.
     */
    @NonNull
    public static String toString(
            @HandsOnDetectionDriverStateInt int handsOnDetectionDriverState) {
        switch (handsOnDetectionDriverState) {
            case OTHER:
                return "OTHER";
            case HANDS_ON:
                return "HANDS_ON";
            case HANDS_OFF:
                return "HANDS_OFF";
            default:
                return "0x" + Integer.toHexString(handsOnDetectionDriverState);
        }
    }

    /** @hide */
    @IntDef({OTHER, HANDS_ON, HANDS_OFF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HandsOnDetectionDriverStateInt {}
}
