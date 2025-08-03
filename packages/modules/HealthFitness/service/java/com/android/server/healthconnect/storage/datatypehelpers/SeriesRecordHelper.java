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

import static android.health.connect.Constants.PARENT_KEY;

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.SeriesRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** @hide */
abstract class SeriesRecordHelper<
                T extends SeriesRecordInternal<?, ?>, U extends SeriesRecordInternal.Sample>
        extends IntervalRecordHelper<T> {
    protected static final String PARENT_KEY_COLUMN_NAME = PARENT_KEY;

    SeriesRecordHelper(@RecordTypeIdentifier.RecordType int recordIdentifier) {
        super(recordIdentifier);
    }

    @Override
    final List<CreateTableRequest> getChildTableCreateRequests() {
        return Collections.singletonList(
                new CreateTableRequest(getSeriesDataTableName(), getSeriesTableColumnInfo())
                        .addForeignKey(
                                getMainTableName(),
                                Collections.singletonList(PARENT_KEY_COLUMN_NAME),
                                Collections.singletonList(PRIMARY_COLUMN_NAME)));
    }

    @Override
    @SuppressWarnings("unchecked")
    final List<UpsertTableRequest> getChildTableUpsertRequests(@NonNull T record) {
        List<? extends SeriesRecordInternal.Sample> samples = record.getSamples().stream().toList();
        List<UpsertTableRequest> requests = new ArrayList<>(samples.size());
        samples.forEach(
                (sample -> {
                    ContentValues contentValues = new ContentValues();
                    populateSampleTo(contentValues, (U) sample);
                    requests.add(
                            new UpsertTableRequest(getSeriesDataTableName(), contentValues)
                                    .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME));
                }));

        return requests;
    }

    /** Returns the INNER JOIN clause for querying from the table for series datatype */
    @Override
    final SqlJoin getJoinForReadRequest() {
        return new SqlJoin(
                getMainTableName(),
                getSeriesDataTableName(),
                PRIMARY_COLUMN_NAME,
                PARENT_KEY_COLUMN_NAME);
    }

    @Override
    final void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull T record) {
        // Empty as we don't want to populate any additional in the main table.
    }

    /** Populates record with datatype specific details */
    @Override
    final void populateSpecificRecordValue(@NonNull Cursor cursor, @NonNull T record) {
        populateSpecificValues(cursor, record);
    }

    /**
     * A typical series data type should not use the main table to store any of its data, and should
     * instead implement get addition table related functions. Hence, an empty final function
     */
    @NonNull
    final List<Pair<String, String>> getIntervalRecordColumnInfo() {
        // We don't want to populate anything additional in the main table. Series data types use
        // additional table to store all the data.
        return Collections.emptyList();
    }

    /**
     * Returns the column names required to store the series data, excluding the parent key field
     */
    @NonNull
    abstract List<Pair<String, String>> getSeriesRecordColumnInfo();

    /** Returns the table name required to store the series data */
    @NonNull
    abstract String getSeriesDataTableName();

    /** Populates the {@code record} with values specific to dataytpe */
    abstract void populateSpecificValues(@NonNull Cursor cursor, T record);

    /** Puts the {@code sample} to the {@code contentValues} */
    abstract void populateSampleTo(@NonNull ContentValues contentValues, @NonNull U sample);

    @NonNull
    private List<Pair<String, String>> getSeriesTableColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PARENT_KEY_COLUMN_NAME, INTEGER));
        columnInfo.addAll(getSeriesRecordColumnInfo());

        return columnInfo;
    }
}
