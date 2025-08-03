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
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV19Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v18ToV19WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        18,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        Map<String, List<ContentValues>> fakeData = createFakeDataV19(source1Id);
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 18, 19);
        // Assertion
        Map<String, Set<String>> droppedKeys = new HashMap<>();
        droppedKeys.put(
                MeasurementTables.SourceContract.TABLE,
                Set.of(MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS));

        MigrationTestHelper.verifyDataInDb(
                db,
                fakeData,
                droppedKeys,
                Map.of(
                        MeasurementTables.SourceContract.TABLE,
                        Set.of(MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS)));
        // Verify the migration for following columns:
        // 1) event_report_windows: null
        // 2) max_event_level_reports: old value (3) of max_bucket_increments
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.EVENT_REPORT_WINDOWS,
                            MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(2, cursor.getCount());
            while (cursor.moveToNext()) {
                assertTrue(
                        cursor.isNull(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract.EVENT_REPORT_WINDOWS)));
                assertEquals(
                        ContentValueFixtures.generateSourceContentValuesV18()
                                .get(
                                        MeasurementTablesDeprecated.SourceContract
                                                .MAX_BUCKET_INCREMENTS),
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS)));
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeDataV19(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV18();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV18());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 19;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV19();
    }
}
