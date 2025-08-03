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

import static android.health.connect.datatypes.ExerciseSegmentType.DURATION_EXCLUDE_TYPES;

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
import android.health.connect.internal.datatypes.ExerciseSegmentInternal;
import android.util.ArraySet;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for {@link android.health.connect.datatypes.ExerciseSegment}.
 *
 * @hide
 */
public class ExerciseSegmentRecordHelper {
    static final String EXERCISE_SEGMENT_RECORD_TABLE_NAME = "exercise_segments_table";
    private static final String EXERCISE_SEGMENT_START_TIME = "segment_start_time";
    private static final String EXERCISE_SEGMENT_END_TIME = "segment_end_time";
    private static final String EXERCISE_SEGMENT_TYPE = "segment_type";
    private static final String EXERCISE_SEGMENT_REPETITIONS_COUNT = "repetitions_count";

    static CreateTableRequest getCreateSegmentsTableRequest(String parentTableName) {
        return new CreateTableRequest(
                        EXERCISE_SEGMENT_RECORD_TABLE_NAME, getSegmentsTableColumnInfo())
                .addForeignKey(
                        parentTableName,
                        Collections.singletonList(PARENT_KEY_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME));
    }

    static List<UpsertTableRequest> getSegmentsUpsertRequests(
            List<ExerciseSegmentInternal> segments) {
        List<UpsertTableRequest> requests = new ArrayList<>(segments.size());
        segments.forEach(
                (sample -> {
                    ContentValues contentValues = new ContentValues();
                    populateSegmentTo(contentValues, sample);
                    requests.add(
                            new UpsertTableRequest(
                                            EXERCISE_SEGMENT_RECORD_TABLE_NAME, contentValues)
                                    .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME));
                }));
        return requests;
    }

    static void updateSetWithRecordedSegment(
            @NonNull Cursor cursor, ArraySet<ExerciseSegmentInternal> segmentsSet) {
        if (isNullValue(cursor, EXERCISE_SEGMENT_START_TIME)) {
            return;
        }

        segmentsSet.add(
                new ExerciseSegmentInternal()
                        .setStarTime(getCursorLong(cursor, EXERCISE_SEGMENT_START_TIME))
                        .setEndTime(getCursorLong(cursor, EXERCISE_SEGMENT_END_TIME))
                        .setSegmentType(getCursorInt(cursor, EXERCISE_SEGMENT_TYPE))
                        .setRepetitionsCount(
                                getCursorInt(cursor, EXERCISE_SEGMENT_REPETITIONS_COUNT)));
    }

    static void populateSegmentTo(ContentValues contentValues, ExerciseSegmentInternal segment) {
        contentValues.put(EXERCISE_SEGMENT_START_TIME, segment.getStartTime());
        contentValues.put(EXERCISE_SEGMENT_END_TIME, segment.getEndTime());
        contentValues.put(EXERCISE_SEGMENT_TYPE, segment.getSegmentType());
        contentValues.put(EXERCISE_SEGMENT_REPETITIONS_COUNT, segment.getRepetitionsCount());
    }

    static SqlJoin getJoinReadRequest(String parentTableName) {
        return new SqlJoin(
                        parentTableName,
                        EXERCISE_SEGMENT_RECORD_TABLE_NAME,
                        PRIMARY_COLUMN_NAME,
                        PARENT_KEY_COLUMN_NAME)
                .setJoinType(SqlJoin.SQL_JOIN_LEFT);
    }

    private static List<Pair<String, String>> getSegmentsTableColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PARENT_KEY_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_SEGMENT_START_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_SEGMENT_END_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_SEGMENT_TYPE, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(EXERCISE_SEGMENT_REPETITIONS_COUNT, INTEGER_NOT_NULL));
        return columnInfo;
    }

    public static String getStartTimeColumnName() {
        return EXERCISE_SEGMENT_START_TIME;
    }

    public static String getEndTimeColumnName() {
        return EXERCISE_SEGMENT_END_TIME;
    }

    /** Returns sql join needed for calculating exercise sessions duration */
    public static SqlJoin getJoinForDurationAggregation(String parentTableName) {
        SqlJoin join = getJoinReadRequest(parentTableName);
        WhereClauses filterPauses = new WhereClauses(AND);
        filterPauses.addWhereInIntsClause(EXERCISE_SEGMENT_TYPE, DURATION_EXCLUDE_TYPES);
        join.setSecondTableWhereClause(filterPauses);
        return join;
    }
}
