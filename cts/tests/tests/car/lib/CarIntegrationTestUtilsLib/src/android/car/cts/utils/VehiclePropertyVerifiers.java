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

package android.car.cts.utils;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.LocationCharacterization;
import android.os.Build;

/**
 * Provides a list of verifiers for vehicle properties.
 */
public class VehiclePropertyVerifiers {

    private VehiclePropertyVerifiers() {
        throw new UnsupportedOperationException("Should only be used as a static class");
    }

    private static final int LOCATION_CHARACTERIZATION_VALID_VALUES_MASK =
            LocationCharacterization.PRIOR_LOCATIONS
            | LocationCharacterization.GYROSCOPE_FUSION
            | LocationCharacterization.ACCELEROMETER_FUSION
            | LocationCharacterization.COMPASS_FUSION
            | LocationCharacterization.WHEEL_SPEED_FUSION
            | LocationCharacterization.STEERING_ANGLE_FUSION
            | LocationCharacterization.CAR_SPEED_FUSION
            | LocationCharacterization.DEAD_RECKONED
            | LocationCharacterization.RAW_GNSS_ONLY;

    /**
     * Gets the verifer for LOCATION_CHARACTERIZATION.
     */
    public static VehiclePropertyVerifier<Integer> getLocationCharacterizationVerifier(
            CarPropertyManager carPropertyManager) {
        return getLocationCharacterizationVerifier(
            carPropertyManager,
            VehiclePropertyIds.LOCATION_CHARACTERIZATION,
            ACCESS_FINE_LOCATION);
    }

    /**
     * Gets the verifer for backported LOCATION_CHARACTERIZATION.
     *
     * @param carPropertyManager the car property manager instance.
     * @param propertyId the backported property ID.
     * @param readPermission the permission for the backported property.
     */
    public static VehiclePropertyVerifier<Integer> getLocationCharacterizationVerifier(
            CarPropertyManager carPropertyManager,
            int propertyId, String readPermission) {
        var builder = getLocationCharacterizationVerifierBuilder(
                carPropertyManager, propertyId, readPermission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.requireProperty();
        }
        return builder.build();
    }

    private static VehiclePropertyVerifier.Builder<Integer>
            getLocationCharacterizationVerifierBuilder(
                    CarPropertyManager carPropertyManager,
                    int locPropertyId, String readPermission) {
        return VehiclePropertyVerifier.newBuilder(
                        locPropertyId,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, carPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, value) -> {
                            boolean deadReckonedIsSet = (value
                                    & LocationCharacterization.DEAD_RECKONED)
                                    == LocationCharacterization.DEAD_RECKONED;
                            boolean rawGnssOnlyIsSet = (value
                                    & LocationCharacterization.RAW_GNSS_ONLY)
                                    == LocationCharacterization.RAW_GNSS_ONLY;
                            assertWithMessage("LOCATION_CHARACTERIZATION must not be 0 "
                                    + "Found value: " + value)
                                    .that(value)
                                    .isNotEqualTo(0);
                            assertWithMessage("LOCATION_CHARACTERIZATION must not have any bits "
                                    + "set outside of the bit flags defined in "
                                    + "LocationCharacterization. Found value: " + value)
                                    .that(value & LOCATION_CHARACTERIZATION_VALID_VALUES_MASK)
                                    .isEqualTo(value);
                            assertWithMessage("LOCATION_CHARACTERIZATION must have one of "
                                    + "DEAD_RECKONED or RAW_GNSS_ONLY set. They both cannot be set "
                                    + "either. Found value: " + value)
                                    .that(deadReckonedIsSet ^ rawGnssOnlyIsSet)
                                    .isTrue();
                        })
                .addReadPermission(readPermission);
    }

}
