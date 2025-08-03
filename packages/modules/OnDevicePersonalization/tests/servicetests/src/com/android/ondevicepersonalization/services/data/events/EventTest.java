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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventTest {
    private static final int EVENT_TYPE = 2;

    @Test
    public void testBuilderAndEquals() {
        byte[] eventData = "data".getBytes();
        ComponentName service = new ComponentName("servicePkg", "cls");
        long queryId = 1;
        long timeMillis = 1;
        long eventId = 1;
        int rowIndex = 1;
        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE)
                .setEventData(eventData)
                .setService(service)
                .setQueryId(queryId)
                .setTimeMillis(timeMillis)
                .setRowIndex(rowIndex)
                .setEventId(eventId)
                .build();

        assertEquals(event1.getType(), EVENT_TYPE);
        assertArrayEquals(event1.getEventData(), eventData);
        assertEquals(event1.getService(), service);
        assertEquals(event1.getQueryId(), queryId);
        assertEquals(event1.getTimeMillis(), timeMillis);
        assertEquals(event1.getRowIndex(), rowIndex);
        assertEquals(event1.getEventId(), eventId);

        Event event2 = new Event.Builder(
                eventId, queryId, rowIndex, service, EVENT_TYPE, timeMillis, eventData)
                .build();
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testBuildTwiceThrows() {
        byte[] eventData = "data".getBytes();
        ComponentName service = new ComponentName("servicePkg", "cls");
        long queryId = 1;
        long timeMillis = 1;
        long eventId = 1;
        int rowIndex = 1;
        Event.Builder builder = new Event.Builder()
                .setType(EVENT_TYPE)
                .setEventData(eventData)
                .setService(service)
                .setQueryId(queryId)
                .setTimeMillis(timeMillis)
                .setRowIndex(rowIndex)
                .setEventId(eventId);

        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.build());
    }
}
