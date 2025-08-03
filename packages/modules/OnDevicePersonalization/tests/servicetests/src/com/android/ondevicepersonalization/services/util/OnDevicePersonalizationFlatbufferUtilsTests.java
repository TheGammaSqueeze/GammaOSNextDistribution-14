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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;

import com.android.ondevicepersonalization.services.fbs.EventFields;
import com.android.ondevicepersonalization.services.fbs.QueryData;
import com.android.ondevicepersonalization.services.fbs.QueryFields;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationFlatbufferUtilsTests {
    private static final double DELTA = 0.001;

    @Test
    public void testCreateEventData() {
        ContentValues data = new ContentValues();
        data.put("a", 1);
        data.put("b", 2.0);
        data.put("c", "abc");
        byte[] blob = new byte[2];
        blob[0] = 1;
        blob[1] = 2;
        data.put("d", blob);
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(data);
        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(eventData));
        assertEquals(4, eventFields.data().entriesLength());
        for (int i = 0; i < 4; ++i) {
            boolean found = false;
            if ("a".equals(eventFields.data().entries(i).key())) {
                found = true;
                assertEquals(
                        OnDevicePersonalizationFlatbufferUtils.DATA_TYPE_INT,
                        eventFields.data().entries(i).type());
                assertEquals(
                        1, eventFields.data().entries(i).intValue());
            } else if ("b".equals(eventFields.data().entries(i).key())) {
                found = true;
                assertEquals(
                        OnDevicePersonalizationFlatbufferUtils.DATA_TYPE_DOUBLE,
                        eventFields.data().entries(i).type());
                assertEquals(
                        2.0, eventFields.data().entries(i).doubleValue(), DELTA);
            } else if ("c".equals(eventFields.data().entries(i).key())) {
                found = true;
                assertEquals(
                        OnDevicePersonalizationFlatbufferUtils.DATA_TYPE_STRING,
                        eventFields.data().entries(i).type());
                assertEquals("abc", eventFields.data().entries(i).stringValue());
            } else if ("d".equals(eventFields.data().entries(i).key())) {
                found = true;
                assertEquals(
                        OnDevicePersonalizationFlatbufferUtils.DATA_TYPE_BLOB,
                        eventFields.data().entries(i).type());
                assertEquals(
                        2, eventFields.data().entries(i).blobValueLength());
                assertEquals(
                        1, eventFields.data().entries(i).blobValue(0));
                assertEquals(
                        2, eventFields.data().entries(i).blobValue(1));
            }
            assertTrue(found);
        }
    }

    @Test
    public void testCreateEventDataNullInput() {
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(null);

        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(eventData));
        assertEquals(0, eventFields.data().entriesLength());
    }

    @Test
    public void testCreateQueryDataNullInput() {
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                null, null, null);

        QueryData queryData = QueryData.getRootAsQueryData(ByteBuffer.wrap(queryDataBytes));
        assertEquals(1, queryData.queryFieldsLength());
        QueryFields queryFields = queryData.queryFields(0);
        assertNull(queryFields.owner().packageName());
        assertNull(queryFields.owner().certDigest());
        assertEquals(0, queryFields.rowsLength());
    }

    @Test
    public void testCreateQueryData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row = new ContentValues();
        row.put("a", 1);
        rows.add(row);
        row = new ContentValues();
        row.put("b", 2);
        rows.add(row);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);

        QueryData queryData = QueryData.getRootAsQueryData(ByteBuffer.wrap(queryDataBytes));
        assertEquals(1, queryData.queryFieldsLength());
        QueryFields queryFields = queryData.queryFields(0);
        assertEquals("com.example.test", queryFields.owner().packageName());
        assertEquals("AABBCCDD", queryFields.owner().certDigest());
        assertEquals(2, queryFields.rowsLength());
        assertEquals("a", queryFields.rows(0).entries(0).key());
        assertEquals(1, queryFields.rows(0).entries(0).intValue());
        assertEquals("b", queryFields.rows(1).entries(0).key());
        assertEquals(2, queryFields.rows(1).entries(0).intValue());
    }

    @Test
    public void testGetContentValuesQueryData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);

        List<ContentValues> contentValuesList =
                OnDevicePersonalizationFlatbufferUtils.getContentValuesFromQueryData(
                        queryDataBytes);
        assertEquals(2, contentValuesList.size());
        assertEquals(row1, contentValuesList.get(0));
        assertEquals(row2, contentValuesList.get(1));

        assertEquals(row1, OnDevicePersonalizationFlatbufferUtils.getContentValuesRowFromQueryData(
                queryDataBytes, 0));
        assertEquals(row2, OnDevicePersonalizationFlatbufferUtils.getContentValuesRowFromQueryData(
                queryDataBytes, 1));
    }

    @Test
    public void testGetContentValuesLengthFromQueryData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);

        assertEquals(2, OnDevicePersonalizationFlatbufferUtils.getContentValuesLengthFromQueryData(
                queryDataBytes));
    }

    @Test
    public void testGetContentValuesFromEventData() {
        ContentValues data = new ContentValues();
        data.put("a", 1);
        data.put("b", 2.0);
        data.put("c", "abc");
        byte[] blob = new byte[2];
        blob[0] = 1;
        blob[1] = 2;
        data.put("d", blob);
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(data);
        ContentValues contentValues =
                OnDevicePersonalizationFlatbufferUtils.getContentValuesFromEventData(eventData);
        // Compare byte[] separately since ContentValues.equals does not do arrayEquals.
        assertArrayEquals(data.getAsByteArray("d"), contentValues.getAsByteArray("d"));
        data.remove("d");
        contentValues.remove("d");
        assertEquals(data, contentValues);
    }
}
