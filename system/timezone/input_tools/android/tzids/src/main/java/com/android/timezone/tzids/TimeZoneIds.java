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
package com.android.timezone.tzids;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

import com.android.timezone.tzids.proto.TzIdsProto;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A representation of Android's country ISO code mapping for TZDB time zone IDs, and information
 * about time zone ID equivalence, for use in host tooling.
 *
 * <p>The Android team maintain the mapping based on information from IANA's TZDB and other
 * sources. Strictly speaking, TZDB no longer supports the idea of a time zone ID being associated
 * with a single country (see zone1970.tab Vs zone.tab), but Android requires most zones be mapped
 * this way: Android has a region-based time zone picker in settings (for selecting the device's
 * time zone manually), and it uses a country-based algorithm for telephony-based time zone
 * detection. Most time zone IDs in TZDB have an exemplar location, and a time zone ID will be
 * mapped to the country of that exemplar location.
 *
 * <p>Besides the country mapping, only some IDs for a country are actually "useful". The others are
 * associated with equivalent alternatives that are preferred. Mappings between different time zone
 * IDs exist in two main situations:
 * <ol>
 *     <li>"Links" : When TZDB has a direct link / synonym. e.g. "GB-Eire", an old ID, maps to
 *     "Europe/London", the modern / canonical equivalent. "GB-Eire" and "Europe/London" are
 *     identical in TZDB. Note: Other libraries with additional metadata like ICU could still treat
 *     "GB-Eire" differently from "Europe/London".
 *     These will typically be as you'd expect from looking at the IANA tzdb backward file. But as
 *     it contains cross-country links we do not follow it blindly and the IDs preferred by Android
 *     are determined by countryzones.txt, which could choose to continue using an old ID, in which
 *     case the link may be reversed. This reversal is expected when an older version of ICU doesn't
 *     have strings for a new ID.</li>
 *     <li>"Replacements" : When zones have coalesced over time within a country. TZDB has many
 *     "extra" IDs for time zones because of historical differences that haven't been relevant for
 *     years. Replacements have a "from" time which indicates the point in time after which the two
 *     zones can be considered equivalent. e.g. "America/Boise" has the same rules as
 *     "America/Phoenix" after Sun, 03 Feb 1974, so the two can be considered equivalent today, but
 *     not for dates before then.</li>
 * </ol>
 *
 * <p>Other notes:
 * <ul>
 *     <li>There are entries in TZDB that are not mapped to a country and so are not included.
 *     e.g. Etc/GMT+5.</li>
 *     <li>Time zone IDs must be mapped to at most one country.</li>
 *     <li>There are many time zone IDs in TZDB that have identical rules, but are associated with
 *     different countries. These are deliberately not recorded as links or replacements.
 *     i.e. Links/replacements do not link across ISO country codes.</li>
 * </ul>
 */
public class TimeZoneIds {

    private final TzIdsProto.TimeZoneIds mTimeZoneIdsProto;

    /** Creates a {@link TimeZoneIds} from the proto definition. */
    public TimeZoneIds(TzIdsProto.TimeZoneIds timeZoneIdsProto) {
        mTimeZoneIdsProto = Objects.requireNonNull(timeZoneIdsProto);
    }

    /** Loads the {@code inputFile} as a {@link TimeZoneIds}. */
    public static TimeZoneIds load(File inputFile) throws IOException {
        TzIdsProto.TimeZoneIds.Builder builder = TzIdsProto.TimeZoneIds.newBuilder();
        try (InputStreamReader reader =
                new InputStreamReader(new FileInputStream(inputFile), UTF_8)) {
            TextFormat.getParser().merge(reader, builder);
        }
        return new TimeZoneIds(builder.build());
    }

    /** Store this in {@code file}. */
    public void store(File file) throws IOException {
        try (OutputStreamWriter writer =
                new OutputStreamWriter(new FileOutputStream(file), UTF_8)) {
            writer.append("# Autogenerated file - DO NOT EDIT.\n");
            TextFormat.print(mTimeZoneIdsProto, writer);
        }
    }

    /**
     * Returns the country code associated with the supplied {@code zoneId} or {@code null} if it
     * is not found.
     */
    public String getCountryCodeForZoneId(String zoneId) {
        for (TzIdsProto.CountryMapping countryMapping
                : mTimeZoneIdsProto.getCountryMappingsList()) {
            if (countryMapping.getTimeZoneIdsList().contains(zoneId)) {
                return countryMapping.getIsoCode();
            }

            for (TzIdsProto.TimeZoneLink timeZoneLink : countryMapping.getTimeZoneLinksList()) {
                if (timeZoneLink.getAlternativeId().equals(zoneId)) {
                    return countryMapping.getIsoCode();
                }
            }

            for (TzIdsProto.TimeZoneReplacement timeZoneReplacement
                    : countryMapping.getTimeZoneReplacementsList()) {
                if (timeZoneReplacement.getReplacedId().equals(zoneId)) {
                    return countryMapping.getIsoCode();
                }
            }
        }
        return null;
    }

