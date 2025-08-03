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
 * Used to enumerate the current state of {@link
 * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class WindshieldWipersState {
    /**
     * This state is used as an alternative for any {@code WindshieldWipersState} value that is not
     * defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_STATE} should not use this state. The
     * framework can use this field to remain backwards compatible if {@code WindshieldWipersState}
     * is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * This state indicates windshield wipers are currently off. If {@link
     * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_SWITCH} is implemented, then it may be set
     * to any of the following modes: {@link WindshieldWipersSwitch#OFF} or {@link
     * WindshieldWipersSwitch#AUTO}.
     */
    public static final int OFF = 1;

    /**
     * This state indicates windshield wipers are currently on. If {@link
     * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_SWITCH} is implemented, then it may be set
     * to any of the following modes: {@link WindshieldWipersSwitch#MIST}, {@code
     * INTERMITTENT_LEVEL_*}, {@code CONTINUOUS_LEVEL_*}, or {@link WindshieldWipersSwitch#AUTO}.
     */
    public static final int ON = 2;

    /**
     * Windshield wipers are in the service mode.
     */
    public static final int SERVICE = 3;

    private WindshieldWipersState() {}

    /**
     * Returns a user-friendly representation of a {@code WindshieldWipersState}.
     */
    @NonNull
    public static String toString(
            @WindshieldWipersStateInt int windshieldWipersState) {
        switch (windshieldWipersState) {
            case OTHER:
                return "OTHER";
            case OFF:
                return "OFF";
            case ON:
                return "ON";
            case SERVICE:
                return "SERVICE";
            default:
                return "0x" + Integer.toHexString(windshieldWipersState);
        }
    }

    /** @hide */
    @IntDef({OTHER, OFF, ON, SERVICE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface WindshieldWipersStateInt {}
}
