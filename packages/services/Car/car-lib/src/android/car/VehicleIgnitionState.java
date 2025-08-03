/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car;

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Possible states of a vehicle's ignition.
 *
 * <p>Applications can use {@link android.car.hardware.property.CarPropertyManager#getProperty(int,
 * int)} with {@link android.car.VehiclePropertyIds#IGNITION_STATE} to query the vehicle's ignition
 * state.
 */
public final class VehicleIgnitionState {

    /**
     * The vehicle's ignition state is undefined.
     */
    public static final int UNDEFINED = 0;

    /**
     * Steering wheel is locked. If car can be in {@code LOCK} and {@code OFF} state at the same
     * time than HAL must report {@code LOCK} state.
     */
    public static final int LOCK = 1;

    /**
     * Steering wheel is not locked, engine and all accessories are off. If car can be in {@code
     * LOCK} and {@code OFF} state at the same time than HAL must report {@code LOCK} state.
     */
    public static final int OFF = 2;

    /**
     * Typically in this state accessories become available (e.g. radio). Instrument cluster and
     * engine are turned off
     */
    public static final int ACC = 3;

    /**
     * Ignition is in state on. Accessories and instrument cluster available, engine might be
     * running or ready to be started.
     */
    public static final int ON = 4;

    /** Typically in this state engine is starting (cranking). */
    public static final int START = 5;

    private VehicleIgnitionState() {
    }

    /**
     * Gets a user-friendly representation of an ignition state.
     */
    @NonNull
    public static String toString(@VehicleIgnitionState.Enum int ignitionState) {
        switch (ignitionState) {
            case UNDEFINED:
                return "UNDEFINED";
            case LOCK:
                return "LOCK";
            case OFF:
                return "OFF";
            case ACC:
                return "ACC";
            case ON:
                return "ON";
            case START:
                return "START";
            default:
                return "0x" + Integer.toHexString(ignitionState);
        }
    }

    /** @hide */
    @IntDef({
            UNDEFINED,
            LOCK,
            OFF,
            ACC,
            ON,
            START,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {
    }
}
