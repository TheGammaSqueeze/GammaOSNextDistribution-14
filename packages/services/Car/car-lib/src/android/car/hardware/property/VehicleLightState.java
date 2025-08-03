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
 * Used by Lights state vehicle properties to enumerate the current state of the lights.
 * Use {@link android.car.hardware.property.CarPropertyManager#getProperty(int, int)} and {@link
 * android.car.hardware.property.CarPropertyManager#setProperty(Class, int, int, java.lang.Object)}
 * to set and get related vehicle properties.
 * @hide
 */
@SystemApi
public final class VehicleLightState {

    /**
     * Off light state.
     */
    public static final int STATE_OFF = 0;

    /**
     * On light state.
     */
    public static final int STATE_ON = 1;

    /**
     * Light state is in daytime running mode. Most cars automatically control daytime running mode,
     * but some cars allow the users to activate them manually.
     */
    public static final int STATE_DAYTIME_RUNNING = 2;

    private VehicleLightState() {}

    /**
     * Gets a user-friendly representation of a vehicle light state.
     */
    @NonNull
    public static String toString(@VehicleLightStateInt int vehicleLightState) {
        switch (vehicleLightState) {
            case STATE_OFF:
                return "STATE_OFF";
            case STATE_ON:
                return "STATE_ON";
            case STATE_DAYTIME_RUNNING:
                return "STATE_DAYTIME_RUNNING";
            default:
                return "0x" + Integer.toHexString(vehicleLightState);
        }
    }

    /** @hide */
    @IntDef({STATE_OFF, STATE_ON, STATE_DAYTIME_RUNNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleLightStateInt {}
}
