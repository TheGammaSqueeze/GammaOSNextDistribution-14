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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_MIN;

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
import android.health.connect.internal.datatypes.PowerRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Helper class for PowerRecord.
 *
 * @hide
 */
public class PowerRecordHelper
        extends SeriesRecordHelper<PowerRecordInternal, PowerRecordInternal.PowerRecordSample> {
    public static final int NUM_LOCAL_COLUMNS = 1;
    private static final String TABLE_NAME = "PowerRecordTable";
    private static final String SERIES_TABLE_NAME = "power_record_table";
    private static final String POWER_COLUMN_NAME = "power";
    private static final String EPOCH_MILLIS_COLUMN_NAME = "epoch_millis";

    public PowerRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_POWER);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public final AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case POWER_RECORD_POWER_MIN:
            case POWER_RECORD_POWER_MAX:
            case POWER_RECORD_POWER_AVG:
                return new AggregateResult<>(
                                results.getDouble(results.getColumnIndex(POWER_COLUMN_NAME)))
                        .setZoneOffset(getZoneOffset(results));

            default:
                return null;
        }
    }

    @Override
    String getMainTableName() {
        return TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    final AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case POWER_RECORD_POWER_MIN:
            case POWER_RECORD_POWER_MAX:
            case POWER_RECORD_POWER_AVG:
                return new AggregateParams(
                                SERIES_TABLE_NAME, Collections.singletonList(POWER_COLUMN_NAME))
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

    @Override
    List<Pair<String, String>> getSeriesRecordColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>(NUM_LOCAL_COLUMNS);
        columnInfo.add(new Pair<>(POWER_COLUMN_NAME, REAL));
        columnInfo.add(new Pair<>(EPOCH_MILLIS_COLUMN_NAME, INTEGER));
        return columnInfo;
    }

    @Override
    String getSeriesDataTableName() {
        return SERIES_TABLE_NAME;
    }
    /** Populates the {@code record} with values specific to datatype */
    @Override
    void populateSpecificValues(@NonNull Cursor seriesTableCursor, PowerRecordInternal record) {
        HashSet<PowerRecordInternal.PowerRecordSample> powerRecordSampleSet = new HashSet<>();
        UUID uuid = getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME);
        do {
            powerRecordSampleSet.add(
                    new PowerRecordInternal.PowerRecordSample(
                            getCursorDouble(seriesTableCursor, POWER_COLUMN_NAME),
                            getCursorLong(seriesTableCursor, EPOCH_MILLIS_COLUMN_NAME)));
        } while (seriesTableCursor.moveToNext()
                && uuid.equals(getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        seriesTableCursor.moveToPrevious();
        record.setSamples(powerRecordSampleSet);
    }

    @Override
    void populateSampleTo(
            ContentValues contentValues, PowerRecordInternal.PowerRecordSample powerRecord) {
        contentValues.put(POWER_COLUMN_NAME, powerRecord.getPower());
        contentValues.put(EPOCH_MILLIS_COLUMN_NAME, powerRecord.getEpochMillis());
    }
}
