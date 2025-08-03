/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class SharedStorageDatabaseMigrationTest {
    private static final String QUERY_TABLES_FROM_SQL_MASTER =
            "SELECT * FROM sqlite_master " + "WHERE type='table' AND name='%s';";
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, SharedStorageDatabase.class);

    @Test
    public void testMigration1To2() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        Cursor c =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER, DBHistogramEventData.TABLE_NAME));
        assertEquals(0, c.getCount());
        c = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, DBHistogramIdentifier.TABLE_NAME));
        assertEquals(0, c.getCount());

        // Re-open the database with version 2 and provide MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true);
        c = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, DBHistogramEventData.TABLE_NAME));
        assertEquals(1, c.getCount());
        c = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, DBHistogramIdentifier.TABLE_NAME));
        assertEquals(1, c.getCount());
    }
}
