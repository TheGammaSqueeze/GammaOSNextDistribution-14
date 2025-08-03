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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.RESTING_HEART_RATE_RECORD_BPM_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.RESTING_HEART_RATE_RECORD_BPM_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.RESTING_HEART_RATE_RECORD_BPM_MIN;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RestingHeartRateRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for RestingHeartRateRecord.
 *
 * @hide
 */
public final class RestingHeartRateRecordHelper
        extends InstantRecordHelper<RestingHeartRateRecordInternal> {
    private static final String RESTING_HEART_RATE_RECORD_TABLE_NAME =
            "resting_heart_rate_record_table";
    private static final String BEATS_PER_MINUTE_COLUMN_NAME = "beats_per_minute";

    public RestingHeartRateRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        long aggregateValue;
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case RESTING_HEART_RATE_RECORD_BPM_MAX:
            case RESTING_HEART_RATE_RECORD_BPM_MIN:
            case RESTING_HEART_RATE_RECORD_BPM_AVG:
                aggregateValue =
                        results.getLong(results.getColumnIndex(BEATS_PER_MINUTE_COLUMN_NAME));
                break;
            default:
                return null;
        }
        return new AggregateResult<>(aggregateValue).setZoneOffset(getZoneOffset(results));
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return RESTING_HEART_RATE_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        List<String> columnNames;
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case RESTING_HEART_RATE_RECORD_BPM_MAX:
            case RESTING_HEART_RATE_RECORD_BPM_MIN:
            case RESTING_HEART_RATE_RECORD_BPM_AVG:
                columnNames = Collections.singletonList(BEATS_PER_MINUTE_COLUMN_NAME);
                break;
            default:
                return null;
        }
        return new AggregateParams(RESTING_HEART_RATE_RECORD_TABLE_NAME, columnNames);
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull RestingHeartRateRecordInternal restingHeartRateRecord) {
        restingHeartRateRecord.setBeatsPerMinute(
                getCursorInt(cursor, BEATS_PER_MINUTE_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull RestingHeartRateRecordInternal restingHeartRateRecord) {
        contentValues.put(BEATS_PER_MINUTE_COLUMN_NAME, restingHeartRateRecord.getBeatsPerMinute());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(new Pair<>(BEATS_PER_MINUTE_COLUMN_NAME, INTEGER));
    }
}
