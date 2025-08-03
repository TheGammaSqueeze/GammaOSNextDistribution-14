/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.car.Car;
import android.car.app.CarDisplayCompatManager;
import android.car.feature.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

public class CarDisplayCompatManagerTest extends CarApiTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private CarDisplayCompatManager mCarDisplayCompatManager;

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_COMPATIBILITY)
    public void testGetCarDisplayCompatManager() {
        mCarDisplayCompatManager = (CarDisplayCompatManager) getCar().getCarManager(
                Car.CAR_DISPLAY_COMPAT_SERVICE);
        assertThat(mCarDisplayCompatManager).isNotNull();
    }
}
