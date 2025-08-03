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
import android.health.connect.internal.datatypes.MenstruationFlowRecordInternal;
import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for MenstruationFlowRecord.
 *
 * @hide
 */
public final class MenstruationFlowRecordHelper
        extends InstantRecordHelper<MenstruationFlowRecordInternal> {
    private static final String MENSTRUATION_FLOW_RECORD_TABLE_NAME =
            "menstruation_flow_record_table";
    private static final String FLOW_COLUMN_NAME = "flow";

    public MenstruationFlowRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW);
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return MENSTRUATION_FLOW_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull MenstruationFlowRecordInternal menstruationFlowRecord) {
        menstruationFlowRecord.setFlow(getCursorInt(cursor, FLOW_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull MenstruationFlowRecordInternal menstruationFlowRecord) {
        contentValues.put(FLOW_COLUMN_NAME, menstruationFlowRecord.getFlow());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(FLOW_COLUMN_NAME, INTEGER));
    }
}
