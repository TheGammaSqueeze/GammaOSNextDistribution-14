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
package com.android.tradefed.referencetests;

import static org.junit.Assert.assertEquals;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Simple class with an ignored and an assumption failure
 *
 * <p>This is so that we can exercise some of the very basic code paths without too many confounding
 * factors
 */
@RunWith(JUnit4.class)
public class PassIgnoreAssumeTest {
    @Test
    public void testPass() {
        assertEquals(2 + 2, 4);
    }

    @Ignore
    @Test
    public void testDoesNotRun() {}

    @Test
    public void testAssume() {
        Assume.assumeTrue(false);
    }
}
