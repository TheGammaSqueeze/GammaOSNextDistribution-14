/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.car.feature.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * List of different supported area types for vehicle properties.
 *
 * <p>The constants defined by {@code VehicleAreaType} indicate the different vehicle area types for
 * properties. A property is mapped to only one {@code VehicleAreaType}. Developers can retrieve the
 * {@code VehicleAreaType} using {@link android.car.hardware.CarPropertyConfig#getAreaType()}. Refer
 * to {@link android.car.hardware.CarPropertyConfig#getAreaIds()} for more information about area
 * IDs.
 */
public final class VehicleAreaType {
    /**
     * Used for global properties. A global property is a property that applies to the entire
     * vehicle and is not associated with a specific vehicle area type. For example, {@link
     * android.car.VehiclePropertyIds#FUEL_LEVEL} and {@link
     * android.car.VehiclePropertyIds#HVAC_STEERING_WHEEL_HEAT} are global properties. A global
     * property is always mapped to {@code VEHICLE_AREA_TYPE_GLOBAL}.
     */
    public static final int VEHICLE_AREA_TYPE_GLOBAL = 0;
    /** Area type is Window */
    public static final int VEHICLE_AREA_TYPE_WINDOW = 2;
    /** Area type is Seat */
    public static final int VEHICLE_AREA_TYPE_SEAT = 3;
    /** Area type is Door */
    public static final int VEHICLE_AREA_TYPE_DOOR = 4;
    /** Area type is Mirror */
    public static final int VEHICLE_AREA_TYPE_MIRROR = 5;
    /** Area type is Wheel */
    public static final int VEHICLE_AREA_TYPE_WHEEL = 6;
    /**
     * Area type Vendor where the meaning of each area ID is vendor defined. For a specific
     * property, each area ID within this area type is unique with no overlapping bits set.
     **/
    @FlaggedApi(Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public static final int VEHICLE_AREA_TYPE_VENDOR = 7;

    private VehicleAreaType() {}

    /** @hide */
    @IntDef(prefix = {"VEHICLE_AREA_TYPE_"}, value = {
        VEHICLE_AREA_TYPE_GLOBAL,
        VEHICLE_AREA_TYPE_WINDOW,
        VEHICLE_AREA_TYPE_SEAT,
        VEHICLE_AREA_TYPE_DOOR,
        VEHICLE_AREA_TYPE_MIRROR,
        VEHICLE_AREA_TYPE_WHEEL,
        VEHICLE_AREA_TYPE_VENDOR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehicleAreaTypeValue {}
}
