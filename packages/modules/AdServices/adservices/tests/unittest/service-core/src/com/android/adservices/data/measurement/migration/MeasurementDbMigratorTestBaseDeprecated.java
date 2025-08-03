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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DbHelperV1;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;

/** @deprecated Use {@link MeasurementDbMigratorTestBase} for migration to V7+ versions. */
@Deprecated
public abstract class MeasurementDbMigratorTestBaseDeprecated {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String DATABASE_NAME_FOR_MIGRATION = "adservices_migration.db";

    private static DbHelper sDbHelper;

    @Mock private SQLiteDatabase mDb;

    @Before
    public void setup() {
        // To force create a fresh db, delete the existing DB file
        File databaseFile = sContext.getDatabasePath(DATABASE_NAME_FOR_MIGRATION);
        if (databaseFile.exists()) {
            databaseFile.delete();
        }

        sDbHelper = null;
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

    protected DbHelper getDbHelper(int version) {
        synchronized (DbHelper.class) {
            if (sDbHelper == null) {
                if (version == 1) {
                    sDbHelper = new DbHelperV1(sContext, DATABASE_NAME_FOR_MIGRATION, version);
                }
            }
            return sDbHelper;
        }
    }

    abstract int getTargetVersion();

    abstract AbstractMeasurementDbMigrator getTestSubject();

    // Create our own method instead of using DatabaseUtils.cursorRowToContentValues because that
    // one reads every type from the cursor as a String.
    public static ContentValues cursorRowToContentValues(Cursor cursor) {
        String[] columns = cursor.getColumnNames();
        ContentValues values = new ContentValues();
        for (int i = 0; i < columns.length; i++) {
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_FLOAT:
                    values.put(columns[i], cursor.getDouble(i));
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    values.put(columns[i], cursor.getLong(i));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                default:
                    values.put(columns[i], cursor.getString(i));
                    break;
            }
        }
        return values;
    }
}


