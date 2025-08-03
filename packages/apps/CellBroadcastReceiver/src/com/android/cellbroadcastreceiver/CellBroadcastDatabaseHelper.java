/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import static java.nio.file.Files.copy;

import android.annotation.NonNull;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.provider.Telephony.CellBroadcasts;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;

/**
 * Open, create, and upgrade the cell broadcast SQLite database. Previously an inner class of
 * {@code CellBroadcastDatabase}, this is now a top-level class. The column definitions in
 * {@code CellBroadcastDatabase} have been moved to {@link Telephony.CellBroadcasts} in the
 * framework, to simplify access to this database from third-party apps.
 */
public class CellBroadcastDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "CellBroadcastDatabaseHelper";

    /**
     * Database version 1: initial version (support removed)
     * Database version 2-9: (reserved for OEM database customization) (support removed)
     * Database version 10: adds ETWS and CMAS columns and CDMA support (support removed)
     * Database version 11: adds delivery time index
     * Database version 12: add slotIndex
     * Database version 13: add smsSyncPending
     */
    private static final int DATABASE_VERSION = 13;

    private static final String OLD_DATABASE_NAME = "cell_broadcasts.db";
    private static final String DATABASE_NAME_V13 = "cell_broadcasts_v13.db";
    @VisibleForTesting
    public static final String TABLE_NAME = "broadcasts";

    // Preference key for whether the data migration from pre-R CBR app was complete.
    public static final String KEY_LEGACY_DATA_MIGRATION = "legacy_data_migration";

    /**
     * Is the message pending for sms synchronization.
     * when received cellbroadcast message in direct boot mode, we will retry synchronizing
     * alert message to sms inbox after user unlock if needed.
     * <P>Type: Boolean</P>
     */
    public static final String SMS_SYNC_PENDING = "isSmsSyncPending";

    /*
     * Query columns for instantiating SmsCbMessage.
     */
    public static final String[] QUERY_COLUMNS = {
            Telephony.CellBroadcasts._ID,
            Telephony.CellBroadcasts.SLOT_INDEX,
            Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
            Telephony.CellBroadcasts.PLMN,
            Telephony.CellBroadcasts.LAC,
            Telephony.CellBroadcasts.CID,
            Telephony.CellBroadcasts.SERIAL_NUMBER,
            Telephony.CellBroadcasts.SERVICE_CATEGORY,
            Telephony.CellBroadcasts.LANGUAGE_CODE,
            Telephony.CellBroadcasts.MESSAGE_BODY,
            Telephony.CellBroadcasts.DELIVERY_TIME,
            Telephony.CellBroadcasts.MESSAGE_READ,
            Telephony.CellBroadcasts.MESSAGE_FORMAT,
            Telephony.CellBroadcasts.MESSAGE_PRIORITY,
            Telephony.CellBroadcasts.ETWS_WARNING_TYPE,
            Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS,
            Telephony.CellBroadcasts.CMAS_CATEGORY,
            Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE,
            Telephony.CellBroadcasts.CMAS_SEVERITY,
            Telephony.CellBroadcasts.CMAS_URGENCY,
            Telephony.CellBroadcasts.CMAS_CERTAINTY
    };

    /**
     * Returns a string used to create the cell broadcast table. This is exposed so the unit test
     * can construct its own in-memory database to match the cell broadcast db.
     */
    @VisibleForTesting
    public static String getStringForCellBroadcastTableCreation(String tableName) {
        return "CREATE TABLE " + tableName + " ("
                + CellBroadcasts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + CellBroadcasts.SLOT_INDEX + " INTEGER DEFAULT 0,"
                + CellBroadcasts.GEOGRAPHICAL_SCOPE + " INTEGER,"
                + CellBroadcasts.PLMN + " TEXT,"
                + CellBroadcasts.LAC + " INTEGER,"
                + CellBroadcasts.CID + " INTEGER,"
                + CellBroadcasts.SERIAL_NUMBER + " INTEGER,"
                + Telephony.CellBroadcasts.SERVICE_CATEGORY + " INTEGER,"
                + Telephony.CellBroadcasts.LANGUAGE_CODE + " TEXT,"
                + Telephony.CellBroadcasts.MESSAGE_BODY + " TEXT,"
                + Telephony.CellBroadcasts.DELIVERY_TIME + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_READ + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_FORMAT + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_PRIORITY + " INTEGER,"
                + Telephony.CellBroadcasts.ETWS_WARNING_TYPE + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_CATEGORY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_SEVERITY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_URGENCY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_CERTAINTY + " INTEGER,"
                + SMS_SYNC_PENDING + " BOOLEAN);";
    }

    private final Context mContext;
    final boolean mLegacyProvider;

    private ContentProviderClient mOverrideContentProviderClient = null;

    @VisibleForTesting
    public CellBroadcastDatabaseHelper(Context context, boolean legacyProvider) {
        super(context, DATABASE_NAME_V13, null, DATABASE_VERSION);
        mContext = context;
        mLegacyProvider = legacyProvider;
    }

    @VisibleForTesting
    public CellBroadcastDatabaseHelper(Context context, boolean legacyProvider, String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
        mContext = context;
        mLegacyProvider = legacyProvider;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(getStringForCellBroadcastTableCreation(TABLE_NAME));

        db.execSQL("CREATE INDEX IF NOT EXISTS deliveryTimeIndex ON " + TABLE_NAME
                + " (" + Telephony.CellBroadcasts.DELIVERY_TIME + ");");
        if (!mLegacyProvider) {
            migrateFromLegacyIfNeeded(db);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) {
            return;
        }
        // always log database upgrade
        log("Upgrading DB from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + Telephony.CellBroadcasts.SLOT_INDEX + " INTEGER DEFAULT 0;");
        }
        if (oldVersion < 13) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + SMS_SYNC_PENDING
                    + " BOOLEAN DEFAULT 0;");
        }
    }

    private synchronized void tryToMigrateV13() {
        File oldDb = mContext.getDatabasePath(OLD_DATABASE_NAME);
        File newDb = mContext.getDatabasePath(DATABASE_NAME_V13);
        if (!oldDb.exists()) {
            return;
        }
        // We do the DB copy in two scenarios:
        //   1. device receives v13 upgrade.
        //   2. device receives v13 upgrade, gets rollback to v12, then receives v13 upgrade again.
        //      If the DB is modified after rollback, we want to copy those changes again.
        if (!newDb.exists() || oldDb.lastModified() > newDb.lastModified()) {
            try {
                // copy() requires that the destination file does not exist
                Log.d(TAG, "copying to v13 db");
                if (newDb.exists()) newDb.delete();
                copy(oldDb.toPath(), newDb.toPath());
            } catch (Exception e) {
                // If the copy failed we don't know if the db is in a safe state, so just delete it
                // and continue with an empty new db. Ignore the exception and just log an error.
                mContext.deleteDatabase(DATABASE_NAME_V13);
                loge("could not copy DB to v13. e=" + e);
            }
        }
        // else the V13 database has already been created.
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        tryToMigrateV13();
        return super.getReadableDatabase();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        tryToMigrateV13();
        return super.getWritableDatabase();
    }

    @VisibleForTesting
    public void setOverrideContentProviderClient(ContentProviderClient client) {
        mOverrideContentProviderClient = client;
    }

    private ContentProviderClient getContentProviderClient() {
        if (mOverrideContentProviderClient != null) {
            return mOverrideContentProviderClient;
        }
        return mContext.getContentResolver()
                .acquireContentProviderClient(Telephony.CellBroadcasts.AUTHORITY_LEGACY);
    }

    /**
     * This is the migration logic to accommodate OEMs move to mainlined CBR for the first time.
     * When the db is initially created, this is called once to
     * migrate predefined data through {@link Telephony.CellBroadcasts#AUTHORITY_LEGACY_URI}
     * from OEM app.
     */
    @VisibleForTesting
    public void migrateFromLegacyIfNeeded(@NonNull SQLiteDatabase db) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (sp.getBoolean(CellBroadcastDatabaseHelper.KEY_LEGACY_DATA_MIGRATION, false)) {
            log("Data migration was complete already");
            return;
        }

        try (ContentProviderClient client = getContentProviderClient()) {
            if (client == null) {
                log("No legacy provider available for migration");
                return;
            }

            db.beginTransaction();
            log("Starting migration from legacy provider");
            // migration columns are same as query columns
            try (Cursor c = client.query(Telephony.CellBroadcasts.AUTHORITY_LEGACY_URI,
                QUERY_COLUMNS, null, null, null)) {
                final ContentValues values = new ContentValues();
                while (c.moveToNext()) {
                    values.clear();
                    for (String column : QUERY_COLUMNS) {
                        copyFromCursorToContentValues(column, c, values);
                    }
                    // remove the primary key to avoid UNIQUE constraint failure.
                    values.remove(Telephony.CellBroadcasts._ID);

                    try {
                        if (db.insert(TABLE_NAME, null, values) == -1) {
                            // We only have one shot to migrate data, so log and
                            // keep marching forward
                            loge("Failed to insert " + values + "; continuing");
                        }
                    } catch (Exception e) {
                        // If insert for one message fails, continue with other messages
                        loge("Failed to insert " + values + " due to exception: " + e);
                    }
                }

                log("Finished migration from legacy provider");
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            } finally {
                // if beginTransaction() is called then setTransactionSuccessful() must be called.
                // This is a nested begin/end transcation block -- since this is called from
                // onCreate() which is inside another block in SQLiteOpenHelper. If a nested
                // transaction fails, all transaction fail and that would result in table not being
                // created (it's created in onCreate()).
                db.setTransactionSuccessful();
                db.endTransaction();
            }
        } catch (Exception e) {
            // We have to guard ourselves against any weird behavior of the
            // legacy provider by trying to catch everything
            loge("Failed migration from legacy provider: " + e);
        } finally {
            // Mark data migration was triggered to make sure this is done only once.
            sp.edit().putBoolean(KEY_LEGACY_DATA_MIGRATION, true).commit();
        }
    }

    public static void copyFromCursorToContentValues(@NonNull String column, @NonNull Cursor cursor,
            @NonNull ContentValues values) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1) {
            if (cursor.isNull(index)) {
                values.putNull(column);
            } else {
                values.put(column, cursor.getString(index));
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
