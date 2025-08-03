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

package com.android.adservices.data.shared.migration;

import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.SharedDbHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class SharedDbMigratorV3Test extends SharedDbMigratorTestBase {

    /**
     * @return shared db target version.
     */
    @Override
    int getTargetVersion() {
        return 3;
    }

    /**
     * @return shared db migrator for version3.
     */
    @Override
    AbstractSharedDbMigrator getTestSubject() {
        return new SharedDbMigratorV3();
    }

    /** Unit test for shared db migration from version 2 to 3. */
    @Test
    public void performMigration_v2ToV3_addLastFetchTimeColumn() {
        // Set up
        SharedDbHelper dbHelper =
                new SharedDbHelper(
                        sContext, SHARED_DATABASE_NAME_FOR_MIGRATION, 2, getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV2();
        MigrationTestHelper.populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 2, 3);

        // Assertion
        assertTrue(SharedDbHelper.hasAllTables(db, EnrollmentTables.ENROLLMENT_TABLES));
        assertTrue(SharedDbHelper.hasAllTables(db, EncryptionKeyTables.ENCRYPTION_KEY_TABLES));
        assertTrue(
                MigrationHelpers.isColumnPresent(
                        db,
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME));
        MigrationTestHelper.verifyDataInDb(db, fakeData);
    }

    /** Create fake data for shared db version 2, contains data in enrollment table. */
    private Map<String, List<ContentValues>> createFakeDataV2() {
        Map<String, List<ContentValues>> tableRowMap = new LinkedHashMap<>();
        // Enrollment table.
        List<ContentValues> enrollmentRows = new ArrayList<>();
        ContentValues enrollmentData =
                ContentValueFixtures.generateEnrollmentDefaultExampleContentValuesV1();
        enrollmentRows.add(enrollmentData);
        tableRowMap.put(EnrollmentTables.EnrollmentDataContract.TABLE, enrollmentRows);
        return tableRowMap;
    }

    /** Unit test for shared db migration from version 3 to 3. */
    @Test
    public void performMigration_v3ToV3() {
        // Set up
        SharedDbHelper dbHelper =
                new SharedDbHelper(
                        sContext, SHARED_DATABASE_NAME_FOR_MIGRATION, 3, getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV2();
        MigrationTestHelper.populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 3, 3);

        // Assertion
        assertTrue(SharedDbHelper.hasAllTables(db, EnrollmentTables.ENROLLMENT_TABLES));
        assertTrue(SharedDbHelper.hasAllTables(db, EncryptionKeyTables.ENCRYPTION_KEY_TABLES));
        assertTrue(
                MigrationHelpers.isColumnPresent(
                        db,
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME));
        MigrationTestHelper.verifyDataInDb(db, fakeData);
    }
}
