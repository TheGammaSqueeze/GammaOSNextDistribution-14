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

package com.android.car.pm;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.CarVersion;
import android.car.test.AbstractExpectableTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

// Life would be so much easier if JUnit allowed parameterized per method (not whole class)...
@RunWith(Parameterized.class)
public final class CarVersionParserParseMethodTest extends AbstractExpectableTestCase {

    private static final String PGK_NAME = "bond.james.bond";
    private static final int TARGET_SDK = 108;

    private final String mAttribute;
    private final int mExceptedMajor;
    private final int mExceptedMinor;

    public CarVersionParserParseMethodTest(String attribute, int expectedMajor,
            int expectedMinor) {
        mAttribute = attribute;
        mExceptedMajor = expectedMajor;
        mExceptedMinor = expectedMinor;
    }

    @Test
    public void testParse() {
        CarVersion actual = CarVersionParser.parse(PGK_NAME, mAttribute, TARGET_SDK);

        assertWithMessage("parse(%s)", mAttribute).that(actual).isNotNull();
        expectWithMessage("parse(%s).major", mAttribute).that(actual.getMajorVersion())
                .isEqualTo(mExceptedMajor);
        expectWithMessage("parse(%s).minor", mAttribute).that(actual.getMinorVersion())
                .isEqualTo(mExceptedMinor);
    }

    @Parameterized.Parameters
    public static Collection<?> parameters() {
        return Arrays.asList(
                // format: attribute, targetSdk, expectedMajor, expectedMinor
                new Object[][] {
                    // valid ones
                    { "42:666", 42, 666},
                    { "042:666", 42, 666},
                    { "42:0666", 42, 666},
                    { "42:0", 42, 0 },
                    { "42:00", 42, 0 },
                    { "42", 42, 0 },
                    { "042", 42, 0 },
                    // invalid ones
                    { null, TARGET_SDK, 0 },
                    { "", TARGET_SDK, 0 },
                    { " ", TARGET_SDK, 0 },
                    { "42: ", TARGET_SDK, 0 },
                    { "42:", TARGET_SDK, 0 },
                    { "42:666 ", TARGET_SDK, 0 },
                    { "42 :666", TARGET_SDK, 0 },
                    { "42 : 666", TARGET_SDK, 0 },
                    { "42: 666", TARGET_SDK, 0 },
                    { " 42:0", TARGET_SDK, 0 },
                    { "42:0 ", TARGET_SDK, 0 },
                    { " 42", TARGET_SDK, 0 },
                    { "42 ", TARGET_SDK, 0 },
                    { "42:six-six-six", TARGET_SDK, 0 },
                    { "42;666", TARGET_SDK, 0 },
                    { "42.666", TARGET_SDK, 0 },
                    { "42-666", TARGET_SDK, 0 },
                    { "42 666", TARGET_SDK, 0 },
                    { "forty-two", TARGET_SDK, 0 },
                });
    }
}
