/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.health.connect.datatypes.SleepSessionRecord.StageType.DURATION_EXCLUDE_TYPES;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.SeriesRecordHelper.PARENT_KEY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.isNullValue;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.internal.datatypes.SleepStageInternal;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sleep stage helper
 *
 * @hide
 */
public final class SleepStageRecordHelper {
    private static final String SLEEP_STAGES_RECORD_TABLE_NAME = "sleep_stages_table";
    private static final String SLEEP_STAGE_START_TIME = "stage_start_time";
    private static final String SLEEP_STAGE_END_TIME = "stage_end_time";
    private static final String SLEEP_STAGE_TYPE = "stage_type";

    public static String getStartTimeColumnName() {
        return SLEEP_STAGE_START_TIME;
    }

    public static String getEndTimeColumnName() {
        return SLEEP_STAGE_END_TIME;
    }

    /** Returns sql join needed for calculating sleep duration */
    public static SqlJoin getJoinForDurationAggregation(String parentTableName) {
        SqlJoin join = getJoinReadRequest(parentTableName);
        WhereClauses filterAwakes = new WhereClauses(AND);
        filterAwakes.addWhereInIntsClause(SLEEP_STAGE_TYPE, DURATION_EXCLUDE_TYPES);
        join.setSecondTableWhereClause(filterAwakes);
        return join;
    }

    static CreateTableRequest getCreateStagesTableRequest(String parentTableName) {
        return new CreateTableRequest(SLEEP_STAGES_RECORD_TABLE_NAME, getStagesTableColumnInfo())
                .addForeignKey(
                        parentTableName,
                        Collections.singletonList(PARENT_KEY_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME));
    }

    static List<UpsertTableRequest> getStagesUpsertRequests(List<SleepStageInternal> stages) {
        List<UpsertTableRequest> requests = new ArrayList<>(stages.size());
        stages.forEach(
                (sample -> {
                    ContentValues contentValues = new ContentValues();
                    populateStageTo(contentValues, sample);
                    requests.add(
                            new UpsertTableRequest(SLEEP_STAGES_RECORD_TABLE_NAME, contentValues)
                                    .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME));
                }));
        return requests;
    }

    @Nullable
    static SleepStageInternal populateStageIfRecorded(@NonNull Cursor cursor) {
        if (isNullValue(cursor, SLEEP_STAGE_START_TIME)) {
            return null;
        }

        return new SleepStageInternal()
                .setStartTime(getCursorLong(cursor, SLEEP_STAGE_START_TIME))
                .setEndTime(getCursorLong(cursor, SLEEP_STAGE_END_TIME))
                .setStageType(getCursorInt(cursor, SLEEP_STAGE_TYPE));
    }

    static void populateStageTo(ContentValues contentValues, SleepStageInternal stage) {
        contentValues.put(SLEEP_STAGE_START_TIME, stage.getStartTime());
        contentValues.put(SLEEP_STAGE_END_TIME, stage.getEndTime());
        contentValues.put(SLEEP_STAGE_TYPE, stage.getStageType());
    }

    static SqlJoin getJoinReadRequest(String parentTableName) {
        return new SqlJoin(
                        parentTableName,
                        SLEEP_STAGES_RECORD_TABLE_NAME,
                        PRIMARY_COLUMN_NAME,
                        PARENT_KEY_COLUMN_NAME)
                .setJoinType(SqlJoin.SQL_JOIN_LEFT);
    }

    private static List<Pair<String, String>> getStagesTableColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PARENT_KEY_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(SLEEP_STAGE_START_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(SLEEP_STAGE_END_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(SLEEP_STAGE_TYPE, INTEGER_NOT_NULL));
        return columnInfo;
    }
}
