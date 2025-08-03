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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HYDRATION_RECORD_VOLUME_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.HydrationRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for HydrationRecord.
 *
 * @hide
 */
public final class HydrationRecordHelper extends IntervalRecordHelper<HydrationRecordInternal> {
    private static final String HYDRATION_RECORD_TABLE_NAME = "hydration_record_table";
    private static final String VOLUME_COLUMN_NAME = "volume";

    public HydrationRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_HYDRATION);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case HYDRATION_RECORD_VOLUME_TOTAL:
                return new AggregateResult<>(
                                results.getDouble(results.getColumnIndex(VOLUME_COLUMN_NAME)))
                        .setZoneOffset(getZoneOffset(results));

            default:
                return null;
        }
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return HYDRATION_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull HydrationRecordInternal hydrationRecord) {
        hydrationRecord.setVolume(getCursorDouble(cursor, VOLUME_COLUMN_NAME));
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case HYDRATION_RECORD_VOLUME_TOTAL:
                return new AggregateParams(
                        HYDRATION_RECORD_TABLE_NAME, Collections.singletonList(VOLUME_COLUMN_NAME));
            default:
                return null;
        }
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull HydrationRecordInternal hydrationRecord) {
        contentValues.put(VOLUME_COLUMN_NAME, hydrationRecord.getVolume());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(VOLUME_COLUMN_NAME, REAL));
    }
}
