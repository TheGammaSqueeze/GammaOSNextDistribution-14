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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.Record;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.util.Pair;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Helper for Activity Date Table. The table maps a record to a date on which there was a db write
 * for that record
 *
 * @hide
 */
public final class ActivityDateHelper extends DatabaseHelper {
    private static final String TABLE_NAME = "activity_date_table";
    private static final String EPOCH_DAYS_COLUMN_NAME = "epoch_days";
    private static final String RECORD_TYPE_ID_COLUMN_NAME = "record_type_id";

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile ActivityDateHelper sActivityDateHelper;

    private ActivityDateHelper() {}

    /**
     * Returns a requests representing the tables that should be created corresponding to this
     * helper
     */
    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo())
                .addUniqueConstraints(List.of(EPOCH_DAYS_COLUMN_NAME, RECORD_TYPE_ID_COLUMN_NAME));
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    /** Insert a new activity dates for the given records */
    @NonNull
    public void insertRecordDate(@NonNull List<RecordInternal<?>> recordInternals) {
        Objects.requireNonNull(recordInternals);

        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();

        List<UpsertTableRequest> upsertTableRequests = new ArrayList<>();
        recordInternals.forEach(
                (recordInternal) -> upsertTableRequests.add(getUpsertTableRequest(recordInternal)));

        transactionManager.insertOrIgnoreOnConflict(upsertTableRequests);
    }

    /** Returns a list of all dates with database writes for the given record types */
    @NonNull
    public List<LocalDate> getActivityDates(@NonNull List<Class<? extends Record>> recordTypes) {
        RecordMapper recordMapper = RecordMapper.getInstance();
        List<Integer> recordTypeIds =
                recordTypes.stream().map(recordMapper::getRecordType).collect(Collectors.toList());

        return readDates(
                new ReadTableRequest(TABLE_NAME)
                        .setWhereClause(
                                new WhereClauses(AND)
                                        .addWhereInIntsClause(
                                                RECORD_TYPE_ID_COLUMN_NAME, recordTypeIds))
                        .setColumnNames(List.of(EPOCH_DAYS_COLUMN_NAME))
                        .setDistinctClause(true));
    }

    public void reSyncForAllRecords() {
        List<Integer> recordTypeIds =
                RecordMapper.getInstance().getRecordIdToExternalRecordClassMap().keySet().stream()
                        .toList();

        reSyncByRecordTypeIds(recordTypeIds);
    }

    public void reSyncByRecordTypeIds(List<Integer> recordTypeIds) {
        List<UpsertTableRequest> upsertTableRequests = new ArrayList<>();
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();

        DeleteTableRequest deleteTableRequest =
                new DeleteTableRequest(TABLE_NAME)
                        .setIds(
                                RECORD_TYPE_ID_COLUMN_NAME,
                                recordTypeIds.stream().map(String::valueOf).toList());

        // Fetch updated dates from respective record table and update the activity dates cache.
        HashMap<Integer, List<Long>> recordTypeIdToEpochDays =
                fetchUpdatedDates(recordTypeIds, transactionManager);

        recordTypeIdToEpochDays.forEach(
                (recordTypeId, epochDays) ->
                        epochDays.forEach(
                                (epochDay) ->
                                        upsertTableRequests.add(
                                                getUpsertTableRequest(recordTypeId, epochDay))));

        transactionManager.runAsTransaction(
                db -> {
                    db.execSQL(deleteTableRequest.getDeleteCommand());
                    upsertTableRequests.forEach(
                            upsertTableRequest ->
                                    transactionManager.insertOrIgnore(db, upsertTableRequest));
                });
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getColumnInfo() {
        return Arrays.asList(
                new Pair<>(RecordHelper.PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT),
                new Pair<>(EPOCH_DAYS_COLUMN_NAME, INTEGER_NOT_NULL),
                new Pair<>(RECORD_TYPE_ID_COLUMN_NAME, INTEGER_NOT_NULL));
    }

    private HashMap<Integer, List<Long>> fetchUpdatedDates(
            List<Integer> recordTypeIds, TransactionManager transactionManager) {

        ReadTableRequest request;
        RecordHelper<?> recordHelper;
        HashMap<Integer, List<Long>> recordTypeIdToEpochDays = new HashMap<>();
        for (int recordTypeId : recordTypeIds) {
            recordHelper = RecordHelperProvider.getInstance().getRecordHelper(recordTypeId);
            request =
                    new ReadTableRequest(recordHelper.getMainTableName())
                            .setColumnNames(List.of(recordHelper.getPeriodGroupByColumnName()))
                            .setDistinctClause(true);
            try (Cursor cursor = transactionManager.read(request)) {
                List<Long> distinctDates = new ArrayList<>();
                while (cursor.moveToNext()) {
                    long epochDay =
                            getCursorLong(cursor, recordHelper.getPeriodGroupByColumnName());
                    distinctDates.add(epochDay);
                }
                recordTypeIdToEpochDays.put(recordTypeId, distinctDates);
            }
        }
        return recordTypeIdToEpochDays;
    }

    @NonNull
    private ContentValues getContentValues(int recordTypeId, long epochDays) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EPOCH_DAYS_COLUMN_NAME, epochDays);
        contentValues.put(RECORD_TYPE_ID_COLUMN_NAME, recordTypeId);

        return contentValues;
    }

    /**
     * Reads the dates stored in the HealthConnect database.
     *
     * @param request a read request.
     * @return Cursor from table based on ids.
     */
    private List<LocalDate> readDates(@NonNull ReadTableRequest request) {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(request)) {
            List<LocalDate> dates = new ArrayList<>();
            while (cursor.moveToNext()) {
                long epochDay = getCursorLong(cursor, EPOCH_DAYS_COLUMN_NAME);
                dates.add(LocalDate.ofEpochDay(epochDay));
            }
            return dates;
        }
    }

    /** Returns an instance of this class */
    public static synchronized ActivityDateHelper getInstance() {
        if (sActivityDateHelper == null) {
            sActivityDateHelper = new ActivityDateHelper();
        }

        return sActivityDateHelper;
    }

    /** Creates UpsertTableRequest to insert into activity_date_table table. */
    public UpsertTableRequest getUpsertTableRequest(int recordTypeId, long epochDays) {
        return new UpsertTableRequest(TABLE_NAME, getContentValues(recordTypeId, epochDays));
    }

    /** Creates UpsertTableRequest to insert into activity_date_table table from recordInternal. */
    public UpsertTableRequest getUpsertTableRequest(RecordInternal<?> recordInternal) {
        return getUpsertTableRequest(
                recordInternal.getRecordType(),
                ChronoUnit.DAYS.between(LocalDate.EPOCH, recordInternal.getLocalDate()));
    }
}
