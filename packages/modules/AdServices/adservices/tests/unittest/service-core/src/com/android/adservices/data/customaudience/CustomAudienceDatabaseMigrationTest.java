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

package com.android.adservices.data.customaudience;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class CustomAudienceDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    private static final String COLUMN_NAME_NAME = "name";

    private static final String QUERY_TABLES_FROM_SQL_MASTER =
            "SELECT * FROM sqlite_master WHERE type='table' AND name='%s';";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, CustomAudienceDatabase.class);

    @Test
    public void testMigration2To3() throws IOException {
        final String customAudienceOverrideTable = "custom_audience_overrides";
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2)) {
            ContentValues contentValuesV2 = new ContentValues();
            contentValuesV2.put("owner", CustomAudienceFixture.VALID_OWNER);
            contentValuesV2.put("buyer", CommonFixture.VALID_BUYER_1.toString());
            contentValuesV2.put("name", CustomAudienceFixture.VALID_NAME);
            contentValuesV2.put("app_package_name", CommonFixture.TEST_PACKAGE_NAME_1);
            contentValuesV2.put("bidding_logic", "Whatever js");
            contentValuesV2.put("trusted_bidding_data", "a whatever json");
            db.insert(customAudienceOverrideTable, CONFLICT_FAIL, contentValuesV2);
        }
        // Re-open the database with version 3.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 3, true)) {
            Cursor c = db.query("SELECT * FROM " + customAudienceOverrideTable);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            int biddingLogicVersionIndex = c.getColumnIndex("bidding_logic_version");
            assertTrue(c.isNull(biddingLogicVersionIndex));

            ContentValues contentValuesV3 = new ContentValues();
            contentValuesV3.put("owner", CustomAudienceFixture.VALID_OWNER);
            contentValuesV3.put("buyer", CommonFixture.VALID_BUYER_2.toString());
            contentValuesV3.put("name", CustomAudienceFixture.VALID_NAME);
            contentValuesV3.put("app_package_name", CommonFixture.TEST_PACKAGE_NAME_2);
            contentValuesV3.put("bidding_logic", "Whatever js");
            contentValuesV3.put("bidding_logic_version", 2L);
            contentValuesV3.put("trusted_bidding_data", "a whatever json");
            db.insert(customAudienceOverrideTable, CONFLICT_FAIL, contentValuesV3);
            c =
                    db.query(
                            "SELECT * FROM "
                                    + customAudienceOverrideTable
                                    + " WHERE buyer = '"
                                    + CommonFixture.VALID_BUYER_2
                                    + "'");
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals(2L, c.getLong(c.getColumnIndex("bidding_logic_version")));
            assertEquals(
                    CommonFixture.TEST_PACKAGE_NAME_2,
                    c.getString(c.getColumnIndex("app_package_name")));
        }
    }

    @Test
    public void testMigration5To6() throws IOException {
        String customAudienceTable = "custom_audience";

        String auctionServerRequestFlagsColumnName = "auction_server_request_flags";
        String debuggableColumnName = "debuggable";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        Cursor cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, customAudienceTable));
        // The table should already exist
        assertEquals(1, cursor.getCount());

        cursor = db.query("PRAGMA table_info(custom_audience)");
        // Columns should not yet exist
        assertFalse(checkIfColumnExists(cursor, auctionServerRequestFlagsColumnName));
        cursor.moveToFirst();
        assertFalse(checkIfColumnExists(cursor, debuggableColumnName));

        // Re-open the database with version 6
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true);
        cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, customAudienceTable));
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();

        assertEquals(
                customAudienceTable, cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME)));

        cursor = db.query("PRAGMA table_info(custom_audience)");
        // Columns should now exist
        assertTrue(checkIfColumnExists(cursor, auctionServerRequestFlagsColumnName));
        cursor.moveToFirst();
        assertTrue(checkIfColumnExists(cursor, debuggableColumnName));

        cursor.close();
    }

    private boolean checkIfColumnExists(Cursor cursor, String name) {
        boolean columnExists = false;
        if (cursor.moveToFirst()) {
            do {
                if (Objects.equals(cursor.getString(1), name)) {
                    columnExists = true;
                    break;
                }
            } while (cursor.moveToNext());
        }
        return columnExists;
    }
}
