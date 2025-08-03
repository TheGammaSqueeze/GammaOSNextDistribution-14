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

package com.android.server.adservices.data.topics;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.adservices.LogUtil;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/** Utility class to test Topics API database related classes */
public final class TopicsDbTestUtil {
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String DATABASE_NAME_FOR_TEST = "adservices_topics_test.db";
    private static final Object LOCK = new Object();

    private static TopicsDbHelper sSingleton;

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
    public static TopicsDbHelper getDbHelperForTest() {
        synchronized (LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new TopicsDbHelper(
                                PPAPI_CONTEXT,
                                DATABASE_NAME_FOR_TEST,
                                TopicsDbHelper.CURRENT_DATABASE_VERSION);
            }
            return sSingleton;
        }
    }

    /** Return true if table exists in the DB and column count matches. */
    public static boolean doesTableExistAndColumnCountMatch(
            SQLiteDatabase db, String tableName, int columnCount) {
        final Set<String> tableColumns = getTableColumns(db, tableName);
        int actualCol = tableColumns.size();
        LogUtil.d("DbTestUtil_log_test: table name: %s, column count: %d", tableName, actualCol);
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
}
