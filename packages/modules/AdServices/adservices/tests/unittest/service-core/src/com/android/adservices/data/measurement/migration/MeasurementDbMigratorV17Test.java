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
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV17Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v16ToV17() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        16,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Map<String, List<ContentValues>> dataV16 = createFakeDataDebugReportV16();
        populateDb(db, dataV16);

        // Execution
        getTestSubject().performMigration(db, 16, 17);

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.DebugReportContract.TABLE, 6));
    }

    private Map<String, List<ContentValues>> createFakeDataDebugReportV16() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        List<ContentValues> rows = new ArrayList<>();
        rows.add(ContentValueFixtures.generateDebugReportContentValuesV16());
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, rows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 17;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV17();
    }
}
