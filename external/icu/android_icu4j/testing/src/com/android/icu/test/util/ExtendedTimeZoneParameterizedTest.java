/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.icu.impl.Grego;
import android.icu.testsharding.MainTestShard;
import android.icu.text.DateFormat;
import android.icu.util.AnnualTimeZoneRule;
import android.icu.util.BasicTimeZone;
import android.icu.util.TimeZone;
import android.icu.util.TimeZoneRule;
import android.icu.util.TimeZoneTransition;
import android.icu.util.ULocale;

import com.android.icu.util.ExtendedTimeZone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Test the {@link ExtendedTimeZone}.
 *
 * This test verifies that the rules returned by that provider behave
 * equivalently to the ICU rules from which they are created.
 */
@MainTestShard
@RunWith(Parameterized.class)
public class ExtendedTimeZoneParameterizedTest {
    private static final DateFormat DATE_FORMATTER =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, ULocale.US);

    static {
        DATE_FORMATTER.setTimeZone(TimeZone.GMT_ZONE);
    }

    @Parameterized.Parameters(name = "{0}")
    public static String[] getZoneIds() {
        String[] zones = TimeZone.getAvailableIDs();
        assertNotEquals("no zones returned", 0, zones.length);
        return zones;
    }

    private final String zoneId;

    public ExtendedTimeZoneParameterizedTest(final String zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Verifies that ICU and java.time return the same transitions before and after a pre-selected
     * set of instants in time.
     */
    @Test
    public void testTransitionsNearInstants() {
        // An arbitrary set of instants at which to test the offsets in both implementations.
        Instant[] instants = new Instant[] {
                LocalDateTime.of(1900, Month.DECEMBER, 24, 12, 0).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(1940, Month.JULY, 1, 0, 0).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(1970, Month.JANUARY, 1, 2, 3).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(1980, Month.FEBRUARY, 4, 5, 6).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(1990, Month.MARCH, 7, 8, 9).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(2000, Month.APRIL, 10, 11, 12).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(2016, Month.MAY, 13, 14, 15).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(2020, Month.JUNE, 16, 17, 18).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(2100, Month.JULY, 19, 20, 21).toInstant(ZoneOffset.UTC),
                // yes, adding "now" makes the test time-dependent, but it also ensures that future
                // updates don't break on the then-current date.
                Instant.now()
        };
        // Coincidentally this test verifies that all zones can be converted to ZoneRules and
        // don't violate any of the assumptions.
        ZoneRules rules = getZoneRules();
        BasicTimeZone timeZone = (BasicTimeZone) TimeZone.getTimeZone(zoneId);

        int[] icuOffsets = new int[2];
        for (Instant instant : instants) {
            ZoneOffset offset = rules.getOffset(instant);
            Duration daylightSavings = rules.getDaylightSavings(instant);
            timeZone.getOffset(instant.toEpochMilli(), false, icuOffsets);

            assertEquals("total offset for " + zoneId + " at " + instant,
                    icuOffsets[1] + icuOffsets[0], offset.getTotalSeconds() * 1000);
            assertEquals("dst offset for " + zoneId + " at " + instant,
                    icuOffsets[1], daylightSavings.toMillis());

            ZoneOffsetTransition jtTrans;
            TimeZoneTransition icuTrans;

            jtTrans = rules.nextTransition(instant);
            icuTrans = timeZone.getNextTransition(instant.toEpochMilli(), false);
            while (isIcuOnlyTransition(icuTrans)) {
                icuTrans = timeZone.getNextTransition(icuTrans.getTime(), false);
            }
            assertEquivalent(icuTrans, jtTrans);

            jtTrans = rules.previousTransition(instant);
            icuTrans = timeZone.getPreviousTransition(instant.toEpochMilli(), false);
            // Find previous "real" transition.
            while (isIcuOnlyTransition(icuTrans)) {
                icuTrans = timeZone.getPreviousTransition(icuTrans.getTime(), false);
            }
            assertEquivalent(icuTrans, jtTrans);
        }
    }

    /**
     * Verifies that ICU and java.time rules return the same transitions between 1900 and 2100.
     */
    @Test
    public void testAllTransitions() {
        final Instant start = LocalDateTime.of(1900, Month.JANUARY, 1, 12, 0)
                .toInstant(ZoneOffset.UTC);
        // Many timezones have ongoing DST changes, so they would generate transitions endlessly.
        // Pick a far-future end date to stop comparing in that case.
        final Instant end = LocalDateTime.of(2100, Month.DECEMBER, 31, 12, 0)
                .toInstant(ZoneOffset.UTC);

        ZoneRules rules = getZoneRules();
        BasicTimeZone timeZone = (BasicTimeZone) TimeZone.getTimeZone(zoneId);

        Instant instant = start;
        while (instant.isBefore(end)) {
            ZoneOffsetTransition jtTrans;
            TimeZoneTransition icuTrans;

            jtTrans = rules.nextTransition(instant);
            icuTrans = timeZone.getNextTransition(instant.toEpochMilli(), false);
            while (isIcuOnlyTransition(icuTrans)) {
                icuTrans = timeZone.getNextTransition(icuTrans.getTime(), false);
            }
            assertEquivalent(icuTrans, jtTrans);
            if (jtTrans == null) {
                break;
            }
            instant = jtTrans.getInstant();
        }
    }

    private static final int[] INTERESTING_OFFSET_FROM_TRANSITION = new int[] {
            0, // at the transition
            Grego.MILLIS_PER_MINUTE * 30,
            - Grego.MILLIS_PER_MINUTE * 30,
            Grego.MILLIS_PER_HOUR,
            - Grego.MILLIS_PER_HOUR,
            Grego.MILLIS_PER_HOUR * 2,
            - Grego.MILLIS_PER_HOUR * 2,
            Grego.MILLIS_PER_DAY,
            - Grego.MILLIS_PER_DAY,
    };

    @Test
    public void testZoneRules_consistencyWithIcu4j() {
        ZoneRules zoneRules = getZoneRules();
        TimeZone icuTimeZone = getTimeZone();
        assumeTrue(icuTimeZone instanceof BasicTimeZone);

        BasicTimeZone timeZone = (BasicTimeZone) icuTimeZone;

        List<Long> interestingInstantInMills = new ArrayList<>(Arrays.asList(
                -3786825600000L,// 00:00:00 1 Jan 1850
                -3771187200000L,// 00:00:00 1 Jul 1850
                0L, // epoch time
                2145916800000L, // 00:00:00 1 Jan 2038
                2177452800000L, // 00:00:00 1 Jan 2039
                4102444800000L, // 00:00:00 1 Jan 2100
                4118083200000L // 00:00:00 1 Jul 2100
        ));

        addTransitionsUntilAnnualRule(timeZone, interestingInstantInMills);

        Collections.sort(interestingInstantInMills);
        // Put future time first because such test failure is more alarming than ones in the past
        Collections.reverse(interestingInstantInMills);

        for (long interestingInstant : interestingInstantInMills) {
            for (int offset : INTERESTING_OFFSET_FROM_TRANSITION) {
                long utcTimeInMillis = interestingInstant + offset;
                assertSameOffset(utcTimeInMillis, timeZone, zoneRules);
            }
        }
    }

    /**
     * Add transition time until annual rule is applied. Annual rule is recurring and there are
     * a lot of transitions when the annaul rule is in effect, and too exhaustive for testing.
     */
    private static void addTransitionsUntilAnnualRule(BasicTimeZone timeZone, List<Long> result) {
        long millis = Long.MIN_VALUE;
        TimeZoneTransition transition = null;
        do {
            transition = timeZone.getNextTransition(millis, false);
            if (transition != null) {
                millis = transition.getTime();
                result.add(millis);
            }
        } while(transition != null && !(transition.getFrom() instanceof AnnualTimeZoneRule));

    }

    private void assertSameOffset(long utcTimeInMillis, BasicTimeZone timeZone,
            ZoneRules zoneRules) {
        int[] offsets = new int[2];
        int icuDataTotalOffset = timeZone.getOffset(utcTimeInMillis);
        timeZone.getOffset(utcTimeInMillis, false, offsets);
        int icuDataStandardOffset = offsets[0];
        int icuInfoDataDstOffset = offsets[1];

        Instant instant = Instant.ofEpochMilli(utcTimeInMillis);
        ZoneOffset zoneRulesTotalOffset = zoneRules.getOffset(instant);
        ZoneOffset zoneRulesStandardOffset = zoneRules.getStandardOffset(instant);
        Duration zoneRulesDstOffset = zoneRules.getDaylightSavings(instant);

        String msg = "" + zoneId + " at UTC time: " + utcTimeInMillis + " (" +
                DATE_FORMATTER.format(new Date(utcTimeInMillis)) + ") ";
        assertEquals(msg + "Total offset", icuDataTotalOffset,
                zoneRulesTotalOffset.getTotalSeconds() * 1000);
        assertEquals(msg + "Standard offset", icuDataStandardOffset,
                zoneRulesStandardOffset.getTotalSeconds() * 1000);
        assertEquals(msg + "Daylight savings offset", icuInfoDataDstOffset,
                zoneRulesDstOffset.getSeconds() * 1000);
    }

    /**
     * Returns {@code true} iff this transition will only be returned by ICU code.
     * ICU reports "no-op" transitions where the raw offset and the dst savings
     * change by the same absolute value in opposite directions, java.time doesn't
     * return them, so find the next "real" transition.
     */
    private static boolean isIcuOnlyTransition(TimeZoneTransition transition) {
        if (transition == null) {
            return false;
        }
        return transition.getFrom().getRawOffset() + transition.getFrom().getDSTSavings()
                == transition.getTo().getRawOffset() + transition.getTo().getDSTSavings();
    }

    /**
     * Asserts that the ICU {@link TimeZoneTransition} is equivalent to the java.time {@link
     * ZoneOffsetTransition}.
     */
    private static void assertEquivalent(
            TimeZoneTransition icuTransition, ZoneOffsetTransition jtTransition) {
        if (icuTransition == null) {
            assertNull(jtTransition);
            return;
        }
        assertEquals("time of transition",
                Instant.ofEpochMilli(icuTransition.getTime()), jtTransition.getInstant());
        TimeZoneRule from = icuTransition.getFrom();
        TimeZoneRule to = icuTransition.getTo();
        assertEquals("offset before",
                (from.getDSTSavings() + from.getRawOffset()) / 1000,
                jtTransition.getOffsetBefore().getTotalSeconds());
        assertEquals("offset after",
                (to.getDSTSavings() + to.getRawOffset()) / 1000,
                jtTransition.getOffsetAfter().getTotalSeconds());
    }

    private ZoneRules getZoneRules() {
        return ExtendedTimeZone.getInstance(zoneId).createZoneRules();
    }

    private TimeZone getTimeZone() {
        return ExtendedTimeZone.getInstance(zoneId).getTimeZone();
    }
}
