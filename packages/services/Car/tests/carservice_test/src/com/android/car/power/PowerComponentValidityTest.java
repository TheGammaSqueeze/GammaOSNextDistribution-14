/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.power;

import static com.google.common.truth.Truth.assertThat;

import android.util.SparseArray;

import org.junit.Test;

import java.lang.reflect.Field;

public final class PowerComponentValidityTest {
    /**
     * Tests if {@link android.car.hardware.power#PowerComponent} and
     * {@link android.frameworks.automotive.powerpolicy#PowerComponent} have the same power
     * component definition.
     */
    @Test
    public void testSamePowerComponent() throws Exception {
        SparseArray<String> fieldMap = new SparseArray<>();
        Field[] carFields = android.car.hardware.power.PowerComponent.class.getFields();
        Field[] frameworksFields =
            android.frameworks.automotive.powerpolicy.PowerComponent.class.getFields();

        for (int i = 0; i < carFields.length; i++) {
            fieldMap.put(carFields[i].getInt(null), carFields[i].getName());
        }

        assertThat(carFields.length).isEqualTo(frameworksFields.length);
        for (int i = 0; i < frameworksFields.length; i++) {
            int frameworksValue = frameworksFields[i].getInt(null);
            String frameworksName = frameworksFields[i].getName();
            String carName = fieldMap.get(frameworksValue);
            assertThat(carName).isNotNull();
            assertThat(carName).isEqualTo(frameworksName);
        }
    }
}
