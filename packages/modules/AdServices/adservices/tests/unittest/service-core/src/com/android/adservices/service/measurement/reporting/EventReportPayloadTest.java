/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class EventReportPayloadTest {

    private static final List<Uri> ATTRIBUTION_DESTINATIONS =
            List.of(Uri.parse("https://toasters.example"));
    private static final Uri DESTINATION_1 = Uri.parse("https://destination1.test");
    private static final Uri DESTINATION_2 = Uri.parse("https://destination2.test");
    private static final String SCHEDULED_REPORT_TIME = "1675459163";
    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(12345L);
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(2L);
    private static final String REPORT_ID = "678";
    private static final String SOURCE_TYPE = "event";
    private static final double RANDOMIZED_TRIGGER_RATE = 0.0024;
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(3894783L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(2387222L);
    private static final Pair<Long, Long> TRIGGER_SUMMARY_BUCKET = Pair.create(5L, 37L);
    private static final List<UnsignedLong> TRIGGER_DEBUG_KEYS = List.of(
            new UnsignedLong(2387222L), new UnsignedLong("9223372036854775809"));

    private static EventReportPayload createEventReportPayload(
            UnsignedLong triggerData,
            List<UnsignedLong> triggerDebugKeys) {
        return createEventReportPayload(
                triggerData,
                null,
                null,
                triggerDebugKeys,
                ATTRIBUTION_DESTINATIONS);
    }

    private static EventReportPayload createEventReportPayload(
            UnsignedLong triggerData,
            UnsignedLong sourceDebugKey,
            UnsignedLong triggerDebugKey) {
        return createEventReportPayload(
                triggerData,
                sourceDebugKey,
                triggerDebugKey,
                Collections.emptyList(),
                ATTRIBUTION_DESTINATIONS);
    }

    private static EventReportPayload createEventReportPayload(
            UnsignedLong triggerData,
            UnsignedLong sourceDebugKey,
            UnsignedLong triggerDebugKey,
            List<Uri> attributionDestinations) {
        return createEventReportPayload(
                triggerData,
                sourceDebugKey,
                triggerDebugKey,
                Collections.emptyList(),
                attributionDestinations);
    }

    private static EventReportPayload.Builder createEventReportPayloadBuilder(
            UnsignedLong triggerData,
            UnsignedLong sourceDebugKey,
            UnsignedLong triggerDebugKey,
            List<UnsignedLong> triggerDebugKeys,
            List<Uri> destinations) {
        return new EventReportPayload.Builder()
                .setAttributionDestination(destinations)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(triggerData)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(sourceDebugKey)
                .setTriggerDebugKey(triggerDebugKey)
                .setTriggerDebugKeys(triggerDebugKeys);
    }

    private static EventReportPayload createEventReportPayload(
            UnsignedLong triggerData,
            UnsignedLong sourceDebugKey,
            UnsignedLong triggerDebugKey,
            List<UnsignedLong> triggerDebugKeys,
            List<Uri> destinations) {
        return createEventReportPayloadBuilder(
                triggerData,
                sourceDebugKey,
                triggerDebugKey,
                triggerDebugKeys,
                destinations)
                        .build();
    }

    @Test
    public void toJson_success() throws JSONException {
        EventReportPayload eventReport =
                createEventReportPayloadBuilder(
                        TRIGGER_DATA,
                        SOURCE_DEBUG_KEY,
                        TRIGGER_DEBUG_KEY,
                        TRIGGER_DEBUG_KEYS,
                        ATTRIBUTION_DESTINATIONS)
                                .setTriggerSummaryBucket(TRIGGER_SUMMARY_BUCKET)
                                .build();

        JSONObject eventPayloadReportJson = eventReport.toJson();

        Object destinationsObj = eventPayloadReportJson.get("attribution_destination");
        assertTrue(destinationsObj instanceof String);
        assertEquals(ATTRIBUTION_DESTINATIONS.get(0).toString(), (String) destinationsObj);
        assertEquals(SCHEDULED_REPORT_TIME, eventPayloadReportJson.get("scheduled_report_time"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(RANDOMIZED_TRIGGER_RATE,
                eventPayloadReportJson.get("randomized_trigger_rate"));
        assertEquals(SOURCE_DEBUG_KEY.toString(), eventPayloadReportJson.get("source_debug_key"));
        assertEquals(TRIGGER_DEBUG_KEY.toString(), eventPayloadReportJson.get("trigger_debug_key"));
        Object triggerDebugKeysObj = eventPayloadReportJson.get("trigger_debug_keys");
        assertTrue(triggerDebugKeysObj instanceof JSONArray);
        assertEquals(TRIGGER_DEBUG_KEYS.get(0).toString(),
                ((JSONArray) triggerDebugKeysObj).getString(0));
        assertEquals(TRIGGER_DEBUG_KEYS.get(1).toString(),
                ((JSONArray) triggerDebugKeysObj).getString(1));
        // Trigger summary bucket
        assertTrue(eventPayloadReportJson.get("trigger_summary_bucket") instanceof JSONArray);
        JSONArray triggerSummaryBucket =
                eventPayloadReportJson.getJSONArray("trigger_summary_bucket");
        Object first = triggerSummaryBucket.get(0);
        assertTrue(first instanceof Number);
        assertEquals(TRIGGER_SUMMARY_BUCKET.first, Long.valueOf((long) first));
        Object second = triggerSummaryBucket.get(1);
        assertTrue(second instanceof Number);
        assertEquals(TRIGGER_SUMMARY_BUCKET.second, Long.valueOf((long) second));
    }

    @Test
    public void toJson_multipleAttributionDestinations_setsDestinationsAsOrderedJSONArray()
            throws JSONException {
        EventReportPayload eventReport =  createEventReportPayload(
                TRIGGER_DATA, null, null, List.of(DESTINATION_2, DESTINATION_1));
        JSONObject eventPayloadReportJson = eventReport.toJson();

        Object obj = eventPayloadReportJson.get("attribution_destination");
        assertTrue(obj instanceof JSONArray);
        assertEquals(DESTINATION_1.toString(), ((JSONArray) obj).getString(0));
        assertEquals(DESTINATION_2.toString(), ((JSONArray) obj).getString(1));
        assertEquals(SCHEDULED_REPORT_TIME, eventPayloadReportJson.get("scheduled_report_time"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(RANDOMIZED_TRIGGER_RATE,
                eventPayloadReportJson.get("randomized_trigger_rate"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithNullDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayload(TRIGGER_DATA, null, null);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_keys"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithNullTriggerData() throws JSONException {
        EventReportPayload eventReport = createEventReportPayload(null, null, null);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(new UnsignedLong(0L).toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleTriggerDebugKey() throws JSONException {
        EventReportPayload eventReport =
                createEventReportPayload(TRIGGER_DATA, null, TRIGGER_DEBUG_KEY);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertEquals(TRIGGER_DEBUG_KEY.toString(), eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithMultipleTriggerDebugKeys()
            throws JSONException {
        EventReportPayload eventReport =
                createEventReportPayload(TRIGGER_DATA, TRIGGER_DEBUG_KEYS);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
        Object triggerDebugKeysObj = eventPayloadReportJson.get("trigger_debug_keys");
        assertTrue(triggerDebugKeysObj instanceof JSONArray);
        assertEquals(TRIGGER_DEBUG_KEYS.get(0).toString(),
                ((JSONArray) triggerDebugKeysObj).getString(0));
        assertEquals(TRIGGER_DEBUG_KEYS.get(1).toString(),
                ((JSONArray) triggerDebugKeysObj).getString(1));
    }

    @Test
    public void testEventPayloadJsonSerialization_debugKeysSourceEventIdAndTriggerDataUse64thBit()
            throws JSONException {
        String unsigned64BitIntString = "18446744073709551615";
        UnsignedLong signed64BitInt = new UnsignedLong(-1L);
        EventReportPayload eventReport = new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATIONS)
                .setSourceEventId(signed64BitInt)
                .setTriggerData(signed64BitInt)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(signed64BitInt)
                .setTriggerDebugKey(signed64BitInt)
                .build();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("source_event_id"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.opt("source_debug_key"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleSourceDebugKey() throws JSONException {
        EventReportPayload eventReport =
                createEventReportPayload(TRIGGER_DATA, SOURCE_DEBUG_KEY, null);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATIONS.get(0).toString(),
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID.toString(), eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA.toString(), eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
        assertEquals(SOURCE_DEBUG_KEY.toString(), eventPayloadReportJson.get("source_debug_key"));
    }
}
