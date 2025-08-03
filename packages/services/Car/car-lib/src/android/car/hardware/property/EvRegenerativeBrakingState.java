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
 * Possible EV regenerative braking states of a vehicle.
 *
 * <p>Applications can use {@link android.car.hardware.property.CarPropertyManager#getProperty(int,
 * int)} with {@link android.car.VehiclePropertyIds#EV_REGENERATIVE_BRAKING_STATE} to query the
 * vehicle's regenerative braking state.
 */
public final class EvRegenerativeBrakingState {

    /**
     * The vehicle's EV regenerative braking state is unknown.
     */
    public static final int STATE_UNKNOWN = 0;

    /**
     * The regenerative braking is disabled.
     */
    public static final int STATE_DISABLED = 1;

    /**
     * The regenerative braking is partially enabled.
     */
    public static final int STATE_PARTIALLY_ENABLED = 2;

    /**
     * The regenerative braking is fully enabled.
     */
    public static final int STATE_FULLY_ENABLED = 3;


    private EvRegenerativeBrakingState() {
    }

    /**
     * Gets a user-friendly representation of an EV regenerative braking state.
     */
    @NonNull
    public static String toString(@EvRegenerativeBrakingStateInt int evRegenerativeBrakingState) {
        switch (evRegenerativeBrakingState) {
            case STATE_UNKNOWN:
                return "STATE_UNKNOWN";
            case STATE_DISABLED:
                return "STATE_DISABLED";
            case STATE_PARTIALLY_ENABLED:
                return "STATE_PARTIALLY_ENABLED";
            case STATE_FULLY_ENABLED:
                return "STATE_FULLY_ENABLED";
            default:
                return "0x" + Integer.toHexString(evRegenerativeBrakingState);
        }
    }

    /** @hide */
    @IntDef({STATE_UNKNOWN, STATE_DISABLED, STATE_PARTIALLY_ENABLED, STATE_FULLY_ENABLED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EvRegenerativeBrakingStateInt {
    }
}
