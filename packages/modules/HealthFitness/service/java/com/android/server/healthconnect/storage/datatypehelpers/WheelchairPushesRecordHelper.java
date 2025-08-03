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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.WHEEL_CHAIR_PUSHES_RECORD_COUNT_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.WheelchairPushesRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for WheelchairPushesRecord.
 *
 * @hide
 */
public final class WheelchairPushesRecordHelper
        extends IntervalRecordHelper<WheelchairPushesRecordInternal> {
    private static final String WHEELCHAIR_PUSHES_RECORD_TABLE_NAME =
            "wheelchair_pushes_record_table";
    private static final String COUNT_COLUMN_NAME = "count";

    public WheelchairPushesRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_WHEELCHAIR_PUSHES);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return WHEELCHAIR_PUSHES_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case WHEEL_CHAIR_PUSHES_RECORD_COUNT_TOTAL:
                return new AggregateParams(
                        WHEELCHAIR_PUSHES_RECORD_TABLE_NAME,
                        new ArrayList(Arrays.asList(COUNT_COLUMN_NAME)),
                        Long.class);
            default:
                return null;
        }
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull WheelchairPushesRecordInternal wheelchairPushesRecord) {
        wheelchairPushesRecord.setCount(getCursorInt(cursor, COUNT_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull WheelchairPushesRecordInternal wheelchairPushesRecord) {
        contentValues.put(COUNT_COLUMN_NAME, wheelchairPushesRecord.getCount());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(COUNT_COLUMN_NAME, INTEGER));
    }
}
