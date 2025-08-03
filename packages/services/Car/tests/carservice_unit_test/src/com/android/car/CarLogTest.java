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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public final class CarLogTest {

    private static final String PREFIX = "CAR.";
    private final Class<?> mClass;
    private final boolean mPrefixExpected;

    public CarLogTest(Class<?> clazz, boolean prefixExpected) {
        this.mClass = clazz;
        this.mPrefixExpected = prefixExpected;
    }

    @Parameterized.Parameters
    public static List<Object[]> inputParameters() {
        return Arrays.asList(new Object[][] {
            {CarKeywordInTheStart.class, false},
            {KeywordInTheEndCar.class, false},
            {KeywordInTheMiddleCarService.class, false},
            {NoKeyword.class, true},
            {NotExactKeywordCarrier.class, true},
            {NotExactKeywordcar.class, true},
            {NotExactKeywordcarService.class, true}
        });
    }

    @Test
    public void test_forTagMethod() {
        assertThat(CarLog.tagFor(mClass))
                .isEqualTo((mPrefixExpected ? PREFIX : "") + mClass.getSimpleName());
    }

    class CarKeywordInTheStart {}
    class KeywordInTheEndCar {}
    class KeywordInTheMiddleCarService {}
    class NoKeyword {}
    class NotExactKeywordCarrier {}
    class NotExactKeywordcar {}
    class NotExactKeywordcarService {}
}
