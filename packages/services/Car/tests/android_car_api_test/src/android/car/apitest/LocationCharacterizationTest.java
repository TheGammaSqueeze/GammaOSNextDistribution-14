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

package android.car.apitest;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@SmallTest
@RunWith(Parameterized.class)
public class LocationCharacterizationTest {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public LocationCharacterizationTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(
            new Object[][] {
                {
                    android.car.hardware.property.LocationCharacterization.PRIOR_LOCATIONS,
                    android.hardware.automotive.vehicle.LocationCharacterization.PRIOR_LOCATIONS
                },
                {
                    android.car.hardware.property.LocationCharacterization.GYROSCOPE_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization.GYROSCOPE_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.ACCELEROMETER_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization
                        .ACCELEROMETER_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.COMPASS_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization.COMPASS_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.WHEEL_SPEED_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization.WHEEL_SPEED_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.STEERING_ANGLE_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization
                        .STEERING_ANGLE_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.CAR_SPEED_FUSION,
                    android.hardware.automotive.vehicle.LocationCharacterization.CAR_SPEED_FUSION
                },
                {
                    android.car.hardware.property.LocationCharacterization.DEAD_RECKONED,
                    android.hardware.automotive.vehicle.LocationCharacterization.DEAD_RECKONED
                },
                {
                    android.car.hardware.property.LocationCharacterization.RAW_GNSS_ONLY,
                    android.hardware.automotive.vehicle.LocationCharacterization.RAW_GNSS_ONLY
                }
            });
    }

    @Test
    public void testMatchWithVehicleHal() {
        assertWithMessage("Java constant")
            .that(mJavaConstantValue)
            .isEqualTo(mHalConstantValue);
    }
}
