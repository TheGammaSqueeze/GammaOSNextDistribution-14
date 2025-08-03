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
import android.health.connect.internal.datatypes.InstantRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AlterTableRequest;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Parent class for all helper classes for the Instant type records
 *
 * @hide
 */
public abstract class InstantRecordHelper<T extends InstantRecordInternal<?>>
        extends RecordHelper<T> {
    public static final String TIME_COLUMN_NAME = "time";
    private static final String ZONE_OFFSET_COLUMN_NAME = "zone_offset";
    private static final String LOCAL_DATE_TIME_EXPRESSION =
            TIME_COLUMN_NAME + " + 1000 * " + ZONE_OFFSET_COLUMN_NAME;
    private static final String LOCAL_DATE_COLUMN_NAME = "local_date";
    public static final String LOCAL_DATE_TIME_COLUMN_NAME = "local_date_time";

    InstantRecordHelper(@RecordTypeIdentifier.RecordType int recordIdentifier) {
        super(recordIdentifier);
    }

    @Override
    public final String getStartTimeColumnName() {
        return TIME_COLUMN_NAME;
    }

    @Override
    public final String getLocalStartTimeColumnName() {
        return LOCAL_DATE_TIME_COLUMN_NAME;
    }

    @Override
    public final String getDurationGroupByColumnName() {
        return TIME_COLUMN_NAME;
    }

    @Override
    public final String getPeriodGroupByColumnName() {
        return LOCAL_DATE_COLUMN_NAME;
    }

    @Override
    public void applyGeneratedLocalTimeUpgrade(@NonNull SQLiteDatabase db) {
        try {
            db.execSQL(
                    AlterTableRequest.getAlterTableCommandToAddGeneratedColumn(
                            getMainTableName(),
                            new CreateTableRequest.GeneratedColumnInfo(
                                    LOCAL_DATE_TIME_COLUMN_NAME,
                                    INTEGER,
                                    LOCAL_DATE_TIME_EXPRESSION)));
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
                        LOCAL_DATE_TIME_COLUMN_NAME, INTEGER, LOCAL_DATE_TIME_EXPRESSION));
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
        columnInfo.add(new Pair<>(TIME_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(ZONE_OFFSET_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(LOCAL_DATE_COLUMN_NAME, INTEGER));

        columnInfo.addAll(getInstantRecordColumnInfo());

        return columnInfo;
    }

    @Override
    final void populateContentValues(
            @NonNull ContentValues contentValues, @NonNull T instantRecord) {
        contentValues.put(TIME_COLUMN_NAME, instantRecord.getTimeInMillis());
        contentValues.put(ZONE_OFFSET_COLUMN_NAME, instantRecord.getZoneOffsetInSeconds());
        contentValues.put(
                LOCAL_DATE_COLUMN_NAME,
                ChronoUnit.DAYS.between(
                        LocalDate.ofEpochDay(0),
                        LocalDate.ofInstant(
                                Instant.ofEpochMilli(instantRecord.getTimeInMillis()),
                                ZoneOffset.ofTotalSeconds(
                                        instantRecord.getZoneOffsetInSeconds()))));

        populateSpecificContentValues(contentValues, instantRecord);
    }

    @Override
    final String getZoneOffsetColumnName() {
        return ZONE_OFFSET_COLUMN_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    final ZoneOffset getZoneOffset(Cursor cursor) {
        ZoneOffset zoneOffset = null;
        if (cursor.getCount() > 0 && cursor.getColumnIndex(ZONE_OFFSET_COLUMN_NAME) != -1) {
            try {
                zoneOffset =
                        ZoneOffset.ofTotalSeconds(
                                StorageUtils.getCursorInt(cursor, ZONE_OFFSET_COLUMN_NAME));
            } catch (Exception exception) {
                zoneOffset = OffsetDateTime.now(ZoneId.systemDefault()).getOffset();
            }
        }

        return zoneOffset;
    }

    abstract void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull T instantRecordInternal);

    @Override
    final void populateRecordValue(@NonNull Cursor cursor, @NonNull T instantRecordInternal) {
        instantRecordInternal.setZoneOffset(getCursorInt(cursor, ZONE_OFFSET_COLUMN_NAME));
        instantRecordInternal.setTime(getCursorLong(cursor, TIME_COLUMN_NAME));

        populateSpecificRecordValue(cursor, instantRecordInternal);
    }

    abstract void populateSpecificRecordValue(@NonNull Cursor cursor, @NonNull T recordInternal);

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @NonNull
    abstract List<Pair<String, String>> getInstantRecordColumnInfo();
}
