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
import android.health.connect.datatypes.HeartRateVariabilityRmssdRecord;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.HeartRateVariabilityRmssdRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for {@link HeartRateVariabilityRmssdRecord}.
 *
 * @hide
 */
public final class HeartRateVariabilityRmssdHelper
        extends InstantRecordHelper<HeartRateVariabilityRmssdRecordInternal> {
    private static final String HEART_RATE_VARIABILITY_RMSSD_RECORD_TABLE_NAME =
            "heart_rate_variability_rmssd_record_table";
    private static final String HEART_RATE_VARIABILITY_RMSSD_RECORD_COLUMN_NAME =
            "heart_rate_variability_millis";

    public HeartRateVariabilityRmssdHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_HEART_RATE_VARIABILITY_RMSSD);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return HEART_RATE_VARIABILITY_RMSSD_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull
                    HeartRateVariabilityRmssdRecordInternal
                            heartRateVariabilityRmssdRecordInternal) {
        contentValues.put(
                HEART_RATE_VARIABILITY_RMSSD_RECORD_COLUMN_NAME,
                heartRateVariabilityRmssdRecordInternal.getHeartRateVariabilityMillis());
    }

    @Override
    protected void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull HeartRateVariabilityRmssdRecordInternal recordInternal) {
        recordInternal.setHeartRateVariabilityMillis(
                getCursorDouble(cursor, HEART_RATE_VARIABILITY_RMSSD_RECORD_COLUMN_NAME));
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(
                new Pair<>(HEART_RATE_VARIABILITY_RMSSD_RECORD_COLUMN_NAME, REAL_NOT_NULL));
    }
}
