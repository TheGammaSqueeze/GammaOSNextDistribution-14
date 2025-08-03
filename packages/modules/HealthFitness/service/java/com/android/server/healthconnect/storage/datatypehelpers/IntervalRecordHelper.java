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
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.IntervalRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AlterTableRequest;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parent class for all the Interval type records
 *
 * @param <T> internal record for which this class is responsible.
 * @hide
 */
public abstract class IntervalRecordHelper<T extends IntervalRecordInternal<?>>
        extends RecordHelper<T> {
    public static final String START_TIME_COLUMN_NAME = "start_time";
    public static final String START_ZONE_OFFSET_COLUMN_NAME = "start_zone_offset";
    private static final String START_LOCAL_DATE_TIME_EXPRESSION =
            START_TIME_COLUMN_NAME + " + 1000 * " + START_ZONE_OFFSET_COLUMN_NAME;
    public static final String END_TIME_COLUMN_NAME = "end_time";
    public static final String END_ZONE_OFFSET_COLUMN_NAME = "end_zone_offset";
    private static final String END_LOCAL_DATE_TIME_EXPRESSION =
            END_TIME_COLUMN_NAME + " + 1000 * " + END_ZONE_OFFSET_COLUMN_NAME;
    private static final String LOCAL_DATE_COLUMN_NAME = "local_date";
    public static final String LOCAL_DATE_TIME_START_TIME_COLUMN_NAME =
            "local_date_time_start_time";
    public static final String LOCAL_DATE_TIME_END_TIME_COLUMN_NAME = "local_date_time_end_time";

    IntervalRecordHelper(@RecordTypeIdentifier.RecordType int recordIdentifier) {
        super(recordIdentifier);
    }

    @Override
    public final String getStartTimeColumnName() {
        return START_TIME_COLUMN_NAME;
    }

    @Override
    public final String getLocalStartTimeColumnName() {
        return LOCAL_DATE_TIME_START_TIME_COLUMN_NAME;
    }

    @Override
    public final String getEndTimeColumnName() {
        return END_TIME_COLUMN_NAME;
    }

    @Override
    public final String getLocalEndTimeColumnName() {
        return LOCAL_DATE_TIME_END_TIME_COLUMN_NAME;
    }

    @Override
    public final void applyGeneratedLocalTimeUpgrade(@NonNull SQLiteDatabase db) {
        try {
            db.execSQL(
                    AlterTableRequest.getAlterTableCommandToAddGeneratedColumn(
                            getMainTableName(),
                            new CreateTableRequest.GeneratedColumnInfo(
                                    LOCAL_DATE_TIME_START_TIME_COLUMN_NAME,
                                    INTEGER,
                                    START_LOCAL_DATE_TIME_EXPRESSION)));
            db.execSQL(
                    AlterTableRequest.getAlterTableCommandToAddGeneratedColumn(
                            getMainTableName(),
                            new CreateTableRequest.GeneratedColumnInfo(
                                    LOCAL_DATE_TIME_END_TIME_COLUMN_NAME,
                                    INTEGER,
                                    END_LOCAL_DATE_TIME_EXPRESSION)));
        } catch (SQLException sqlException) {
            // Ignore this means the field exists. This is possible via module rollback followed by
            // an upgrade
        }
    }


    @Override
    @NonNull
    protected List<CreateTableRequest.GeneratedColumnInfo> getGeneratedColumnInfo() {
        return List.of(
                new CreateTableRequest.GeneratedColumnInfo(
                        LOCAL_DATE_TIME_START_TIME_COLUMN_NAME,
                        INTEGER,
                        START_LOCAL_DATE_TIME_EXPRESSION),
                new CreateTableRequest.GeneratedColumnInfo(
                        LOCAL_DATE_TIME_END_TIME_COLUMN_NAME,
                        INTEGER,
                        END_LOCAL_DATE_TIME_EXPRESSION));
    }

    @Override
    public final String getDurationGroupByColumnName() {
        return START_TIME_COLUMN_NAME;
    }

    @Override
    public final String getPeriodGroupByColumnName() {
        return LOCAL_DATE_COLUMN_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    final ZoneOffset getZoneOffset(Cursor cursor) {
        ZoneOffset zoneOffset = null;
        if (cursor.getCount() > 0 && cursor.getColumnIndex(START_ZONE_OFFSET_COLUMN_NAME) != -1) {
            zoneOffset =
                    ZoneOffset.ofTotalSeconds(
                            StorageUtils.getCursorInt(cursor, START_ZONE_OFFSET_COLUMN_NAME));
        }

        return zoneOffset;
    }

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @Override
    @NonNull
    final List<Pair<String, String>> getSpecificColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(START_TIME_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(START_ZONE_OFFSET_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(END_TIME_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(END_ZONE_OFFSET_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(LOCAL_DATE_COLUMN_NAME, INTEGER));

        columnInfo.addAll(getIntervalRecordColumnInfo());

        return columnInfo;
    }

    @Override
    final void populateContentValues(
            @NonNull ContentValues contentValues, @NonNull T intervalRecord) {
        contentValues.put(START_TIME_COLUMN_NAME, intervalRecord.getStartTimeInMillis());
        contentValues.put(
                START_ZONE_OFFSET_COLUMN_NAME, intervalRecord.getStartZoneOffsetInSeconds());
        contentValues.put(END_TIME_COLUMN_NAME, intervalRecord.getEndTimeInMillis());
        contentValues.put(END_ZONE_OFFSET_COLUMN_NAME, intervalRecord.getEndZoneOffsetInSeconds());
        contentValues.put(
                LOCAL_DATE_COLUMN_NAME,
                ChronoUnit.DAYS.between(
                        LocalDate.ofEpochDay(0),
                        LocalDate.ofInstant(
                                Instant.ofEpochMilli(intervalRecord.getStartTimeInMillis()),
                                ZoneOffset.ofTotalSeconds(
                                        intervalRecord.getStartZoneOffsetInSeconds()))));

        populateSpecificContentValues(contentValues, intervalRecord);
    }

    @Override
    final void populateRecordValue(@NonNull Cursor cursor, @NonNull T recordInternal) {
        recordInternal.setStartTime(getCursorLong(cursor, START_TIME_COLUMN_NAME));
        recordInternal.setStartZoneOffset(getCursorInt(cursor, START_ZONE_OFFSET_COLUMN_NAME));
        recordInternal.setEndTime(getCursorLong(cursor, END_TIME_COLUMN_NAME));
        recordInternal.setEndZoneOffset(getCursorInt(cursor, END_ZONE_OFFSET_COLUMN_NAME));
        populateSpecificRecordValue(cursor, recordInternal);
    }

    /** This implementation should populate record with datatype specific values from the table. */
    abstract void populateSpecificRecordValue(@NonNull Cursor cursor, @NonNull T recordInternal);

    @Override
    final String getZoneOffsetColumnName() {
        return START_ZONE_OFFSET_COLUMN_NAME;
    }

    abstract void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull T intervalRecordInternal);

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @NonNull
    abstract List<Pair<String, String>> getIntervalRecordColumnInfo();

    /** Outputs list of columns needed for interval priority aggregations. */
    List<String> getPriorityAggregationColumnNames() {
        return Arrays.asList(
                APP_INFO_ID_COLUMN_NAME,
                LAST_MODIFIED_TIME_COLUMN_NAME,
                UUID_COLUMN_NAME,
                START_ZONE_OFFSET_COLUMN_NAME);
    }
}
