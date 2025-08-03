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
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.AsyncRegistrationContract;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migrates Measurement DB to version 11.
 *
 * <p>
 *
 * <h3>Changes:</h3>
 *
 * <p>Table: msmt_async_registration_contract
 *
 * <p>Updated Columns:
 *
 * <ol>
 *   <li>registration_id: {@code "TEXT"} to {@code "TEXT NOT NULL"}
 * </ol>
 *
 * <p>Removed Columns:
 *
 * <ol>
 *   <li>enrollment_id
 *   <li>redirect_type
 *   <li>redirect_count
 * </ol>
 */
public class MeasurementDbMigratorV11 extends AbstractMeasurementDbMigrator {

    private static final String ASYNC_REGISTRATION_BACKUP_TABLE =
            MeasurementTables.AsyncRegistrationContract.TABLE + "_backup";

    private static final List<String> ASYNC_REGISTRATION_COLUMNS =
            ImmutableList.of(
                    MeasurementTables.AsyncRegistrationContract.ID,
                    MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                    MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                    MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                    MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                    MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                    MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                    MeasurementTables.AsyncRegistrationContract.TYPE,
                    MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                    MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                    MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                    MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                    MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                    MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID);

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V11 =
            "CREATE TABLE "
                    + AsyncRegistrationContract.TABLE
                    + " ("
                    + AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AsyncRegistrationContract.REGISTRATION_URI
                    + " TEXT, "
                    + AsyncRegistrationContract.WEB_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.OS_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.VERIFIED_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.TOP_ORIGIN
                    + " TEXT, "
                    + AsyncRegistrationContract.SOURCE_TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + AsyncRegistrationContract.REQUEST_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.DEBUG_KEY_ALLOWED
                    + " INTEGER, "
                    + AsyncRegistrationContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT NOT NULL"
                    + ")";

    public static final String CREATE_TABLE_KEY_VALUE_DATA_V11 =
            "CREATE TABLE "
                    + MeasurementTables.KeyValueDataContract.TABLE
                    + " ("
                    + MeasurementTables.KeyValueDataContract.DATA_TYPE
                    + " TEXT NOT NULL, "
                    + MeasurementTables.KeyValueDataContract.KEY
                    + " TEXT NOT NULL, "
                    + MeasurementTables.KeyValueDataContract.VALUE
                    + " TEXT, "
                    + " CONSTRAINT type_key_primary_con PRIMARY KEY ( "
                    + MeasurementTables.KeyValueDataContract.DATA_TYPE
                    + ", "
                    + MeasurementTables.KeyValueDataContract.KEY
                    + " )"
                    + " )";

    public MeasurementDbMigratorV11() {
        super(11);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        setRegistrationIdsForAsyncRegistration(db);
        MigrationHelpers.copyAndUpdateTable(
                db,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                ASYNC_REGISTRATION_BACKUP_TABLE,
                CREATE_TABLE_ASYNC_REGISTRATION_V11,
                ASYNC_REGISTRATION_COLUMNS);
        db.execSQL(CREATE_TABLE_KEY_VALUE_DATA_V11);
    }

    private void setRegistrationIdsForAsyncRegistration(SQLiteDatabase db) {
        List<String> asyncRegIds = new ArrayList<>();
        // Querying all the AsyncReg ID's which have no registrationId set.
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {MeasurementTables.AsyncRegistrationContract.ID},
                        MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID + " IS NULL ",
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                asyncRegIds.add(cursor.getString(0));
            }
        }
        // Setting registrationIds for the retrieved AsyncReg records.
        asyncRegIds.forEach(
                (asyncRegId) -> {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                            UUID.randomUUID().toString());
                    db.update(
                            MeasurementTables.AsyncRegistrationContract.TABLE,
                            contentValues,
                            MeasurementTables.AsyncRegistrationContract.ID + " = ? ",
                            new String[] {asyncRegId});
                });
    }
}
