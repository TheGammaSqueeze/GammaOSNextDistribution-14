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
public class JoinedEventTest {
    @Test
    public void testBuilderAndEquals() {
        ComponentName service = new ComponentName("servicePkg", "cls");
        long queryId = 1;
        long eventId = 1;
        int rowIndex = 1;
        int type = 1;
        long eventTimeMillis = 100;
        long queryTimeMillis = 50;
        byte[] eventData = "eventData".getBytes();
        byte[] queryData = "queryData".getBytes();

        JoinedEvent joinedEvent1 = new JoinedEvent.Builder()
                .setService(service)
                .setQueryId(queryId)
                .setEventId(eventId)
                .setRowIndex(rowIndex)
                .setType(type)
                .setEventTimeMillis(eventTimeMillis)
                .setQueryTimeMillis(queryTimeMillis)
                .setEventData(eventData)
                .setQueryData(queryData)
                .build();

        assertEquals(joinedEvent1.getQueryId(), queryId);
        assertEquals(joinedEvent1.getEventId(), eventId);
        assertEquals(joinedEvent1.getRowIndex(), rowIndex);
        assertEquals(joinedEvent1.getType(), type);
        assertEquals(joinedEvent1.getEventTimeMillis(), eventTimeMillis);
        assertEquals(joinedEvent1.getQueryTimeMillis(), queryTimeMillis);
        assertArrayEquals(joinedEvent1.getEventData(), eventData);
        assertArrayEquals(joinedEvent1.getQueryData(), queryData);
        assertEquals(joinedEvent1.getService(), service);

        JoinedEvent joinedEvent2 = new JoinedEvent.Builder(
                eventId, queryId, rowIndex, service, type, eventTimeMillis, eventData,
                queryTimeMillis, queryData)
                .build();
        assertEquals(joinedEvent1, joinedEvent2);
        assertEquals(joinedEvent1.hashCode(), joinedEvent2.hashCode());
    }

    @Test
    public void testBuildTwiceThrows() {
        ComponentName service = new ComponentName("servicePkg", "cls");
        long queryId = 1;
        long eventId = 1;
        int rowIndex = 1;
        int type = 1;
        long eventTimeMillis = 100;
        long queryTimeMillis = 50;
        byte[] eventData = "eventData".getBytes();
        byte[] queryData = "queryData".getBytes();

        JoinedEvent.Builder builder = new JoinedEvent.Builder()
                .setService(service)
                .setQueryId(queryId)
                .setEventId(eventId)
                .setRowIndex(rowIndex)
                .setType(type)
                .setEventTimeMillis(eventTimeMillis)
                .setQueryTimeMillis(queryTimeMillis)
                .setEventData(eventData)
                .setQueryData(queryData);

        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }
}
