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

package com.android.icu.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.icu.testsharding.MainTestShard;
import android.icu.util.TimeZone;

import com.android.icu.util.ExtendedTimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ExtendedTimeZoneTest {

    private TimeZone originalDefaultTimeZone;

    @Before
    public void setUp() {
        originalDefaultTimeZone = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    /**
     * Verify that ICU4J synchronizes the default time zone from {@link java.util.TimeZone} once
     * {@link ExtendedTimeZone#clearDefaultTimeZone()} is called.
     */
    @Test
    public void testClearDefaultTimeZone_synchronizeTimeZone_equals() {
        TimeZone.setDefault(TimeZone.GMT_ZONE);
        // Verify libcore's default time zone is also set to TimeZone.GMT_ZONE.
        assertLibcoreDefaultTimeZoneUtc();

        TimeZone newYorkTz = TimeZone.getTimeZone("America/New_York");
        TimeZone.setICUDefault(newYorkTz);
        // Verify that libcore is not affected by TimeZone.setICUDefault.
        assertLibcoreDefaultTimeZoneUtc();
        assertEquals(newYorkTz, TimeZone.getDefault());

        ExtendedTimeZone.clearDefaultTimeZone();
        assertLibcoreDefaultTimeZoneUtc();
        assertEquals(TimeZone.GMT_ZONE.getID(), TimeZone.getDefault().getID());
    }

    private static void assertLibcoreDefaultTimeZoneUtc() {
        java.util.TimeZone defaultTz = java.util.TimeZone.getDefault();
        assertEquals(TimeZone.GMT_ZONE.getID(), defaultTz.getID());
    }
}
