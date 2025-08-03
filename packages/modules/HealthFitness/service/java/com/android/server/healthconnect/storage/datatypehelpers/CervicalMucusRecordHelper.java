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
import android.health.connect.internal.datatypes.CervicalMucusRecordInternal;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for CervicalMucusRecord.
 *
 * @hide
 */
public final class CervicalMucusRecordHelper
        extends InstantRecordHelper<CervicalMucusRecordInternal> {
    private static final String CERVICAL_MUCUS_RECORD_TABLE_NAME = "cervical_mucus_record_table";
    private static final String SENSATION_COLUMN_NAME = "sensation";
    private static final String APPEARANCE_COLUMN_NAME = "appearance";

    public CervicalMucusRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_CERVICAL_MUCUS);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return CERVICAL_MUCUS_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull CervicalMucusRecordInternal cervicalMucusRecord) {
        cervicalMucusRecord.setSensation(getCursorInt(cursor, SENSATION_COLUMN_NAME));
        cervicalMucusRecord.setAppearance(getCursorInt(cursor, APPEARANCE_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull CervicalMucusRecordInternal cervicalMucusRecord) {
        contentValues.put(SENSATION_COLUMN_NAME, cervicalMucusRecord.getSensation());
        contentValues.put(APPEARANCE_COLUMN_NAME, cervicalMucusRecord.getAppearance());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(SENSATION_COLUMN_NAME, INTEGER),
                new Pair<>(APPEARANCE_COLUMN_NAME, INTEGER));
    }
}
