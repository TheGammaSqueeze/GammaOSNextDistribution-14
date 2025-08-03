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
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.OvulationTestRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for OvulationTestRecord.
 *
 * @hide
 */
public final class OvulationTestRecordHelper
        extends InstantRecordHelper<OvulationTestRecordInternal> {
    private static final String OVULATION_TEST_RECORD_TABLE_NAME = "ovulation_test_record_table";
    private static final String RESULT_COLUMN_NAME = "result";

    public OvulationTestRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_OVULATION_TEST);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return OVULATION_TEST_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull OvulationTestRecordInternal ovulationTestRecord) {
        ovulationTestRecord.setResult(getCursorInt(cursor, RESULT_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull OvulationTestRecordInternal ovulationTestRecord) {
        contentValues.put(RESULT_COLUMN_NAME, ovulationTestRecord.getResult());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(RESULT_COLUMN_NAME, INTEGER));
    }
}
