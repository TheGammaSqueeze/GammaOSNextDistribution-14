/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.eab;

import static com.android.ims.rcs.uce.eab.EabProvider.EabDatabaseHelper.SQL_CREATE_COMMON_TABLE;
import static com.android.ims.rcs.uce.eab.EabProvider.EabDatabaseHelper.SQL_CREATE_CONTACT_TABLE;
import static com.android.ims.rcs.uce.eab.EabProvider.EabDatabaseHelper.SQL_CREATE_OPTIONS_TABLE;
import static com.android.ims.rcs.uce.eab.EabProvider.EabDatabaseHelper.SQL_CREATE_PRESENCE_TUPLE_TABLE;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class EabProviderTestable extends EabProvider {
    private static final String TAG = EabProviderTestable.class.getSimpleName();

    private InMemoryEabProviderDbHelper mDbHelper;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate called");
        mDbHelper = new InMemoryEabProviderDbHelper();
        return true;
    }

    // close mDbHelper database object
    protected void closeDatabase() {
        mDbHelper.close();
    }

    void initializeForTesting(Context context) {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = EabProvider.AUTHORITY;

        attachInfoForTesting(context, providerInfo);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor cursor = super.query(uri, projection, selection, selectionArgs, sortOrder);
        Log.d(TAG, "InMemoryEabProviderDbHelper query" + DatabaseUtils.dumpCursorToString(cursor));
        return cursor;
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        Log.d(TAG, "getReadableDatabase called" + mDbHelper.getReadableDatabase());
        return mDbHelper.getReadableDatabase();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        Log.d(TAG, "getWritableDatabase called" + mDbHelper.getWritableDatabase());
        return mDbHelper.getWritableDatabase();
    }

    /**
     * An in memory DB for EabProviderTestable to use
     */
    public static class InMemoryEabProviderDbHelper extends SQLiteOpenHelper {
        public InMemoryEabProviderDbHelper() {
            super(null,      // no context is needed for in-memory db
                    null,      // db file name is null for in-memory db
                    null,      // CursorFactory is null by default
                    1);        // db version is no-op for tests
            Log.d(TAG, "InMemoryEabProviderDbHelper creating in-memory database");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            //set up the EAB table
            Log.d(TAG, "InMemoryEabProviderDbHelper onCreate");
            db.execSQL(SQL_CREATE_CONTACT_TABLE);
            db.execSQL(SQL_CREATE_COMMON_TABLE);
            db.execSQL(SQL_CREATE_PRESENCE_TUPLE_TABLE);
            db.execSQL(SQL_CREATE_OPTIONS_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "InMemoryEabProviderDbHelper onUpgrade");
            return;
        }
    }
}
