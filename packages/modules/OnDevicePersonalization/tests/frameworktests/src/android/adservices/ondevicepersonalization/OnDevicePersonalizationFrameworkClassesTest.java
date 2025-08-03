/*
 * Copyright 2022 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Unit Tests of Framework API Classes.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OnDevicePersonalizationFrameworkClassesTest {
    /**
     * Tests that the ExecuteInput object serializes correctly.
     */
    @Test
    public void testExecuteInput() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("a", 5);
        ExecuteInputParcel data = new ExecuteInputParcel.Builder()
                .setAppPackageName("com.example.test")
                .setSerializedAppParams(new ByteArrayParceledSlice(
                        PersistableBundleUtils.toByteArray(bundle)))
                .build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ExecuteInputParcel data2 = ExecuteInputParcel.CREATOR.createFromParcel(parcel);
        ExecuteInput result = new ExecuteInput(data2);

        assertEquals("com.example.test", result.getAppPackageName());
        assertEquals(5, result.getAppParams().getInt("a"));
    }

    /**
     * Tests that the ExecuteOutput object serializes correctly.
     */
    @Test
    public void testExecuteOutput() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        ExecuteOutput data =
                new ExecuteOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                    .setRenderingConfig(new RenderingConfig.Builder().addKey("abc").build())
                    .addEventLogRecord(new EventLogRecord.Builder().setType(1).build())
                    .build();
        ExecuteOutputParcel result = new ExecuteOutputParcel(data);

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ExecuteOutputParcel result2 = ExecuteOutputParcel.CREATOR.createFromParcel(parcel);

        assertEquals(
                5, result2.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals("abc", result2.getRenderingConfig().getKeys().get(0));
        assertEquals(1, result2.getEventLogRecords().get(0).getType());
    }

    /**
     * Tests that the RenderInput object serializes correctly.
     */
    @Test
    public void testRenderInput() {
        RenderInputParcel data = new RenderInputParcel.Builder()
                .setWidth(10)
                .setHeight(20)
                .setRenderingConfig(new RenderingConfig.Builder().addKey("abc").build())
                .build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RenderInputParcel data2 = RenderInputParcel.CREATOR.createFromParcel(parcel);
        RenderInput result = new RenderInput(data2);

        assertEquals(10, result.getWidth());
        assertEquals(20, result.getHeight());
        assertEquals("abc", result.getRenderingConfig().getKeys().get(0));
    }

    /**
     * Tests that the RenderOutput object serializes correctly.
     */
    @Test
    public void testRenderOutput() {
        RenderOutput data = new RenderOutput.Builder().setContent("abc").build();
        RenderOutputParcel result = new RenderOutputParcel(data);

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RenderOutputParcel result2 = RenderOutputParcel.CREATOR.createFromParcel(parcel);

        assertEquals("abc", result2.getContent());
    }

    /**
     * Tests that the DownloadOutput object serializes correctly.
     */
    @Test
    public void teetDownloadCompletedOutput() {
        DownloadCompletedOutput data = new DownloadCompletedOutput.Builder()
                .addRetainedKey("abc").addRetainedKey("def").build();
        DownloadCompletedOutputParcel result =
                new DownloadCompletedOutputParcel(data);

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DownloadCompletedOutputParcel result2 =
                DownloadCompletedOutputParcel.CREATOR.createFromParcel(parcel);

        assertEquals("abc", result2.getRetainedKeys().get(0));
        assertEquals("def", result2.getRetainedKeys().get(1));
    }

    /**
     * Tests that the EventInput object serializes correctly.
     */
    @Test
    public void testEventInput() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("x", 3);
        ArrayList<ContentValues> rows = new ArrayList<>();
        rows.add(new ContentValues());
        rows.get(0).put("a", 5);
        EventInputParcel data = new EventInputParcel.Builder()
                .setParameters(params)
                .setRequestLogRecord(
                    new RequestLogRecord.Builder()
                        .setRows(rows)
                        .build())
                .build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        EventInputParcel data2 = EventInputParcel.CREATOR.createFromParcel(parcel);
        EventInput result = new EventInput(data2);

        assertEquals(3, result.getParameters().getInt("x"));
        assertEquals(
                5, result.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
    }

    /**
     * Tests that the EventOutput object serializes correctly.
     */
    @Test
    public void testEventOutput() {
        ContentValues data = new ContentValues();
        data.put("a", 3);
        EventOutput output = new EventOutput.Builder()
                .setEventLogRecord(
                    new EventLogRecord.Builder()
                        .setType(5)
                        .setRowIndex(6)
                        .setData(data)
                        .build())
                .build();
        EventOutputParcel result = new EventOutputParcel(output);

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        EventOutputParcel result2 = EventOutputParcel.CREATOR.createFromParcel(parcel);

        assertEquals(5, result2.getEventLogRecord().getType());
        assertEquals(6, result2.getEventLogRecord().getRowIndex());
        assertEquals(3, result2.getEventLogRecord().getData().getAsInteger("a").intValue());
    }

    /** Test for RenderingConfig class. */
    @Test
    public void testRenderingConfig() {
        RenderingConfig data = new RenderingConfig.Builder().addKey("a").addKey("b").build();
        assertEquals(2, data.getKeys().size());
        assertEquals("a", data.getKeys().get(0));
        assertEquals("b", data.getKeys().get(1));
    }

    /** Test for RequestLogRecord class. */
    @Test
    public void testRequestLogRecord() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row = new ContentValues();
        row.put("a", 5);
        rows.add(row);
        row = new ContentValues();
        row.put("b", 6);
        rows.add(row);
        RequestLogRecord logRecord = new RequestLogRecord.Builder().setRows(rows)
                .setRequestId(1).build();
        assertEquals(2, logRecord.getRows().size());
        assertEquals(5, logRecord.getRows().get(0).getAsInteger("a").intValue());
        assertEquals(6, logRecord.getRows().get(1).getAsInteger("b").intValue());
        assertEquals(1, logRecord.getRequestId());
    }

    /** Test for RequestLogRecord class. */
    @Test
    public void testEventLogRecord() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        EventLogRecord logRecord = new EventLogRecord.Builder()
                .setType(1)
                .setRowIndex(2)
                .setData(row)
                .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                .build();
        assertEquals(1, logRecord.getType());
        assertEquals(2, logRecord.getRowIndex());
        assertEquals(5, logRecord.getData().getAsInteger("a").intValue());
        assertEquals(5, logRecord.getRequestLogRecord().getRows()
                .get(0).getAsInteger("a").intValue());
    }
}
