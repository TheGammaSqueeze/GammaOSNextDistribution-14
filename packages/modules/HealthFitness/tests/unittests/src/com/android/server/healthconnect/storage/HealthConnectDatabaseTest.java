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

package com.android.server.healthconnect.storage;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HealthConnectDatabaseTest {
    // This number can only increase, as we are not allowed to make changes that remove tables or
    // columns
    private static final int NUM_OF_TABLES = 59;

    @Mock Context mContext;
    private HealthConnectDatabase mHealthConnectDatabase;
    private SQLiteDatabase mSQLiteDatabase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getDatabasePath(anyString()))
                .thenReturn(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getDatabasePath("mock"));
        mHealthConnectDatabase = new HealthConnectDatabase(mContext);
        mSQLiteDatabase = mHealthConnectDatabase.getWritableDatabase();
    }

    @Test
    public void testCreateTable() {
        Truth.assertThat(mHealthConnectDatabase).isNotNull();
        Truth.assertThat(mSQLiteDatabase).isNotNull();
        Cursor cursor =
                mSQLiteDatabase.rawQuery(
                        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND"
                                + " name != 'android_metadata' AND name != 'sqlite_sequence';",
                        null);

        cursor.moveToNext();
        Truth.assertThat(cursor.getInt(0)).isEqualTo(NUM_OF_TABLES);
    }
}
