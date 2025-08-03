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

import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV11Test extends MeasurementDbMigratorTestBase {

    @Override
    int getTargetVersion() {
        return 11;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV11();
    }

    @Test
    public void performMigration_v10ToV11WithData_maintainsDataIntegrity() {
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        10,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeData();
        MigrationTestHelper.populateDb(db, fakeData);

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 18));

        getTestSubject().performMigration(db, 10, 11);

        MigrationTestHelper.verifyDataInDb(
                db,
                fakeData,
                ImmutableMap.of(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        Set.of(
                                MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID,
                                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_TYPE,
                                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT,
                                MeasurementTablesDeprecated.AsyncRegistration
                                        .LAST_PROCESSING_TIME)),
                ImmutableMap.of(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        Set.of(MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID)));

        verifyRegistrationIdWasUpdated(db);
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.KeyValueDataContract.TABLE, 3));
    }

    private void verifyRegistrationIdWasUpdated(SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID},
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(6, cursor.getCount());
            while (cursor.moveToNext()) {
                String registrationId = cursor.getString(0);
                assertNotNull(registrationId);
                assertFalse(registrationId.isEmpty());
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeData() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // Async Registration Table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration1 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration1.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration1);
        ContentValues asyncRegistration2 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration2.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration2);
        ContentValues asyncRegistration3 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration3.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration3);

        // Add records with RegistrationID as null
        ContentValues asyncRegistration4 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration4.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistration4.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID, (String) null);
        asyncRegistrationRows.add(asyncRegistration4);
        ContentValues asyncRegistration5 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration5.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistration4.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID, (String) null);
        asyncRegistrationRows.add(asyncRegistration5);
        ContentValues asyncRegistration6 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV10();
        asyncRegistration4.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID, (String) null);
        asyncRegistrationRows.add(asyncRegistration6);

        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);
        return tableRowsMap;
    }
}
