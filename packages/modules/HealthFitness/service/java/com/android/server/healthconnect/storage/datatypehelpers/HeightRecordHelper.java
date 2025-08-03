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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEIGHT_RECORD_HEIGHT_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEIGHT_RECORD_HEIGHT_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEIGHT_RECORD_HEIGHT_MIN;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.HeightRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for HeightRecord.
 *
 * @hide
 */
public final class HeightRecordHelper extends InstantRecordHelper<HeightRecordInternal> {

    public static final String HEIGHT_RECORD_TABLE_NAME = "height_record_table";
    static final String HEIGHT_COLUMN_NAME = "height";

    public HeightRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_HEIGHT);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        double aggregateValue;
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case HEIGHT_RECORD_HEIGHT_AVG:
            case HEIGHT_RECORD_HEIGHT_MAX:
            case HEIGHT_RECORD_HEIGHT_MIN:
                aggregateValue = results.getDouble(results.getColumnIndex(HEIGHT_COLUMN_NAME));
                break;
            default:
                return null;
        }
        return new AggregateResult<>(aggregateValue).setZoneOffset(getZoneOffset(results));
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return HEIGHT_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        List<String> columnNames;
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case HEIGHT_RECORD_HEIGHT_AVG:
            case HEIGHT_RECORD_HEIGHT_MAX:
            case HEIGHT_RECORD_HEIGHT_MIN:
                columnNames = Collections.singletonList(HEIGHT_COLUMN_NAME);
                break;
            default:
                return null;
        }
        return new AggregateParams(HEIGHT_RECORD_TABLE_NAME, columnNames);
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull HeightRecordInternal heightRecord) {
        heightRecord.setHeight(getCursorDouble(cursor, HEIGHT_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull HeightRecordInternal heightRecord) {
        contentValues.put(HEIGHT_COLUMN_NAME, heightRecord.getHeight());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(new Pair<>(HEIGHT_COLUMN_NAME, REAL));
    }
}
