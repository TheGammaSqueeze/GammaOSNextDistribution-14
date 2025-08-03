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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.ACTIVE_CALORIES_BURNED_RECORD_ACTIVE_CALORIES_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.ActiveCaloriesBurnedRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for ActiveCaloriesBurnedRecord.
 *
 * @hide
 */
public final class ActiveCaloriesBurnedRecordHelper
        extends IntervalRecordHelper<ActiveCaloriesBurnedRecordInternal> {
    public static final String ACTIVE_CALORIES_BURNED_RECORD_TABLE_NAME =
            "active_calories_burned_record_table";
    public static final String ENERGY_COLUMN_NAME = "energy";

    public ActiveCaloriesBurnedRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType, double aggregation) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case ACTIVE_CALORIES_BURNED_RECORD_ACTIVE_CALORIES_TOTAL:
                results.moveToFirst();
                ZoneOffset zoneOffset = getZoneOffset(results);
                return new AggregateResult<>(aggregation).setZoneOffset(zoneOffset);
            default:
                return null;
        }
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return ACTIVE_CALORIES_BURNED_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case ACTIVE_CALORIES_BURNED_RECORD_ACTIVE_CALORIES_TOTAL:
                return new AggregateParams(
                        ACTIVE_CALORIES_BURNED_RECORD_TABLE_NAME,
                        new ArrayList(Arrays.asList(ENERGY_COLUMN_NAME)),
                        Double.class);
            default:
                return null;
        }
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull ActiveCaloriesBurnedRecordInternal activeCaloriesBurnedRecord) {
        activeCaloriesBurnedRecord.setEnergy(getCursorDouble(cursor, ENERGY_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull ActiveCaloriesBurnedRecordInternal activeCaloriesBurnedRecord) {
        contentValues.put(ENERGY_COLUMN_NAME, activeCaloriesBurnedRecord.getEnergy());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(ENERGY_COLUMN_NAME, REAL));
    }
}
