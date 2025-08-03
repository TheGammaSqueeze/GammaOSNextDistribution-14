/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.android.bluetooth.btservice.storage.BluetoothDatabaseMigration;
import com.android.bluetooth.opp.BluetoothOppProvider;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * @hide
 */
final class DataMigration {
    private DataMigration(){}
    private static final String TAG = "DataMigration";

    @VisibleForTesting
    static final String AUTHORITY = "bluetooth_legacy.provider";

    @VisibleForTesting
    static final String START_MIGRATION_CALL = "start_legacy_migration";
    @VisibleForTesting
    static final String FINISH_MIGRATION_CALL = "finish_legacy_migration";

    @VisibleForTesting
    static final String BLUETOOTH_DATABASE = "bluetooth_db";
    @VisibleForTesting
    static final String OPP_DATABASE = "btopp.db";

    // AvrcpVolumeManager.VOLUME_MAP
    private static final String VOLUME_MAP_PREFERENCE_FILE = "bluetooth_volume_map";
    // com.android.blueotooth.opp.Constants.BLUETOOTHOPP_CHANNEL_PREFERENCE
    private static final String BLUETOOTHOPP_CHANNEL_PREFERENCE = "btopp_channels";

    // com.android.blueotooth.opp.Constants.BLUETOOTHOPP_NAME_PREFERENCE
    private static final String BLUETOOTHOPP_NAME_PREFERENCE = "btopp_names";

    // com.android.blueotooth.opp.OPP_PREFERENCE_FILE
    private static final String OPP_PREFERENCE_FILE = "OPPMGR";

    @VisibleForTesting
    static final String[] sharedPreferencesKeys = {
        // Bundles of Boolean
        AdapterService.PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE,
        AdapterService.MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE,
        AdapterService.SIM_ACCESS_PERMISSION_PREFERENCE_FILE,

        // Bundles of Integer
        VOLUME_MAP_PREFERENCE_FILE,
        BLUETOOTHOPP_CHANNEL_PREFERENCE,

        // Bundles of String
        BLUETOOTHOPP_NAME_PREFERENCE,

        // Bundle of Boolean and String
        OPP_PREFERENCE_FILE,
    };

    // Main key use for storing all the key in the associate bundle
    @VisibleForTesting
    static final String KEY_LIST = "key_list";

    @VisibleForTesting
    static final String BLUETOOTH_CONFIG = "bluetooth_config";
    static final String MIGRATION_DONE_PROPERTY = "migration_done";
    @VisibleForTesting
    static final String MIGRATION_ATTEMPT_PROPERTY = "migration_attempt";

    @VisibleForTesting
    public static final int MIGRATION_STATUS_TO_BE_DONE = 0;
    @VisibleForTesting
    public static final int MIGRATION_STATUS_COMPLETED = 1;
    @VisibleForTesting
    public static final int MIGRATION_STATUS_MISSING_APK = 2;
    @VisibleForTesting
    public static final int MIGRATION_STATUS_MAX_ATTEMPT = 3;

    @VisibleForTesting
    static final int MAX_ATTEMPT = 3;

    static int run(Context ctx) {
        if (migrationStatus(ctx) == MIGRATION_STATUS_COMPLETED) {
            Log.d(TAG, "Legacy migration skiped: already completed");
            return MIGRATION_STATUS_COMPLETED;
        }
        if (!isMigrationApkInstalled(ctx)) {
            Log.d(TAG, "Legacy migration skiped: no migration app installed");
            markMigrationStatus(ctx, MIGRATION_STATUS_MISSING_APK);
            return MIGRATION_STATUS_MISSING_APK;
        }
        if (!incrementeMigrationAttempt(ctx)) {
            Log.d(TAG, "Legacy migration skiped: still failing after too many attempt");
            markMigrationStatus(ctx, MIGRATION_STATUS_MAX_ATTEMPT);
            return MIGRATION_STATUS_MAX_ATTEMPT;
        }

        for (String pref: sharedPreferencesKeys) {
            sharedPreferencesMigration(pref, ctx);
        }
        // Migration for DefaultSharedPreferences used in PbapUtils. Contains Long
        sharedPreferencesMigration(ctx.getPackageName() + "_preferences", ctx);

        bluetoothDatabaseMigration(ctx);
        oppDatabaseMigration(ctx);

        markMigrationStatus(ctx, MIGRATION_STATUS_COMPLETED);
        Log.d(TAG, "Legacy migration completed");
        return MIGRATION_STATUS_COMPLETED;
    }

