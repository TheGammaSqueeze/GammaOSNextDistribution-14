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
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.BodyFatRecordInternal;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for BodyFatRecord.
 *
 * @hide
 */
public final class BodyFatRecordHelper extends InstantRecordHelper<BodyFatRecordInternal> {
    private static final String BODY_FAT_RECORD_TABLE_NAME = "body_fat_record_table";
    private static final String PERCENTAGE_COLUMN_NAME = "percentage";

    public BodyFatRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_BODY_FAT);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return BODY_FAT_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull BodyFatRecordInternal bodyFatRecord) {
        bodyFatRecord.setPercentage(getCursorDouble(cursor, PERCENTAGE_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull BodyFatRecordInternal bodyFatRecord) {
        contentValues.put(PERCENTAGE_COLUMN_NAME, bodyFatRecord.getPercentage());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(new Pair<>(PERCENTAGE_COLUMN_NAME, REAL));
    }
}
