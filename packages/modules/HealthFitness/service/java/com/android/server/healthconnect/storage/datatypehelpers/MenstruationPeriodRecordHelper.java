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

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.MenstruationPeriodRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for MenstruationPeriodRecord.
 *
 * @hide
 */
public final class MenstruationPeriodRecordHelper
        extends IntervalRecordHelper<MenstruationPeriodRecordInternal> {
    private static final String MENSTRUATION_PERIOD_RECORD_TABLE_NAME =
            "menstruation_period_record_table";

    public MenstruationPeriodRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_PERIOD);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return MENSTRUATION_PERIOD_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull MenstruationPeriodRecordInternal menstruationPeriodRecord) {}

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull MenstruationPeriodRecordInternal menstruationPeriodRecord) {}

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.emptyList();
    }
}
