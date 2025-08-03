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

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import com.android.car.internal.util.ConstantDebugUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to enumerate the current state of {@link
 * android.car.VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class DriverDrowsinessAttentionState {
    /**
     * This state is used as an alternative for any {@code DriverDrowsinessAttentionState} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * DriverDrowsinessAttentionState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * Karolinska Sleepiness Scale Rating 1 described as extermely alert.
     */
    public static final int KSS_RATING_1_EXTREMELY_ALERT = 1;

    /**
     * Karolinska Sleepiness Scale Rating 2 described as very alert.
     */
    public static final int KSS_RATING_2_VERY_ALERT = 2;

    /**
     * Karolinska Sleepiness Scale Rating 3 described as alert.
     */
    public static final int KSS_RATING_3_ALERT = 3;

    /**
     * Karolinska Sleepiness Scale Rating 4 described as rather alert.
     */
    public static final int KSS_RATING_4_RATHER_ALERT = 4;

    /**
     * Karolinska Sleepiness Scale Rating 5 described as neither alert nor sleepy.
     */
    public static final int KSS_RATING_5_NEITHER_ALERT_NOR_SLEEPY = 5;

    /**
     * Karolinska Sleepiness Scale Rating 6 described as some signs of sleepiness.
     */
    public static final int KSS_RATING_6_SOME_SLEEPINESS = 6;

    /**
     * Karolinska Sleepiness Scale Rating 7 described as sleepy with no effort to keep awake.
     */
    public static final int KSS_RATING_7_SLEEPY_NO_EFFORT = 7;

    /**
     * Karolinska Sleepiness Scale Rating 8 described as sleepy with some effort to keep awake.
     */
    public static final int KSS_RATING_8_SLEEPY_SOME_EFFORT = 8;

    /**
     * Karolinska Sleepiness Scale Rating 9 described as very sleepy, with great effort to keep
     * away, and fighthing sleep.
     */
    public static final int KSS_RATING_9_VERY_SLEEPY = 9;

    private DriverDrowsinessAttentionState() {}

    /**
     * Returns a user-friendly representation of a {@code DriverDrowsinessAttentionState}.
     */
    @NonNull
    public static String toString(
            @DriverDrowsinessAttentionStateInt int driverDrowsinessAttentionState) {
        String driverDrowsinessAttentionStateString = ConstantDebugUtils.toName(
                DriverDrowsinessAttentionState.class, driverDrowsinessAttentionState);
        return (driverDrowsinessAttentionStateString != null)
                ? driverDrowsinessAttentionStateString
                : "0x" + Integer.toHexString(driverDrowsinessAttentionState);
    }

    /** @hide */
    @IntDef({
        OTHER,
        KSS_RATING_1_EXTREMELY_ALERT,
        KSS_RATING_2_VERY_ALERT, KSS_RATING_3_ALERT,
        KSS_RATING_4_RATHER_ALERT,
        KSS_RATING_5_NEITHER_ALERT_NOR_SLEEPY,
        KSS_RATING_6_SOME_SLEEPINESS,
        KSS_RATING_7_SLEEPY_NO_EFFORT,
        KSS_RATING_8_SLEEPY_SOME_EFFORT,
        KSS_RATING_9_VERY_SLEEPY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DriverDrowsinessAttentionStateInt {}
}
