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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.SLEEP_SESSION_DURATION_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.HealthConnectException;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.SleepSessionRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Record helper for Sleep session.
 *
 * @hide
 */
public final class SleepSessionRecordHelper
        extends IntervalRecordHelper<SleepSessionRecordInternal> {
    private static final String SLEEP_SESSION_RECORD_TABLE_NAME = "sleep_session_record_table";

    // Sleep session columns names
    private static final String NOTES_COLUMN_NAME = "notes";
    private static final String TITLE_COLUMN_NAME = "title";

    private static final int NO_SLEEP_TABLE_DB_VERSION = 1;

    public SleepSessionRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION);
    }

    /** Returns the table name to be created corresponding to this helper */
    @Override
    String getMainTableName() {
        return SLEEP_SESSION_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        if (aggregateRequest.getAggregationTypeIdentifier() == SLEEP_SESSION_DURATION_TOTAL) {
            ArrayList<String> sessionColumns =
                    new ArrayList<>(super.getPriorityAggregationColumnNames());
            sessionColumns.add(SleepStageRecordHelper.getStartTimeColumnName());
            sessionColumns.add(SleepStageRecordHelper.getEndTimeColumnName());
            return new AggregateParams(SLEEP_SESSION_RECORD_TABLE_NAME, sessionColumns)
                    .setJoin(
                            SleepStageRecordHelper.getJoinForDurationAggregation(
                                    getMainTableName()))
                    .setPriorityAggregationExtraParams(
                            new AggregateParams.PriorityAggregationExtraParams(
                                    SleepStageRecordHelper.getStartTimeColumnName(),
                                    SleepStageRecordHelper.getEndTimeColumnName()));
        }
        return null;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull SleepSessionRecordInternal sleepSessionRecord) {
        UUID uuid = getCursorUUID(cursor, UUID_COLUMN_NAME);
        sleepSessionRecord.setNotes(getCursorString(cursor, NOTES_COLUMN_NAME));
        sleepSessionRecord.setTitle(getCursorString(cursor, TITLE_COLUMN_NAME));

        do {
            // Populate stages from each row.
            sleepSessionRecord.addSleepStage(
                    SleepStageRecordHelper.populateStageIfRecorded(cursor));
        } while (cursor.moveToNext() && uuid.equals(getCursorUUID(cursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        cursor.moveToPrevious();
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull SleepSessionRecordInternal sleepSessionRecord) {
        contentValues.put(NOTES_COLUMN_NAME, sleepSessionRecord.getNotes());
        contentValues.put(TITLE_COLUMN_NAME, sleepSessionRecord.getTitle());
    }

    @Override
    List<CreateTableRequest> getChildTableCreateRequests() {
        return Collections.singletonList(
                SleepStageRecordHelper.getCreateStagesTableRequest(getMainTableName()));
    }

    @Override
    public boolean isRecordOperationsEnabled() {
        return HealthConnectDeviceConfigManager.getInitialisedInstance()
                .isSessionDatatypeFeatureEnabled();
    }

    @Override
    List<UpsertTableRequest> getChildTableUpsertRequests(
            @NonNull SleepSessionRecordInternal record) {
        if (record.getSleepStages() != null) {
            return SleepStageRecordHelper.getStagesUpsertRequests(record.getSleepStages());
        }

        return Collections.emptyList();
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(NOTES_COLUMN_NAME, TEXT_NULL), new Pair<>(TITLE_COLUMN_NAME, TEXT_NULL));
    }

    @Override
    SqlJoin getJoinForReadRequest() {
        return SleepStageRecordHelper.getJoinReadRequest(getMainTableName());
    }

    @Override
    public void checkRecordOperationsAreEnabled(RecordInternal<?> recordInternal) {
        super.checkRecordOperationsAreEnabled(recordInternal);
        if (!isRecordOperationsEnabled()) {
            throw new HealthConnectException(
                    HealthConnectException.ERROR_UNSUPPORTED_OPERATION,
                    "Writing sleep sessions is not supported.");
        }
    }
}
