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

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.SeriesRecordHelper.PARENT_KEY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.isNullValue;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.util.ArraySet;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exercise laps helper
 *
 * @hide
 */
public class ExerciseLapRecordHelper {

    static final String EXERCISE_LAPS_RECORD_TABLE_NAME = "exercise_laps_table";
    private static final String EXERCISE_LAPS_START_TIME = "lap_start_time";
    private static final String EXERCISE_LAPS_END_TIME = "lap_end_time";
    private static final String EXERCISE_LAPS_LENGTH = "lap_length";

    static CreateTableRequest getCreateLapsTableRequest(String parentTableName) {
        return new CreateTableRequest(EXERCISE_LAPS_RECORD_TABLE_NAME, getLapsTableColumnInfo())
                .addForeignKey(
                        parentTableName,
                        Collections.singletonList(PARENT_KEY_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME));
    }

    static List<UpsertTableRequest> getLapsUpsertRequests(List<ExerciseLapInternal> laps) {
        List<UpsertTableRequest> requests = new ArrayList<>(laps.size());
        laps.forEach(
                (sample -> {
                    ContentValues contentValues = new ContentValues();
                    populateLapTo(contentValues, sample);
                    requests.add(
                            new UpsertTableRequest(EXERCISE_LAPS_RECORD_TABLE_NAME, contentValues)
                                    .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME));
                }));
        return requests;
    }

    static void populateLapIfRecorded(
            @NonNull Cursor cursor, ArraySet<ExerciseLapInternal> lapsSet) {
        if (isNullValue(cursor, EXERCISE_LAPS_START_TIME)) {
            return;
        }

        lapsSet.add(
                new ExerciseLapInternal()
                        .setStarTime(getCursorLong(cursor, EXERCISE_LAPS_START_TIME))
                        .setEndTime(getCursorLong(cursor, EXERCISE_LAPS_END_TIME))
                        .setLength(getCursorDouble(cursor, EXERCISE_LAPS_LENGTH)));
    }

    static void populateLapTo(ContentValues contentValues, ExerciseLapInternal lap) {
        contentValues.put(EXERCISE_LAPS_START_TIME, lap.getStartTime());
        contentValues.put(EXERCISE_LAPS_END_TIME, lap.getEndTime());
        contentValues.put(EXERCISE_LAPS_LENGTH, lap.getLength());
    }

    static SqlJoin getJoinReadRequest(String parentTableName) {
        return new SqlJoin(
                        parentTableName,
                        EXERCISE_LAPS_RECORD_TABLE_NAME,
                        PRIMARY_COLUMN_NAME,
                        PARENT_KEY_COLUMN_NAME)
                .setJoinType(SqlJoin.SQL_JOIN_LEFT);
    }

    private static List<Pair<String, String>> getLapsTableColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PARENT_KEY_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_LAPS_START_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_LAPS_END_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_LAPS_LENGTH, REAL_NOT_NULL));
        return columnInfo;
    }
}
