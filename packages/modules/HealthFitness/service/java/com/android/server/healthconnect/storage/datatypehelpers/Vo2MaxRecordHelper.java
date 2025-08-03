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

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.Vo2MaxRecordInternal;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for Vo2MaxRecord.
 *
 * @hide
 */
public final class Vo2MaxRecordHelper extends InstantRecordHelper<Vo2MaxRecordInternal> {

    @VisibleForTesting
    public static final String VO2_MAX_RECORD_TABLE_NAME = "vo2_max_record_table";

    private static final String MEASUREMENT_METHOD_COLUMN_NAME = "measurement_method";
    private static final String VO2_MILLILITERS_PER_MINUTE_KILOGRAM_COLUMN_NAME =
            "vo2_milliliters_per_minute_kilogram";

    public Vo2MaxRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_VO2_MAX);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return VO2_MAX_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull Vo2MaxRecordInternal vo2MaxRecord) {
        vo2MaxRecord.setMeasurementMethod(getCursorInt(cursor, MEASUREMENT_METHOD_COLUMN_NAME));
        vo2MaxRecord.setVo2MillilitersPerMinuteKilogram(
                getCursorDouble(cursor, VO2_MILLILITERS_PER_MINUTE_KILOGRAM_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull Vo2MaxRecordInternal vo2MaxRecord) {
        contentValues.put(MEASUREMENT_METHOD_COLUMN_NAME, vo2MaxRecord.getMeasurementMethod());
        contentValues.put(
                VO2_MILLILITERS_PER_MINUTE_KILOGRAM_COLUMN_NAME,
                vo2MaxRecord.getVo2MillilitersPerMinuteKilogram());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(MEASUREMENT_METHOD_COLUMN_NAME, INTEGER),
                new Pair<>(VO2_MILLILITERS_PER_MINUTE_KILOGRAM_COLUMN_NAME, REAL));
    }
}
