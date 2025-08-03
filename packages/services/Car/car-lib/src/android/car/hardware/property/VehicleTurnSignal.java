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
 * Possible turn signal states of a vehicle.
 *
 * <p>Applications can use {@link android.car.hardware.property.CarPropertyManager#getProperty(int,
 * int)} with {@link android.car.VehiclePropertyIds#TURN_SIGNAL_STATE} to query the
 * vehicle's turn signal state.
 *
 * @hide
 */
@SystemApi
public final class VehicleTurnSignal {

    /**
     * Neither right nor left signal in a vehicle are being used.
     */
    public static final int STATE_NONE = 0;

    /**
     * Right turn signal in a vehicle is being used.
     */
    public static final int STATE_RIGHT = 1;

    /**
     * Left turn signal in a vehicle is being used.
     */
    public static final int STATE_LEFT = 2;

    private VehicleTurnSignal() {
    }

    /**
     * Gets a user-friendly representation of a turn signal state.
     */
    @NonNull
    public static String toString(@VehicleTurnSignalInt int vehicleTurnSignal) {
        switch (vehicleTurnSignal) {
            case STATE_NONE:
                return "STATE_NONE";
            case STATE_RIGHT:
                return "STATE_RIGHT";
            case STATE_LEFT:
                return "STATE_LEFT";
            default:
                return "0x" + Integer.toHexString(vehicleTurnSignal);
        }
    }

    /** @hide */
    @IntDef({STATE_NONE, STATE_RIGHT, STATE_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleTurnSignalInt {
    }
}
