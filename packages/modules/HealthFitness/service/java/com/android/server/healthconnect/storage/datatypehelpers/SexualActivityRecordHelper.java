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
import android.health.connect.internal.datatypes.SexualActivityRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for SexualActivityRecord.
 *
 * @hide
 */
public final class SexualActivityRecordHelper
        extends InstantRecordHelper<SexualActivityRecordInternal> {
    private static final String SEXUAL_ACTIVITY_RECORD_TABLE_NAME = "sexual_activity_record_table";
    private static final String PROTECTION_USED_COLUMN_NAME = "protection_used";

    public SexualActivityRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return SEXUAL_ACTIVITY_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull SexualActivityRecordInternal sexualActivityRecord) {
        sexualActivityRecord.setProtectionUsed(getCursorInt(cursor, PROTECTION_USED_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull SexualActivityRecordInternal sexualActivityRecord) {
        contentValues.put(PROTECTION_USED_COLUMN_NAME, sexualActivityRecord.getProtectionUsed());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(PROTECTION_USED_COLUMN_NAME, INTEGER));
    }
}
