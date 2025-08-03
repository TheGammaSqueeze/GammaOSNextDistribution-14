/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public class ReportUtilTest {
    private static final String DESTINATION_1 = "https://destination-1.test";
    private static final String DESTINATION_2 = "https://destination-2.test";
    private static final String DESTINATION_3 = "https://destination-3.test";
    private static final List<UnsignedLong> UNSIGNED_LONGS = List.of(
            new UnsignedLong("1234"), new UnsignedLong("9223372036854775809"));

    @Test
    public void serializeAttributionDestinations_emptyList_throwsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportUtil.serializeAttributionDestinations(List.of()));
    }

    @Test
    public void serializeAttributionDestinations_singleDestination_returnsString() {
        List<Uri> destinations = List.of(Uri.parse(DESTINATION_1));
        assertEquals(DESTINATION_1, ReportUtil.serializeAttributionDestinations(destinations));
    }

    @Test
    public void serializeAttributionDestinations_multipleDestinations_returnsOrderedJSONArray() {
        List<Uri> unordered =
                List.of(
                        Uri.parse(DESTINATION_2),
                        Uri.parse(DESTINATION_3),
                        Uri.parse(DESTINATION_1));
        JSONArray expected = new JSONArray(List.of(DESTINATION_1, DESTINATION_2, DESTINATION_3));
        assertEquals(expected, ReportUtil.serializeAttributionDestinations(unordered));
    }

    @Test
    public void serializeUnsignedLongs_returnsJSONArray() {
        JSONArray serialized = ReportUtil.serializeUnsignedLongs(UNSIGNED_LONGS);
        assertEquals(UNSIGNED_LONGS.get(0).toString(), serialized.optString(0));
        assertEquals(UNSIGNED_LONGS.get(1).toString(), serialized.optString(1));
    }

    @Test
    public void serializeSummaryBucket_baseCase_returnsExpectedFormat() throws JSONException {
        Pair<Long, Long> summaryBucket = new Pair<>(1L, 5L);
        JSONArray result = ReportUtil.serializeSummaryBucket(summaryBucket);
        Object first = result.get(0);
        assertTrue(first instanceof Number);
        assertEquals(summaryBucket.first, Long.valueOf((long) first));
        Object second = result.get(1);
        assertTrue(second instanceof Number);
        assertEquals(summaryBucket.second, Long.valueOf((long) second));
    }

    @Test
    public void serializeSummaryBucket_largestBucket_returnsExpectedFormat() throws JSONException {
        Pair<Long, Long> summaryBucket = new Pair<>(100L, Long.MAX_VALUE);
        JSONArray result = ReportUtil.serializeSummaryBucket(summaryBucket);
        Object first = result.get(0);
        assertTrue(first instanceof Number);
        assertEquals(summaryBucket.first, Long.valueOf((long) first));
        Object second = result.get(1);
        assertTrue(second instanceof Number);
        assertEquals(summaryBucket.second, Long.valueOf((long) second));
    }
}
