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
package com.android.tradefed.testtype.suite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SuiteTestFilter}.
 */
@RunWith(JUnit4.class)
public class SuiteTestFilterTest {

    @Test
    public void testParseFilter_module() {
        SuiteTestFilter filter = SuiteTestFilter.createFrom("x86_64 module");
        assertEquals("x86_64", filter.getAbi());
        assertEquals("module", filter.getBaseName());
        assertNull(filter.getTest());
        assertEquals("x86_64 module", filter.toString());
    }

    @Test
    public void testParseFilter() {
        SuiteTestFilter filter = SuiteTestFilter.createFrom("x86 module class#method");
        assertEquals("x86", filter.getAbi());
        assertEquals("module", filter.getBaseName());
        assertEquals("class#method", filter.getTest());
        assertEquals("x86 module class#method", filter.toString());
    }

    @Test
    public void testParseFilter_space() {
        SuiteTestFilter filter = SuiteTestFilter.createFrom("x86 module    class#method");
        assertEquals("x86", filter.getAbi());
        assertEquals("module", filter.getBaseName());
        assertEquals("class#method", filter.getTest());
    }

    @Test
    public void testParseFilter_moduleParam() {
        SuiteTestFilter filter = SuiteTestFilter.createFrom("x86_64 module[instant]");
        assertEquals("x86_64", filter.getAbi());
        assertEquals("module", filter.getBaseName());
        assertEquals("instant", filter.getParameterName());
        assertNull(filter.getTest());
        assertEquals("x86_64 module[instant]", filter.toString());
    }

    @Test
    public void testEquals() {
        SuiteTestFilter filter1 = SuiteTestFilter.createFrom("x86 module class#method");
        SuiteTestFilter filter2 = SuiteTestFilter.createFrom("x86 module class#method");
        assertEquals(filter1, filter2);
    }

    @Test
    public void testNotEquals() {
        SuiteTestFilter filter1 = SuiteTestFilter.createFrom("0 x86 module class#method");
        SuiteTestFilter filter2 = SuiteTestFilter.createFrom("x86 module class#method");
        assertNotEquals(filter1, filter2);
    }
}
