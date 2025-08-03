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

package com.android.adservices.service.signals.evict;

import static org.junit.Assert.assertEquals;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBProtectedSignal;

import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class SignalSizeCalculatorTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCalculate() {
        assertEquals(10, SignalSizeCalculator.calculate(createProtectedSignal("Key1", "Value1")));

        assertEquals(
                15, SignalSizeCalculator.calculate(createProtectedSignal("Key2", "Value222222")));
    }

    @Test
    public void testCalculateList() {
        assertEquals(0, SignalSizeCalculator.calculate(List.of()));
        assertEquals(
                10,
                SignalSizeCalculator.calculate(List.of(createProtectedSignal("Key1", "Value1"))));
        assertEquals(
                25,
                SignalSizeCalculator.calculate(
                        List.of(
                                createProtectedSignal("Key1", "Value1"),
                                createProtectedSignal("Key2", "Value222222"))));
    }

    private DBProtectedSignal createProtectedSignal(String key, String value) {
        return DBProtectedSignal.builder()
                .setBuyer(CommonFixture.VALID_BUYER_1)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setId(1L)
                .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                .setKey(key.getBytes(StandardCharsets.UTF_8))
                .setValue(value.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