    /**
     * Returns a country mapping for TZDB time zone IDs on or after the specified time.
     * The {@link Map} returned contains entries that link Olson IDs to themselves, or to preferred
     * Olson IDs. The {@code replacementThreshold} is used to identify which "replacements" should
     * be considered (see {@link TimeZoneIds} for details).
     *
     * <p>This mapping is useful when interpreting data from third party sources that could use any
     * of the many equivalent zone IDs from TZDB and need to interact with Android devices.
     *
     * <p>If no map entry is present for an ID, then the IDs is either not valid or is not mapped to
     * the ISO country code by Android's mapping data.
     *
     * @param isoCode the ISO 3166 alpha-2 country code for the country
     * @param replacementThreshold the point in time to use when interpreting replacements
     * @throws IllegalArgumentException if the country code is not recognized
     */
    public Map<String, String> getCountryIdMap(String isoCode, Instant replacementThreshold) {
        for (TzIdsProto.CountryMapping countryMapping
                : mTimeZoneIdsProto.getCountryMappingsList()) {
            if (countryMapping.getIsoCode().equalsIgnoreCase(isoCode)) {
                return createCountryIdMap(countryMapping, replacementThreshold);
            }
        }
        throw new IllegalArgumentException("Unknown country code: " + isoCode);
    }

    private Map<String, String> createCountryIdMap(
            TzIdsProto.CountryMapping countryMapping, Instant replacementThreshold) {
        Map<String, String> tzIdMap = new HashMap<>();

        // Add identity entries for the preferred IDs.
        putAllSafely(tzIdMap, countryMapping.getTimeZoneIdsList()
                .stream()
                .collect(toMap(Function.identity(), Function.identity())));

        // Handle links: just add straightforward map entries.
        putAllSafely(tzIdMap, countryMapping.getTimeZoneLinksList()
                .stream()
                .collect(toMap(x -> x.getAlternativeId(), x -> x.getPreferredId())));

        // Handle replacements: replacements are zone IDs that are recognized but potentially no
        // longer used. Each replacement has a time when it comes into effect, and it may link to
        // another replacement or a "top level" time zone ID. Below, the replacementThreshold is
        // used to work out which replacements are in use and map each replacedId to a zone ID. It
        // is ok if a zone maps to itself, i.e. when a replacement is in the future.

        // Create a lookup of replacements by replacedId to make it easier to traverse the linked
        // list formed by replacements referring to other replacements.
        Map<String, TzIdsProto.TimeZoneReplacement> replacementLookupMap =
                countryMapping.getTimeZoneReplacementsList()
                        .stream()
                        .collect(toMap(x -> x.getReplacedId(), Function.identity()));

        // For each replacement in the replacement list, work out what the replacedId should map to
        // before the replacementThreshold.
        Map<String, String> replacementsAtThreshold = countryMapping.getTimeZoneReplacementsList()
                .stream()
                .collect(toMap(x -> x.getReplacedId(),
                        x -> traverseReplacementList(x, replacementLookupMap, replacementThreshold)
                ));
        putAllSafely(tzIdMap, replacementsAtThreshold);
        return tzIdMap;
    }

    /**
     * Returns the replacement ID for {@code start} by following the replacement links until there
     * are no more replacements or the replacement happens after {@code replacementThreshold}.
     */
    private static String traverseReplacementList(TzIdsProto.TimeZoneReplacement start,
            Map<String, TzIdsProto.TimeZoneReplacement> replacementLookupMap,
            Instant replacementThreshold) {
        TzIdsProto.TimeZoneReplacement current = start;
        while (current.getFromMillis() <= replacementThreshold.toEpochMilli()) {
            TzIdsProto.TimeZoneReplacement next =
                    replacementLookupMap.get(current.getReplacementId());
            if (next == null) {
                // No more links to follow - use the replacement ID.
                return current.getReplacementId();
            }
            current = next;
        }
        // There were more links that could be followed, but we stopped following
        // because of the replacementThreshold.
        return current.getReplacedId();
    }

    /**
     * Like {@link Map#putAll(Map)} but throws an exception if {@code sourceMap} contains a key
     * already present in {@code targetMap}.
     */
    private static void putAllSafely(Map<String, String> targetMap, Map<String, String> sourceMap) {
        for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
            String oldMapping = targetMap.put(entry.getKey(), entry.getValue());
            if (oldMapping != null) {
                throw new IllegalArgumentException("Key is not unique: "
                        + entry.getKey() + " maps to both "
                        + entry.getValue() + " and " + oldMapping);
            }
        }
    }
}
