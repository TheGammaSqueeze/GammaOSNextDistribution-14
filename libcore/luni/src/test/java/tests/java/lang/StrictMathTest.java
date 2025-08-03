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
package tests.java.lang;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * These tests are added primariy for test coverage. Actual implementation
 * is based on {@link java.lang.Math#absExact(int}) and
 * {@link java.lang.Math#absExact(long)}, and those methods are covered by
 * ojluni/src/test/java/lang/Math/AbsTests.java
 */
@RunWith(JUnit4.class)
public class StrictMathTest {

    @Test
    public void test_absExact_int() {
        assertEquals(0, StrictMath.absExact(0));
        assertEquals(5, StrictMath.absExact(5));
        assertEquals(5, StrictMath.absExact(-5));
        assertEquals(Integer.MAX_VALUE, StrictMath.absExact(-Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, StrictMath.absExact(Integer.MAX_VALUE));
    }

    @Test
    public void test_absExact_long() {
        assertEquals(0L, StrictMath.absExact(0L));
        assertEquals(5L, StrictMath.absExact(5L));
        assertEquals(5L, StrictMath.absExact(-5L));
        assertEquals(Long.MAX_VALUE, StrictMath.absExact(-Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, StrictMath.absExact(Long.MAX_VALUE));
    }

}
