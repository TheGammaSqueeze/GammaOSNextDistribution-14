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

package com.android.libcore.timezone.tzlookup;

import static java.util.stream.Collectors.toSet;

import com.ibm.icu.util.TimeZone;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * <p>A class that provides access to known IANA TZDB time zone IDs and their offset / DST rules.
 * This class sprinkles in some metadata like "which time zones are actually used in a region?",
 * needed for generating tzlookup.xml since the file is used for selecting from eligible zones by
 * region.
 *
 * <p>Implementation note: For simplicity, ICU4J is used as the way to query TZDB information,
 * rather than attempting to read the raw IANA files.
 * Much of the logic in this class is to strip out ICU's additions that aren't present in upstream
 * TZDB.
 */
public final class Tzdb {

    // These are abbreviations that refer to fixed offsets, and are not region-based zones.
    // These are considered obsolete and therefore not referenced in tzlookup.xml.
    private static final Set<String> TZ_ABBRS = Set.of("ACT", "AET", "AGT", "ART", "AST", "BET",
            "BST", "CAT", "CET", "CNT", "CST", "CTT", "EAT", "ECT", "EET", "EST", "GMT", "GMT+0",
            "GMT-0", "GMT0", "HST", "IET", "IST", "JST", "MET", "MIT", "MST", "NET", "NST", "PLT",
            "PNT", "PRT", "PST", "SST", "UCT", "UTC", "VST", "WET");

    // US/Pacific-New was deleted from IANA TZDB, but exists in ICU, so it can't be removed yet.
    // Greenwich is just an alias to Etc/GMT, and none of them should appear in the tzlookup file.
    private static final Set<String> MISSING_TZ_IDS = Set.of("US/Pacific-New", "Greenwich");

    // According to backward file comment this name exceeds 14 chars limit and is unused misnomer.
    // Supported by ICU only, not valid TZDB time zone IDs. Not added to tzlookup.xml.
    // See https://data.iana.org/time-zones/theory.html
    private static final Set<String> MISNOMERS = Set.of("Canada/East-Saskatchewan");

    // These zones should not be used and exist mostly for backward compatibility purposes.
    // These zone IDs are non-region based and not included in tzlookup.xml.
    private static final Set<String> NON_COUNTRY_AND_LEGACY_IDS = Set.of(
            // Factory is for distributors who don't want to specify time zone and indicates that
            // time zone is unknown.
            "Factory",
            // Universal is coming from "Universal Coordinated Time".
            "Universal",
            // Zulu stands for Z in NATO Phonetic alphabet. In Nautical time Z means zero zone and
            // is equivalent to GMT.
            "Zulu",
            // These zone IDs are not region-based zones. They express the idea of a zone that
            // explicitly obeys DST rules, and their behavior may diverge from the areas they
            // originally covered (which have been assigned "America/" IDs now instead), if those
            // areas decide to stop using DST.
            "PST8PDT", "EST5EDT", "MST7MDT", "CST6CDT");

    /**
     * Returns the set of TZDB time zone IDs expected to be associated with geographical areas
     * (regions / countries), and excluding those that are region free or have a fixed
     * offset (e.g. like Etc/UTC, PST).
     */
    public static Set<String> regionalTimeZoneIds() {
        return Arrays.stream(TimeZone.getAvailableIDs())
                .filter(timeZoneId -> !TZ_ABBRS.contains(timeZoneId))
                .filter(timeZoneId -> !MISSING_TZ_IDS.contains(timeZoneId))
                .filter(timeZoneId -> !NON_COUNTRY_AND_LEGACY_IDS.contains(timeZoneId))
                .filter(timeZoneId -> !MISNOMERS.contains(timeZoneId))
                .filter(timeZoneId -> !timeZoneId.startsWith("Etc/"))
                .filter(timeZoneId -> !timeZoneId.startsWith("SystemV/"))
                .collect(toSet());
    }

    /** Returns standard offset of {@code timeZoneId} at {@code offsetSampleTimeMillis}. */
    public static int getZoneOffset(String timeZoneId, long offsetSampleTimeMillis) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
        int[] offsets = new int[2];
        timeZone.getOffset(offsetSampleTimeMillis, false /* local */, offsets);

        return offsets[0];
    }

    public static boolean areEquivalent(String firstTimeZoneId, String secondTimeZoneId) {
        long now = System.currentTimeMillis();

        int[] firstOffsets = new int[2];
        TimeZone.getTimeZone(firstTimeZoneId).getOffset(now, false /* local */, firstOffsets);

        int[] secondOffsets = new int[2];
        TimeZone.getTimeZone(secondTimeZoneId).getOffset(now, false /* local */, secondOffsets);

        return Arrays.equals(firstOffsets, secondOffsets);
    }
}
