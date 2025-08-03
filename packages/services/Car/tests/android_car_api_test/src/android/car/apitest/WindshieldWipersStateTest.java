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
public class WindshieldWipersStateTest {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public WindshieldWipersStateTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(
                new Object[][] {
                        {
                                android.car.hardware.property.WindshieldWipersState.OTHER,
                                android.hardware.automotive.vehicle.WindshieldWipersState.OTHER
                        },
                        {
                                android.car.hardware.property.WindshieldWipersState.OFF,
                                android.hardware.automotive.vehicle.WindshieldWipersState.OFF
                        },
                        {
                                android.car.hardware.property.WindshieldWipersState.ON,
                                android.hardware.automotive.vehicle.WindshieldWipersState.ON
                        },
                        {
                                android.car.hardware.property.WindshieldWipersState.SERVICE,
                                android.hardware.automotive.vehicle.WindshieldWipersState.SERVICE
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
