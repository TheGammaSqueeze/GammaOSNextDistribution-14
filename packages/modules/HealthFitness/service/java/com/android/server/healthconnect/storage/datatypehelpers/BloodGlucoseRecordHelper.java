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

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.BloodGlucoseRecordInternal;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for BloodGlucoseRecord.
 *
 * @hide
 */
public final class BloodGlucoseRecordHelper
        extends InstantRecordHelper<BloodGlucoseRecordInternal> {
    private static final String BLOOD_GLUCOSE_RECORD_TABLE_NAME = "blood_glucose_record_table";
    private static final String SPECIMEN_SOURCE_COLUMN_NAME = "specimen_source";
    private static final String LEVEL_COLUMN_NAME = "level";
    private static final String RELATION_TO_MEAL_COLUMN_NAME = "relation_to_meal";
    private static final String MEAL_TYPE_COLUMN_NAME = "meal_type";

    public BloodGlucoseRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_BLOOD_GLUCOSE);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return BLOOD_GLUCOSE_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull BloodGlucoseRecordInternal bloodGlucoseRecord) {
        bloodGlucoseRecord.setSpecimenSource(getCursorInt(cursor, SPECIMEN_SOURCE_COLUMN_NAME));
        bloodGlucoseRecord.setLevel(getCursorDouble(cursor, LEVEL_COLUMN_NAME));
        bloodGlucoseRecord.setRelationToMeal(getCursorInt(cursor, RELATION_TO_MEAL_COLUMN_NAME));
        bloodGlucoseRecord.setMealType(getCursorInt(cursor, MEAL_TYPE_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull BloodGlucoseRecordInternal bloodGlucoseRecord) {
        contentValues.put(SPECIMEN_SOURCE_COLUMN_NAME, bloodGlucoseRecord.getSpecimenSource());
        contentValues.put(LEVEL_COLUMN_NAME, bloodGlucoseRecord.getLevel());
        contentValues.put(RELATION_TO_MEAL_COLUMN_NAME, bloodGlucoseRecord.getRelationToMeal());
        contentValues.put(MEAL_TYPE_COLUMN_NAME, bloodGlucoseRecord.getMealType());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(SPECIMEN_SOURCE_COLUMN_NAME, TEXT_NOT_NULL),
                new Pair<>(LEVEL_COLUMN_NAME, REAL),
                new Pair<>(RELATION_TO_MEAL_COLUMN_NAME, TEXT_NOT_NULL),
                new Pair<>(MEAL_TYPE_COLUMN_NAME, TEXT_NOT_NULL));
    }
}
