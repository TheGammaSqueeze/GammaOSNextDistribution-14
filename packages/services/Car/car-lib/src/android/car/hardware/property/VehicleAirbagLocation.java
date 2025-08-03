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
 * Used to enumerate the various airbag locations per seat.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class VehicleAirbagLocation {
    /**
     * This state is used as an alternative for any {@code VehicleAirbagLocation} value that is not
     * defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#SEAT_AIRBAGS_DEPLOYED} should not use this state. The
     * framework can use this field to remain backwards compatible if {@code VehicleAirbagLocation}
     * is extended to include additional states.
     */
    public static final int OTHER = 0x01;
    /**
     * Front airbags. This enum is for the airbags that protect the seated person from the front,
     * particularly the seated person's torso.
     */
    public static final int FRONT = 0x02;
    /**
     * Knee airbags. This enum is for the airbags that protect the seated person's knees.
     */
    public static final int KNEE = 0x04;
    /**
     * Left side airbags. This enum is for the side airbags that protect the left side of the seated
     * person.
     */
    public static final int LEFT_SIDE = 0x08;
    /**
     * Right side airbags. This enum is for the side airbags that protect the right side of the
     * seated person.
     */
    public static final int RIGHT_SIDE = 0x10;
    /**
     * Curtain airbags. This enum is for the airbags lined above the windows of the vehicle.
     */
    public static final int CURTAIN = 0x20;

    private VehicleAirbagLocation() {}

    /**
     * Returns a user-friendly representation of {@code VehicleAirbagLocation}.
     */
    @NonNull
    public static String toString(@VehicleAirbagLocationInt int vehicleAirbagLocation) {
        String vehicleAirbagLocationString = ConstantDebugUtils.toName(
                VehicleAirbagLocation.class, vehicleAirbagLocation);
        return (vehicleAirbagLocationString != null)
                ? vehicleAirbagLocationString
                : "0x" + Integer.toHexString(vehicleAirbagLocation);
    }

    /** @hide */
    @IntDef({OTHER, FRONT, KNEE, LEFT_SIDE, RIGHT_SIDE, CURTAIN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleAirbagLocationInt {}
}
