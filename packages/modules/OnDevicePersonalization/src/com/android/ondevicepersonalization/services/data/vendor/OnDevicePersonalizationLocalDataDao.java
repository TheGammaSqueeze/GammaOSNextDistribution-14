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

package com.android.ondevicepersonalization.services.data.vendor;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;


import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dao used to manage access to local data tables
 */
public class OnDevicePersonalizationLocalDataDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationLocalDataDao";
    private static final String LOCAL_DATA_TABLE_NAME_PREFIX = "localdata";

    private static final long BLOB_SIZE_LIMIT = 100000;

    private static final Map<String, OnDevicePersonalizationLocalDataDao> sLocalDataDaos =
            new ConcurrentHashMap<>();
    private final OnDevicePersonalizationDbHelper mDbHelper;
    private final ComponentName mOwner;
    private final String mCertDigest;
    private final String mTableName;
    private final String mFileDir;

    private OnDevicePersonalizationLocalDataDao(OnDevicePersonalizationDbHelper dbHelper,
            ComponentName owner, String certDigest, String fileDir) {
        this.mDbHelper = dbHelper;
        this.mOwner = owner;
        this.mCertDigest = certDigest;
        this.mTableName = getTableName(owner, certDigest);
        this.mFileDir = fileDir;
    }

    /**
     * Returns an instance of the OnDevicePersonalizationLocalDataDao given a context.
     *
     * @param context    The context of the application
     * @param owner      Name of service that owns the table
     * @param certDigest Hash of the certificate used to sign the package
     * @return Instance of OnDevicePersonalizationLocalDataDao for accessing the requested
     * package's table
     */
    public static OnDevicePersonalizationLocalDataDao getInstance(Context context,
            ComponentName owner, String certDigest) {
        // TODO: Validate the owner and certDigest
        String tableName = getTableName(owner, certDigest);
        String fileDir = getFileDir(tableName, context.getFilesDir());
        OnDevicePersonalizationLocalDataDao instance = sLocalDataDaos.get(tableName);
        if (instance == null) {
            synchronized (sLocalDataDaos) {
                instance = sLocalDataDaos.get(tableName);
                if (instance == null) {
                    OnDevicePersonalizationDbHelper dbHelper =
                            OnDevicePersonalizationDbHelper.getInstance(context);
                    instance = new OnDevicePersonalizationLocalDataDao(
                            dbHelper, owner, certDigest, fileDir);
                    sLocalDataDaos.put(tableName, instance);
                }
            }
        }
        return instance;
    }

    /**
     * Returns an instance of the OnDevicePersonalizationLocalDataDao given a context. This is used
     * for testing only
     */
    @VisibleForTesting
    public static OnDevicePersonalizationLocalDataDao getInstanceForTest(Context context,
            ComponentName owner, String certDigest) {
        synchronized (OnDevicePersonalizationLocalDataDao.class) {
            String tableName = getTableName(owner, certDigest);
            String fileDir = getFileDir(tableName, context.getFilesDir());
            OnDevicePersonalizationLocalDataDao instance = sLocalDataDaos.get(tableName);
            if (instance == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                instance = new OnDevicePersonalizationLocalDataDao(
                        dbHelper, owner, certDigest, fileDir);
                sLocalDataDaos.put(tableName, instance);
            }
            return instance;
        }
    }

    /**
     * Creates file directory name based on table name and base directory
     */
    public static String getFileDir(String tableName, File baseDir) {
        return baseDir + "/LocalData/" + tableName;
    }

    /**
     * Attempts to create the LocalData table
     *
     * @return true if it already exists or was created, false otherwise.
     */
    protected boolean createTableIfNotExists() {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.execSQL(LocalDataContract.LocalDataEntry.getCreateTableIfNotExistsStatement(
                    mTableName));
        } catch (SQLException e) {
            sLogger.e(TAG + ": Failed to create table: " + mTableName, e);
            return false;
        }
        // Create directory for large files
        File dir = new File(mFileDir);
        if (!dir.isDirectory()) {
            return dir.mkdirs();
        }
        return true;
    }

    /**
     * Creates local data tables and adds corresponding vendor_settings metadata
     */
    public boolean createTable() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            if (!createTableIfNotExists()) {
                return false;
            }
            if (!OnDevicePersonalizationVendorDataDao.insertNewSyncToken(db, mOwner, mCertDigest,
                    0L)) {
                return false;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /**
     * Creates the LocalData table name for the given owner
     */
    public static String getTableName(ComponentName owner, String certDigest) {
        return DbUtils.getTableName(LOCAL_DATA_TABLE_NAME_PREFIX, owner, certDigest);
    }

    /**
     * Reads single row in the local data table
     *
     * @return Local data for the single row requested
     */
    public byte[] readSingleLocalDataRow(String key) {
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String[] projection = {
                    LocalDataContract.LocalDataEntry.TYPE,
                    LocalDataContract.LocalDataEntry.DATA
            };
            String selection = LocalDataContract.LocalDataEntry.KEY + " = ?";
            String[] selectionArgs = {key};
            try (Cursor cursor = db.query(
                    mTableName,
                    projection,
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    /* orderBy= */ null
            )) {
                if (cursor.getCount() < 1) {
                    sLogger.d(TAG + ": Failed to find requested key: " + key);
                    return null;
                }
                cursor.moveToNext();
                byte[] blob = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(LocalDataContract.LocalDataEntry.DATA));
                int type = cursor.getInt(
                        cursor.getColumnIndexOrThrow(LocalDataContract.LocalDataEntry.TYPE));
                if (type == LocalDataContract.DATA_TYPE_FILE) {
                    File file = new File(mFileDir, new String(blob));
                    return Files.readAllBytes(file.toPath());
                }
                return blob;
            }
        } catch (SQLiteException | IOException e) {
            sLogger.e(TAG + ": Failed to read local data row", e);
        }
        return null;
    }

    /**
     * Updates the given local data row, adds it if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    public boolean updateOrInsertLocalData(LocalData localData) {
        long timeMillis = System.currentTimeMillis();
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(LocalDataContract.LocalDataEntry.KEY, localData.getKey());
            if (localData.getData().length > BLOB_SIZE_LIMIT) {
                String filename = localData.getKey() + "_" + timeMillis;
                File file = new File(mFileDir, filename);
                Files.write(file.toPath(), localData.getData());
                values.put(LocalDataContract.LocalDataEntry.TYPE,
                        LocalDataContract.DATA_TYPE_FILE);
                values.put(LocalDataContract.LocalDataEntry.DATA, filename.getBytes());
            } else {
                values.put(LocalDataContract.LocalDataEntry.DATA, localData.getData());
            }
            // TODO: Cleanup file on replace instead of waiting for maintenance job.
            return db.insertWithOnConflict(mTableName, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException | IOException e) {
            sLogger.e(TAG + ": Failed to update or insert local data", e);
            // Attempt to delete file if something failed
            String filename = localData.getKey() + "_" + timeMillis;
            File file = new File(mFileDir, filename);
            file.delete();
        }
        return false;
    }

    /**
     * Deletes the row with the specified key from the local data table
     *
     * @param key the key specifying the row to delete
     * @return true if the row was deleted, false otherwise.
     */
    public boolean deleteLocalDataRow(@NonNull String key) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            String whereClause = LocalDataContract.LocalDataEntry.KEY + " = ?";
            String[] selectionArgs = {key};
            return db.delete(mTableName, whereClause, selectionArgs) == 1;
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to delete row from local data", e);
        }
        return false;
    }

    /**
     * Reads all keys in the local data table
     *
     * @return Set of keys in the local data table.
     */
    public Set<String> readAllLocalDataKeys() {
        Set<String> keyset = new HashSet<>();
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String[] projection = {VendorDataContract.VendorDataEntry.KEY};
            try (Cursor cursor = db.query(
                    mTableName,
                    projection,
                    /* selection= */ null,
                    /* selectionArgs= */ null,
                    /* groupBy= */ null,
                    /* having= */ null,
                    /* orderBy= */ null
            )) {
                while (cursor.moveToNext()) {
                    String key = cursor.getString(
                            cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.KEY));
                    keyset.add(key);
                }
                cursor.close();
                return keyset;
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read all vendor data keys", e);
        }
        return keyset;
    }

    /**
     * Deletes LocalData table for given owner
     */
    public static void deleteTable(Context context, ComponentName owner, String certDigest) {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstance(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + getTableName(owner, certDigest));
    }
}
