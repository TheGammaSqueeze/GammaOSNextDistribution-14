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
import static com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV16Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v15ToV16WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        15,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        Map<String, List<ContentValues>> fakeData = createFakeDataSourcesV15(source1Id);
        populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 15, 16);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value
        int count = 0;
        try (Cursor cursor =
                db.query(
                        SourceContract.TABLE,
                        new String[] {SourceContract.COARSE_EVENT_REPORT_DESTINATIONS},
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertNotEquals(0, cursor.getCount());
            while (cursor.moveToNext()) {
                count++;
                assertTrue(
                        cursor.isNull(
                                cursor.getColumnIndex(
                                        SourceContract.COARSE_EVENT_REPORT_DESTINATIONS)));
            }
        }

        assertEquals(2, count);
    }

    private Map<String, List<ContentValues>> createFakeDataSourcesV15(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV15();
        source1.put(SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV15());
        tableRowsMap.put(SourceContract.TABLE, sourceRows);

        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 16;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV16();
    }
}
