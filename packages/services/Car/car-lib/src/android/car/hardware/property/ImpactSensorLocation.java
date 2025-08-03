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
 * Used to enumerate the various impact sensor locations on the car.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class ImpactSensorLocation {
    /**
     * Other impact sensor location. Ideally this should never be used.
     */
    public static final int OTHER = 0x01;
    /**
     * Frontal impact sensor. Used for the sensor that detects head-on impact.
     */
    public static final int FRONT = 0x02;
    /**
     * Front-left door side impact sensor. Used for the sensor that detects collisions from the
     * side, in particular on the front-left door.
     */
    public static final int FRONT_LEFT_DOOR_SIDE = 0x04;
    /**
     * Front-right door side impact sensor. Used for the sensor that detects collisions from the
     * side, in particular on the front-right door.
     */
    public static final int FRONT_RIGHT_DOOR_SIDE = 0x08;
    /**
     * Rear-left door side impact sensor. Used for the sensor that detects collisions from the
     * side, in particular on the rear-left door.
     */
    public static final int REAR_LEFT_DOOR_SIDE = 0x10;
    /**
     * Rear-right door side impact sensor. Used for the sensor that detects collisions from the
     * side, in particular on the rear-right door.
     */
    public static final int REAR_RIGHT_DOOR_SIDE = 0x20;
    /**
     * Rear impact sensor. Used for the sensor that detects collisions from the rear.
     */
    public static final int REAR = 0x40;

    private ImpactSensorLocation() {}

    /**
     * Returns a user-friendly representation of {@code ImpactSensorLocation}.
     */
    @NonNull
    public static String toString(@ImpactSensorLocationInt int impactSensorLocation) {
        String impactSensorLocationString = ConstantDebugUtils.toName(
                ImpactSensorLocation.class, impactSensorLocation);
        return (impactSensorLocationString != null)
                ? impactSensorLocationString
                : "0x" + Integer.toHexString(impactSensorLocation);
    }

    /** @hide */
    @IntDef({OTHER, FRONT, FRONT_LEFT_DOOR_SIDE, FRONT_RIGHT_DOOR_SIDE, REAR_LEFT_DOOR_SIDE,
            REAR_RIGHT_DOOR_SIDE, REAR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImpactSensorLocationInt {}
}
