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

package com.android.adservices.data.signals;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class ProtectedSignalsDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, ProtectedSignalsDatabase.class);

    @Test
    public void testMigration1To2() throws IOException {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertFalse(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertFalse(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertFalse(tables.contains(DBEncoderEndpoint.TABLE_NAME));
        }
        // Re-open the database with version 2.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 2, true)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertTrue(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderEndpoint.TABLE_NAME));
        }
    }

    private static List<String> listTables(SupportSQLiteDatabase db) {
        Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' order by name");
        c.moveToFirst();
        ImmutableList.Builder<String> tables = new ImmutableList.Builder<>();
        do {
            tables.add(c.getString(0));
        } while (c.moveToNext());
        return tables.build();
    }

    @Test
    public void testMigration2To3() throws IOException {
        final String encoderLogicMetadataTable = "encoder_logics";
        final int version = 2;
        final int failedEncodingCount = 3;
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2)) {
            ContentValues contentValuesV2 = new ContentValues();

            contentValuesV2.put("buyer", CommonFixture.VALID_BUYER_1.toString());
            contentValuesV2.put("version", version);
            contentValuesV2.put("creation_time", CommonFixture.FIXED_NOW.toEpochMilli());
            db.insert(encoderLogicMetadataTable, CONFLICT_FAIL, contentValuesV2);
        }
        // Re-open the database with version 3.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 3, true)) {
            Cursor c = db.query("SELECT * FROM " + encoderLogicMetadataTable);
            assertEquals(1, c.getCount());
            c.moveToFirst();

            int failedEncodingCountIndex = c.getColumnIndex("failed_encoding_count");
            assertEquals(0, c.getInt(failedEncodingCountIndex));

            ContentValues contentValuesV3 = new ContentValues();
            contentValuesV3.put("buyer", CommonFixture.VALID_BUYER_2.toString());
            contentValuesV3.put("version", version);
            contentValuesV3.put("creation_time", CommonFixture.FIXED_NOW.toEpochMilli());
            contentValuesV3.put("failed_encoding_count", failedEncodingCount);
            db.insert(encoderLogicMetadataTable, CONFLICT_FAIL, contentValuesV3);
            c =
                    db.query(
                            "SELECT * FROM "
                                    + encoderLogicMetadataTable
                                    + " WHERE buyer = '"
                                    + CommonFixture.VALID_BUYER_2
                                    + "'");
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals(3, c.getInt(c.getColumnIndex("failed_encoding_count")));
            assertEquals(
                    CommonFixture.VALID_BUYER_2.toString(), c.getString(c.getColumnIndex("buyer")));
        }
    }

    @Test
    public void testMigration3To4() throws IOException {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertTrue(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderEndpoint.TABLE_NAME));
            assertFalse(tables.contains(DBSignalsUpdateMetadata.TABLE_NAME));
        }
        // Re-open the database with version 4.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 4, true)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertTrue(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderEndpoint.TABLE_NAME));
            assertTrue(tables.contains(DBSignalsUpdateMetadata.TABLE_NAME));
        }
    }
}
