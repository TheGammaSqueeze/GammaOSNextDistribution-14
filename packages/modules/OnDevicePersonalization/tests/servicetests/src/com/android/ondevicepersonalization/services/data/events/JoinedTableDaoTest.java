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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class JoinedTableDaoTest {

    private static final String SERVICE_CLASS = "TestClass";
    private static final int EVENT_TYPE_B2D = 1;
    private static final int EVENT_TYPE_CLICK = 2;
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private EventsDao mDao;
    private ComponentName mService;

    @Before
    public void setup() {
        mDao = EventsDao.getInstanceForTest(mContext);
        mService = new ComponentName(mContext.getPackageName(), SERVICE_CLASS);
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
    public void invalidProvidedColumns() {
        List<ColumnSchema> columnSchemaList = new ArrayList<>();
        columnSchemaList.add(new ColumnSchema.Builder().setName(
                JoinedTableDao.SERVICE_NAME_COL).setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
        assertThrows(IllegalArgumentException.class,
                () -> new JoinedTableDao(columnSchemaList, 0, 0, mContext));
    }

    @Test
    public void emptyColumns() {
        List<ColumnSchema> columnSchemaList = new ArrayList<>();
        assertThrows(IllegalArgumentException.class,
                () -> new JoinedTableDao(columnSchemaList, 0, 0, mContext));
    }

    @Test
    public void duplicateProvidedColumnNames() {
        List<ColumnSchema> columnSchemaList = new ArrayList<>();
        columnSchemaList.add(new ColumnSchema.Builder().setName("ColumnName").setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
        columnSchemaList.add(new ColumnSchema.Builder().setName("ColumnName").setType(
                ColumnSchema.SQL_DATA_TYPE_BLOB).build());
        assertThrows(IllegalArgumentException.class,
                () -> new JoinedTableDao(columnSchemaList, 0, 0, mContext));
    }

    @Test
    public void testRawQuery() {
        insertEventAndQueryData();

        List<ColumnSchema> columnSchemaList = new ArrayList<>(
                JoinedTableDao.ODP_PROVIDED_COLUMNS.values());
        columnSchemaList.add(new ColumnSchema.Builder().setName("eventCol1").setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
        columnSchemaList.add(new ColumnSchema.Builder().setName("eventCol2").setType(
                ColumnSchema.SQL_DATA_TYPE_TEXT).build());
        columnSchemaList.add(new ColumnSchema.Builder().setName("eventCol4").setType(
                ColumnSchema.SQL_DATA_TYPE_REAL).build());
        columnSchemaList.add(new ColumnSchema.Builder().setName("queryCol1").setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build());

        JoinedTableDao joinedTableDao = new JoinedTableDao(columnSchemaList, 0, 0, mContext);
        try (Cursor cursor = joinedTableDao.rawQuery(
                "SELECT * FROM " + JoinedTableDao.TABLE_NAME + " ORDER BY ROWID")) {
            // Assert two rows for the two joined events. two rows for the query.
            assertEquals(4, cursor.getCount());
            for (int i = 0; i < 4; i++) {
                cursor.moveToNext();
                String serviceName = cursor.getString(
                        cursor.getColumnIndexOrThrow(JoinedTableDao.SERVICE_NAME_COL));
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(JoinedTableDao.TYPE_COL));
                long eventTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(JoinedTableDao.EVENT_TIME_MILLIS_COL));
                long queryTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(JoinedTableDao.QUERY_TIME_MILLIS_COL));
                int eventCol1 = cursor.getInt(cursor.getColumnIndexOrThrow("eventCol1"));
                String eventCol2 = cursor.getString(cursor.getColumnIndexOrThrow("eventCol2"));
                double eventCol4 = cursor.getDouble(cursor.getColumnIndexOrThrow("eventCol4"));
                int queryCol1 = cursor.getInt(cursor.getColumnIndexOrThrow("queryCol1"));
                assertThrows(IllegalArgumentException.class,
                        () -> cursor.getColumnIndexOrThrow("eventCol3"));
                assertThrows(IllegalArgumentException.class,
                        () -> cursor.getColumnIndexOrThrow("random"));
                assertThrows(IllegalArgumentException.class,
                        () -> cursor.getColumnIndexOrThrow("someCol"));
                String expectedServiceName = DbUtils.toTableValue(mService);
                if (i == 0) {
                    assertEquals(expectedServiceName, serviceName);
                    assertEquals(EVENT_TYPE_B2D, type);
                    assertEquals(1L, eventTimeMillis);
                    assertEquals(1L, queryTimeMillis);
                    assertEquals(100, eventCol1);
                    assertEquals("helloWorld", eventCol2);
                    assertEquals(0.0, eventCol4, 0.001);
                    assertEquals(1, queryCol1);
                } else if (i == 1) {
                    assertEquals(expectedServiceName, serviceName);
                    assertEquals(EVENT_TYPE_CLICK, type);
                    assertEquals(2L, eventTimeMillis);
                    assertEquals(1L, queryTimeMillis);
                    assertEquals(50, eventCol1);
                    assertEquals("helloEarth", eventCol2);
                    assertEquals(2.0, eventCol4, 0.001);
                    assertEquals(2, queryCol1);
                } else if (i == 2) {
                    assertEquals(expectedServiceName, serviceName);
                    assertEquals(0L, type);
                    assertEquals(0L, eventTimeMillis);
                    assertEquals(1L, queryTimeMillis);
                    assertEquals(0, eventCol1);
                    assertNull(eventCol2);
                    assertEquals(0.0, eventCol4, 0.001);
                    assertEquals(1, queryCol1);
                } else if (i == 3) {
                    assertEquals(expectedServiceName, serviceName);
                    assertEquals(0L, type);
                    assertEquals(0L, eventTimeMillis);
                    assertEquals(1L, queryTimeMillis);
                    assertEquals(0, eventCol1);
                    assertNull(eventCol2);
                    assertEquals(0.0, eventCol4, 0.001);
                    assertEquals(2, queryCol1);
                }
            }
        }
    }

    private void insertEventAndQueryData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row = new ContentValues();
        row.put("queryCol1", 1);
        rows.add(row);
        row = new ContentValues();
        row.put("queryCol1", 2);
        rows.add(row);
        Query query = new Query.Builder()
                .setTimeMillis(1L)
                .setService(mService)
                .setQueryData(OnDevicePersonalizationFlatbufferUtils.createQueryData(
                        DbUtils.toTableValue(mService), "AABBCCDD", rows))
                .build();
        long queryId = mDao.insertQuery(query);

        ContentValues eventData = new ContentValues();
        eventData.put("eventCol1", 100);
        eventData.put("eventCol2", "helloWorld");
        eventData.put("eventCol3", "unused");
        eventData.put("eventCol4", "wrong_type");
        eventData.put("random", 20);
        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData(OnDevicePersonalizationFlatbufferUtils.createEventData(eventData))
                .setService(mService)
                .setQueryId(queryId)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        mDao.insertEvent(event1);

        ContentValues eventData2 = new ContentValues();
        eventData2.put("eventCol1", 50);
        eventData2.put("eventCol2", "helloEarth");
        eventData2.put("eventCol3", "unused");
        eventData2.put("eventCol4", 2.0);
        eventData2.put("someCol", 600);
        Event event2 = new Event.Builder()
                .setType(EVENT_TYPE_CLICK)
                .setEventData(OnDevicePersonalizationFlatbufferUtils.createEventData(eventData2))
                .setService(mService)
                .setQueryId(queryId)
                .setTimeMillis(2L)
                .setRowIndex(1)
                .build();
        mDao.insertEvent(event2);
    }
}
