/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * Possible vehicle light switch states.
 *
 * <p>Applications can use {@link
 * android.car.hardware.property.CarPropertyManager#getProperty(int, int)} and {@link
 * android.car.hardware.property.CarPropertyManager#setProperty(Class, int, int, java.lang.Object)}
 * to get and set the vehicle's light switch.
 *
 * @hide
 */
@SystemApi
public final class VehicleLightSwitch {

    /**
     * Off light switch state.
     */
    public static final int STATE_OFF = 0;

    /**
     * On light switch state.
     */
    public static final int STATE_ON = 1;

    /**
     * Daytime running light switch state. Most cars automatically control daytime running mode, but
     * some cars allow the users to activate them manually.
     */
    public static final int STATE_DAYTIME_RUNNING = 2;

    /**
     * Automatic light switch state. Allows the ECU to set the lights automatically.
     */
    public static final int STATE_AUTOMATIC = 0x100;

    private VehicleLightSwitch() {
    }

    /**
     * Gets a user-friendly representation of a vehicle light switch.
     */
    @NonNull
    public static String toString(@VehicleLightSwitchInt int vehicleLightSwitch) {
        switch (vehicleLightSwitch) {
            case STATE_OFF:
                return "STATE_OFF";
            case STATE_ON:
                return "STATE_ON";
            case STATE_DAYTIME_RUNNING:
                return "STATE_DAYTIME_RUNNING";
            case STATE_AUTOMATIC:
                return "STATE_AUTOMATIC";
            default:
                return "0x" + Integer.toHexString(vehicleLightSwitch);
        }
    }

    /** @hide */
    @IntDef({STATE_OFF, STATE_ON, STATE_DAYTIME_RUNNING, STATE_AUTOMATIC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleLightSwitchInt {
    }
}
