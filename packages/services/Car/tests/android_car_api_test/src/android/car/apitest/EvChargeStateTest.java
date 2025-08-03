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

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@SmallTest
@RunWith(Parameterized.class)
public final class EvChargeStateTest extends CarLessApiTestBase {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public EvChargeStateTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(new Object[][]{
                {android.car.hardware.property.EvChargeState.STATE_UNKNOWN,
                        android.hardware.automotive.vehicle.EvChargeState.UNKNOWN},
                {android.car.hardware.property.EvChargeState.STATE_CHARGING,
                        android.hardware.automotive.vehicle.EvChargeState.CHARGING},
                {android.car.hardware.property.EvChargeState.STATE_FULLY_CHARGED,
                        android.hardware.automotive.vehicle.EvChargeState.FULLY_CHARGED},
                {android.car.hardware.property.EvChargeState.STATE_NOT_CHARGING,
                        android.hardware.automotive.vehicle.EvChargeState.NOT_CHARGING},
                {android.car.hardware.property.EvChargeState.STATE_ERROR,
                        android.hardware.automotive.vehicle.EvChargeState.ERROR}
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.EvChargeState#STATE_UNKNOWN",
            "android.car.hardware.property.EvChargeState#STATE_CHARGING",
            "android.car.hardware.property.EvChargeState#STATE_FULLY_CHARGED",
            "android.car.hardware.property.EvChargeState#STATE_NOT_CHARGING",
            "android.car.hardware.property.EvChargeState#STATE_ERROR"})
    public void testMatchWithVehicleHal() {
        assertThat(mJavaConstantValue).isEqualTo(mHalConstantValue);
    }
}
