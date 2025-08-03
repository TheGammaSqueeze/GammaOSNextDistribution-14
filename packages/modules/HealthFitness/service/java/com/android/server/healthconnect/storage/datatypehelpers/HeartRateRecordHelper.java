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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEART_RATE_RECORD_BPM_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEART_RATE_RECORD_BPM_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEART_RATE_RECORD_BPM_MIN;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEART_RATE_RECORD_MEASUREMENTS_COUNT;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;

import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.HeartRateRecordInternal;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Helper class for HeartRateRecord.
 *
 * @hide
 */
public class HeartRateRecordHelper
        extends SeriesRecordHelper<
                HeartRateRecordInternal, HeartRateRecordInternal.HeartRateSample> {

    @VisibleForTesting public static final String TABLE_NAME = "heart_rate_record_table";
    public static final int NUM_LOCAL_COLUMNS = 2;
    private static final String SERIES_TABLE_NAME = "heart_rate_record_series_table";
    private static final String BEATS_PER_MINUTE_COLUMN_NAME = "beats_per_minute";
    private static final String EPOCH_MILLIS_COLUMN_NAME = "epoch_millis";

    public HeartRateRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_HEART_RATE);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public final AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case HEART_RATE_RECORD_BPM_MAX:
            case HEART_RATE_RECORD_BPM_MIN:
            case HEART_RATE_RECORD_BPM_AVG:
            case HEART_RATE_RECORD_MEASUREMENTS_COUNT:
                return new AggregateResult<>(
                                results.getLong(
                                        results.getColumnIndex(BEATS_PER_MINUTE_COLUMN_NAME)))
                        .setZoneOffset(getZoneOffset(results));
            default:
                return null;
        }
    }

    @Override
    final String getMainTableName() {
        return TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    final AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case HEART_RATE_RECORD_BPM_MAX:
            case HEART_RATE_RECORD_BPM_MIN:
            case HEART_RATE_RECORD_BPM_AVG:
            case HEART_RATE_RECORD_MEASUREMENTS_COUNT:
                return new AggregateParams(
                                SERIES_TABLE_NAME,
                                Collections.singletonList(BEATS_PER_MINUTE_COLUMN_NAME))
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
    final List<Pair<String, String>> getSeriesRecordColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>(NUM_LOCAL_COLUMNS);
        columnInfo.add(new Pair<>(BEATS_PER_MINUTE_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(EPOCH_MILLIS_COLUMN_NAME, INTEGER));
        return columnInfo;
    }

    @Override
    final String getSeriesDataTableName() {
        return SERIES_TABLE_NAME;
    }

    @Override
    void populateSpecificValues(Cursor seriesTableCursor, HeartRateRecordInternal record) {
        HashSet<HeartRateRecordInternal.HeartRateSample> heartRateSamplesSet = new HashSet<>();
        UUID uuid = getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME);
        do {
            heartRateSamplesSet.add(
                    new HeartRateRecordInternal.HeartRateSample(
                            getCursorInt(seriesTableCursor, BEATS_PER_MINUTE_COLUMN_NAME),
                            getCursorLong(seriesTableCursor, EPOCH_MILLIS_COLUMN_NAME)));
        } while (seriesTableCursor.moveToNext()
                && uuid.equals(getCursorUUID(seriesTableCursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        seriesTableCursor.moveToPrevious();
        record.setSamples(heartRateSamplesSet);
    }

    @Override
    final void populateSampleTo(
            ContentValues contentValues, HeartRateRecordInternal.HeartRateSample heartRateSample) {
        contentValues.put(BEATS_PER_MINUTE_COLUMN_NAME, heartRateSample.getBeatsPerMinute());
        contentValues.put(EPOCH_MILLIS_COLUMN_NAME, heartRateSample.getEpochMillis());
    }
}
