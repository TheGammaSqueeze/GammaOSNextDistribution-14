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

package com.android.adservices.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.shared.SharedDbHelper;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class DbTestUtil {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String DATABASE_NAME_FOR_TEST = "adservices_test.db";
    private static final String MSMT_DATABASE_NAME_FOR_TEST = "adservices_msmt_test.db";
    private static final String SHARED_DATABASE_NAME_FOR_TEST = "adservices_shared_test.db";

    private static DbHelper sSingleton;
    private static MeasurementDbHelper sMsmtSingleton;
    private static SharedDbHelper sSharedSingleton;

    /** Erases all data from the table rows */
    public static void deleteTable(String tableName) {
        SQLiteDatabase db = getDbHelperForTest().safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        db.delete(tableName, /* whereClause= */ null, /* whereArgs= */ null);
    }

    /**
     * Create an instance of database instance for testing.
     *
     * @return a test database
     */
    public static DbHelper getDbHelperForTest() {
        synchronized (DbHelper.class) {
            if (sSingleton == null) {
                sSingleton =
                        new DbHelper(sContext, DATABASE_NAME_FOR_TEST, DbHelper.DATABASE_VERSION_7);
            }
            return sSingleton;
        }
    }

    public static MeasurementDbHelper getMeasurementDbHelperForTest() {
        synchronized (MeasurementDbHelper.class) {
            if (sMsmtSingleton == null) {
                sMsmtSingleton =
                        new MeasurementDbHelper(
                                sContext,
                                MSMT_DATABASE_NAME_FOR_TEST,
                                MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                                getDbHelperForTest());
            }
            return sMsmtSingleton;
        }
    }

    public static SharedDbHelper getSharedDbHelperForTest() {
        synchronized (SharedDbHelper.class) {
            if (sSharedSingleton == null) {
                sSharedSingleton =
                        new SharedDbHelper(
                                sContext,
                                SHARED_DATABASE_NAME_FOR_TEST,
                                SharedDbHelper.CURRENT_DATABASE_VERSION,
                                getDbHelperForTest());
            }
            return sSharedSingleton;
        }
    }

    /** Return true if table exists in the DB and column count matches. */
    public static boolean doesTableExistAndColumnCountMatch(
            SQLiteDatabase db, String tableName, int columnCount) {
        final Set<String> tableColumns = getTableColumns(db, tableName);
        int actualCol = tableColumns.size();
        Log.d("DbTestUtil_log_test,", " table name: " + tableName + " column count: " + actualCol);
        return tableColumns.size() == columnCount;
    }

    /** Returns column names of the table. */
    public static Set<String> getTableColumns(SQLiteDatabase db, String tableName) {
        String query =
                "select p.name from sqlite_master s "
                        + "join pragma_table_info(s.name) p "
                        + "where s.tbl_name = '"
                        + tableName
                        + "'";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor is null.");
        }

        ImmutableSet.Builder<String> tableColumns = ImmutableSet.builder();
        while (cursor.moveToNext()) {
            tableColumns.add(cursor.getString(0));
        }

        return tableColumns.build();
    }

    /** Return true if the given index exists in the DB. */
    public static boolean doesIndexExist(SQLiteDatabase db, String index) {
        String query = "SELECT * FROM sqlite_master WHERE type='index' and name='" + index + "'";
        Cursor cursor = db.rawQuery(query, null);
        return cursor != null && cursor.getCount() > 0;
    }

    public static boolean doesTableExist(SQLiteDatabase db, String table) {
        String query = "SELECT * FROM sqlite_master WHERE type='table' and name='" + table + "'";
        Cursor cursor = db.rawQuery(query, null);
        return cursor != null && cursor.getCount() > 0;
    }

    /** Return test database name */
    public static String getDatabaseNameForTest() {
        return DATABASE_NAME_FOR_TEST;
    }

    public static void assertDatabasesEqual(SQLiteDatabase expectedDb, SQLiteDatabase actualDb) {
        List<String> expectedTables = getTables(expectedDb);
        List<String> actualTables = getTables(actualDb);
        assertArrayEquals(expectedTables.toArray(), actualTables.toArray());
        assertTableSchemaEqual(expectedDb, actualDb, expectedTables);
        assertIndexesEqual(expectedDb, actualDb, expectedTables);
    }

    public static void assertMeasurementTablesDoNotExist(SQLiteDatabase db) {
        assertFalse(doesTableExist(db, "msmt_source"));
        assertFalse(doesTableExist(db, "msmt_trigger"));
        assertFalse(doesTableExist(db, "msmt_async_registration_contract"));
        assertFalse(doesTableExist(db, "msmt_event_report"));
        assertFalse(doesTableExist(db, "msmt_attribution"));
        assertFalse(doesTableExist(db, "msmt_aggregate_report"));
        assertFalse(doesTableExist(db, "msmt_aggregate_encryption_key"));
        assertFalse(doesTableExist(db, "msmt_debug_report"));
        assertFalse(doesTableExist(db, "msmt_xna_ignored_sources"));
    }

    private static void assertTableSchemaEqual(
            SQLiteDatabase expectedDb, SQLiteDatabase actualDb, List<String> tableNames) {
        for (String tableName : tableNames) {
            Cursor columnsCursorExpected =
                    expectedDb.rawQuery("PRAGMA TABLE_INFO(" + tableName + ")", null);
            Cursor columnsCursorActual =
                    actualDb.rawQuery("PRAGMA TABLE_INFO(" + tableName + ")", null);
            assertEquals(
                    "Table columns mismatch for " + tableName,
                    columnsCursorExpected.getCount(),
                    columnsCursorActual.getCount());

            // Checks the columns in order. Newly created columns should be inserted as the end.
            while (columnsCursorExpected.moveToNext() && columnsCursorActual.moveToNext()) {
                assertEquals(
                        "Column mismatch for " + tableName,
                        columnsCursorExpected.getString(
                                columnsCursorExpected.getColumnIndex("name")),
                        columnsCursorActual.getString(columnsCursorActual.getColumnIndex("name")));
                assertEquals(
                        "Column mismatch for " + tableName,
                        columnsCursorExpected.getString(
                                columnsCursorExpected.getColumnIndex("type")),
                        columnsCursorActual.getString(columnsCursorActual.getColumnIndex("type")));
                assertEquals(
                        "Column mismatch for " + tableName,
                        columnsCursorExpected.getInt(
                                columnsCursorExpected.getColumnIndex("notnull")),
                        columnsCursorActual.getInt(columnsCursorActual.getColumnIndex("notnull")));
                assertEquals(
                        "Column mismatch for " + tableName,
                        columnsCursorExpected.getString(
                                columnsCursorExpected.getColumnIndex("dflt_value")),
                        columnsCursorActual.getString(
                                columnsCursorActual.getColumnIndex("dflt_value")));
                assertEquals(
                        "Column mismatch for " + tableName,
                        columnsCursorExpected.getInt(columnsCursorExpected.getColumnIndex("pk")),
                        columnsCursorActual.getInt(columnsCursorActual.getColumnIndex("pk")));
            }

            columnsCursorExpected.close();
            columnsCursorActual.close();
        }
    }

    private static List<String> getTables(SQLiteDatabase db) {
        String listTableQuery = "SELECT name FROM sqlite_master where type = 'table'";
        List<String> tables = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(listTableQuery, null)) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(cursor.getColumnIndex("name")));
            }
        }
        Collections.sort(tables);
        return tables;
    }

    private static void assertIndexesEqual(
            SQLiteDatabase expectedDb, SQLiteDatabase actualDb, List<String> tables) {
        for (String tableName : tables) {
            String indexListQuery =
                    "SELECT name FROM sqlite_master where type = 'index' AND tbl_name = '"
                            + tableName
                            + "' ORDER BY name ASC";
            Cursor indexListCursorExpected = expectedDb.rawQuery(indexListQuery, null);
            Cursor indexListCursorActual = actualDb.rawQuery(indexListQuery, null);
            assertEquals(
                    "Table indexes mismatch for " + tableName,
                    indexListCursorExpected.getCount(),
                    indexListCursorActual.getCount());

            while (indexListCursorExpected.moveToNext() && indexListCursorActual.moveToNext()) {
                String expectedIndexName =
                        indexListCursorExpected.getString(
                                indexListCursorExpected.getColumnIndex("name"));
                assertEquals(
                        "Index mismatch for " + tableName,
                        expectedIndexName,
                        indexListCursorActual.getString(
                                indexListCursorActual.getColumnIndex("name")));

                assertIndexInfoEqual(expectedDb, actualDb, expectedIndexName);
            }

            indexListCursorExpected.close();
            indexListCursorActual.close();
        }
    }

    private static void assertIndexInfoEqual(
            SQLiteDatabase expectedDb, SQLiteDatabase actualDb, String indexName) {
        Cursor indexInfoCursorExpected =
                expectedDb.rawQuery("PRAGMA main.INDEX_INFO (" + indexName + ")", null);
        Cursor indexInfoCursorActual =
                actualDb.rawQuery("PRAGMA main.INDEX_INFO (" + indexName + ")", null);
        assertEquals(
                "Index columns count mismatch for " + indexName,
                indexInfoCursorExpected.getCount(),
                indexInfoCursorActual.getCount());

        while (indexInfoCursorExpected.moveToNext() && indexInfoCursorActual.moveToNext()) {
            assertEquals(
                    "Index info mismatch for " + indexName,
                    indexInfoCursorExpected.getInt(indexInfoCursorExpected.getColumnIndex("seqno")),
                    indexInfoCursorActual.getInt(indexInfoCursorActual.getColumnIndex("seqno")));
            assertEquals(
                    "Index info mismatch for " + indexName,
                    indexInfoCursorExpected.getInt(indexInfoCursorExpected.getColumnIndex("cid")),
                    indexInfoCursorActual.getInt(indexInfoCursorActual.getColumnIndex("cid")));
            assertEquals(
                    "Index info mismatch for " + indexName,
                    indexInfoCursorExpected.getString(
                            indexInfoCursorExpected.getColumnIndex("name")),
                    indexInfoCursorActual.getString(indexInfoCursorActual.getColumnIndex("name")));
        }

        indexInfoCursorExpected.close();
        indexInfoCursorActual.close();
    }
}
