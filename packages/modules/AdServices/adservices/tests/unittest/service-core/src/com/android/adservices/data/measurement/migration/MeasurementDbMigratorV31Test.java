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

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.registration.AsyncRedirect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV31Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v30ToV31WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        30,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> testData = createFakeDataV30();
        MigrationTestHelper.populateDb(db, testData);

        // Execution
        getTestSubject().performMigration(db, 30, 31);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, testData);

        String[] columns =
                new String[] {
                    MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                };
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        columns, /* selection */
                        null, /* selectionArgs */
                        null, /* groupBy */
                        null, /* having */
                        null, /* orderBy */
                        null)) {
            assertEquals(1, cursor.getCount());
            while (cursor.moveToNext()) {
                assertEquals(
                        AsyncRedirect.RedirectBehavior.AS_IS.name(),
                        cursor.getString(cursor.getColumnIndex(columns[0])));
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeDataV30() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // AsyncRegistration table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV24();
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration);
        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);

        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 31;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV31();
    }
}
