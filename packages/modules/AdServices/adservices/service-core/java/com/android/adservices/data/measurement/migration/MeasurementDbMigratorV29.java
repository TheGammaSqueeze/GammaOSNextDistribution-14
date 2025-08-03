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

package com.android.adservices.data.measurement.migration;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Attribution;

import java.util.UUID;

/**
 * Migrates Measurement DB to version 29. This upgrade adds the scope column to the attribution
 * table, updates all records to scope event and creates a copy for each record with scope
 * aggregate; also updates the index for the attribution table.
 */
public class MeasurementDbMigratorV29 extends AbstractMeasurementDbMigrator {
    private static final String ATTRIBUTION_DROP_INDEX_SS_SO_DS_DO_EI_TT =
            "DROP INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.AttributionContract.TABLE
                    + "_ss_so_ds_do_ei_tt";

    private static final String ATTRIBUTION_CREATE_INDEX_S_SS_DS_EI_TT =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.AttributionContract.TABLE
                    + "_s_ss_ds_ei_tt"
                    + " ON "
                    + MeasurementTables.AttributionContract.TABLE
                    + "("
                    + MeasurementTables.AttributionContract.SCOPE
                    + ", "
                    + MeasurementTables.AttributionContract.SOURCE_SITE
                    + ", "
                    + MeasurementTables.AttributionContract.DESTINATION_SITE
                    + ", "
                    + MeasurementTables.AttributionContract.ENROLLMENT_ID
                    + ", "
                    + MeasurementTables.AttributionContract.TRIGGER_TIME
                    + ")";

    private void updateAttributionIndex(@NonNull SQLiteDatabase db) {
        db.execSQL(ATTRIBUTION_DROP_INDEX_SS_SO_DS_DO_EI_TT);
        db.execSQL(ATTRIBUTION_CREATE_INDEX_S_SS_DS_EI_TT);
    }

    private void addScopeColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.AttributionContract.TABLE,
                new String[]{ MeasurementTables.AttributionContract.SCOPE });
    }

    private void updateScopeForAllAttributionRecords(@NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AttributionContract.SCOPE,
                Attribution.Scope.EVENT);
        long rows =
                db.update(
                        MeasurementTables.AttributionContract.TABLE,
                        values,
                        null,
                        new String[0]);
        LoggerFactory.getMeasurementLogger()
                .d("Updated attribution scope for " + rows + " attribution records.");
    }

    private void createAggregateScopeAttributionForEachAttributionRecord(
            @NonNull SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        /*table=*/ MeasurementTables.AttributionContract.TABLE,
                        /*columns=*/ null,
                        /*selection*/ null,
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                values.put(
                        MeasurementTables.AttributionContract.ID,
                        UUID.randomUUID().toString());
                values.put(
                        MeasurementTables.AttributionContract.SCOPE,
                        Attribution.Scope.AGGREGATE);
                long rows =
                        db.insert(
                                MeasurementTables.AttributionContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
                if (rows == -1) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Failed to insert attribution record copy with aggregate scope.");
                }
            }
        }
    }

    public MeasurementDbMigratorV29() {
        super(29);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addScopeColumn(db);
        updateAttributionIndex(db);
        updateScopeForAllAttributionRecords(db);
        createAggregateScopeAttributionForEachAttributionRecord(db);
    }
}
