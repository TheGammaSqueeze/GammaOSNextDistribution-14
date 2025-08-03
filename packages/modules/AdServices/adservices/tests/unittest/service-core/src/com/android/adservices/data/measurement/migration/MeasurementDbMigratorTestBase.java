/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.createReferenceDbAtVersion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.MeasurementDbHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.stream.Stream;

/**
 * Base class for {@link IMeasurementDbMigrator}s migrating to v7+ versions. Extending this class
 * brings in schema validation for creating the DB at a version as well as migrating from previous
 * version to the target version. Verification is done against a database built at the target
 * version using the scripts defined in {@link
 * com.android.adservices.data.measurement.MeasurementDbSchemaTrail}. To introduce test class for
 * migration to a new version x, i.e. MeasurementDbMigratorVxTest, the following steps need to be
 * followed -
 *
 * <ol>
 *   <li>Create new entries for create table statements and create index statements for the new
 *       version in {@link com.android.adservices.data.measurement.MeasurementDbSchemaTrail}
 *   <li>Extend {@link MeasurementDbMigratorTestBase}
 *   <li>Override {@link MeasurementDbMigratorTestBase#getTargetVersion()} and return the integer x.
 *   <li>Override {@link MeasurementDbMigratorTestBase#getTestSubject()} and return the object to
 *       test, i.e. an instance of MeasurementDbMigratorVx.
 *   <li>Add a test for data migration to MeasurementDbMigratorVxTest class.
 * </ol>
 */
public abstract class MeasurementDbMigratorTestBase {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    protected static final String MEASUREMENT_DATABASE_NAME_FOR_MIGRATION =
            "adservices_msmt_migration.db";
    protected static final String MEASUREMENT_DATABASE_REFERENCE_DB_NAME =
            "adservices_msmt_migration_reference.db";

    @Mock private SQLiteDatabase mDb;

    @Before
    public void setup() {
        Stream.of(MEASUREMENT_DATABASE_NAME_FOR_MIGRATION, MEASUREMENT_DATABASE_REFERENCE_DB_NAME)
                .map(sContext::getDatabasePath)
                .filter(File::exists)
                .forEach(File::delete);
    }

    @Test
    public void performMigration_alreadyOnHigherVersion_skipMigration() {
        // Execution
        getTestSubject().performMigration(mDb, (getTargetVersion() + 1), (getTargetVersion() + 2));

        // Verify
        verify(mDb, never()).execSQL(any());
    }

    @Test
    public void performMigration_lowerRequestedVersion_skipMigration() {
        // Execution
        getTestSubject().performMigration(mDb, (getTargetVersion() - 2), (getTargetVersion() - 1));

        // Verify
        verify(mDb, never()).execSQL(any());
    }

    @Test
    public void performMigration_createAtTargetVersion_dbIsAsExpected() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        getTargetVersion(),
                        getDbHelperForTest());
        SQLiteDatabase goldenDb =
                createReferenceDbAtVersion(
                        sContext, MEASUREMENT_DATABASE_REFERENCE_DB_NAME, getTargetVersion());

        // Execution - invokes onCreate implicitly
        SQLiteDatabase actualDb = dbHelper.getWritableDatabase();

        // Assertion
        DbTestUtil.assertDatabasesEqual(goldenDb, actualDb);
    }

    @Test
    public void performMigration_migrateFromPrevToTargetVersion_dbIsAsExpected() {
        // Setup
        int prevVersion = getTargetVersion() - 1;
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        prevVersion,
                        getDbHelperForTest());
        SQLiteDatabase goldenDb =
                createReferenceDbAtVersion(
                        sContext, MEASUREMENT_DATABASE_REFERENCE_DB_NAME, getTargetVersion());

        // Execution
        SQLiteDatabase actualDb = dbHelper.getWritableDatabase();
        getTestSubject().performMigration(actualDb, prevVersion, getTargetVersion());

        // Assertion
        DbTestUtil.assertDatabasesEqual(goldenDb, actualDb);
    }

    abstract int getTargetVersion();

    abstract AbstractMeasurementDbMigrator getTestSubject();
}
