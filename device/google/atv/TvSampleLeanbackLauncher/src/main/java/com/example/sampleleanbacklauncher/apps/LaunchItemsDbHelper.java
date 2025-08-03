/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.apps;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.os.Trace;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.util.ArrayMap;

import java.util.Date;
import java.util.Map;

@WorkerThread
public class LaunchItemsDbHelper extends SQLiteOpenHelper {

    // Increment whenever schema changes
    private static final int DATABASE_VERSION = 1;
    // Database name
    private static final String DATABASE_NAME = "launch_items";

    private static final String CREATE_APP_TABLE =
            "CREATE TABLE IF NOT EXISTS " + AppDbItem.TABLE_NAME + "("
                + AppDbItem.COLUMN_COMPONENT + " TEXT NOT NULL PRIMARY KEY , "
                + AppDbItem.COLUMN_ORDER_PRIORITY + " INTEGER NOT NULL DEFAULT 0 , "
                + AppDbItem.COLUMN_LAST_OPEN + " INTEGER "
            + " )";

    public LaunchItemsDbHelper(Context context) {
        super(context, DATABASE_NAME, new SQLiteDatabase.CursorFactory() {
            @Override
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                                    String editTable, SQLiteQuery query) {
                return new SQLiteCursor(masterQuery, editTable, query);
            }
        }, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_APP_TABLE);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // DO NOT USE CONSTANTS FOR DB UPGRADE STEPS, USE ONLY LITERAL SQL STRINGS!
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        Trace.beginSection("getWritableDatabase");
        try {
            return super.getWritableDatabase();
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        Trace.beginSection("getReadableDatabase");
        try {
            return super.getReadableDatabase();
        } finally {
            Trace.endSection();
        }
    }

    @Nullable
    public Map<ComponentName, Date> readLastOpens() {
        Trace.beginSection("readLastOpens");
        try {
            final SQLiteDatabase db = getReadableDatabase();

            try (Cursor c = db.query(
                    AppDbItem.TABLE_NAME,
                    new String[] {AppDbItem.COLUMN_COMPONENT, AppDbItem.COLUMN_LAST_OPEN},
                    null, null, null, null, null, null)) {

                final int componentIndex = c.getColumnIndex(AppDbItem.COLUMN_COMPONENT);
                final int lastOpenIndex = c.getColumnIndex(AppDbItem.COLUMN_LAST_OPEN);

                final Map<ComponentName, Date> map = new ArrayMap<>(c.getCount());
                while (c.moveToNext()) {
                    final ComponentName componentName =
                            ComponentName.unflattenFromString(c.getString(componentIndex));
                    if (c.isNull(lastOpenIndex)) {
                        map.put(componentName, null);
                    } else {
                        map.put(componentName, new Date(c.getLong(lastOpenIndex)));
                    }
                }
                return map;

            }
        } finally {
            Trace.endSection();
        }
    }

    public void writeLastOpen(ComponentName componentName, Date lastOpen) {
        Trace.beginSection("writeLastOpen");
        try {
            final SQLiteDatabase db = getWritableDatabase();

            db.beginTransactionNonExclusive();
            try {
                final ContentValues contentValues = new ContentValues(2);
                contentValues.put(AppDbItem.COLUMN_LAST_OPEN, lastOpen.getTime());

                final int cnt = db.update(
                        AppDbItem.TABLE_NAME,
                        contentValues,
                        AppDbItem.COLUMN_COMPONENT + "=?",
                        new String[]{componentName.flattenToString()});

                if (cnt < 1) {
                    contentValues.put(AppDbItem.COLUMN_COMPONENT, componentName.flattenToString());
                    db.insert(AppDbItem.TABLE_NAME, AppDbItem.COLUMN_COMPONENT, contentValues);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            Trace.endSection();
        }
    }

    public Map<ComponentName, Long> readOrderPriorities() {
        Trace.beginSection("readOrderPriorities");
        try {
            final SQLiteDatabase db = getReadableDatabase();

            try (Cursor c = db.query(
                    AppDbItem.TABLE_NAME,
                    new String[] {AppDbItem.COLUMN_COMPONENT, AppDbItem.COLUMN_ORDER_PRIORITY},
                    null, null, null, null, null, null)) {

                final int componentIndex = c.getColumnIndex(AppDbItem.COLUMN_COMPONENT);
                final int priorityIndex = c.getColumnIndex(AppDbItem.COLUMN_ORDER_PRIORITY);

                final Map<ComponentName, Long> map = new ArrayMap<>(c.getCount());
                while (c.moveToNext()) {
                    final ComponentName componentName =
                            ComponentName.unflattenFromString(c.getString(componentIndex));
                    map.put(componentName, c.getLong(priorityIndex));
                }
                return map;
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Writes the order priority field, and does not touch other items' priorities
     * @param componentName {@link ComponentName} of item to update
     * @param priority New priority
     */
    public void writeOrderPriority(ComponentName componentName, long priority) {
        Trace.beginSection("writeOrderPriority");
        try {
            final SQLiteDatabase db = getWritableDatabase();

            db.beginTransactionNonExclusive();
            try {
                final ContentValues contentValues = new ContentValues(2);
                contentValues.put(AppDbItem.COLUMN_ORDER_PRIORITY, priority);

                final int cnt = db.update(
                        AppDbItem.TABLE_NAME,
                        contentValues,
                        AppDbItem.COLUMN_COMPONENT + "=?",
                        new String[]{componentName.flattenToString()});

                if (cnt < 1) {
                    contentValues.put(AppDbItem.COLUMN_COMPONENT, componentName.flattenToString());
                    db.insert(AppDbItem.TABLE_NAME, AppDbItem.COLUMN_COMPONENT, contentValues);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Writes the order priority field, and increments all priorities equal or greater if the new
     * priority is greater than 0
     * @param componentName {@link ComponentName} of item to update
     * @param priority New priority
     */
    public void writeOrderPriorityAndShift(ComponentName componentName, int priority) {
        Trace.beginSection("writeOrderPriorityAndShift");
        try {
            final SQLiteDatabase db = getWritableDatabase();

            db.beginTransactionNonExclusive();
            try {
                if (priority > 0) {
                    db.execSQL("UPDATE " + AppDbItem.TABLE_NAME
                            + " SET " + AppDbItem.COLUMN_ORDER_PRIORITY + "="
                                    + AppDbItem.COLUMN_ORDER_PRIORITY + "+1"
                            + " WHERE " + AppDbItem.COLUMN_ORDER_PRIORITY + ">=?",
                            new String[] {Integer.toString(priority)});
                }

                writeOrderPriority(componentName, priority);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            Trace.endSection();
        }
    }

    private static class AppDbItem {
        public static final String TABLE_NAME = "app_items";

        // Primary key
        public static final String COLUMN_COMPONENT = "component";
        // Ordering priority: higher numbers are higher in list
        public static final String COLUMN_ORDER_PRIORITY = "order_priority";
        // Recency date
        public static final String COLUMN_LAST_OPEN = "last_open";
    }
}
