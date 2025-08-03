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

package com.android.storage.util;

import static com.android.storage.testing.MoreAsserts.assertThrows;

import org.junit.Test;

public class ConditionsTest {

    @Test
    public void checkStateInRange() {
        int minIncl = 2;
        int maxIncl = 4;
        int[] validValues = { minIncl, 3, maxIncl };
        int[] invalidValues = { Integer.MIN_VALUE, -1, 1, 5, Integer.MAX_VALUE };

        for (int validValue : validValues) {
            Conditions.checkStateInRange("foo", validValue, "min", minIncl, "max", maxIncl);
        }
        for (int invalidValue : invalidValues) {
            assertThrows(IllegalStateException.class,
                    () -> Conditions.checkStateInRange("foo", invalidValue, "min", minIncl, "max",
                                maxIncl));
        }
    }

    @Test
    public void checkArgInRange() {
        int minIncl = 2;
        int maxIncl = 4;
        int[] validValues = { minIncl, 3, maxIncl };
        int[] invalidValues = { Integer.MIN_VALUE, -1, 1, 5, Integer.MAX_VALUE };

        for (int validValue : validValues) {
            Conditions.checkArgInRange("foo", validValue, "min", minIncl, "max", maxIncl);
            Conditions.checkArgInRange("foo", validValue, minIncl, maxIncl);
        }
        for (int invalidValue : invalidValues) {
            assertThrows(IllegalArgumentException.class,
                    () -> Conditions.checkArgInRange("foo", invalidValue, "min", minIncl, "max",
                                maxIncl));
            assertThrows(IllegalArgumentException.class,
                    () -> Conditions.checkArgInRange("foo", invalidValue, minIncl, maxIncl));
        }
    }
}
