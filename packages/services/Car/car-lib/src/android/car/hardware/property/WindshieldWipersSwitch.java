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
 * Used to enumerate the current position of {@link
 * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_SWITCH}.
 *
 * <p>This list of enum values may be extended in future releases to include additional values.
 * @hide
 */
@SystemApi
public final class WindshieldWipersSwitch {
    /**
     * This value is used as an alternative for any {@code WindshieldWipersSwitch} value that is not
     * defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#WINDSHIELD_WIPERS_SWITCH} should not use this value. The
     * framework can use this field to remain backwards compatible if {@code WindshieldWipersSwitch}
     * is extended to include additional values.
     */
    public static final int OTHER = 0;

    /**
     * The windshield wipers switch is set to the off position.
     */
    public static final int OFF = 1;

    /**
     * {@code MIST} mode performs a single wipe, and then returns to the {@link #OFF} position.
     */
    public static final int MIST = 2;

    /**
     * {@code INTERMITTENT_LEVEL_*} modes performs intermittent wiping. As the level increases, the
     * intermittent time period decreases.
     */
    public static final int INTERMITTENT_LEVEL_1 = 3;

    /**
     * See {@link #INTERMITTENT_LEVEL_1}.
     */
    public static final int INTERMITTENT_LEVEL_2 = 4;

    /**
     * See {@link #INTERMITTENT_LEVEL_1}.
     */
    public static final int INTERMITTENT_LEVEL_3 = 5;

    /**
     * See {@link #INTERMITTENT_LEVEL_1}.
     */
    public static final int INTERMITTENT_LEVEL_4 = 6;

    /**
     * See {@link #INTERMITTENT_LEVEL_1}.
     */
    public static final int INTERMITTENT_LEVEL_5 = 7;

    /**
     * {@code CONTINUOUS_LEVEL_*} modes performs continuous wiping. As the level increases the speed
     * of the wiping increases as well.
     */
    public static final int CONTINUOUS_LEVEL_1 = 8;

    /**
     * See {@link #CONTINUOUS_LEVEL_1}.
     */
    public static final int CONTINUOUS_LEVEL_2 = 9;

    /**
     * See {@link #CONTINUOUS_LEVEL_1}.
     */
    public static final int CONTINUOUS_LEVEL_3 = 10;

    /**
     * See {@link #CONTINUOUS_LEVEL_1}.
     */
    public static final int CONTINUOUS_LEVEL_4 = 11;

    /**
     * See {@link #CONTINUOUS_LEVEL_1}.
     */
    public static final int CONTINUOUS_LEVEL_5 = 12;

    /**
     * {@code AUTO} allows the vehicle to decide the required wiping level based on the exterior
     * weather conditions.
     */
    public static final int AUTO = 13;

    /**
     * Windshield wipers are set to the service mode.
     */
    public static final int SERVICE = 14;

    private WindshieldWipersSwitch() {}

    /**
     * Returns a user-friendly representation of a {@code WindshieldWipersSwitch}.
     */
    @NonNull
    public static String toString(
            @WindshieldWipersSwitchInt int windshieldWipersSwitch) {
        switch (windshieldWipersSwitch) {
            case OTHER:
                return "OTHER";
            case OFF:
                return "OFF";
            case MIST:
                return "MIST";
            case INTERMITTENT_LEVEL_1:
                return "INTERMITTENT_LEVEL_1";
            case INTERMITTENT_LEVEL_2:
                return "INTERMITTENT_LEVEL_2";
            case INTERMITTENT_LEVEL_3:
                return "INTERMITTENT_LEVEL_3";
            case INTERMITTENT_LEVEL_4:
                return "INTERMITTENT_LEVEL_4";
            case INTERMITTENT_LEVEL_5:
                return "INTERMITTENT_LEVEL_5";
            case CONTINUOUS_LEVEL_1:
                return "CONTINUOUS_LEVEL_1";
            case CONTINUOUS_LEVEL_2:
                return "CONTINUOUS_LEVEL_2";
            case CONTINUOUS_LEVEL_3:
                return "CONTINUOUS_LEVEL_3";
            case CONTINUOUS_LEVEL_4:
                return "CONTINUOUS_LEVEL_4";
            case CONTINUOUS_LEVEL_5:
                return "CONTINUOUS_LEVEL_5";
            case AUTO:
                return "AUTO";
            case SERVICE:
                return "SERVICE";
            default:
                return "0x" + Integer.toHexString(windshieldWipersSwitch);
        }
    }

    /** @hide */
    @IntDef({
            OTHER,
            OFF,
            MIST,
            INTERMITTENT_LEVEL_1,
            INTERMITTENT_LEVEL_2,
            INTERMITTENT_LEVEL_3,
            INTERMITTENT_LEVEL_4,
            INTERMITTENT_LEVEL_5,
            CONTINUOUS_LEVEL_1,
            CONTINUOUS_LEVEL_2,
            CONTINUOUS_LEVEL_3,
            CONTINUOUS_LEVEL_4,
            CONTINUOUS_LEVEL_5,
            AUTO,
            SERVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WindshieldWipersSwitchInt {}
}
