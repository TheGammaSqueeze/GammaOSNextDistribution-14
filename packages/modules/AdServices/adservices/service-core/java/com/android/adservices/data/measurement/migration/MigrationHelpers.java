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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Helper methods for database migrations. */
public final class MigrationHelpers {

    /**
     * Creates a table using {@code createTableQuery} and fills it with values from {@code
     * tableName} using {@code backupTableName} as a temporary storage.
     */
    static void copyAndUpdateTable(
            SQLiteDatabase db, String tableName, String backupTableName, String createTableQuery) {
        copyAndUpdateTable(
                db, tableName, backupTableName, createTableQuery, null);
    }

    /**
     * Creates a table using {@code createTableQuery} and fills {@code columns} with values from
     * {@code tableName} using {@code backupTableName} as a temporary storage.
     */
    static void copyAndUpdateTable(SQLiteDatabase db, String tableName, String backupTableName,
            String createTableQuery, @Nullable List<String> columns) {
        if (!isTablePresent(db, tableName)) {
            return;
        }
        String renameTableQuery =
                String.format("ALTER TABLE %1$s RENAME TO %2$s", tableName, backupTableName);
        db.execSQL("PRAGMA foreign_keys=OFF");
        db.execSQL(renameTableQuery);
        db.execSQL(createTableQuery);

        String columnsStr;
        String dropBackupTable = String.format("DROP TABLE %1$s", backupTableName);
        if (columns == null) {
            List<String> backupTableColumnNames = getTableColumnNames(db, backupTableName);
            List<String> newTableColumnNames = getTableColumnNames(db, tableName);

            if (!new HashSet<>(newTableColumnNames).containsAll(backupTableColumnNames)) {
                // Not all the columns in the old table are present in the new table.  Proceeding
                // with an empty table.
                LoggerFactory.getMeasurementLogger()
                        .w(
                                "Error during measurement migration (copyAndUpdateTable()).  The"
                                        + " new table does not have all of the columns from the old"
                                        + " table.");
                db.execSQL(dropBackupTable);
                return;
            }

            columnsStr = String.join(",", backupTableColumnNames);
        } else {
            columnsStr = String.join(",", columns);
        }

        String insertValuesFromBackupQuery =
                String.format(
                        "INSERT INTO %1$s (%2$s) SELECT %2$s FROM %3$s",
                        tableName, columnsStr, backupTableName);

        db.execSQL(insertValuesFromBackupQuery);
        db.execSQL(dropBackupTable);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    /** Add INTEGER columns {@code columnNames} to {@code tableName} in the {@code db}. */
    static void addIntColumnsIfAbsent(SQLiteDatabase db, String tableName, String[] columnNames) {
        for (String column : columnNames) {
            addIntColumnIfAbsent(db, tableName, column);
        }
    }

    /** Add an INTEGER column {@code columnName} to {@code tableName} in the {@code db}. */
    private static void addIntColumnIfAbsent(
            SQLiteDatabase db, String tableName, String columnName) {
        if (isColumnPresent(db, tableName, columnName)) {
            return;
        }
        db.execSQL(String.format("ALTER TABLE %1$s ADD %2$s INTEGER", tableName, columnName));
    }

    /** Add a TEXT column {@code columnName} to {@code tableName} in the {@code db}. */
    static void addTextColumnIfAbsent(SQLiteDatabase db, String tableName, String columnName) {
        if (isColumnPresent(db, tableName, columnName)) {
            return;
        }
        db.execSQL(String.format("ALTER TABLE %1$s ADD %2$s TEXT", tableName, columnName));
    }

    static void logUpdateQuery(String column, long count, String table) {
        String log =
                String.format(Locale.ENGLISH, "Updated %s for %d %s records", column, count, table);
        LoggerFactory.getMeasurementLogger().d(log);
    }

    /** Returns true if {@code tableName} contains a column {@code columnName}. */
    static boolean isColumnPresent(SQLiteDatabase db, String tableName, String columnName) {
        final String query =
                "select p.name from sqlite_master s join pragma_table_info(s.name) p where "
                        + "s.tbl_name = '"
                        + tableName
                        + "' and "
                        + "p.name = '"
                        + columnName
                        + "'";

        try (Cursor cursor = db.rawQuery(query, null)) {
            return cursor.getCount() == 1;
        }
    }

    /** Returns true if {@code db} contains a table {@code tableName}. */
    private static boolean isTablePresent(SQLiteDatabase db, String tableName) {
        String query =
                String.format(
                        "SELECT name FROM sqlite_master where type = 'table' and name = '%1$s'",
                        tableName);
        try (Cursor cursor = db.rawQuery(query, null)) {
            return cursor.getCount() != 0;
        }
    }

    private static List<String> getTableColumnNames(SQLiteDatabase db, String tableName) {
        String getTableColumnNamesQuery = String.format("PRAGMA TABLE_INFO(%1$s)", tableName);
        ArrayList<String> tableColumnNames = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(getTableColumnNamesQuery, null)) {
            while (cursor.moveToNext()) {
                tableColumnNames.add(cursor.getString(cursor.getColumnIndex("name")));
            }
        }
        return tableColumnNames;
    }
}