    @VisibleForTesting
    static boolean bluetoothDatabaseMigration(Context ctx) {
        final String logHeader = BLUETOOTH_DATABASE + ": ";
        ContentResolver resolver = ctx.getContentResolver();
        Cursor cursor = resolver.query(
                Uri.parse("content://" + AUTHORITY + "/" + BLUETOOTH_DATABASE),
                null, null, null, null);
        if (cursor == null) {
            Log.d(TAG, logHeader + "Nothing to migrate");
            return true;
        }
        boolean status = BluetoothDatabaseMigration.run(ctx, cursor);
        cursor.close();
        if (status) {
            resolver.call(AUTHORITY, FINISH_MIGRATION_CALL, BLUETOOTH_DATABASE, null);
            Log.d(TAG, logHeader + "Migration complete. File is deleted");
        } else {
            Log.e(TAG, logHeader + "Invalid data. Incomplete migration. File is not deleted");
        }
        return status;
    }

    @VisibleForTesting
    static boolean oppDatabaseMigration(Context ctx) {
        final String logHeader = OPP_DATABASE + ": ";
        ContentResolver resolver = ctx.getContentResolver();
        Cursor cursor = resolver.query(
                Uri.parse("content://" + AUTHORITY + "/" + OPP_DATABASE),
                null, null, null, null);
        if (cursor == null) {
            Log.d(TAG, logHeader + "Nothing to migrate");
            return true;
        }
        boolean status = BluetoothOppProvider.oppDatabaseMigration(ctx, cursor);
        cursor.close();
        if (status) {
            resolver.call(AUTHORITY, FINISH_MIGRATION_CALL, OPP_DATABASE, null);
            Log.d(TAG, logHeader + "Migration complete. File is deleted");
        } else {
            Log.e(TAG, logHeader + "Invalid data. Incomplete migration. File is not deleted");
        }
        return status;
    }

    private static boolean writeObjectToEditor(SharedPreferences.Editor editor, Bundle b,
            String itemKey) {
        Object value = b.get(itemKey);
        if (value == null) {
            Log.e(TAG, itemKey + ": No value associated with this itemKey");
            return false;
        }
        if (value instanceof Boolean) {
            editor.putBoolean(itemKey, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(itemKey, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(itemKey, (Long) value);
        } else if (value instanceof String) {
            editor.putString(itemKey, (String) value);
        } else {
            Log.e(TAG, itemKey + ": Failed to migrate: "
                     + value.getClass().getSimpleName() + ": Data type not handled");
            return false;
        }
        return true;
    }

    @VisibleForTesting
    static boolean sharedPreferencesMigration(String prefKey, Context ctx) {
        final String logHeader = "SharedPreferencesMigration - " + prefKey + ": ";
        ContentResolver resolver = ctx.getContentResolver();
        Bundle b = resolver.call(AUTHORITY, START_MIGRATION_CALL, prefKey, null);
        if (b == null) {
            Log.d(TAG, logHeader + "Nothing to migrate");
            return true;
        }
        List<String> keys = b.getStringArrayList(KEY_LIST);
        if (keys == null) {
            Log.e(TAG, logHeader + "Wrong format of bundle: No keys to migrate");
            return false;
        }
        SharedPreferences pref = ctx.getSharedPreferences(prefKey, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        boolean status = true;
        for (String itemKey : keys) {
            // prevent overriding any user settings if it's a new attempt
            if (!pref.contains(itemKey)) {
                status &= writeObjectToEditor(editor, b, itemKey);
            } else {
                Log.d(TAG, logHeader + itemKey + ": Already exists, not overriding data.");
            }
        }
        editor.apply();
        if (status) {
            resolver.call(AUTHORITY, FINISH_MIGRATION_CALL, prefKey, null);
            Log.d(TAG, logHeader + "Migration complete. File is deleted");
        } else {
            Log.e(TAG, logHeader + "Invalid data. Incomplete migration. File is not deleted");
        }
        return status;
    }

    @VisibleForTesting
    static int migrationStatus(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences(BLUETOOTH_CONFIG, Context.MODE_PRIVATE);
        return pref.getInt(MIGRATION_DONE_PROPERTY, MIGRATION_STATUS_TO_BE_DONE);
    }

    @VisibleForTesting
    static boolean incrementeMigrationAttempt(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences(BLUETOOTH_CONFIG, Context.MODE_PRIVATE);
        int currentAttempt = Math.min(pref.getInt(MIGRATION_ATTEMPT_PROPERTY, 0), MAX_ATTEMPT);
        pref.edit()
            .putInt(MIGRATION_ATTEMPT_PROPERTY, currentAttempt + 1)
            .apply();
        return currentAttempt < MAX_ATTEMPT;
    }

    @VisibleForTesting
    static boolean isMigrationApkInstalled(Context ctx) {
        ContentResolver resolver = ctx.getContentResolver();
        ContentProviderClient client = resolver.acquireContentProviderClient(AUTHORITY);
        if (client != null) {
            client.close();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    static void markMigrationStatus(Context ctx, int status) {
        ctx.getSharedPreferences(BLUETOOTH_CONFIG, Context.MODE_PRIVATE)
            .edit()
            .putInt(MIGRATION_DONE_PROPERTY, status)
            .apply();
    }
}
