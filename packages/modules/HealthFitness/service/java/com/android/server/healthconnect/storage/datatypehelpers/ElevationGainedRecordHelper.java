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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.ELEVATION_RECORD_ELEVATION_GAINED_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.ElevationGainedRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for ElevationGainedRecord.
 *
 * @hide
 */
public final class ElevationGainedRecordHelper
        extends IntervalRecordHelper<ElevationGainedRecordInternal> {
    private static final String ELEVATION_GAINED_RECORD_TABLE_NAME =
            "elevation_gained_record_table";
    private static final String ELEVATION_COLUMN_NAME = "elevation";

    public ElevationGainedRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_ELEVATION_GAINED);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return ELEVATION_GAINED_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case ELEVATION_RECORD_ELEVATION_GAINED_TOTAL:
                return new AggregateParams(
                        ELEVATION_GAINED_RECORD_TABLE_NAME,
                        new ArrayList(Arrays.asList(ELEVATION_COLUMN_NAME)),
                        Double.class);
            default:
                return null;
        }
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull ElevationGainedRecordInternal elevationGainedRecord) {
        elevationGainedRecord.setElevation(getCursorDouble(cursor, ELEVATION_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull ElevationGainedRecordInternal elevationGainedRecord) {
        contentValues.put(ELEVATION_COLUMN_NAME, elevationGainedRecord.getElevation());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(ELEVATION_COLUMN_NAME, REAL));
    }
}
