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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.EventSurfaceType;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Migrates Measurement DB from user version 8 to 9. */
public class MeasurementDbMigratorV9 extends AbstractMeasurementDbMigrator {
    private static final String SOURCE_CONTRACT_BACKUP =
            MeasurementTables.SourceContract.TABLE + "_backup";

    public static final String CREATE_TABLE_SOURCE_V9 =
            "CREATE TABLE "
                    + MeasurementTables.SourceContract.TABLE
                    + " ("
                    + MeasurementTables.SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.SourceContract.EVENT_ID
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.PUBLISHER
                    + " TEXT, "
                    + MeasurementTables.SourceContract.PUBLISHER_TYPE
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.EVENT_REPORT_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.PRIORITY
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.STATUS
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + MeasurementTables.SourceContract.SOURCE_TYPE
                    + " TEXT, "
                    + MeasurementTables.SourceContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTables.SourceContract.ATTRIBUTION_MODE
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.FILTER_DATA
                    + " TEXT, "
                    + MeasurementTables.SourceContract.AGGREGATE_SOURCE
                    + " TEXT, "
                    + MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.DEBUG_KEY
                    + " INTEGER , "
                    + MeasurementTables.SourceContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.REGISTRATION_ID
                    + " TEXT, "
                    + MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS
                    + " TEXT, "
                    + MeasurementTables.SourceContract.INSTALL_TIME
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.DEBUG_JOIN_KEY
                    + " TEXT "
                    + ")";

    private static final List<String> SOURCE_COLUMNS = ImmutableList.of(
            MeasurementTables.SourceContract.ID,
            MeasurementTables.SourceContract.EVENT_ID,
            MeasurementTables.SourceContract.PUBLISHER,
            MeasurementTables.SourceContract.PUBLISHER_TYPE,
            MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS,
            MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
            MeasurementTables.SourceContract.EVENT_TIME,
            MeasurementTables.SourceContract.EXPIRY_TIME,
            MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
            MeasurementTables.SourceContract.PRIORITY,
            MeasurementTables.SourceContract.STATUS,
            MeasurementTables.SourceContract.SOURCE_TYPE,
            MeasurementTables.SourceContract.ENROLLMENT_ID,
            MeasurementTables.SourceContract.REGISTRANT,
            MeasurementTables.SourceContract.ATTRIBUTION_MODE,
            MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
            MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
            MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
            MeasurementTables.SourceContract.FILTER_DATA,
            MeasurementTables.SourceContract.AGGREGATE_SOURCE,
            MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
            MeasurementTables.SourceContract.DEBUG_KEY,
            MeasurementTables.SourceContract.DEBUG_REPORTING,
            MeasurementTables.SourceContract.AD_ID_PERMISSION,
            MeasurementTables.SourceContract.AR_DEBUG_PERMISSION,
            MeasurementTables.SourceContract.REGISTRATION_ID,
            MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
            MeasurementTables.SourceContract.INSTALL_TIME,
            MeasurementTables.SourceContract.DEBUG_JOIN_KEY);

    private static final String SOURCE_DESTINATION_COLUMNS =
            String.join(",", List.of(
                    MeasurementTables.SourceDestination.SOURCE_ID,
                    MeasurementTables.SourceDestination.DESTINATION_TYPE,
                    MeasurementTables.SourceDestination.DESTINATION));

    private static final String MIGRATE_SOURCE_APP_DESTINATION =
            String.format("INSERT INTO %1$s(%2$s) SELECT %3$s, %4$s, %5$s FROM %6$s "
                    + "WHERE %5$s IS NOT NULL;",
                    MeasurementTables.SourceDestination.TABLE,
                    SOURCE_DESTINATION_COLUMNS,
                    MeasurementTables.SourceContract.ID,
                    EventSurfaceType.APP,
                    MeasurementTablesDeprecated.SourceContract.APP_DESTINATION,
                    MeasurementTables.SourceContract.TABLE);

