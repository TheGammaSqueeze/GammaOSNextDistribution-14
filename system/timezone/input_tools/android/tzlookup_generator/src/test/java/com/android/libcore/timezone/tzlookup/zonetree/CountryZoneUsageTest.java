/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.libcore.timezone.tzlookup.zonetree;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CountryZoneUsageTest {

    @Test
    public void testGetIsoCode() {
        CountryZoneUsage countryZoneUsage = new CountryZoneUsage("us");
        assertEquals("us", countryZoneUsage.getIsoCode());
    }

    @Test
    public void testNoEntry() {
        CountryZoneUsage countryZoneUsage = new CountryZoneUsage("us");
        String missingZoneId = "Europe/London";
        assertFalse(countryZoneUsage.hasEntry(missingZoneId));
        try {
            countryZoneUsage.getNotUsedAfterInstant(missingZoneId);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testWithEntry() {
        CountryZoneUsage countryZoneUsage = new CountryZoneUsage("us");

        String usZoneId1 = "America/Boise";
        countryZoneUsage.addEntry(usZoneId1, null /* notUsedAfterInstant */, null /* altTzId */);
        assertTrue(countryZoneUsage.hasEntry(usZoneId1));
        assertNull(countryZoneUsage.getNotUsedAfterInstant(usZoneId1));
        assertNull(null, countryZoneUsage.getNotUsedReplacementId(usZoneId1));

        String usZoneId2 = "America/Los_Angeles";
        Instant instant = Instant.ofEpochSecond(1234);
        countryZoneUsage.addEntry(usZoneId2, instant, usZoneId1 /* notUsedReplacementId */);
        assertTrue(countryZoneUsage.hasEntry(usZoneId2));
        assertEquals(instant, countryZoneUsage.getNotUsedAfterInstant(usZoneId2));
        assertEquals(usZoneId1, countryZoneUsage.getNotUsedReplacementId(usZoneId2));

        // Duplicate IDs are not allowed.
        try {
            countryZoneUsage.addEntry(usZoneId1, instant, "" /* notUsedReplacementId */);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
