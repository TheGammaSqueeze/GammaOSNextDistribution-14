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

package com.android.car.test.power;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.power.CarPowerPolicy;

import java.util.Arrays;

public final class CarPowerPolicyUtil {

    public static void assertPolicyIdentical(CarPowerPolicy first, CarPowerPolicy second) {
        assertThat(first.getPolicyId()).isEqualTo(second.getPolicyId());
        assertComponentsIdentical(first.getEnabledComponents(), second.getEnabledComponents());
        assertComponentsIdentical(first.getDisabledComponents(), second.getDisabledComponents());
    }

    private static void assertComponentsIdentical(int[] first, int[] second) {
        Integer[] boxedArr = Arrays.stream(second).boxed().toArray(Integer[]::new);
        assertThat(first).asList().containsExactlyElementsIn(boxedArr);
    }
}
