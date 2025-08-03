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

import static java.time.ZoneOffset.UTC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.timezone.tzids.proto.TzIdsProto;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

/** Tests for {@link TimeZoneIds}. */
public final class TimeZoneIdsTest {

    @Test
    public void getCountryIdMap_links() throws Exception {
        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds.newBuilder();

        TzIdsProto.CountryMapping gb = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneIds("Europe/London")
                .addTimeZoneLinks(createLink("GB", "Europe/London"))
                .build();
        tzIdsBuilder.addCountryMappings(gb);

        TimeZoneIds tzIds = new TimeZoneIds(tzIdsBuilder.build());

        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("Europe/London", "Europe/London");
        expectedMap.put("GB", "Europe/London");

        assertEquals(expectedMap, tzIds.getCountryIdMap("Gb", Instant.EPOCH));
        assertEquals(expectedMap, tzIds.getCountryIdMap("GB", Instant.EPOCH));
        assertEquals(expectedMap, tzIds.getCountryIdMap("gB", Instant.EPOCH));

        assertEquals(expectedMap, tzIds.getCountryIdMap("gb", Instant.MIN));
        assertEquals(expectedMap, tzIds.getCountryIdMap("gb", Instant.EPOCH));
        assertEquals(expectedMap, tzIds.getCountryIdMap("gb", Instant.MAX));
    }

    @Test
    public void getCountryIdMap_replacements() throws Exception {
        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds.newBuilder();

        // A much-simplified version of the US time zone IDs with a chain of replacements.
        Instant knoxFrom = LocalDateTime.of(1991, Month.OCTOBER, 27, 7, 0).toInstant(UTC);
        Instant tellCityFrom = LocalDateTime.of(2006, Month.APRIL, 2, 8, 0).toInstant(UTC);
        TzIdsProto.CountryMapping us = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("us")
                .addTimeZoneIds("America/Chicago")
                .addTimeZoneReplacements(
                        createReplacement(
                                "America/Indiana/Tell_City", "America/Chicago", tellCityFrom))
                .addTimeZoneReplacements(
                        createReplacement(
                                "America/Indiana/Knox", "America/Indiana/Tell_City", knoxFrom))
                .build();
        tzIdsBuilder.addCountryMappings(us);

        TimeZoneIds tzIds = new TimeZoneIds(tzIdsBuilder.build());

        Map<String, String> baseExpectedMap = new HashMap<>();
        baseExpectedMap.put("America/Chicago", "America/Chicago");

        // Before all replacements are in effect.
        {
            Map<String, String> expectedMap = new HashMap<>(baseExpectedMap);
            expectedMap.put("America/Indiana/Tell_City", "America/Indiana/Tell_City");
            expectedMap.put("America/Indiana/Knox", "America/Indiana/Knox");

            assertEquals(expectedMap, tzIds.getCountryIdMap("us", Instant.EPOCH));
            assertEquals(expectedMap, tzIds.getCountryIdMap("us", knoxFrom.minusMillis(1)));
        }

        // One replacement is in effect.
        {
            Map<String, String> expectedMap = new HashMap<>(baseExpectedMap);
            expectedMap.put("America/Indiana/Knox", "America/Indiana/Tell_City");
            expectedMap.put("America/Indiana/Tell_City", "America/Indiana/Tell_City");

            assertEquals(expectedMap, tzIds.getCountryIdMap("us", knoxFrom));
            assertEquals(expectedMap, tzIds.getCountryIdMap("us", tellCityFrom.minusMillis(1)));
        }

        // All replacements are in effect.
        {
            Map<String, String> expectedMap = new HashMap<>(baseExpectedMap);
            expectedMap.put("America/Indiana/Knox", "America/Chicago");
            expectedMap.put("America/Indiana/Tell_City", "America/Chicago");

            assertEquals(expectedMap, tzIds.getCountryIdMap("us", tellCityFrom));
            assertEquals(expectedMap,
                    tzIds.getCountryIdMap("us", Instant.ofEpochMilli(Long.MAX_VALUE)));
        }
    }

    @Test
    public void getCountryCodeForZoneId() {
        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds.newBuilder();

        TzIdsProto.CountryMapping gb = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneIds("Europe/London")
                .addTimeZoneLinks(createLink("GB", "Europe/London"))
                .build();
        tzIdsBuilder.addCountryMappings(gb);

        Instant boiseFrom = LocalDateTime.of(1974, Month.FEBRUARY, 3, 9, 0).toInstant(UTC);
        TzIdsProto.CountryMapping us = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("us")
                .addTimeZoneIds("America/Phoenix")
                .addTimeZoneLinks(createLink("US/Arizona", "America/Phoenix"))
                .addTimeZoneReplacements(
                        createReplacement("America/Boise", "America/Phoenix", boiseFrom))
                .build();
        tzIdsBuilder.addCountryMappings(us);

        TimeZoneIds tzIds = new TimeZoneIds(tzIdsBuilder.build());
        assertNull(tzIds.getCountryCodeForZoneId("FooBar"));
        assertEquals("gb", tzIds.getCountryCodeForZoneId("GB"));
        assertEquals("gb", tzIds.getCountryCodeForZoneId("Europe/London"));
        assertEquals("us", tzIds.getCountryCodeForZoneId("America/Phoenix"));
        assertEquals("us", tzIds.getCountryCodeForZoneId("US/Arizona"));
        assertEquals("us", tzIds.getCountryCodeForZoneId("America/Boise"));
    }

    private static TzIdsProto.TimeZoneLink createLink(
            String alternativeId, String preferredId) {
        return TzIdsProto.TimeZoneLink.newBuilder()
                .setAlternativeId(alternativeId)
                .setPreferredId(preferredId)
                .build();
    }

    private static TzIdsProto.TimeZoneReplacement createReplacement(String replacedId,
            String replacementId, Instant from) {
        return TzIdsProto.TimeZoneReplacement.newBuilder()
                .setReplacedId(replacedId)
                .setReplacementId(replacementId)
                .setFromMillis(from.toEpochMilli())
                .build();
    }
}
