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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class EventTriggerTest {
    private static JSONObject sFilterMap1;
    private static JSONObject sFilterMap2;
    private static JSONObject sNotFilterMap1;
    private static JSONObject sNotFilterMap2;

    static {
        try {
            sFilterMap1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"navigation\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sFilterMap2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"EVENT\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterMap1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"EVENT\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterMap2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"navigation\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
        } catch (JSONException e) {
            fail();
        }
    }

    @Test
    public void testDefaults() throws Exception {
        EventTrigger eventTrigger = new EventTrigger.Builder(new UnsignedLong(0L)).build();
        assertEquals(0L, eventTrigger.getTriggerPriority());
        assertNotNull(eventTrigger.getTriggerData());
        assertNull(eventTrigger.getDedupKey());
        assertFalse(eventTrigger.getFilterSet().isPresent());
        assertFalse(eventTrigger.getNotFilterSet().isPresent());
    }

    @Test
    public void test_equals_pass() throws Exception {
        EventTrigger eventTrigger1 =
                new EventTrigger.Builder(new UnsignedLong(101L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(100L)
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilterSet(
                                List.of(
                                        new FilterMap.Builder()
                                                .buildFilterData(sFilterMap1)
                                                .build()))
                        .setNotFilterSet(
                                List.of(
                                        new FilterMap.Builder()
                                                .buildFilterData(sNotFilterMap1)
                                                .build()))
                        .build();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder(new UnsignedLong(101L))
                        .setTriggerPriority(1L)
                        .setTriggerValue(100L)
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilterSet(
                                List.of(
                                        new FilterMap.Builder()
                                                .buildFilterData(sFilterMap1)
                                                .build()))
                        .setNotFilterSet(
                                List.of(
                                        new FilterMap.Builder()
                                                .buildFilterData(sNotFilterMap1)
                                                .build()))
                        .build();

        assertEquals(eventTrigger1, eventTrigger2);
    }

    @Test
    public void test_equals_fail() throws Exception {
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(0L)).setTriggerPriority(1L).build(),
                new EventTrigger.Builder(new UnsignedLong(0L)).setTriggerPriority(2L).build());
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(0L)).setTriggerValue(100L).build(),
                new EventTrigger.Builder(new UnsignedLong(0L)).setTriggerValue(120L).build());
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(1L)).build(),
                new EventTrigger.Builder(new UnsignedLong(2L)).build());
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setDedupKey(new UnsignedLong(1L))
                        .build(),
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setDedupKey(new UnsignedLong(2L))
                        .build());
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sFilterMap1)
                                        .build()))
                        .build(),
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sFilterMap2)
                                        .build()))
                        .build());
        assertNotEquals(
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setNotFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sNotFilterMap1)
                                        .build()))
                        .build(),
                new EventTrigger.Builder(new UnsignedLong(0L))
                        .setNotFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sNotFilterMap2)
                                        .build()))
                        .build());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        EventTrigger eventTrigger1 = createExample();
        EventTrigger eventTrigger2 = createExample();
        Set<EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
        Set<EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
        assertEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
        assertEquals(eventTrigger1, eventTrigger2);
        assertEquals(eventTriggerSet1, eventTriggerSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        EventTrigger eventTrigger1 = createExample();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder(new UnsignedLong(101L))
                        .setTriggerPriority(2L)
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sFilterMap1)
                                        .build()))
                        .setNotFilterSet(List.of(
                                new FilterMap.Builder()
                                        .buildFilterData(sNotFilterMap1)
                                        .build()))
                        .build();
        Set<EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
        Set<EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
        assertNotEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
        assertNotEquals(eventTrigger1, eventTrigger2);
        assertNotEquals(eventTriggerSet1, eventTriggerSet2);
    }

    @Test
    public void eventTriggerBuilder_nullTriggerData_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EventTrigger.Builder(null)
                                .setTriggerPriority(124L)
                                .setDedupKey(new UnsignedLong(101L))
                                .build());
    }

    private EventTrigger createExample() throws JSONException {
        return new EventTrigger.Builder(new UnsignedLong(101L))
                .setTriggerPriority(1L)
                .setDedupKey(new UnsignedLong(1001L))
                .setFilterSet(List.of(
                        new FilterMap.Builder()
                                .buildFilterData(sFilterMap1)
                                .build()))
                .setNotFilterSet(List.of(
                        new FilterMap.Builder()
                                .buildFilterData(sNotFilterMap1)
                                .build()))
                .build();
    }
}
