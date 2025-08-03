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

package com.android.federatedcompute.services.data;

import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.ENCRYPTION_KEY_TABLE;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.FederatedComputeEncryptionColumns;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/** DAO for accessing encryption key table */
public class FederatedComputeEncryptionKeyDao {
    private static final String TAG = FederatedComputeEncryptionKeyDao.class.getSimpleName();

    private final FederatedComputeDbHelper mDbHelper;

    private final Clock mClock;

    private static volatile FederatedComputeEncryptionKeyDao sSingletonInstance;

    private FederatedComputeEncryptionKeyDao(FederatedComputeDbHelper dbHelper, Clock clock) {
        mDbHelper = dbHelper;
        mClock = clock;
    }

    /**
     * @return an instance of FederatedComputeEncryptionKeyDao given a context
     */
    @NonNull
    public static FederatedComputeEncryptionKeyDao getInstance(Context context) {
        if (sSingletonInstance == null) {
            synchronized (FederatedComputeEncryptionKeyDao.class) {
                if (sSingletonInstance == null) {
                    sSingletonInstance =
                            new FederatedComputeEncryptionKeyDao(
                                    FederatedComputeDbHelper.getInstance(context),
                                    MonotonicClock.getInstance());
                }
            }
        }
        return sSingletonInstance;
    }

    /** It is only public to unit test. */
    @VisibleForTesting
    public static FederatedComputeEncryptionKeyDao getInstanceForTest(Context context) {
        if (sSingletonInstance == null) {
            synchronized (FederatedComputeEncryptionKeyDao.class) {
                if (sSingletonInstance == null) {
                    FederatedComputeDbHelper dbHelper =
                            FederatedComputeDbHelper.getInstanceForTest(context);
                    Clock clk = MonotonicClock.getInstance();
                    sSingletonInstance = new FederatedComputeEncryptionKeyDao(dbHelper, clk);
                }
            }
        }
        return sSingletonInstance;
    }

    /** Insert a key to the encryption_key table. */
    public boolean insertEncryptionKey(FederatedComputeEncryptionKey key) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        ContentValues values = new ContentValues();
        values.put(FederatedComputeEncryptionColumns.KEY_IDENTIFIER, key.getKeyIdentifier());
        values.put(FederatedComputeEncryptionColumns.PUBLIC_KEY, key.getPublicKey());
        values.put(FederatedComputeEncryptionColumns.KEY_TYPE, key.getKeyType());
        values.put(FederatedComputeEncryptionColumns.CREATION_TIME, key.getCreationTime());
        values.put(FederatedComputeEncryptionColumns.EXPIRY_TIME, key.getExpiryTime());

        long jobId =
                db.insertWithOnConflict(
                        ENCRYPTION_KEY_TABLE, "", values, SQLiteDatabase.CONFLICT_REPLACE);
        return jobId != -1;
    }

    /**
     * Read from encryption key table given selection, order and limit conidtions.
     *
     * @return a list of {@link FederatedComputeEncryptionKey}.
     */
    @VisibleForTesting
    public List<FederatedComputeEncryptionKey> readFederatedComputeEncryptionKeysFromDatabase(
            String selection, String[] selectionArgs, String orderBy, int count) {
        List<FederatedComputeEncryptionKey> keyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        String[] selectColumns = {
            FederatedComputeEncryptionColumns.KEY_IDENTIFIER,
            FederatedComputeEncryptionColumns.PUBLIC_KEY,
            FederatedComputeEncryptionColumns.KEY_TYPE,
            FederatedComputeEncryptionColumns.CREATION_TIME,
            FederatedComputeEncryptionColumns.EXPIRY_TIME
        };

        Cursor cursor = null;
        try {
            cursor =
                    db.query(
                            ENCRYPTION_KEY_TABLE,
                            selectColumns,
                            selection,
                            selectionArgs,
                            /* groupBy= */ null,
                            /* having= */ null,
                            /* orderBy= */ orderBy,
                            /* limit= */ String.valueOf(count));
            while (cursor.moveToNext()) {
                FederatedComputeEncryptionKey.Builder encryptionKeyBuilder =
                        new FederatedComputeEncryptionKey.Builder()
                                .setKeyIdentifier(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedComputeEncryptionColumns
                                                                .KEY_IDENTIFIER)))
                                .setPublicKey(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedComputeEncryptionColumns
                                                                .PUBLIC_KEY)))
                                .setKeyType(
                                        cursor.getInt(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedComputeEncryptionColumns
                                                                .KEY_TYPE)))
                                .setCreationTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedComputeEncryptionColumns
                                                                .CREATION_TIME)))
                                .setExpiryTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedComputeEncryptionColumns
                                                                .EXPIRY_TIME)));
                keyList.add(encryptionKeyBuilder.build());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return keyList;
    }

    /**
     * @return latest expired keys (order by expiry time).
     */
    public List<FederatedComputeEncryptionKey> getLatestExpiryNKeys(int count) {
        String selection = FederatedComputeEncryptionColumns.EXPIRY_TIME + " > ?";
        String[] selectionArgs = {String.valueOf(mClock.currentTimeMillis())};
        // reverse order of expiry time
        String orderBy = FederatedComputeEncryptionColumns.EXPIRY_TIME + " DESC";
        return readFederatedComputeEncryptionKeysFromDatabase(
                selection, selectionArgs, orderBy, count);
    }

    /**
     * Delete expired keys.
     *
     * @return number of keys deleted.
     */
    public int deleteExpiredKeys() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        String whereClause = FederatedComputeEncryptionColumns.EXPIRY_TIME + " < ?";
        String[] whereArgs = {String.valueOf(mClock.currentTimeMillis())};
        int deletedRows = db.delete(ENCRYPTION_KEY_TABLE, whereClause, whereArgs);
        LogUtil.d(TAG, "Deleted %s expired keys from database", deletedRows);
        return deletedRows;
    }
}
