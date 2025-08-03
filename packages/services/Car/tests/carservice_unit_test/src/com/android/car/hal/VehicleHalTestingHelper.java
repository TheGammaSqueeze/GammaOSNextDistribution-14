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
package com.android.car.hal;

import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;

/**
 * Provides utilities for Vehicle HAL related tasks.
 */
public final class VehicleHalTestingHelper {

    /**
     * Creates an empty config for the given property.
     */
    public static HalPropConfig newConfig(int prop) {
        VehiclePropConfig config = new VehiclePropConfig();
        config.prop = prop;
        config.configString = new String();
        config.configArray = new int[0];
        return new AidlHalPropConfig(config);
    }

    /**
     * Creates a config for the given property that passes the
     * {@link com.android.car.hal.VehicleHal.VehicleHal#isPropertySubscribable(VehiclePropConfig)}
     * criteria.
     */
    public static HalPropConfig newSubscribableConfig(int prop) {
        VehiclePropConfig config = new VehiclePropConfig();
        config.prop = prop;
        config.configString = new String();
        config.configArray = new int[0];
        config.access = VehiclePropertyAccess.READ_WRITE;
        config.changeMode = VehiclePropertyChangeMode.ON_CHANGE;
        return new AidlHalPropConfig(config);
    }

    private VehicleHalTestingHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
