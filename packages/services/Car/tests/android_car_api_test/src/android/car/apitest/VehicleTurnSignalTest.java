/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@SmallTest
@RunWith(Parameterized.class)
public final class VehicleTurnSignalTest {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public VehicleTurnSignalTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(
                new Object[][] {
                        {
                                android.car.hardware.property.VehicleTurnSignal.STATE_NONE,
                                android.hardware.automotive.vehicle.VehicleTurnSignal.NONE
                        },
                        {
                                android.car.hardware.property.VehicleTurnSignal.STATE_RIGHT,
                                android.hardware.automotive.vehicle.VehicleTurnSignal.RIGHT
                        },
                        {
                                android.car.hardware.property.VehicleTurnSignal.STATE_LEFT,
                                android.hardware.automotive.vehicle.VehicleTurnSignal.LEFT
                        }
                });
    }

    @Test
    public void testMatchWithVehicleHal() {
        assertThat(mJavaConstantValue).isEqualTo(mHalConstantValue);
    }
}
