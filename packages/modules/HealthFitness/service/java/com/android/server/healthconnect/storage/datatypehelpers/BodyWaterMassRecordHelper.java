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

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.BodyWaterMassRecord;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.BodyWaterMassRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for {@link BodyWaterMassRecord}.
 *
 * @hide
 */
public final class BodyWaterMassRecordHelper
        extends InstantRecordHelper<BodyWaterMassRecordInternal> {
    private static final String BODY_WATER_MASS_RECORD_TABLE_NAME = "body_water_mass_record_table";
    private static final String BODY_WATER_MASS_RECORD_COLUMN_NAME = "body_water_mass";

    public BodyWaterMassRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_BODY_WATER_MASS);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return BODY_WATER_MASS_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull BodyWaterMassRecordInternal bodyWaterMassRecordInternal) {
        contentValues.put(
                BODY_WATER_MASS_RECORD_COLUMN_NAME, bodyWaterMassRecordInternal.getBodyWaterMass());
    }

    @Override
    protected void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull BodyWaterMassRecordInternal recordInternal) {
        recordInternal.setBodyWaterMass(
                getCursorDouble(cursor, BODY_WATER_MASS_RECORD_COLUMN_NAME));
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(
                new Pair<>(BODY_WATER_MASS_RECORD_COLUMN_NAME, REAL_NOT_NULL));
    }
}