    private static final String MIGRATE_SOURCE_WEB_DESTINATION =
            String.format("INSERT INTO %1$s(%2$s) SELECT %3$s, %4$s, %5$s FROM %6$s "
                    + "WHERE %5$s IS NOT NULL;",
                    MeasurementTables.SourceDestination.TABLE,
                    SOURCE_DESTINATION_COLUMNS,
                    MeasurementTables.SourceContract.ID,
                    EventSurfaceType.WEB,
                    MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION,
                    MeasurementTables.SourceContract.TABLE);

    private static final String SOURCE_CREATE_INDEX_EI =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceContract.TABLE
                    + "_ei "
                    + "ON "
                    + MeasurementTables.SourceContract.TABLE
                    + "("
                    + MeasurementTables.SourceContract.ENROLLMENT_ID
                    + ")";

    private static final String SOURCE_CREATE_INDEX_EI_ET =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceContract.TABLE
                    + "_ei_et "
                    + "ON "
                    + MeasurementTables.SourceContract.TABLE
                    + "( "
                    + MeasurementTables.SourceContract.ENROLLMENT_ID
                    + ", "
                    + MeasurementTables.SourceContract.EXPIRY_TIME
                    + " DESC "
                    + ")";

    private static final String SOURCE_CREATE_INDEX_ET =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceContract.TABLE
                    + "_et "
                    + "ON "
                    + MeasurementTables.SourceContract.TABLE
                    + "("
                    + MeasurementTables.SourceContract.EXPIRY_TIME
                    + ")";

    private static final String SOURCE_CREATE_INDEX_P_S_ET =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceContract.TABLE
                    + "_p_s_et "
                    + "ON "
                    + MeasurementTables.SourceContract.TABLE
                    + "("
                    + MeasurementTables.SourceContract.PUBLISHER
                    + ", "
                    + MeasurementTables.SourceContract.STATUS
                    + ", "
                    + MeasurementTables.SourceContract.EVENT_TIME
                    + ")";

    private static final String SOURCE_DESTINATION_CREATE_INDEX_D =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceDestination.TABLE
                    + "_d"
                    + " ON "
                    + MeasurementTables.SourceDestination.TABLE
                    + "("
                    + MeasurementTables.SourceDestination.DESTINATION
                    + ")";

    private static final String SOURCE_DESTINATION_CREATE_INDEX_S =
            "CREATE INDEX "
                    + MeasurementTables.INDEX_PREFIX
                    + MeasurementTables.SourceDestination.TABLE
                    + "_s"
                    + " ON "
                    + MeasurementTables.SourceDestination.TABLE
                    + "("
                    + MeasurementTables.SourceDestination.SOURCE_ID
                    + ")";

    public MeasurementDbMigratorV9() {
        super(9);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        db.execSQL(MeasurementTables.CREATE_TABLE_SOURCE_DESTINATION_LATEST);
        db.execSQL(SOURCE_DESTINATION_CREATE_INDEX_D);
        db.execSQL(SOURCE_DESTINATION_CREATE_INDEX_S);
        db.execSQL(MIGRATE_SOURCE_APP_DESTINATION);
        db.execSQL(MIGRATE_SOURCE_WEB_DESTINATION);
        alterSourceTable(db);
    }

    private static void alterSourceTable(SQLiteDatabase db) {
        MigrationHelpers.copyAndUpdateTable(
                db,
                MeasurementTables.SourceContract.TABLE,
                SOURCE_CONTRACT_BACKUP,
                CREATE_TABLE_SOURCE_V9,
                SOURCE_COLUMNS);
        db.execSQL(SOURCE_CREATE_INDEX_EI);
        db.execSQL(SOURCE_CREATE_INDEX_EI_ET);
        db.execSQL(SOURCE_CREATE_INDEX_ET);
        db.execSQL(SOURCE_CREATE_INDEX_P_S_ET);
    }
}
