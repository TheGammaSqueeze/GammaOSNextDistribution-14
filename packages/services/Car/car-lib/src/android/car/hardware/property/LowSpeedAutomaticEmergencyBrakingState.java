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
 * android.car.VehiclePropertyIds#LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE}.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class LowSpeedAutomaticEmergencyBrakingState {

    /**
     * This state is used as an alternative to any {@code LowSpeedAutomaticEmergencyBrakingState}
     * value that is not defined in the platform. Ideally, implementations of
     * {@link android.car.VehiclePropertyIds#LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE} should not
     * use this state. The framework can use this field to remain backwards compatible if
     * {@code LowSpeedAutomaticEmergencyBrakingState} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * Low Speed Automatic Emergency Braking is enabled and monitoring safety, but brakes are not
     * activated.
     */
    public static final int ENABLED = 1;
    /**
     * Low Speed Automatic Emergency Braking is enabled and currently has the brakes applied for the
     * vehicle.
     */
    public static final int ACTIVATED = 2;
    /**
     * Many Low Speed Automatic Emergency Braking implementations allow the driver to override Low
     * Speed Automatic Emergency Braking. This means that the car has determined it should brake,
     * but a user decides to take over and do something else. This is often done for safety reasons
     * and to ensure that the driver can always take control of the vehicle. This state should be
     * set when the user is actively overriding the low speed automatic emergency braking system.
     */
    public static final int USER_OVERRIDE = 3;

    private LowSpeedAutomaticEmergencyBrakingState() {}

    /**
     * Returns a user-friendly representation of {@code LowSpeedAutomaticEmergencyBrakingState}.
     */
    @NonNull
    public static String toString(
            @LowSpeedAutomaticEmergencyBrakingStateInt int lowSpeedAutomaticEmergencyBrakingState) {
        String lowSpeedAutomaticEmergencyBrakingStateString = ConstantDebugUtils.toName(
                LowSpeedAutomaticEmergencyBrakingState.class,
                lowSpeedAutomaticEmergencyBrakingState);
        return (lowSpeedAutomaticEmergencyBrakingStateString != null)
                ? lowSpeedAutomaticEmergencyBrakingStateString
                : "0x" + Integer.toHexString(lowSpeedAutomaticEmergencyBrakingState);
    }

    /** @hide */
    @IntDef({OTHER, ENABLED, ACTIVATED, USER_OVERRIDE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LowSpeedAutomaticEmergencyBrakingStateInt {}
}
