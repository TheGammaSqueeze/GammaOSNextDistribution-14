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

package com.android.icu.test.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.icu.testsharding.MainTestShard;
import android.icu.text.TimeZoneNames;
import android.icu.util.ULocale;

import com.android.icu.text.ExtendedTimeZoneNames;
import com.android.icu.text.ExtendedTimeZoneNames.Match;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ExtendedTimeZoneNamesTest {

    private static final String LONDON_TZID = "Europe/London";
    private static final String LOS_ANGELES_TZID = "America/Los_Angeles";
    private static final String VANCOUVER_TZID = "America/Vancouver";

    @Test
    public void testMatchName_preferCurrentTzId() {
        assertMatchName(ULocale.US, "PST", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, false);
        assertMatchName(ULocale.US, "PDT", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, true);
        assertMatchName(ULocale.US, "Pacific Standard Time", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, false);
        assertMatchName(ULocale.US, "Pacific Daylight Time", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, true);

        // current tz id is preferred even though the locale country is different.
        assertMatchName(ULocale.UK, "Pacific Standard Time", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, false);
        assertMatchName(ULocale.CANADA, "Pacific Standard Time", LOS_ANGELES_TZID,
                LOS_ANGELES_TZID, false);
        assertMatchName(ULocale.US, "Pacific Standard Time", VANCOUVER_TZID,
                VANCOUVER_TZID, false);
    }

    @Test
    public void testMatchName_shouldGuessTimeZoneForLocaleCounty() {
        // Guess the time zone from the locale country.
        assertMatchName(ULocale.CANADA, "Pacific Standard Time", LONDON_TZID,
                VANCOUVER_TZID, false);
    }

    @Test
    public void testMatchName_withStartOffset() {
        String ignored = "IGNORED_STR";
        assertMatchName(ULocale.US, ignored + "Pacific Standard Time", ignored.length(),
                LOS_ANGELES_TZID, LOS_ANGELES_TZID, false, "Pacific Standard Time".length());
        assertNullMatch(ULocale.US, ignored + "Pacific Standard Time", /*start=*/ 0,
                LOS_ANGELES_TZID);
    }

    @Test
    public void testMatchName_withDifferentLocales() {
        long date = 1609459200000L; // 1 January 2021 00:00:00 GMT+00:00

        // Test some locales
        for (ULocale locale : ULocale.getAvailableLocales()) {
            ExtendedTimeZoneNames extendedTimeZoneNames = ExtendedTimeZoneNames.getInstance(locale);
            String name = extendedTimeZoneNames.getTimeZoneNames().getDisplayName(LOS_ANGELES_TZID,
                    TimeZoneNames.NameType.LONG_STANDARD, date);

            // Skip this locale if the name is not available.
            if (name == null) {
                continue;
            }
            assertMatchName(locale, name, LOS_ANGELES_TZID, LOS_ANGELES_TZID, false);
        }
    }

    private static void assertMatchName(ULocale locale, String input, int start, String currentTzId,
            String expectedTzId, boolean isDst, int expectedNameLength) {
        String msg = "locale: " + locale + " input: " + input;
        ExtendedTimeZoneNames timeZoneNames = ExtendedTimeZoneNames.getInstance(locale);
        Match match = timeZoneNames.matchName(input, start, currentTzId);
        assertNotNull(msg, match);
        assertEquals(msg, expectedTzId, match.getTzId());
        assertEquals(msg, isDst, match.isDst());
        assertEquals(msg, expectedNameLength, match.getMatchLength());
    }

    private static void assertMatchName(ULocale locale, String input, String currentTzId,
            String expectedTzId, boolean isDst, int expectedNameLength) {
        assertMatchName(locale, input, 0, currentTzId, expectedTzId, isDst, expectedNameLength);
    }

    private static void assertMatchName(ULocale locale, String input,  String currentTzId,
            String expectedTzId, boolean isDst) {
        assertMatchName(locale, input, currentTzId, expectedTzId, isDst, input.length());
    }

    private static void assertNullMatch(ULocale locale, String input, int start,
            String currentTzId) {
        String msg = "locale: " + locale + " input: " + input;
        ExtendedTimeZoneNames timeZoneNames = ExtendedTimeZoneNames.getInstance(locale);
        assertNull(msg, timeZoneNames.matchName(input, start, currentTzId));
    }
}
