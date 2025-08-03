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
public class LaneCenteringAssistStateTest {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public LaneCenteringAssistStateTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(
                new Object[][] {
                        {
                                android.car.hardware.property.LaneCenteringAssistState.OTHER,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState.OTHER
                        },
                        {
                                android.car.hardware.property.LaneCenteringAssistState.ENABLED,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState.ENABLED
                        },
                        {
                                android.car.hardware.property.LaneCenteringAssistState
                                        .ACTIVATION_REQUESTED,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState
                                        .ACTIVATION_REQUESTED
                        },
                        {
                                android.car.hardware.property.LaneCenteringAssistState.ACTIVATED,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState
                                        .ACTIVATED
                        },
                        {
                                android.car.hardware.property.LaneCenteringAssistState
                                        .USER_OVERRIDE,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState
                                        .USER_OVERRIDE
                        },
                        {
                                android.car.hardware.property.LaneCenteringAssistState
                                        .FORCED_DEACTIVATION_WARNING,
                                android.hardware.automotive.vehicle.LaneCenteringAssistState
                                        .FORCED_DEACTIVATION_WARNING
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
