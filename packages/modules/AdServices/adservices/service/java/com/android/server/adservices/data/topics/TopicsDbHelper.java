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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;

import java.io.File;
import java.io.PrintWriter;

/**
 * Helper to manage the Topics API system service database. Designed as a singleton to make sure
 * that all Topics API system service usages get the same reference.
 *
 * @hide
 */
public class TopicsDbHelper extends SQLiteOpenHelper {
    /** The current version of the database. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public static final int CURRENT_DATABASE_VERSION = 1;

    private static final String DATABASE_NAME = "adservices_topics.db";
    private static final Object LOCK = new Object();

    private static TopicsDbHelper sSingleton = null;
    private final File mDbFile;
    // The version when the database is actually created
    private final int mDbVersion;

    /**
     * It's only public to unit test.
     *
     * @param context the context
     * @param dbName Name of database to query
     * @param dbVersion db version
     */
    @VisibleForTesting
    public TopicsDbHelper(@NonNull Context context, @NonNull String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        mDbFile = context.getDatabasePath(dbName);
        this.mDbVersion = dbVersion;
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static TopicsDbHelper getInstance(@NonNull Context ctx) {
        synchronized (LOCK) {
            if (sSingleton == null) {
                sSingleton = new TopicsDbHelper(ctx, DATABASE_NAME, CURRENT_DATABASE_VERSION);
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        LogUtil.d(
                "TopicsDbHelper.onCreate with version %d. Name: %s", mDbVersion, mDbFile.getName());
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d(
                "DbHelper.onUpgrade. Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d("Downgrade database version from %d to %d.", oldVersion, newVersion);
        // prevent parent class to throw SQLiteException
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to get a readable database");
            return null;
        }
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to get a writeable database");
            return null;
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix, String[] args) {
        writer.printf("%sTopicsDbHelper\n", prefix);
        String prefix2 = prefix + "  ";
        writer.printf("%sCURRENT_DATABASE_VERSION: %d\n", prefix2, CURRENT_DATABASE_VERSION);
        writer.printf("%smDbFile: %s\n", prefix2, mDbFile);
        writer.printf("%smDbVersion: %d\n", prefix2, mDbVersion);
    }
}
