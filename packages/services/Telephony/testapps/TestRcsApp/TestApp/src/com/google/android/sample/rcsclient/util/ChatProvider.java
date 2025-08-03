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

package com.google.android.sample.rcsclient.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

/** A database to store chat message. */
public class ChatProvider extends ContentProvider {
    public static final Uri CHAT_URI = Uri.parse("content://rcsprovider/chat");
    public static final Uri SUMMARY_URI = Uri.parse("content://rcsprovider/summary");
    public static final String AUTHORITY = "rcsprovider";
    private static final String TAG = "TestRcsApp.ChatProvider";
    private static final int DATABASE_VERSION = 2;
    private static final String CHAT_TABLE_NAME = "chat";
    private static final String SUMMARY_TABLE_NAME = "summary";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int URI_CHAT = 1;
    private static final int URI_SUMMARY = 2;
    private static final String QUERY_CHAT_TABLE = " SELECT * FROM " + CHAT_TABLE_NAME;

    static {
        URI_MATCHER.addURI(AUTHORITY, "chat", URI_CHAT);
        URI_MATCHER.addURI(AUTHORITY, "summary", URI_SUMMARY);
    }

    private RcsDatabaseHelper mRcsHelper;

    @Override
    public boolean onCreate() {
        mRcsHelper = new RcsDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = mRcsHelper.getReadableDatabase();
        int match = URI_MATCHER.match(uri);

        Log.d(TAG, "Query URI: " + match);
        switch (match) {
            case URI_CHAT:
                qb.setTables(CHAT_TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            case URI_SUMMARY:
                qb.setTables(SUMMARY_TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            default:
                Log.e(TAG, "no such uri");
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        SQLiteDatabase db = mRcsHelper.getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        long id;
        switch (match) {
            case URI_CHAT:
                id = db.insert(CHAT_TABLE_NAME, "", contentValues);
                break;
            case URI_SUMMARY:
                id = db.insert(SUMMARY_TABLE_NAME, "", contentValues);
                break;
            default:
                Log.e(TAG, "no such uri");
                throw new SQLException("no such uri");
        }
        if (id > 0) {
            Uri msgUri = Uri.withAppendedPath(uri, String.valueOf(id));
            getContext().getContentResolver().notifyChange(uri, null);
            Log.i(TAG, "msgUri:" + msgUri);
            return msgUri;
        } else {
            throw new SQLException("fail to add chat message");
        }
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = mRcsHelper.getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        int result = 0;
        String tableName = "";
        switch (match) {
            case URI_CHAT:
                tableName = CHAT_TABLE_NAME;
                break;
            case URI_SUMMARY:
                tableName = SUMMARY_TABLE_NAME;
                break;
        }
        if (!TextUtils.isEmpty(tableName)) {
            result = db.updateWithOnConflict(tableName, contentValues,
                    selection, selectionArgs, SQLiteDatabase.CONFLICT_REPLACE);
            getContext().getContentResolver().notifyChange(uri, null);
            Log.d(TAG, "Update uri: " + match + " result: " + result);
        } else {
            Log.d(TAG, "Update. Not support URI.");
        }
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    /** Define columns for the chat table. */
    public static class RcsColumns implements BaseColumns {
        public static final String SRC_PHONE_NUMBER = "source_phone_number";
        public static final String DEST_PHONE_NUMBER = "destination_phone_number";
        public static final String CHAT_MESSAGE = "chat_message";
        public static final String MSG_TIMESTAMP = "msg_timestamp";
        public static final String IS_READ = "is_read";
        public static final String RESULT = "result";
    }

    /** Define columns for the summary table. */
    public static class SummaryColumns implements BaseColumns {
        public static final String REMOTE_PHONE_NUMBER = "remote_phone_number";
        public static final String LATEST_MESSAGE = "latest_message";
        public static final String MSG_TIMESTAMP = "msg_timestamp";
        public static final String IS_READ = "is_read";
    }

    /** Database helper */
    public static final class RcsDatabaseHelper extends SQLiteOpenHelper {
        public static final String SQL_CREATE_RCS_TABLE = "CREATE TABLE "
                + CHAT_TABLE_NAME
                + " ("
                + RcsColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + RcsColumns.SRC_PHONE_NUMBER + " Text DEFAULT NULL, "
                + RcsColumns.DEST_PHONE_NUMBER + " Text DEFAULT NULL, "
                + RcsColumns.CHAT_MESSAGE + " Text DEFAULT NULL, "
                + RcsColumns.MSG_TIMESTAMP + " LONG DEFAULT NULL, "
                + RcsColumns.IS_READ + " BOOLEAN DEFAULT false, "
                + RcsColumns.RESULT + " BOOLEAN DEFAULT true);";

        public static final String SQL_CREATE_SUMMARY_TABLE = "CREATE TABLE "
                + SUMMARY_TABLE_NAME
                + " ("
                + SummaryColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SummaryColumns.REMOTE_PHONE_NUMBER + " Text DEFAULT NULL, "
                + SummaryColumns.LATEST_MESSAGE + " Text DEFAULT NULL, "
                + SummaryColumns.MSG_TIMESTAMP + " LONG DEFAULT NULL, "
                + SummaryColumns.IS_READ + " BOOLEAN DEFAULT false);";
        private static final String DB_NAME = "RcsDatabase";

        public RcsDatabaseHelper(Context context) {
            super(context, DB_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_RCS_TABLE);
            db.execSQL(SQL_CREATE_SUMMARY_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "DB upgrade from " + oldVersion + " to " + newVersion);
            db.beginTransaction();
            try {
                switch (oldVersion) {
                    case 1:
                        upgradeDatabaseToVersion2(db);
                        break;
                    default: // fall out
                }
                db.setTransactionSuccessful();
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage(), ex);
            } finally {
                db.endTransaction();
            }
        }

        private static void upgradeDatabaseToVersion2(SQLiteDatabase db) {
            try {
                Log.d(TAG, "upgradeDatabaseToVersion2");
                String alterTable = "ALTER TABLE " + CHAT_TABLE_NAME + " ADD COLUMN ";
                db.execSQL(alterTable + RcsColumns.RESULT + " BOOLEAN DEFAULT true");
            } catch (SQLiteException e) {
                Log.w(TAG, "[upgradeDatabaseToVersion10] Exception adding column: " + e);
            }
        }
    }
}
