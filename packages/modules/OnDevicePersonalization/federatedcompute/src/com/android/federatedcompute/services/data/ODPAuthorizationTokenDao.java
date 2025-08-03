/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.data;

import static com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODPAuthorizationTokenColumns;

import com.google.common.annotations.VisibleForTesting;

public class ODPAuthorizationTokenDao {
    private static final String TAG = ODPAuthorizationTokenDao.class.getSimpleName();

    private final FederatedComputeDbHelper mDbHelper;

    private final Clock mClock;

    private static volatile ODPAuthorizationTokenDao sSingletonInstance;

    private ODPAuthorizationTokenDao(FederatedComputeDbHelper dbHelper, Clock clock) {
        mDbHelper = dbHelper;
        mClock = clock;
    }

    /**
     * @return an instance of ODPAuthorizationTokenDao given a context
     */
    @NonNull
    public static ODPAuthorizationTokenDao getInstance(Context context) {
        if (sSingletonInstance == null) {
            synchronized (ODPAuthorizationTokenDao.class) {
                if (sSingletonInstance == null) {
                    sSingletonInstance = new ODPAuthorizationTokenDao(
                            FederatedComputeDbHelper.getInstance(context),
                            MonotonicClock.getInstance()
                    );
                }
            }
        }
        return sSingletonInstance;
    }

    /** Return a test instance with in-memory database. It is for test only. */
    @VisibleForTesting
    public static ODPAuthorizationTokenDao getInstanceForTest(Context context) {
        if (sSingletonInstance == null) {
            synchronized (ODPAuthorizationTokenDao.class) {
                if (sSingletonInstance == null) {
                    sSingletonInstance = new ODPAuthorizationTokenDao(
                            FederatedComputeDbHelper.getInstanceForTest(context),
                            MonotonicClock.getInstance()
                    );
                }
            }
        }
        return sSingletonInstance;
    }

    /** Insert a token to the odp authorization token table. */
    public boolean insertAuthorizationToken(ODPAuthorizationToken authorizationToken) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        ContentValues values = new ContentValues();
        values.put(ODPAuthorizationTokenColumns.OWNER_IDENTIFIER,
                authorizationToken.getOwnerIdentifier());
        values.put(ODPAuthorizationTokenColumns.AUTHORIZATION_TOKEN,
                authorizationToken.getAuthorizationToken());
        values.put(ODPAuthorizationTokenColumns.CREATION_TIME,
                authorizationToken.getCreationTime());
        values.put(ODPAuthorizationTokenColumns.EXPIRY_TIME,
                authorizationToken.getExpiryTime());


        long jobId =
                db.insertWithOnConflict(
                        ODP_AUTHORIZATION_TOKEN_TABLE, "", values, SQLiteDatabase.CONFLICT_REPLACE);
        return jobId != -1;
    }

    /** Get an ODP adopter's unexpired authorization token.
     * @return an unexpired authorization token. */
    public ODPAuthorizationToken getUnexpiredAuthorizationToken(String ownerIdentifier) {
        String selection = ODPAuthorizationTokenColumns.EXPIRY_TIME + " > ? " + "AND "
                + ODPAuthorizationTokenColumns.OWNER_IDENTIFIER + " = ?";
        String[] selectionArgs = {String.valueOf(mClock.currentTimeMillis()), ownerIdentifier};
        String orderBy = ODPAuthorizationTokenColumns.EXPIRY_TIME + " DESC";
        return readTokenFromDatabase(selection, selectionArgs, orderBy);
    }

    /** Delete an ODP adopter's authorization token.
     * @return the number of rows deleted. */
    public int deleteAuthorizationToken(String ownerIdentifier) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        String whereClause = ODPAuthorizationTokenColumns.OWNER_IDENTIFIER + " = ?";
        String[] whereArgs = {ownerIdentifier};
        int deletedRows = db.delete(ODP_AUTHORIZATION_TOKEN_TABLE, whereClause, whereArgs);
        LogUtil.d(TAG, "Deleted %d expired tokens for %s from database", deletedRows,
                ownerIdentifier);
        return deletedRows;
    }


    /** Batch delete all expired authorization tokens.
     * @return the number of rows deleted. */
    public int deleteExpiredAuthorizationTokens() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        String whereClause = ODPAuthorizationTokenColumns.EXPIRY_TIME + " < ?";
        String[] whereArgs = { String.valueOf(mClock.currentTimeMillis()) };
        int deletedRows = db.delete(ODP_AUTHORIZATION_TOKEN_TABLE, whereClause, whereArgs);
        LogUtil.d(TAG, "Deleted %d expired tokens", deletedRows);
        return deletedRows;
    }



    private ODPAuthorizationToken readTokenFromDatabase(
            String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        String[] selectColumns = {
                ODPAuthorizationTokenColumns.OWNER_IDENTIFIER,
                ODPAuthorizationTokenColumns.AUTHORIZATION_TOKEN,
                ODPAuthorizationTokenColumns.CREATION_TIME,
                ODPAuthorizationTokenColumns.EXPIRY_TIME,
        };

        Cursor cursor = null;
        ODPAuthorizationToken authToken = null;
        try {
            cursor =
                    db.query(
                            ODP_AUTHORIZATION_TOKEN_TABLE,
                            selectColumns,
                            selection,
                            selectionArgs,
                            /* groupBy= */ null,
                            /* having= */ null,
                            /* orderBy= */ orderBy,
                            /* limit= */ String.valueOf(1));
            while (cursor.moveToNext()) {
                ODPAuthorizationToken.Builder encryptionKeyBuilder =
                        new ODPAuthorizationToken.Builder(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                ODPAuthorizationTokenColumns.OWNER_IDENTIFIER)),
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                ODPAuthorizationTokenColumns.AUTHORIZATION_TOKEN)),
                                cursor.getLong(
                                        cursor.getColumnIndexOrThrow(
                                                ODPAuthorizationTokenColumns.CREATION_TIME)),
                                cursor.getLong(
                                        cursor.getColumnIndexOrThrow(
                                                ODPAuthorizationTokenColumns.EXPIRY_TIME))
                        );
                authToken = encryptionKeyBuilder.build();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return authToken;
    }
}
