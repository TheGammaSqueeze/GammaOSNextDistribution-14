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

package com.android.adservices.data.encryptionkey;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats;
import com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionStatus;
import com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionType;
import com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.MethodName;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Data Access Object for EncryptionKey. */
public class EncryptionKeyDao implements IEncryptionKeyDao {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static EncryptionKeyDao sSingleton;
    private final SharedDbHelper mDbHelper;
    private final AdServicesLogger mAdServicesLogger;

    @VisibleForTesting
    public EncryptionKeyDao(SharedDbHelper dbHelper) {
        mDbHelper = dbHelper;
        mAdServicesLogger = AdServicesLoggerImpl.getInstance();
    }

    @VisibleForTesting
    public EncryptionKeyDao(SharedDbHelper dbHelper, AdServicesLogger logger) {
        mDbHelper = dbHelper;
        mAdServicesLogger = logger;
    }

    /** Returns an instance of the EncryptionKeyDao given a context. */
    @NonNull
    public static EncryptionKeyDao getInstance(@NonNull Context context) {
        synchronized (EncryptionKeyDao.class) {
            if (sSingleton == null) {
                sSingleton = new EncryptionKeyDao(SharedDbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromEnrollmentId(String enrollmentId)
            throws SQLException {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? ", // The columns for the WHERE clause
                        new String[] {enrollmentId}, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null, // The sort order
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No EncryptionKey in DB with enrollment id: %s.", enrollmentId);
                return encryptionKeyList;
            }

            sLogger.i("Found %s keys for enrollment id %s.", cursor.getCount(), enrollmentId);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID);
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to find EncryptionKey in DB with enrollment id: " + enrollmentId);
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SEARCH_EXCEPTION,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID);
        }
        return encryptionKeyList;
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromEnrollmentIdAndKeyType(
            String enrollmentId, EncryptionKey.KeyType keyType) throws SQLException {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                                + " = ?",
                        new String[] {enrollmentId, keyType.toString()},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No EncryptionKey in DB with enrollment id: %s.", enrollmentId);
                return encryptionKeyList;
            }

            sLogger.i(
                    "Found %s keys for key type: %s, enrollment id %s.",
                    cursor.getCount(), keyType, enrollmentId);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_TYPE);
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to find EncryptionKey in DB with enrollment id:" + enrollmentId);
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SEARCH_EXCEPTION,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_TYPE);
        }
        return encryptionKeyList;
    }

    @Override
    @Nullable
    public EncryptionKey getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
            String enrollmentId, int keyCommitmentId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID
                                + " = ?",
                        new String[] {enrollmentId, String.valueOf(keyCommitmentId)},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i(
                        "No EncryptionKey in DB with enrollmentId: %s, keyCommitmentId: %s.",
                        enrollmentId, String.valueOf(keyCommitmentId));
                return null;
            }
            cursor.moveToNext();
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_ID);
            return SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor);
        } catch (SQLException e) {
            sLogger.e(
                    e,
                    "Failed to find EncryptionKey in DB with keyCommitmentId: " + keyCommitmentId);
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SEARCH_EXCEPTION,
                    MethodName.GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_ID);
        }
        return null;
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromReportingOrigin(
            Uri reportingOrigin, EncryptionKey.KeyType keyType) {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                                + " = ?",
                        new String[] {reportingOrigin.toString(), keyType.toString()},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i(
                        "No EncryptionKey in DB with reportingOrigin: %s, keyType: %s",
                        reportingOrigin.toString(), keyType.toString());
                return encryptionKeyList;
            }

            sLogger.i(
                    "Found %s keys for key type: %s, reporting origin %s.",
                    cursor.getCount(), keyType, reportingOrigin);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.GET_KEY_FROM_REPORTING_ORIGIN);
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(
                    e,
                    "Failed to find EncryptionKey in DB with reportingOrigin: " + reportingOrigin);
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SEARCH_EXCEPTION,
                    MethodName.GET_KEY_FROM_REPORTING_ORIGIN);
        }
        return encryptionKeyList;
    }

    @Override
    public List<EncryptionKey> getAllEncryptionKeys() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        List<EncryptionKey> encryptionKeys = new ArrayList<>();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No Encryption keys in DB.");
                return encryptionKeys;
            }
            while (cursor.moveToNext()) {
                encryptionKeys.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.GET_ALL_KEYS);
            return encryptionKeys;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to get all encryption keys from DB.");
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.READ_TRANSACTION_TYPE,
                    DbTransactionStatus.SEARCH_EXCEPTION,
                    MethodName.GET_ALL_KEYS);
        }
        return encryptionKeys;
    }

    @Override
    public boolean insert(EncryptionKey encryptionKey) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        if (!isEncryptionKeyValid(encryptionKey)) {
            sLogger.e("Encryption key is invalid, can't insert into DB.");
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.INVALID_KEY,
                    MethodName.INSERT_KEY);
            return false;
        }
        EncryptionKey existKey =
                getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        encryptionKey.getEnrollmentId(), encryptionKey.getKeyCommitmentId());
        if (existKey != null) {
            delete(existKey.getId());
        }
        try {
            insertToDb(encryptionKey, db);
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.INSERT_KEY);
        } catch (SQLException e) {
            sLogger.e(e, "Failed to insert EncryptionKey into DB.");
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.INSERT_EXCEPTION,
                    MethodName.INSERT_KEY);
            return false;
        }
        return true;
    }

    @Override
    public boolean insert(List<EncryptionKey> encryptionKeys) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        for (EncryptionKey encryptionKey : encryptionKeys) {
            if (!isEncryptionKeyValid(encryptionKey)) {
                sLogger.e("Encryption key is invalid, can't insert into DB.");
                logEncryptionKeyDbTransactionEndedStats(
                        DbTransactionType.WRITE_TRANSACTION_TYPE,
                        DbTransactionStatus.INVALID_KEY,
                        MethodName.INSERT_KEYS);
                return false;
            }
            EncryptionKey existKey =
                    getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                            encryptionKey.getEnrollmentId(), encryptionKey.getKeyCommitmentId());
            if (existKey != null) {
                // If a key with same enrollment id and key id is saved previously, update the key
                // to the new one.
                delete(existKey.getId());
            }
            try {
                insertToDb(encryptionKey, db);
                logEncryptionKeyDbTransactionEndedStats(
                        DbTransactionType.WRITE_TRANSACTION_TYPE,
                        DbTransactionStatus.SUCCESS,
                        MethodName.INSERT_KEYS);
            } catch (SQLException e) {
                sLogger.e(e, "Failed to insert EncryptionKey into DB.");
                logEncryptionKeyDbTransactionEndedStats(
                        DbTransactionType.WRITE_TRANSACTION_TYPE,
                        DbTransactionStatus.INSERT_EXCEPTION,
                        MethodName.INSERT_KEYS);
                return false;
            }
        }
        return true;
    }

    private static boolean isEncryptionKeyValid(EncryptionKey encryptionKey) {
        return encryptionKey.getEnrollmentId() != null
                && encryptionKey.getKeyType() != null
                && encryptionKey.getEncryptionKeyUrl() != null
                && encryptionKey.getProtocolType() != null
                && encryptionKey.getBody() != null
                && encryptionKey.getExpiration() != 0L
                && encryptionKey.getLastFetchTime() != 0L;
    }

    private void insertToDb(EncryptionKey encryptionKey, SQLiteDatabase db) throws SQLException {
        ContentValues values = new ContentValues();
        values.put(EncryptionKeyTables.EncryptionKeyContract.ID, encryptionKey.getId());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE,
                encryptionKey.getKeyType().name());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID,
                encryptionKey.getEnrollmentId());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN,
                encryptionKey.getReportingOrigin().toString());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.ENCRYPTION_KEY_URL,
                encryptionKey.getEncryptionKeyUrl());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.PROTOCOL_TYPE,
                encryptionKey.getProtocolType().name());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID,
                encryptionKey.getKeyCommitmentId());
        values.put(EncryptionKeyTables.EncryptionKeyContract.BODY, encryptionKey.getBody());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.EXPIRATION,
                encryptionKey.getExpiration());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME,
                encryptionKey.getLastFetchTime());
        try {
            db.insertWithOnConflict(
                    EncryptionKeyTables.EncryptionKeyContract.TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            sLogger.e("Failed to insert EncryptionKey into DB. Exception : " + e.getMessage());
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.INSERT_EXCEPTION,
                    MethodName.INSERT_KEY);
        }
    }

    @Override
    public boolean delete(String id) {
        Objects.requireNonNull(id);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        try {
            db.delete(
                    EncryptionKeyTables.EncryptionKeyContract.TABLE,
                    EncryptionKeyTables.EncryptionKeyContract.ID + " = ?",
                    new String[] {id});
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.SUCCESS,
                    MethodName.DELETE_KEY);
        } catch (SQLException e) {
            sLogger.e(
                    "Failed to delete EncryptionKey in DB with id %s, error: %s ",
                    id, e.getMessage());
            logEncryptionKeyDbTransactionEndedStats(
                    DbTransactionType.WRITE_TRANSACTION_TYPE,
                    DbTransactionStatus.DELETE_EXCEPTION,
                    MethodName.DELETE_KEY);
            return false;
        }
        return true;
    }

    private void logEncryptionKeyDbTransactionEndedStats(
            DbTransactionType dbTransactionType,
            DbTransactionStatus dbTransactionStatus,
            MethodName methodName) {
        AdServicesEncryptionKeyDbTransactionEndedStats stats =
                AdServicesEncryptionKeyDbTransactionEndedStats.builder()
                        .setDbTransactionType(dbTransactionType)
                        .setDbTransactionStatus(dbTransactionStatus)
                        .setMethodName(methodName)
                        .build();
        mAdServicesLogger.logEncryptionKeyDbTransactionEndedStats(stats);
    }
}
