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
 * Used to enumerate the state of {@link
 * android.car.VehiclePropertyIds#LOW_SPEED_COLLISION_WARNING_STATE}.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class LowSpeedCollisionWarningState {

    /**
     * This state is used as an alternative for any {@code LowSpeedCollisionWarningState} value that
     * is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#LOW_SPEED_COLLISION_WARNING_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * LowSpeedCollisionWarningState} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * Low Speed Collision Warning is enabled and monitoring for potential collision, but no
     * potential collision is detected.
     */
    public static final int NO_WARNING = 1;
    /**
     * Low Speed Collision Warning is enabled, detects a potential collision, and is actively
     * warning the user.
     */
    public static final int WARNING = 2;

    private LowSpeedCollisionWarningState() {}

    /**
     * Returns a user-friendly representation of {@code LowSpeedCollisionWarningState}.
     */
    @NonNull
    public static String toString(
            @LowSpeedCollisionWarningStateInt int lowSpeedCollisionWarningState) {
        String lowSpeedCollisionWarningStateString = ConstantDebugUtils.toName(
                LowSpeedCollisionWarningState.class, lowSpeedCollisionWarningState);
        return (lowSpeedCollisionWarningStateString != null)
                ? lowSpeedCollisionWarningStateString
                : "0x" + Integer.toHexString(lowSpeedCollisionWarningState);
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LowSpeedCollisionWarningStateInt {}
}
