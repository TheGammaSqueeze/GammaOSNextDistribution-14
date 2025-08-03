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
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used by {@link android.car.VehiclePropertyIds#EV_STOPPING_MODE} to enumerate the current state of
 * the stopping mode.
 *
 * <p>This list of states may be extended to include more states in the future.
 * @hide
 */
@SystemApi
public final class EvStoppingMode {
    /**
     * Other EV stopping mode. Ideally, this should never be used.
     */
    public static final int STATE_OTHER = 0;

    /**
     * Vehicle slowly moves forward when the brake pedal is released.
     */
    public static final int STATE_CREEP = 1;

    /**
     * Vehicle rolls freely when the brake pedal is released (similar to neutral gear).
     */
    public static final int STATE_ROLL = 2;

    /**
     * Vehicle stops and holds its position when the brake pedal is released.
     */
    public static final int STATE_HOLD = 3;

    private EvStoppingMode() {}

    /**
     * Returns a user-friendly representation of an EV stopping mode.
     */
    @NonNull
    public static String toString(@EvStoppingModeInt int evStoppingMode) {
        switch (evStoppingMode) {
            case STATE_OTHER:
                return "STATE_OTHER";
            case STATE_CREEP:
                return "STATE_CREEP";
            case STATE_ROLL:
                return "STATE_ROLL";
            case STATE_HOLD:
                return "STATE_HOLD";
            default:
                return "0x" + Integer.toHexString(evStoppingMode);
        }
    }

    /** @hide */
    @IntDef({STATE_OTHER, STATE_CREEP, STATE_ROLL, STATE_HOLD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EvStoppingModeInt {}
}

