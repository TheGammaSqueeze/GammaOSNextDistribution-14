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

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@SmallTest
@RunWith(Parameterized.class)
public class LowSpeedAutomaticEmergencyBrakingStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public LowSpeedAutomaticEmergencyBrakingStateTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(
                new Object[][] {
                        {
                                android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState
                                        .OTHER,
                                android.hardware.automotive.vehicle
                                        .LowSpeedAutomaticEmergencyBrakingState.OTHER
                        },
                        {
                                android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState
                                        .ENABLED,
                                android.hardware.automotive.vehicle
                                        .LowSpeedAutomaticEmergencyBrakingState.ENABLED
                        },
                        {
                                android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState
                                        .ACTIVATED,
                                android.hardware.automotive.vehicle
                                        .LowSpeedAutomaticEmergencyBrakingState.ACTIVATED
                        },
                        {
                                android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState
                                        .USER_OVERRIDE,
                                android.hardware.automotive.vehicle
                                        .LowSpeedAutomaticEmergencyBrakingState.USER_OVERRIDE
                        },
                });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testMatchWithVehicleHal() {
        assertWithMessage("Java constant")
                .that(mJavaConstantValue)
                .isEqualTo(mHalConstantValue);
    }
}
