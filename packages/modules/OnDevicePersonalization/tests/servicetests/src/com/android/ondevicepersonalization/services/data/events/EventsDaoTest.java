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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class EventsDaoTest {
    private static final int EVENT_TYPE_B2D = 1;
    private static final int EVENT_TYPE_CLICK = 2;
    private static final String TASK_IDENTIFIER = "taskIdentifier";
    private static final String SERVICE_CLASS = "TestClass";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final ComponentName mService =
            new ComponentName(mContext.getPackageName(), SERVICE_CLASS);
    private final Event mTestEvent = new Event.Builder()
            .setType(EVENT_TYPE_B2D)
            .setEventData("event".getBytes(StandardCharsets.UTF_8))
            .setService(mService)
            .setQueryId(1L)
            .setTimeMillis(1L)
            .setRowIndex(0)
            .build();
    private final Query mTestQuery = new Query.Builder()
            .setTimeMillis(1L)
            .setService(mService)
            .setQueryData("query".getBytes(StandardCharsets.UTF_8))
            .build();
    private final EventState mEventState = new EventState.Builder()
            .setTaskIdentifier(TASK_IDENTIFIER)
            .setService(mService)
            .setToken(new byte[]{1})
            .build();
    private EventsDao mDao;

    @Before
    public void setup() {
        mDao = EventsDao.getInstanceForTest(mContext);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testInsertQueryAndEvent() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(1, mDao.insertEvent(mTestEvent));
        Event testEvent = new Event.Builder()
                .setType(EVENT_TYPE_CLICK)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(1L)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        assertEquals(2, mDao.insertEvent(testEvent));
    }

    @Test
    public void testInsertEvents() {
        mDao.insertQuery(mTestQuery);
        Event testEvent = new Event.Builder()
                .setType(EVENT_TYPE_CLICK)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(1L)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        List<Event> events = new ArrayList<>();
        events.add(mTestEvent);
        events.add(testEvent);
        assertTrue(mDao.insertEvents(events));
    }

    @Test
    public void testInsertEventsFalse() {
        List<Event> events = new ArrayList<>();
        events.add(mTestEvent);
        assertFalse(mDao.insertEvents(events));
    }

    @Test
    public void testInsertAndReadEventState() {
        assertTrue(mDao.updateOrInsertEventState(mEventState));
        assertEquals(mEventState, mDao.getEventState(TASK_IDENTIFIER, mService));
        EventState testEventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setService(mService)
                .setToken(new byte[]{100})
                .build();
        assertTrue(mDao.updateOrInsertEventState(testEventState));
        assertEquals(testEventState,
                mDao.getEventState(TASK_IDENTIFIER, mService));
    }


    @Test
    public void testInsertAndReadEventStatesTransaction() {
        EventState testEventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setService(mService)
                .setToken(new byte[]{100})
                .build();
        List<EventState> eventStates = new ArrayList<>();
        eventStates.add(mEventState);
        eventStates.add(testEventState);
        assertTrue(mDao.updateOrInsertEventStatesTransaction(eventStates));
        assertEquals(testEventState,
                mDao.getEventState(TASK_IDENTIFIER, mService));
    }
    @Test
    public void testDeleteEventState() {
        ComponentName serviceA = new ComponentName("packageA", "clsA");
        mDao.updateOrInsertEventState(mEventState);
        EventState testEventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setService(serviceA)
                .setToken(new byte[]{100})
                .build();
        mDao.updateOrInsertEventState(testEventState);
        mDao.deleteEventState(mService);
        assertEquals(testEventState,
                mDao.getEventState(TASK_IDENTIFIER, serviceA));
        assertNull(mDao.getEventState(TASK_IDENTIFIER, mService));

        mDao.deleteEventState(serviceA);
        assertNull(mDao.getEventState(TASK_IDENTIFIER, serviceA));
    }

    @Test
    public void testDeleteEventsAndQueries() {
        mDao.insertQuery(mTestQuery);
        mDao.insertEvent(mTestEvent);
        long queryId2 = mDao.insertQuery(mTestQuery);
        Event testEvent = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(3L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(testEvent);

        Query testQuery = new Query.Builder()
                .setTimeMillis(5L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(testQuery);

        // Delete query1 event1. Assert query2 and event2 still exist.
        mDao.deleteEventsAndQueries(2);
        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertEquals(3, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(testEvent, mTestQuery, eventId2, queryId2),
                joinedEventList.get(0));
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId2),
                joinedEventList.get(1));
        assertEquals(createExpectedJoinedEvent(null, testQuery, 0, queryId3),
                joinedEventList.get(2));

        // Delete query2 event2. Assert query3 still exist.
        mDao.deleteEventsAndQueries(4);
        joinedEventList = mDao.readAllNewRows(0, 0);
        assertEquals(1, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(null, testQuery, 0, queryId3),
                joinedEventList.get(0));
    }


    @Test
    public void testReadAllNewRowsEmptyTable() {
        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertTrue(joinedEventList.isEmpty());
    }

    @Test
    public void testReadAllNewRowsForPackageEmptyTable() {
        List<JoinedEvent> joinedEventList = mDao.readAllNewRowsForPackage(mService,
                0, 0);
        assertTrue(joinedEventList.isEmpty());
    }

    @Test
    public void testReadAllNewRowsForPackage() {
        long queryId1 = mDao.insertQuery(mTestQuery);
        long eventId1 = mDao.insertEvent(mTestEvent);
        long queryId2 = mDao.insertQuery(mTestQuery);
        ComponentName serviceA = new ComponentName("packageA", "clsA");

        Query packageAQuery = new Query.Builder()
                .setTimeMillis(1L)
                .setService(serviceA)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(packageAQuery);

        Event packageAEvent = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(serviceA)
                .setQueryId(queryId3)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(packageAEvent);

        List<JoinedEvent> joinedEventList = mDao.readAllNewRowsForPackage(mService,
                0, 0);
        assertEquals(3, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(mTestEvent, mTestQuery, eventId1, queryId1),
                joinedEventList.get(0));
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId1),
                joinedEventList.get(1));
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId2),
                joinedEventList.get(2));

        joinedEventList = mDao.readAllNewRowsForPackage(mService, eventId1,
                queryId2);
        assertTrue(joinedEventList.isEmpty());

        joinedEventList = mDao.readAllNewRowsForPackage(mService, eventId1,
                queryId1);
        assertEquals(1, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId2),
                joinedEventList.get(0));
    }

    @Test
    public void testReadAllNewRows() {
        long queryId1 = mDao.insertQuery(mTestQuery);
        long eventId1 = mDao.insertEvent(mTestEvent);
        long queryId2 = mDao.insertQuery(mTestQuery);
        ComponentName serviceA = new ComponentName("packageA", "clsA");

        Query packageAQuery = new Query.Builder()
                .setTimeMillis(1L)
                .setService(serviceA)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(packageAQuery);

        Event packageAEvent = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(serviceA)
                .setQueryId(queryId3)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(packageAEvent);

        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertEquals(5, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(mTestEvent, mTestQuery, eventId1, queryId1),
                joinedEventList.get(0));
        assertEquals(createExpectedJoinedEvent(packageAEvent, packageAQuery, eventId2, queryId3),
                joinedEventList.get(1));
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId1),
                joinedEventList.get(2));
        assertEquals(createExpectedJoinedEvent(null, mTestQuery, 0, queryId2),
                joinedEventList.get(3));
        assertEquals(createExpectedJoinedEvent(null, packageAQuery, 0, queryId3),
                joinedEventList.get(4));

        joinedEventList = mDao.readAllNewRows(eventId2, queryId3);
        assertTrue(joinedEventList.isEmpty());

        joinedEventList = mDao.readAllNewRows(eventId2, queryId2);
        assertEquals(1, joinedEventList.size());
        assertEquals(createExpectedJoinedEvent(null, packageAQuery, 0, queryId3),
                joinedEventList.get(0));
    }

    @Test
    public void testReadAllQueries() {
        ComponentName otherService = new ComponentName("package", "cls");
        Query query1 = new Query.Builder()
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 = new Query.Builder()
                .setTimeMillis(10L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 = new Query.Builder()
                .setTimeMillis(100L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(query3);
        Query query4 = new Query.Builder()
                .setTimeMillis(100L)
                .setService(otherService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId4 = mDao.insertQuery(query4);

        List<Query> result = mDao.readAllQueries(0, 1000, mService);
        assertEquals(3, result.size());
        assertEquals(queryId1, (long) result.get(0).getQueryId());
        assertEquals(queryId2, (long) result.get(1).getQueryId());
        assertEquals(queryId3, (long) result.get(2).getQueryId());

        result = mDao.readAllQueries(0, 1000, otherService);
        assertEquals(1, result.size());
        assertEquals(queryId4, (long) result.get(0).getQueryId());

        result = mDao.readAllQueries(500, 1000, mService);
        assertEquals(0, result.size());

        result = mDao.readAllQueries(5, 1000, mService);
        assertEquals(2, result.size());
        assertEquals(queryId2, (long) result.get(0).getQueryId());
        assertEquals(queryId3, (long) result.get(1).getQueryId());
    }

    @Test
    public void testReadAllEventIds() {
        Query query1 = new Query.Builder()
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 = new Query.Builder()
                .setTimeMillis(10L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 = new Query.Builder()
                .setTimeMillis(100L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(query3);

        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId1)
                .setTimeMillis(2L)
                .setRowIndex(0)
                .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(11L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId3)
                .setTimeMillis(101L)
                .setRowIndex(0)
                .build();
        long eventId3 = mDao.insertEvent(event3);

        List<Long> result = mDao.readAllEventIds(0, 1000, mService);
        assertEquals(3, result.size());
        assertEquals(eventId1, (long) result.get(0));
        assertEquals(eventId2, (long) result.get(1));
        assertEquals(eventId3, (long) result.get(2));

        result = mDao.readAllEventIds(0, 1000, new ComponentName("pkg", "cls"));
        assertEquals(0, result.size());

        result = mDao.readAllEventIds(500, 1000, mService);
        assertEquals(0, result.size());

        result = mDao.readAllEventIds(5, 1000, mService);
        assertEquals(2, result.size());
        assertEquals(eventId2, (long) result.get(0));
        assertEquals(eventId3, (long) result.get(1));
    }

    @Test
    public void testReadEventIdsForRequest() {
        Query query1 = new Query.Builder()
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 = new Query.Builder()
                .setTimeMillis(10L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId2 = mDao.insertQuery(query2);

        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId1)
                .setTimeMillis(2L)
                .setRowIndex(0)
                .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(11L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 = new Event.Builder()
                .setType(EVENT_TYPE_CLICK)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(101L)
                .setRowIndex(0)
                .build();
        long eventId3 = mDao.insertEvent(event3);

        List<Long> result = mDao.readAllEventIdsForQuery(queryId1, mService);
        assertEquals(1, result.size());
        assertEquals(eventId1, (long) result.get(0));

        result = mDao.readAllEventIdsForQuery(queryId2, mService);
        assertEquals(2, result.size());
        assertEquals(eventId2, (long) result.get(0));
        assertEquals(eventId3, (long) result.get(1));

        result = mDao.readAllEventIdsForQuery(1000, mService);
        assertEquals(0, result.size());

        result = mDao.readAllEventIdsForQuery(queryId1, new ComponentName("pkg", "cls"));
        assertEquals(0, result.size());
    }

    @Test
    public void testReadJoinedEvents() {
        Query query1 = new Query.Builder()
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 = new Query.Builder()
                .setTimeMillis(10L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 = new Query.Builder()
                .setTimeMillis(100L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId3 = mDao.insertQuery(query3);

        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId1)
                .setTimeMillis(2L)
                .setRowIndex(0)
                .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(11L)
                .setRowIndex(0)
                .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(mService)
                .setQueryId(queryId3)
                .setTimeMillis(101L)
                .setRowIndex(0)
                .build();
        long eventId3 = mDao.insertEvent(event3);

        List<JoinedEvent> result = mDao.readJoinedTableRows(0, 1000, mService);
        assertEquals(3, result.size());
        assertEquals(createExpectedJoinedEvent(event1, query1, eventId1, queryId1), result.get(0));
        assertEquals(createExpectedJoinedEvent(event2, query2, eventId2, queryId2), result.get(1));
        assertEquals(createExpectedJoinedEvent(event3, query3, eventId3, queryId3), result.get(2));

        result = mDao.readJoinedTableRows(0, 1000, new ComponentName("pkg", "cls"));
        assertEquals(0, result.size());

        result = mDao.readJoinedTableRows(500, 1000, mService);
        assertEquals(0, result.size());

        result = mDao.readJoinedTableRows(5, 1000, mService);
        assertEquals(2, result.size());
        assertEquals(createExpectedJoinedEvent(event2, query2, eventId2, queryId2), result.get(0));
        assertEquals(createExpectedJoinedEvent(event3, query3, eventId3, queryId3), result.get(1));
    }

    @Test
    public void testReadSingleQuery() {
        Query query1 = new Query.Builder()
                .setQueryId(1)
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        mDao.insertQuery(query1);
        assertEquals(query1, mDao.readSingleQueryRow(1, mService));
        assertNull(mDao.readSingleQueryRow(100, mService));
        assertNull(mDao.readSingleQueryRow(1, new ComponentName("pkg", "cls")));
    }

    @Test
    public void testReadSingleJoinedTableRow() {
        mDao.insertQuery(mTestQuery);
        mDao.insertEvent(mTestEvent);
        assertEquals(createExpectedJoinedEvent(mTestEvent, mTestQuery, 1, 1),
                mDao.readSingleJoinedTableRow(1, mService));
        assertNull(mDao.readSingleJoinedTableRow(100, mService));
        assertNull(mDao.readSingleJoinedTableRow(1, new ComponentName("pkg", "cls")));
    }

    @Test
    public void testReadEventStateNoEventState() {
        assertNull(mDao.getEventState(TASK_IDENTIFIER, mService));
    }


    @Test
    public void testInsertEventFKError() {
        assertEquals(-1, mDao.insertEvent(mTestEvent));
    }

    @Test
    public void testInsertQueryId() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(2, mDao.insertQuery(mTestQuery));
    }

    @Test
    public void testInsertEventExistingKey() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(1, mDao.insertEvent(mTestEvent));
        assertEquals(-1, mDao.insertEvent(mTestEvent));
    }


    private JoinedEvent createExpectedJoinedEvent(Event event, Query query, long eventId,
            long queryId) {
        if (event == null) {
            return new JoinedEvent.Builder()
                    .setService(query.getService())
                    .setQueryData(query.getQueryData())
                    .setQueryId(queryId)
                    .setQueryTimeMillis(query.getTimeMillis())
                    .build();
        }
        return new JoinedEvent.Builder()
                .setService(event.getService())
                .setQueryId(queryId)
                .setEventId(eventId)
                .setRowIndex(event.getRowIndex())
                .setType(event.getType())
                .setEventTimeMillis(event.getTimeMillis())
                .setQueryTimeMillis(query.getTimeMillis())
                .setEventData(event.getEventData())
                .setQueryData(query.getQueryData())
                .build();
    }
}
