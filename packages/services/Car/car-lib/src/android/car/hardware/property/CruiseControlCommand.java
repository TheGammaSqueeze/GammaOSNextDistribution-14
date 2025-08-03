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
 * Used by {@link android.car.VehiclePropertyIds#CRUISE_CONTROL_COMMAND} to enumerate commands.
 *
 * <p>This enum could be extended in future releases to include additional feature states.
 * @hide
 */
@SystemApi
public class CruiseControlCommand {
    /**
     * Activate cruise control, which means CC takes control of maintaining the vehicle's target
     * speed without the driver having to keep their foot on the accelerator. The target speed for
     * CC is generally set to the vehicle's speed at the time of activation.
     */
    public static final int ACTIVATE = 1;
    /**
     * Suspend cruise control, but still keep it enabled. Once CC is activated again, the
     * target speed should resume to the previous setting.
     */
    public static final int SUSPEND = 2;
    /**
     * Increase the target speed when CC is activated. The increment value should be decided by the
     * OEM. The updated value can be read from {@link
     * android.car.VehiclePropertyIds#CRUISE_CONTROL_TARGET_SPEED}.
     */
    public static final int INCREASE_TARGET_SPEED = 3;
    /**
     * Decrease the target speed when CC is activated. The decrement value should be decided by the
     * OEM. The updated value can be read from {@link
     * android.car.VehiclePropertyIds#CRUISE_CONTROL_TARGET_SPEED}.
     */
    public static final int DECREASE_TARGET_SPEED = 4;
    /**
     * Increase the target time gap or distance from the vehicle ahead when adaptive/predictive CC
     * is activated. The increment value should be decided by the OEM. The updated value can be read
     * from {@link android.car.VehiclePropertyIds#ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP}. Writing
     * this command to a standard CC vehicle should throw a {@link PropertyNotAvailableException}.
     */
    public static final int INCREASE_TARGET_TIME_GAP = 5;
    /**
     * Decrease the target time gap or distance from the vehicle ahead when adaptive/predictive CC
     * is activated. The decrement value should be decided by the 0EM. The updated value can be read
     * from {@link android.car.VehiclePropertyIds#ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP}. Writing
     * this command to a standard CC vehicle should throw a {@link PropertyNotAvailableException}.
     */
    public static final int DECREASE_TARGET_TIME_GAP = 6;


    private CruiseControlCommand() {}

    /**
     * Returns a user-friendly representation of a {@code CruiseControlCommand}.
     */
    @NonNull
    public static String toString(
            @CruiseControlCommand.CruiseControlCommandInt int cruiseControlCommand) {
        switch (cruiseControlCommand) {
            case ACTIVATE:
                return "ACTIVATE";
            case SUSPEND:
                return "SUSPEND";
            case INCREASE_TARGET_SPEED:
                return "INCREASE_TARGET_SPEED";
            case DECREASE_TARGET_SPEED:
                return "DECREASE_TARGET_SPEED";
            case INCREASE_TARGET_TIME_GAP:
                return "INCREASE_TARGET_TIME_GAP";
            case DECREASE_TARGET_TIME_GAP:
                return "DECREASE_TARGET_TIME_GAP";
            default:
                return "0x" + Integer.toHexString(cruiseControlCommand);
        }
    }

    /** @hide */
    @IntDef({ACTIVATE, SUSPEND, INCREASE_TARGET_SPEED, DECREASE_TARGET_SPEED,
            INCREASE_TARGET_TIME_GAP, DECREASE_TARGET_TIME_GAP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CruiseControlCommandInt {}
}
