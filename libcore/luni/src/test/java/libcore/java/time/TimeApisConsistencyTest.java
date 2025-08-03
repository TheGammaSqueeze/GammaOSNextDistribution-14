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

package libcore.java.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import dalvik.system.VMRuntime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import tests.support.PlatformVersions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * Tests that Android's java.time interpretation of TZDB rules matches with other available APIs:
 * bionic and java.util.TimeZone.
 *
 * On Android, java.time is implemented using ICU4J, bionic and java.util.TimeZone read transitions
 * from tzdata file. These tests ensure that they have consistent view on what offset was on a given
 * time zone at a given time.
 */
@RunWith(Parameterized.class)
public class TimeApisConsistencyTest {

    // Before U java.util.TimeZone could handle dates prior to the year 2038 only.
    private static final Instant PRE_U_JAVA_UTIL_TIME_ZONE_MAX_SUPPORTED
            = Instant.ofEpochSecond(2145916800); // 1 Jan 2038

    // The lower bound for testing.
    private static final LocalDateTime START_DATE;

    // The upper bound for testing.
    private static final LocalDateTime END_DATE;

    static {
        // time_t is platform dependent on Linux at the moment of writing. So we can't test dates
        // outside of the range allowed by 32-bit integer.
        if (VMRuntime.getRuntime().is64Bit()) {
            // Chosen because TZDB has no rules defined prior to this date.
            START_DATE = LocalDateTime.of(1800, 1, 1, 0, 0);
            // Chosen because it's considered to be sufficiently in the future that Android devices
            // won't be running. Any TZDB entries affecting this date are recurring so going beyond
            // shouldn't do anything unexpected.
            END_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);
        } else {
            // Date close to minimal value allowed by 32-bit integer.
            START_DATE = LocalDateTime.of(1902, 1, 1, 0, 0);
            // Date close to maximal value allowed by 32-bit integer.
            END_DATE = LocalDateTime.of(2038, 1, 1, 0, 0);
        }
    }

    // Android's DateTimeFormatter implementation formats time zone as offset only for historic
    // dates on certain time zones, while bionic takes abbreviation from TZif file.
    // So time zone is not included here because we know it can differ. Equality of numeric local
    // date/time components implies that used offsets were the same in libcore and bionic.
    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd MM yyyy HH:mm:ss").withLocale(Locale.US);

    private static final Set<Duration> INTERESTING_OFFSETS =
            Set.of(Duration.ZERO, Duration.ofMinutes(30), Duration.ofMinutes(-30),
                    Duration.ofHours(1), Duration.ofHours(-1),
                    Duration.ofHours(2), Duration.ofHours(-2),
                    Duration.ofDays(1), Duration.ofDays(-1));

    @Parameters(name = "{0}")
    public static String[] getZoneIds() {
        // We use java.util.TimeZone.getAvailableIDs() since that uses the IDs from TZDB, not the
        // expanded set recognized by ICU.
        String[] zones = TimeZone.getAvailableIDs();
        assertNotEquals("no zones returned", 0, zones.length);
        return zones;
    }

    static {
        System.loadLibrary("javacoretests");
    }

    private final String timeZoneId;
    private final ZoneRules zoneRules;

    public TimeApisConsistencyTest(String timeZoneId) {
        this.timeZoneId = timeZoneId;
        this.zoneRules = ZoneId.of(timeZoneId).getRules();
    }

    /**
     * Compares bionic's instant formatting output (localtime()) with java.time's. If it fails,
     * bionic's data and ICU's data have not been updated at the same time, or ICU and bionic's
     * understanding of the same data differs.
     */
    @Test
    public void compareBionicFormattingWithJavaTime() {
        runChecksAroundInterestingTimestamps(this::compareWithBionic);
    }

    @Test
    public void compareJavaUtilTimeZoneWthJavaTime() {
        runChecksAroundInterestingTimestamps(this::compareWithJavaUtilTimeZone);
    }

    private void runChecksAroundInterestingTimestamps(Consumer<Instant> checkFunction) {
        Instant start = START_DATE.atOffset(ZoneOffset.UTC).toInstant();
        Instant stop = END_DATE.atOffset(ZoneOffset.UTC).toInstant();

        ZoneOffsetTransition zoneOffsetTransition = zoneRules.nextTransition(start);

        while (start.isBefore(stop)) {
            checkFunction.accept(start);

            if (zoneOffsetTransition == null) {
                break;
            }

            start = zoneOffsetTransition.getInstant();
            zoneOffsetTransition = zoneRules.nextTransition(start);
        }
    }

    private void compareWithBionic(Instant timestamp) {
        for (Duration interestingOffset : INTERESTING_OFFSETS) {
            Instant instantToCheck = timestamp.plus(interestingOffset);
            String bionicResult = formatWithBionic(instantToCheck, timeZoneId);
            String javaResult = instantToCheck.atZone(ZoneId.of(timeZoneId)).format(formatter);

            String errorMessage = "Failed to format " + timestamp + " at " + timeZoneId
                    + " with offset=" + interestingOffset;
            assertEquals(errorMessage, javaResult, bionicResult);
        }
    }

    private static String formatWithBionic(Instant instant, String timeZoneId) {
        return formatWithBionic(instant.getEpochSecond(), timeZoneId);
    }

    private static native String formatWithBionic(long epochSeconds, String timeZoneId);

    /**
     * TZif format does not store enough information to reliably tell what was DST savings offset
     * even when the offset was not ambiguous at the time. This usually happens around dates when
     * time zone's standard offset was changed.
     *
     * <p> Heuristics used to determine DST offset do not always work, so this test compares total
     * (standard offset + DST savings) offset only.
     */
    private void compareWithJavaUtilTimeZone(Instant timestamp) {
        if (!PlatformVersions.isAtLeastU()) {
            if (timestamp.isAfter(PRE_U_JAVA_UTIL_TIME_ZONE_MAX_SUPPORTED)) {
                return;
            }
        }
        for (Duration interestingOffset : INTERESTING_OFFSETS) {
            Instant instantToCheck = timestamp.plus(interestingOffset);

            int javaTimeOffset = zoneRules.getOffset(instantToCheck).getTotalSeconds() * 1_000;

            int javaUtilOffset = java.util.TimeZone.getTimeZone(timeZoneId)
                    .getOffset(instantToCheck.toEpochMilli());

            String errorMessage = "java.time and java.util.TimeZone got different offsets for "
                    + timestamp + " at " + timeZoneId + " with offset=" + interestingOffset;
            assertEquals(errorMessage, javaTimeOffset, javaUtilOffset);
        }
    }
}
