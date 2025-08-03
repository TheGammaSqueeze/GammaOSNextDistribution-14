/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * Possible EV charge states of a vehicle.
 *
 * <p>Applications can use {@link android.car.hardware.property.CarPropertyManager#getProperty(int,
 * int)} with {@link android.car.VehiclePropertyIds#EV_CHARGE_STATE} to query the vehicle's ignition
 * charge state.
 */
public final class EvChargeState {

    /**
     * The vehicle's EV charge state is unknown.
     */
    public static final int STATE_UNKNOWN = 0;

    /**
     * The vehicle is charging.
     */
    public static final int STATE_CHARGING = 1;

    /**
     * The vehicle is fully charged.
     */
    public static final int STATE_FULLY_CHARGED = 2;

    /**
     * The vehicle is not charging.
     */
    public static final int STATE_NOT_CHARGING = 3;

    /**
     * The vehicle is not charging due to an error.
     */
    public static final int STATE_ERROR = 4;


    private EvChargeState() {
    }

    /**
     * Gets a user-friendly representation of an EV charge state.
     */
    @NonNull
    public static String toString(@EvChargeStateInt int evChargeState) {
        switch (evChargeState) {
            case STATE_UNKNOWN:
                return "STATE_UNKNOWN";
            case STATE_CHARGING:
                return "STATE_CHARGING";
            case STATE_FULLY_CHARGED:
                return "STATE_FULLY_CHARGED";
            case STATE_NOT_CHARGING:
                return "STATE_NOT_CHARGING";
            case STATE_ERROR:
                return "STATE_ERROR";
            default:
                return "0x" + Integer.toHexString(evChargeState);
        }
    }

    /** @hide */
    @IntDef({STATE_UNKNOWN, STATE_CHARGING, STATE_FULLY_CHARGED, STATE_NOT_CHARGING, STATE_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EvChargeStateInt {
    }
}
