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

import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV13Test extends MeasurementDbMigratorTestBase {

    @Test
    public void performMigration_v12Tov13WithData_maintainDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        12,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> testData = createFakeDataV12();
        MigrationTestHelper.populateDb(db, testData);

        // Execution
        getTestSubject().performMigration(db, 12, 13);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, testData);

        // Check that new columns are initialized with null values.
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.PLATFORM_AD_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.DEBUG_AD_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.PLATFORM_AD_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.DEBUG_AD_ID));

        tableAndNewColumnPairs.forEach(
                pair -> {
                    try (Cursor cursor =
                            db.query(
                                    pair.first,
                                    new String[] {pair.second}, /* selection */
                                    null, /* selectionArgs */
                                    null, /* groupBy */
                                    null, /* having */
                                    null, /* orderBy */
                                    null)) {
                        assertNotEquals(0, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataV12() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // AsyncRegistration table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV12();
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration);
        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);

        // Source table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV12();
        source.put(MeasurementTables.SourceContract.ID, UUID.randomUUID().toString());
        sourceRows.add(source);
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger = ContentValueFixtures.generateTriggerContentValuesV12();
        trigger.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger);
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 13;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV13();
    }
}
