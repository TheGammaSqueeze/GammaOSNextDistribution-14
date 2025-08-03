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
import android.health.connect.internal.datatypes.LeanBodyMassRecordInternal;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for LeanBodyMassRecord.
 *
 * @hide
 */
public final class LeanBodyMassRecordHelper
        extends InstantRecordHelper<LeanBodyMassRecordInternal> {
    static final String LEAN_BODY_MASS_RECORD_TABLE_NAME = "lean_body_mass_record_table";
    static final String MASS_COLUMN_NAME = "mass";

    public LeanBodyMassRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return LEAN_BODY_MASS_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull LeanBodyMassRecordInternal leanBodyMassRecord) {
        leanBodyMassRecord.setMass(getCursorDouble(cursor, MASS_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull LeanBodyMassRecordInternal leanBodyMassRecord) {
        contentValues.put(MASS_COLUMN_NAME, leanBodyMassRecord.getMass());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(new Pair<>(MASS_COLUMN_NAME, REAL));
    }
}
