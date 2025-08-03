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
package com.android.adservices.common;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.RequiredRange;
import com.android.adservices.common.AndroidSdk.Range;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class RequiredRangeTest {

    private static final String REASON = "To get to the other side.";
    private static final String NULL_REASON = null;

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testToString() {
        var range = Range.forAnyLevel();
        var requiredRange = new RequiredRange(range, REASON);

        String toString = requiredRange.toString();

        expect.that(toString).contains(range.toString());
        expect.that(toString).contains(REASON);
    }

    @Test
    public void testEqualsHashCode() {
        var range1 = Range.forAnyLevel();
        var range2 = Range.forExactly(42);

        expectEquals(new RequiredRange(range1, REASON), new RequiredRange(range1, REASON));
        expectEquals(new RequiredRange(range1, REASON), new RequiredRange(range1, NULL_REASON));

        expectNotEquals(new RequiredRange(range1, REASON), new RequiredRange(range2, REASON));
        expectNotEquals(new RequiredRange(range1, REASON), new RequiredRange(range2, NULL_REASON));
    }

    private void expectEquals(RequiredRange requiredRange1, RequiredRange requiredRange2) {
        expect.withMessage("equals()").that(requiredRange1).isEqualTo(requiredRange2);
        expect.withMessage("equals()").that(requiredRange2).isEqualTo(requiredRange1);

        expect.withMessage("hashcode()")
                .that(requiredRange1.hashCode())
                .isEqualTo(requiredRange2.hashCode());
    }

    private void expectNotEquals(RequiredRange requiredRange1, RequiredRange requiredRange2) {
        expect.withMessage("equals()").that(requiredRange1).isNotEqualTo(requiredRange2);
        expect.withMessage("equals()").that(requiredRange2).isNotEqualTo(requiredRange1);

        expect.withMessage("hashcode()")
                .that(requiredRange1.hashCode())
                .isNotEqualTo(requiredRange2.hashCode());
    }
}
