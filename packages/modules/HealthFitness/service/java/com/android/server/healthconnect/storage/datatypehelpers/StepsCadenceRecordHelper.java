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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.STEPS_CADENCE_RECORD_RATE_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.STEPS_CADENCE_RECORD_RATE_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.STEPS_CADENCE_RECORD_RATE_MIN;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.StepsCadenceRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Helper class for StepsCadenceRecord.
 *
 * @hide
 */
public class StepsCadenceRecordHelper
        extends SeriesRecordHelper<
                StepsCadenceRecordInternal, StepsCadenceRecordInternal.StepsCadenceRecordSample> {
    public static final int NUM_LOCAL_COLUMNS = 2;
    private static final String TABLE_NAME = "StepsCadenceRecordTable";
    private static final String SERIES_TABLE_NAME = "steps_cadence_record_table";
    private static final String RATE_COLUMN_NAME = "rate";
    private static final String EPOCH_MILLIS_COLUMN_NAME = "epoch_millis";

    public StepsCadenceRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE);
    }

    @Override
    String getMainTableName() {
        return TABLE_NAME;
    }

    @Override
    List<Pair<String, String>> getSeriesRecordColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>(NUM_LOCAL_COLUMNS);
        columnInfo.add(new Pair<>(RATE_COLUMN_NAME, REAL));
        columnInfo.add(new Pair<>(EPOCH_MILLIS_COLUMN_NAME, INTEGER));
        return columnInfo;
    }

    @Override
    String getSeriesDataTableName() {
        return SERIES_TABLE_NAME;
    }
    /** Populates the {@code record} with values specific to datatype */
    @Override
    void populateSpecificValues(
            @NonNull Cursor seriesTableCursor, StepsCadenceRecordInternal record) {
        HashSet<StepsCadenceRecordInternal.StepsCadenceRecordSample> stepsCadenceRecordSampleSet =
                new HashSet<>();
        UUID uuid = getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME);
        do {
            stepsCadenceRecordSampleSet.add(
                    new StepsCadenceRecordInternal.StepsCadenceRecordSample(
                            getCursorDouble(seriesTableCursor, RATE_COLUMN_NAME),
                            getCursorLong(seriesTableCursor, EPOCH_MILLIS_COLUMN_NAME)));
        } while (seriesTableCursor.moveToNext()
                && uuid.equals(getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        seriesTableCursor.moveToPrevious();
        record.setSamples(stepsCadenceRecordSampleSet);
    }

    @Override
    void populateSampleTo(
            ContentValues contentValues,
            StepsCadenceRecordInternal.StepsCadenceRecordSample stepsCadenceRecord) {
        contentValues.put(RATE_COLUMN_NAME, stepsCadenceRecord.getRate());
        contentValues.put(EPOCH_MILLIS_COLUMN_NAME, stepsCadenceRecord.getEpochMillis());
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case STEPS_CADENCE_RECORD_RATE_AVG:
            case STEPS_CADENCE_RECORD_RATE_MIN:
            case STEPS_CADENCE_RECORD_RATE_MAX:
                return new AggregateResult<>(
                                results.getDouble(results.getColumnIndex(RATE_COLUMN_NAME)))
                        .setZoneOffset(getZoneOffset(results));
            default:
                return null;
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case STEPS_CADENCE_RECORD_RATE_AVG:
            case STEPS_CADENCE_RECORD_RATE_MIN:
            case STEPS_CADENCE_RECORD_RATE_MAX:
                return new AggregateParams(
                                SERIES_TABLE_NAME, Collections.singletonList(RATE_COLUMN_NAME))
                        .setJoin(
                                new SqlJoin(
                                        SERIES_TABLE_NAME,
                                        TABLE_NAME,
                                        PARENT_KEY_COLUMN_NAME,
                                        PRIMARY_COLUMN_NAME));
            default:
                return null;
        }
    }
}
