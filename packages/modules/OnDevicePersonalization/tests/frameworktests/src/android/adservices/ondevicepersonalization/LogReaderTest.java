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

package android.adservices.ondevicepersonalization;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit Tests of LogReader API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LogReaderTest {

    LogReader mLogReader;

    @Before
    public void setup() {
        mLogReader = new LogReader(
                IDataAccessService.Stub.asInterface(
                        new LogReaderTest.LocalDataService()));
    }

    @Test
    public void testGetRequestsSuccess() {
        List<RequestLogRecord> result = mLogReader.getRequests(
                Instant.ofEpochMilli(10), Instant.ofEpochMilli(100));
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getRows().size());
        assertEquals((int) (result.get(0).getRows().get(0).getAsInteger("a")), 1);
        assertEquals((int) (result.get(0).getRows().get(0).getAsInteger("b")), 1);
        assertEquals(1, result.get(1).getRows().size());
        assertEquals((int) (result.get(1).getRows().get(0).getAsInteger("a")), 1);
        assertEquals((int) (result.get(1).getRows().get(0).getAsInteger("b")), 1);
    }

    @Test
    public void testGetRequestsNullTimeError() {
        assertThrows(NullPointerException.class, () -> mLogReader.getRequests(
                null, Instant.ofEpochMilli(100)));
        assertThrows(NullPointerException.class, () -> mLogReader.getRequests(
                Instant.ofEpochMilli(100), null));
    }

    @Test
    public void testGetRequestsError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getRequests(
                Instant.ofEpochMilli(7), Instant.ofEpochMilli(100)));
    }

    @Test
    public void testGetRequestsNegativeTimeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequests(
                Instant.ofEpochMilli(-1), Instant.ofEpochMilli(100)));
    }

    @Test
    public void testGetRequestsBadTimeRangeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequests(
                Instant.ofEpochMilli(100), Instant.ofEpochMilli(100)));
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequests(
                Instant.ofEpochMilli(1000), Instant.ofEpochMilli(100)));
    }

    @Test
    public void testGetJoinedEventsSuccess() {
        List<EventLogRecord> result = mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(10), Instant.ofEpochMilli(100));
        assertEquals(2, result.size());
        assertEquals(result.get(0).getTimeMillis(), 30);
        assertEquals(result.get(0).getRequestLogRecord().getTimeMillis(), 20);
        assertEquals(result.get(0).getType(), 1);
        assertEquals((int) (result.get(0).getData().getAsInteger("a")), 1);
        assertEquals((int) (result.get(0).getData().getAsInteger("b")), 1);
        assertEquals(result.get(1).getTimeMillis(), 40);
        assertEquals(result.get(1).getRequestLogRecord().getTimeMillis(), 30);
        assertEquals(result.get(1).getType(), 2);
        assertEquals((int) (result.get(1).getData().getAsInteger("a")), 1);
        assertEquals((int) (result.get(1).getData().getAsInteger("b")), 1);
    }

    @Test
    public void testGetJoinedEventsError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(7), Instant.ofEpochMilli(100)));
    }

    @Test
    public void testGetJoinedEventsNullTimeError() {
        assertThrows(NullPointerException.class, () -> mLogReader.getJoinedEvents(
                null, Instant.ofEpochMilli(100)));
        assertThrows(NullPointerException.class, () -> mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(100), null));
    }

    @Test
    public void testGetJoinedEventsNegativeTimeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(-1), Instant.ofEpochMilli(100)));
    }

    @Test
    public void testGetJoinedEventsInputError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(100), Instant.ofEpochMilli(100)));
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getJoinedEvents(
                Instant.ofEpochMilli(1000), Instant.ofEpochMilli(100)));
    }

    public static class LocalDataService extends IDataAccessService.Stub {

        public LocalDataService() {
        }

        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_REQUESTS
                    || operation == Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS) {
                long[] timestamps = params.getLongArray(Constants.EXTRA_LOOKUP_KEYS);
                if (timestamps[0] == 7) {
                    // Raise expected error.
                    try {
                        callback.onError(Constants.STATUS_INTERNAL_ERROR);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                    return;
                }

                Bundle result = new Bundle();
                ContentValues values = new ContentValues();
                values.put("a", 1);
                values.put("b", 1);
                if (operation == Constants.DATA_ACCESS_OP_GET_REQUESTS) {
                    List<RequestLogRecord> records = new ArrayList<>();
                    records.add(new RequestLogRecord.Builder()
                            .setRequestId(1)
                            .addRow(values)
                            .build());
                    records.add(new RequestLogRecord.Builder()
                            .setRequestId(2)
                            .addRow(values)
                            .build());
                    result.putParcelable(Constants.EXTRA_RESULT,
                            new OdpParceledListSlice<RequestLogRecord>(records));
                } else if (operation == Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS) {
                    List<EventLogRecord> records = new ArrayList<>();
                    records.add(new EventLogRecord.Builder()
                            .setType(1)
                            .setTimeMillis(30)
                            .setData(values)
                            .setRequestLogRecord(new RequestLogRecord.Builder()
                                    .setRequestId(0)
                                    .addRow(values)
                                    .setTimeMillis(20)
                                    .build())
                            .build());
                    records.add(new EventLogRecord.Builder()
                            .setType(2)
                            .setTimeMillis(40)
                            .setData(values)
                            .setRequestLogRecord(new RequestLogRecord.Builder()
                                    .setRequestId(0)
                                    .addRow(values)
                                    .setTimeMillis(30)
                                    .build())
                            .build());
                    result.putParcelable(Constants.EXTRA_RESULT,
                            new OdpParceledListSlice<EventLogRecord>(records));
                }
                try {
                    callback.onSuccess(result);
                } catch (RemoteException e) {
                    // Ignored.
                }
            }
        }
    }
}
