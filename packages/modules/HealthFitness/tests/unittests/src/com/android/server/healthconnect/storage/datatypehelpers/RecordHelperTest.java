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

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.Constants.MAXIMUM_ALLOWED_CURSOR_COUNT;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;

import static com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper.STEPS_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.database.Cursor;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.aidl.ReadRecordsRequestParcel;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.StepsRecordInternal;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class RecordHelperTest {
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Rule public final HealthConnectDatabaseTestRule testRule = new HealthConnectDatabaseTestRule();
    private TransactionTestUtils mTransactionTestUtils;

    private TransactionManager mTransactionManager;

    @Before
    public void setup() throws Exception {
        HealthConnectUserContext context = testRule.getUserContext();
        mTransactionManager = TransactionManager.getInstance(context);
        DatabaseHelper.clearAllData(mTransactionManager);
        mTransactionTestUtils = new TransactionTestUtils(context, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
    }

    @After
    public void tearDown() {
        DatabaseHelper.clearAllData(mTransactionManager);
        TransactionManager.clearInstance();
    }

    @Test
    public void getInternalRecords_insertThenRead_recordReturned() {
        RecordHelper<?> helper = new StepsRecordHelper();
        String uid =
                mTransactionTestUtils
                        .insertRecords(TEST_PACKAGE_NAME, createStepsRecord(4000, 5000, 100))
                        .get(0);
        ReadTableRequest request = new ReadTableRequest(STEPS_TABLE_NAME);
        try (Cursor cursor = mTransactionManager.read(request)) {
            List<RecordInternal<?>> records = helper.getInternalRecords(cursor);
            assertThat(records).hasSize(1);

            StepsRecordInternal record = (StepsRecordInternal) records.get(0);
            assertThat(record.getUuid()).isEqualTo(UUID.fromString(uid));
            assertThat(record.getStartTimeInMillis()).isEqualTo(4000);
            assertThat(record.getEndTimeInMillis()).isEqualTo(5000);
            assertThat(record.getCount()).isEqualTo(100);
        }
    }

    @Test
    public void getInternalRecords_requestSizeMoreThanRecordNumber_recordsReturned() {
        RecordHelper<?> helper = new StepsRecordHelper();
        String uid =
                mTransactionTestUtils
                        .insertRecords(TEST_PACKAGE_NAME, createStepsRecord(4000, 5000, 100))
                        .get(0);
        ReadTableRequest request = new ReadTableRequest(STEPS_TABLE_NAME);
        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            List<RecordInternal<?>> records = helper.getInternalRecords(cursor);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uid));
        }
    }

    @Test
    public void getInternalRecords_cursorHasTooManyData_throws() {
        RecordHelper<?> helper = new StepsRecordHelper();
        int startTime = 9527;
        List<RecordInternal<?>> records = new ArrayList<>(MAXIMUM_ALLOWED_CURSOR_COUNT + 1);
        for (int i = 0; i <= MAXIMUM_ALLOWED_CURSOR_COUNT; i++) {
            records.add(createStepsRecord(startTime + i, startTime + i + 1, 100));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        ReadTableRequest request = new ReadTableRequest(STEPS_TABLE_NAME);
        try (Cursor cursor = mTransactionManager.read(request)) {
            Throwable thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> helper.getInternalRecords(cursor));
            assertThat(thrown.getMessage()).contains("Too many records in the cursor.");
        }
    }

    @Test
    public void getNextInternalRecordsPageAndToken_zeroOffsetDesc_correctResults() {
        RecordHelper<?> helper = new StepsRecordHelper();
        int pageSize = 1;
        boolean isAscending = false;
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        "client.id1",
                        /* startTimeMillis= */ 4000,
                        /* endTimeMillis= */ 4500,
                        /* stepsCount= */ 1000),
                createStepsRecord(
                        "client.id2",
                        /* startTimeMillis= */ 6000,
                        /* endTimeMillis= */ 7000,
                        /* stepsCount= */ 500));

        long expectedTimestamp = 4000L;
        int expectedOffset = 0;
        PageTokenWrapper expectedPageToken =
                PageTokenWrapper.of(isAscending, expectedTimestamp, expectedOffset);

        OrderByClause orderByStartTime =
                new OrderByClause().addOrderByClause(helper.getStartTimeColumnName(), isAscending);
        ReadTableRequest request1 =
                new ReadTableRequest(STEPS_TABLE_NAME)
                        .setOrderBy(orderByStartTime)
                        .setLimit(pageSize + 1);
        try (Cursor cursor = mTransactionManager.read(request1)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> page1 =
                    helper.getNextInternalRecordsPageAndToken(
                            cursor, pageSize, PageTokenWrapper.ofAscending(isAscending));
            assertThat(page1.first).hasSize(pageSize);
            assertThat(page1.first.get(0).getClientRecordId()).isEqualTo("client.id2");
            assertThat(page1.second).isEqualTo(expectedPageToken);
        }

        WhereClauses whereClause =
                new WhereClauses(AND)
                        .addWhereLessThanOrEqualClause(
                                helper.getStartTimeColumnName(), expectedTimestamp);
        ReadTableRequest request2 =
                new ReadTableRequest(STEPS_TABLE_NAME)
                        .setOrderBy(orderByStartTime)
                        .setWhereClause(whereClause)
                        .setLimit(pageSize + 1 + expectedOffset);
        try (Cursor cursor = mTransactionManager.read(request2)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> page2 =
                    helper.getNextInternalRecordsPageAndToken(cursor, pageSize, expectedPageToken);
            assertThat(page2.first).hasSize(pageSize);
            assertThat(page2.first.get(0).getClientRecordId()).isEqualTo("client.id1");
            assertThat(page2.second).isEqualTo(EMPTY_PAGE_TOKEN);
        }
    }

    @Test
    public void getNextInternalRecordsPageAndToken_sameStartTimeAsc_correctResults() {
        RecordHelper<?> helper = new StepsRecordHelper();
        int pageSize = 3;
        boolean isAscending = true;
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                // in page 1
                createStepsRecord(
                        "id1",
                        /* startTimeMillis= */ 3000,
                        /* endTimeMillis= */ 45000,
                        /* stepsCount= */ 1000),
                createStepsRecord(
                        "id2",
                        /* startTimeMillis= */ 4000,
                        /* endTimeMillis= */ 5000,
                        /* stepsCount= */ 100),
                createStepsRecord(
                        "id3",
                        /* startTimeMillis= */ 4000,
                        /* endTimeMillis= */ 6000,
                        /* stepsCount= */ 200),
                // in page 2
                createStepsRecord(
                        "id4",
                        /* startTimeMillis= */ 4000,
                        /* endTimeMillis= */ 7000,
                        /* stepsCount= */ 300),
                createStepsRecord(
                        "id5",
                        /* startTimeMillis= */ 5000,
                        /* endTimeMillis= */ 6000,
                        /* stepsCount= */ 400),
                createStepsRecord(
                        "id6",
                        /* startTimeMillis= */ 6000,
                        /* endTimeMillis= */ 7000,
                        /* stepsCount= */ 500));

        long expectedTimestamp = 4000L;
        int expectedOffset = 2;
        PageTokenWrapper expectedPageToken =
                PageTokenWrapper.of(isAscending, expectedTimestamp, expectedOffset);

        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.ofEpochMilli(3000))
                        .setEndTime(Instant.ofEpochMilli(10000))
                        .build();
        ReadRecordsRequestUsingFilters<StepsRecord> readRequest1 =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(filter)
                        .setPageSize(pageSize)
                        .build();
        ReadTableRequest request1 =
                getReadTableRequest(helper, readRequest1.toReadRecordsRequestParcel());
        try (Cursor cursor = mTransactionManager.read(request1)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> page1 =
                    helper.getNextInternalRecordsPageAndToken(
                            cursor, pageSize, PageTokenWrapper.ofAscending(isAscending));
            assertThat(page1.first).hasSize(3);
            assertThat(page1.first.get(0).getClientRecordId()).isEqualTo("id1");
            assertThat(page1.first.get(1).getClientRecordId()).isEqualTo("id2");
            assertThat(page1.first.get(2).getClientRecordId()).isEqualTo("id3");
            assertThat(page1.second).isEqualTo(expectedPageToken);
        }

        ReadRecordsRequestUsingFilters<StepsRecord> readRequest2 =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(filter)
                        .setPageSize(pageSize)
                        .setPageToken(expectedPageToken.encode())
                        .build();
        ReadTableRequest request2 =
                getReadTableRequest(helper, readRequest2.toReadRecordsRequestParcel());
        try (Cursor cursor = mTransactionManager.read(request2)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> page2 =
                    helper.getNextInternalRecordsPageAndToken(cursor, pageSize, expectedPageToken);
            assertThat(page2.first).hasSize(pageSize);
            assertThat(page2.first.get(0).getClientRecordId()).isEqualTo("id4");
            assertThat(page2.first.get(1).getClientRecordId()).isEqualTo("id5");
            assertThat(page2.first.get(2).getClientRecordId()).isEqualTo("id6");
            assertThat(page2.second).isEqualTo(EMPTY_PAGE_TOKEN);
        }
    }

    @Test
    public void getNextInternalRecordsPageAndToken_wrongOffsetPageToken_skipSameStartTimeRecords() {
        RecordHelper<?> helper = new StepsRecordHelper();
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord("id1", 4000, 5000, 100),
                createStepsRecord("id2", 5000, 6000, 100));
        PageTokenWrapper incorrectToken = PageTokenWrapper.of(true, 4000, 2);
        ReadTableRequest request = new ReadTableRequest(STEPS_TABLE_NAME);
        try (Cursor cursor = mTransactionManager.read(request)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> result =
                    helper.getNextInternalRecordsPageAndToken(
                            cursor, /* requestSize= */ 2, incorrectToken);
            // skip the first record, but preserve the second because start time is different
            assertThat(result.first).hasSize(1);
            assertThat(result.first.get(0).getClientRecordId()).isEqualTo("id2");
            assertThat(result.second).isEqualTo(EMPTY_PAGE_TOKEN);
        }
    }

    private static ReadTableRequest getReadTableRequest(
            RecordHelper<?> helper, ReadRecordsRequestParcel request) {
        return helper.getReadTableRequest(
                request,
                TEST_PACKAGE_NAME,
                /* enforceSelfRead= */ false,
                /* startDateAccess= */ 0,
                /* grantedExtraReadPermissions= */ Set.of(),
                /* isInForeground= */ true);
    }
}
