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

package com.android.adservices.service.signals;

import static org.junit.Assert.assertEquals;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class ProtectedSignalTest {

    private static final String VALUE = "A5";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateProtectedSignal() {
        ProtectedSignal signal =
                ProtectedSignal.builder()
                        .setBase64EncodedValue(VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        assertEquals(VALUE, signal.getBase64EncodedValue());
        assertEquals(CommonFixture.FIXED_NOW, signal.getCreationTime());
        assertEquals(CommonFixture.TEST_PACKAGE_NAME_1, signal.getPackageName());
    }
}
