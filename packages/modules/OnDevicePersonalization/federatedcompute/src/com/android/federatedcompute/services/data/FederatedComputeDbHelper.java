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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.ENCRYPTION_KEY_TABLE;
import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;
import static com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE;
import static com.android.federatedcompute.services.data.TaskHistoryContract.TaskHistoryEntry.CREATE_TASK_HISTORY_TABLE_STATEMENT;

import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.FederatedComputeEncryptionColumns;
import com.android.federatedcompute.services.data.FederatedTraningTaskContract.FederatedTrainingTaskColumns;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODPAuthorizationTokenColumns;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.internal.annotations.VisibleForTesting;

/** Helper to manage FederatedTrainingTask database. */
public class FederatedComputeDbHelper extends SQLiteOpenHelper {

    private static final String TAG = FederatedComputeDbHelper.class.getSimpleName();

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "federatedcompute.db";
    private static final String CREATE_TRAINING_TASK_TABLE =
            "CREATE TABLE "
                    + FEDERATED_TRAINING_TASKS_TABLE
                    + " ( "
                    + FederatedTrainingTaskColumns._ID
                    + " INTEGER PRIMARY KEY, "
                    + FederatedTrainingTaskColumns.APP_PACKAGE_NAME
                    + " TEXT NOT NULL, "
                    + FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID
                    + " INTEGER, "
                    + FederatedTrainingTaskColumns.OWNER_ID
                    + " TEXT NOT NULL, "
                    + FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST
                    + " TEXT NOT NULL, "
                    + FederatedTrainingTaskColumns.POPULATION_NAME
                    + " TEXT NOT NULL,"
                    + FederatedTrainingTaskColumns.SERVER_ADDRESS
                    + " TEXT NOT NULL,"
                    + FederatedTrainingTaskColumns.INTERVAL_OPTIONS
                    + " BLOB, "
                    + FederatedTrainingTaskColumns.CONTEXT_DATA
                    + " BLOB, "
                    + FederatedTrainingTaskColumns.CREATION_TIME
                    + " INTEGER NOT NULL, "
                    + FederatedTrainingTaskColumns.LAST_SCHEDULED_TIME
                    + " INTEGER, "
                    + FederatedTrainingTaskColumns.LAST_RUN_START_TIME
                    + " INTEGER, "
                    + FederatedTrainingTaskColumns.LAST_RUN_END_TIME
                    + " INTEGER, "
                    + FederatedTrainingTaskColumns.EARLIEST_NEXT_RUN_TIME
                    + " INTEGER NOT NULL, "
                    + FederatedTrainingTaskColumns.CONSTRAINTS
                    + " BLOB, "
                    + FederatedTrainingTaskColumns.SCHEDULING_REASON
                    + " INTEGER, "
                    + FederatedTrainingTaskColumns.RESCHEDULE_COUNT
                    + " INTEGER, "
                    + "UNIQUE("
                    + FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID
                    + "))";

    private static final String CREATE_ENCRYPTION_KEY_TABLE =
            "CREATE TABLE "
                    + ENCRYPTION_KEY_TABLE
                    + " ( "
                    + FederatedComputeEncryptionColumns.KEY_IDENTIFIER
                    + " TEXT PRIMARY KEY, "
                    + FederatedComputeEncryptionColumns.PUBLIC_KEY
                    + " TEXT NOT NULL, "
                    + FederatedComputeEncryptionColumns.KEY_TYPE
                    + " INTEGER, "
                    + FederatedComputeEncryptionColumns.CREATION_TIME
                    + " INTEGER NOT NULL, "
                    + FederatedComputeEncryptionColumns.EXPIRY_TIME
                    + " INTEGER NOT NULL)";

    private static final String CREATE_ODP_AUTHORIZATION_TOKEN_TABLE =
            "CREATE TABLE "
                    + ODP_AUTHORIZATION_TOKEN_TABLE
                    + " ( "
                    + ODPAuthorizationTokenColumns.OWNER_IDENTIFIER
                    + " TEXT PRIMARY KEY, "
                    + ODPAuthorizationTokenColumns.AUTHORIZATION_TOKEN
                    + " TEXT NOT NULL, "
                    + ODPAuthorizationTokenColumns.CREATION_TIME
                    + " INTEGER NOT NULL, "
                    + ODPAuthorizationTokenColumns.EXPIRY_TIME
                    + " INTEGER NOT NULL)";

    private static volatile FederatedComputeDbHelper sInstance = null;

    private FederatedComputeDbHelper(Context context, String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
    }

    /** Returns an instance of the FederatedComputeDbHelper given a context. */
    public static FederatedComputeDbHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (FederatedComputeDbHelper.class) {
                if (sInstance == null) {
                    sInstance =
                            new FederatedComputeDbHelper(
                                    context.getApplicationContext(), DATABASE_NAME);
                }
            }
        }
        return sInstance;
    }

    /**
     * Returns an instance of the FederatedComputeDbHelper given a context. This is used for testing
     * only.
     */
    @VisibleForTesting
    public static FederatedComputeDbHelper getInstanceForTest(Context context) {
        synchronized (FederatedComputeDbHelper.class) {
            if (sInstance == null) {
                // Use null database name to make it in-memory
                sInstance = new FederatedComputeDbHelper(context, null);
            }
            return sInstance;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TRAINING_TASK_TABLE);
        db.execSQL(CREATE_ENCRYPTION_KEY_TABLE);
        db.execSQL(CREATE_ODP_AUTHORIZATION_TOKEN_TABLE);
        db.execSQL(CREATE_TASK_HISTORY_TABLE_STATEMENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: handle upgrade when the db schema is changed.
        LogUtil.d(TAG, "DB upgrade from %d to %d", oldVersion, newVersion);
        throw new UnsupportedOperationException(
                "Database upgrade for FederatedCompute is unsupported");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d(TAG, "DB downgrade from %d to %d", newVersion, oldVersion);
        // All data is retained for the package between upgrades and rollbacks. Update the
        // DB version to the oldVersion, but maintain the data and schema from the new Version. It
        // is assumed that the new version will be fully backward compatible.
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.enableWriteAheadLogging();
    }

    /** It's only public to testing. */
    @VisibleForTesting
    public static void resetInstance() {
        synchronized (FederatedComputeDbHelper.class) {
            if (sInstance != null) {
                sInstance.close();
                sInstance = null;
            }
        }
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(TAG, e, "Failed to get a readable database");
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(TAG, e, "Failed to get a writeable database");
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }
}
