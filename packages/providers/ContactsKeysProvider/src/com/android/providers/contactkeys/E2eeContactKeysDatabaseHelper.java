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

package com.android.providers.contactkeys;

import android.annotation.FlaggedApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.E2eeContactKeysManager;
import android.provider.E2eeContactKeysManager.E2eeContactKeys;
import android.provider.Flags;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Database helper for end-to-end encryption contact keys.
 */
@FlaggedApi(Flags.FLAG_USER_KEYS)
public class E2eeContactKeysDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "E2eeContactKeysDatabaseHelper";

    private static final String CONTACT_KEYS_TABLE_NAME = "contactkeys";
    private static final String SELF_KEYS_TABLE_NAME = "selfkeys";
    private static final String DATABASE_NAME = "contact_keys_provider.db";
    private static final int DATABASE_VERSION = 1;

    E2eeContactKeysDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Bootstrapping database " + DATABASE_NAME + " version: " + DATABASE_VERSION);
        db.execSQL("CREATE TABLE " + CONTACT_KEYS_TABLE_NAME + " ("
                + E2eeContactKeys.LOOKUP_KEY + " TEXT NOT NULL, "
                + E2eeContactKeys.DEVICE_ID + " TEXT NOT NULL, "
                + E2eeContactKeys.ACCOUNT_ID + " TEXT NOT NULL, "
                + E2eeContactKeys.OWNER_PACKAGE_NAME + " TEXT NOT NULL, "
                + E2eeContactKeys.TIME_UPDATED + " INTEGER NOT NULL default 0, "
                + E2eeContactKeys.KEY_VALUE + " BLOB NOT NULL, "
                + E2eeContactKeys.DISPLAY_NAME + " TEXT, "
                + E2eeContactKeys.PHONE_NUMBER + " TEXT, "
                + E2eeContactKeys.EMAIL_ADDRESS + " TEXT, "
                + E2eeContactKeys.LOCAL_VERIFICATION_STATE + " INTEGER NOT NULL default 0, "
                + E2eeContactKeys.REMOTE_VERIFICATION_STATE + " INTEGER NOT NULL default 0, "
                + " PRIMARY KEY ("
                + E2eeContactKeys.LOOKUP_KEY + ", "
                + E2eeContactKeys.DEVICE_ID + ", "
                + E2eeContactKeys.ACCOUNT_ID + ", "
                + E2eeContactKeys.OWNER_PACKAGE_NAME
                + "));");
        db.execSQL("CREATE TABLE " + SELF_KEYS_TABLE_NAME + " ("
                + E2eeContactKeys.DEVICE_ID + " TEXT NOT NULL, "
                + E2eeContactKeys.ACCOUNT_ID + " TEXT NOT NULL, "
                + E2eeContactKeys.OWNER_PACKAGE_NAME + " TEXT NOT NULL, "
                + E2eeContactKeys.TIME_UPDATED + " INTEGER NOT NULL default 0, "
                + E2eeContactKeys.KEY_VALUE + " BLOB NOT NULL, "
                + E2eeContactKeys.REMOTE_VERIFICATION_STATE + " INTEGER NOT NULL default 0,"
                + " PRIMARY KEY ("
                + E2eeContactKeys.DEVICE_ID + ", "
                + E2eeContactKeys.ACCOUNT_ID + ", "
                + E2eeContactKeys.OWNER_PACKAGE_NAME
                + "));");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to do here, still on version 1
    }

    /**
     * Gets all end-to-end encryption contact keys in the contact keys table for a given lookupKey.
     */
    public List<E2eeContactKeysManager.E2eeContactKey> getAllContactKeys(String lookupKey) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT " + E2eeContactKeys.DEVICE_ID + ","
                        + E2eeContactKeys.ACCOUNT_ID + ","
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.DISPLAY_NAME + ","
                        + E2eeContactKeys.PHONE_NUMBER + ","
                        + E2eeContactKeys.EMAIL_ADDRESS + ","
                        + E2eeContactKeys.LOCAL_VERIFICATION_STATE + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE
                        + " FROM " + CONTACT_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?",
                new String[] {lookupKey})) {
            final List<E2eeContactKeysManager.E2eeContactKey> result =
                    new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                String deviceId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.DEVICE_ID));
                String accountId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.ACCOUNT_ID));
                String ownerPackageName = c.getString(
                        c.getColumnIndexOrThrow(E2eeContactKeys.OWNER_PACKAGE_NAME));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                String displayName = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.DISPLAY_NAME));
                String number = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.PHONE_NUMBER));
                String emailAddress = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.EMAIL_ADDRESS));
                int localVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.LOCAL_VERIFICATION_STATE));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result.add(new E2eeContactKeysManager.E2eeContactKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, localVerificationState,
                        remoteVerificationState, displayName, number, emailAddress));
            }
            return result;
        }
    }

    /**
     * Gets all end-to-end encryption contact keys in the contact keys table for a given lookupKey
     * and ownerPackageName.
     */
    public List<E2eeContactKeysManager.E2eeContactKey> getContactKeysForOwnerPackageName(
            String lookupKey, String ownerPackageName) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT " + E2eeContactKeys.DEVICE_ID + ","
                        + E2eeContactKeys.ACCOUNT_ID + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.DISPLAY_NAME + ","
                        + E2eeContactKeys.PHONE_NUMBER + ","
                        + E2eeContactKeys.EMAIL_ADDRESS + ","
                        + E2eeContactKeys.LOCAL_VERIFICATION_STATE + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE
                        + " FROM " + CONTACT_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?",
                new String[] {lookupKey, String.valueOf(ownerPackageName)})) {
            final List<E2eeContactKeysManager.E2eeContactKey> result =
                    new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                String deviceId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.DEVICE_ID));
                String accountId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.ACCOUNT_ID));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                String displayName = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.DISPLAY_NAME));
                String number = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.PHONE_NUMBER));
                String emailAddress = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.EMAIL_ADDRESS));
                int localVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.LOCAL_VERIFICATION_STATE));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result.add(new E2eeContactKeysManager.E2eeContactKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, localVerificationState,
                        remoteVerificationState, displayName, number, emailAddress));
            }
            return result;
        }
    }

    /**
     * Get a end-to-end encryption contact key for given parameters.
     */
    public E2eeContactKeysManager.E2eeContactKey getContactKey(String lookupKey,
            String ownerPackageName, String deviceId, String accountId) {
        E2eeContactKeysManager.E2eeContactKey result = null;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.DISPLAY_NAME + ","
                        + E2eeContactKeys.PHONE_NUMBER + ","
                        + E2eeContactKeys.EMAIL_ADDRESS + ","
                        + E2eeContactKeys.LOCAL_VERIFICATION_STATE + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE + " FROM "
                        + CONTACT_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?"
                        + " AND " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?",
                new String[] {lookupKey, String.valueOf(ownerPackageName), deviceId, accountId})) {
            if (c.moveToNext()) {
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                String displayName = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.DISPLAY_NAME));
                String number = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.PHONE_NUMBER));
                String emailAddress = c.getString(c.getColumnIndexOrThrow(
                        E2eeContactKeys.EMAIL_ADDRESS));
                int localVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.LOCAL_VERIFICATION_STATE));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result = new E2eeContactKeysManager.E2eeContactKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, localVerificationState,
                        remoteVerificationState, displayName, number, emailAddress);
            }
        }

        return result;
    }

    /**
     * Updates the end-to-end encryption contact key local verification state and returns the
     * number of keys updated (should always be 0 or 1).
     */
    public int updateContactKeyLocalVerificationState(String lookupKey, String ownerPackageName,
            String deviceId, String accountId, int localVerificationState, long timeUpdated) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "UPDATE " + CONTACT_KEYS_TABLE_NAME
                        + " SET " + E2eeContactKeys.LOCAL_VERIFICATION_STATE
                        + " = ?, "
                        + E2eeContactKeys.TIME_UPDATED + " = ?"
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?"
                        + " AND " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?")) {
            updateStatement.bindLong(1, localVerificationState);
            updateStatement.bindLong(2, timeUpdated);
            updateStatement.bindString(3, lookupKey);
            updateStatement.bindString(4, ownerPackageName);
            updateStatement.bindString(5, deviceId);
            updateStatement.bindString(6, accountId);
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Updates the end-to-end encryption contact key remote verification state and returns the
     * number of keys updated (should always be 0 or 1).
     */
    public int updateContactKeyRemoteVerificationState(String lookupKey, String ownerPackageName,
            String deviceId, String accountId, int remoteVerificationState, long timeUpdated) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "UPDATE " + CONTACT_KEYS_TABLE_NAME
                        + " SET " + E2eeContactKeys.REMOTE_VERIFICATION_STATE
                        + " = ?, "
                        + E2eeContactKeys.TIME_UPDATED + " = ?"
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?"
                        + " AND " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?")) {
            updateStatement.bindLong(1, remoteVerificationState);
            updateStatement.bindLong(2, timeUpdated);
            updateStatement.bindString(3, lookupKey);
            updateStatement.bindString(4, ownerPackageName);
            updateStatement.bindString(5, deviceId);
            updateStatement.bindString(6, accountId);
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Inserts a new end-to-end encryption contact key or updates an existing one and returns the
     * number of keys inserted/updated (should always be 0 or 1).
     */
    public int updateOrInsertContactKey(String lookupKey, byte[] keyValue, String deviceId,
            String accountId, String ownerPackageName, long timeUpdated,
            String displayName, String number, String emailAddress) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "INSERT INTO " + CONTACT_KEYS_TABLE_NAME + " ("
                        + E2eeContactKeys.LOOKUP_KEY + ", "
                        + E2eeContactKeys.DEVICE_ID + ", "
                        + E2eeContactKeys.ACCOUNT_ID + ", "
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ", "
                        + E2eeContactKeys.KEY_VALUE + ", "
                        + E2eeContactKeys.TIME_UPDATED + ", "
                        + E2eeContactKeys.DISPLAY_NAME + ", "
                        + E2eeContactKeys.PHONE_NUMBER + ", "
                        + E2eeContactKeys.EMAIL_ADDRESS
                        + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT ("
                        + E2eeContactKeys.LOOKUP_KEY + ", "
                        + E2eeContactKeys.DEVICE_ID + ", "
                        + E2eeContactKeys.ACCOUNT_ID + ", "
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ") "
                        + "DO UPDATE SET " + E2eeContactKeys.KEY_VALUE + " = ?, "
                        + E2eeContactKeys.TIME_UPDATED + " = ?, "
                        + E2eeContactKeys.DISPLAY_NAME + " = ?, "
                        + E2eeContactKeys.PHONE_NUMBER + " = ?, "
                        + E2eeContactKeys.EMAIL_ADDRESS + " = ?")) {
            updateStatement.bindString(1, lookupKey);
            updateStatement.bindString(2, deviceId);
            updateStatement.bindString(3, accountId);
            updateStatement.bindString(4, ownerPackageName);
            updateStatement.bindBlob(5, keyValue);
            updateStatement.bindLong(6, timeUpdated);
            if (displayName != null) {
                updateStatement.bindString(7, displayName);
            } else {
                updateStatement.bindNull(7);
            }
            if (number != null) {
                updateStatement.bindString(8, number);
            } else {
                updateStatement.bindNull(8);
            }
            if (emailAddress != null) {
                updateStatement.bindString(9, emailAddress);
            } else {
                updateStatement.bindNull(9);
            }
            updateStatement.bindBlob(10, keyValue);
            updateStatement.bindLong(11, timeUpdated);
            if (displayName != null) {
                updateStatement.bindString(12, displayName);
            } else {
                updateStatement.bindNull(12);
            }
            if (number != null) {
                updateStatement.bindString(13, number);
            } else {
                updateStatement.bindNull(13);
            }
            if (emailAddress != null) {
                updateStatement.bindString(14, emailAddress);
            } else {
                updateStatement.bindNull(14);
            }
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Removes the end-to-end encryption contact key and returns the number of keys removed
     * (should always be 0 or 1).
     */
    public int removeContactKey(String lookupKey, String deviceId, String accountId,
            String ownerPackageName) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "DELETE FROM " + CONTACT_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.LOOKUP_KEY + " = ?"
                        + " AND " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?")) {
            updateStatement.bindString(1, lookupKey);
            updateStatement.bindString(2, deviceId);
            updateStatement.bindString(3, accountId);
            updateStatement.bindString(4, ownerPackageName);
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Gets an end-to-end encryption self key for given parameters.
     */
    public E2eeContactKeysManager.E2eeSelfKey getSelfKey(String deviceId, String accountId,
            String ownerPackageName) {
        E2eeContactKeysManager.E2eeSelfKey result = null;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE + " FROM "
                        + SELF_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?",
                new String[] {deviceId, accountId, ownerPackageName})) {
            if (c.moveToNext()) {
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result = new E2eeContactKeysManager.E2eeSelfKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, remoteVerificationState);
            }
        }
        return result;
    }

    /**
     * Gets all end-to-end encryption self keys in the self keys table.
     */
    public List<E2eeContactKeysManager.E2eeSelfKey> getAllSelfKeys() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT " + E2eeContactKeys.DEVICE_ID + ","
                        + E2eeContactKeys.ACCOUNT_ID + ","
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE
                        + " FROM " + SELF_KEYS_TABLE_NAME,
                new String[] {})) {
            final List<E2eeContactKeysManager.E2eeSelfKey> result = new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                String deviceId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.DEVICE_ID));
                String accountId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.ACCOUNT_ID));
                String ownerPackageName = c.getString(
                        c.getColumnIndexOrThrow(E2eeContactKeys.OWNER_PACKAGE_NAME));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result.add(new E2eeContactKeysManager.E2eeSelfKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, remoteVerificationState));
            }
            return result;
        }
    }

    /**
     * Gets all end-to-end encryption self keys in the self keys table for a given ownerPackageName.
     */
    public List<E2eeContactKeysManager.E2eeSelfKey> getSelfKeysForOwnerPackageName(
            String ownerPackageName) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT " + E2eeContactKeys.DEVICE_ID + ","
                        + E2eeContactKeys.ACCOUNT_ID + ","
                        + E2eeContactKeys.TIME_UPDATED + ","
                        + E2eeContactKeys.KEY_VALUE + ","
                        + E2eeContactKeys.REMOTE_VERIFICATION_STATE
                        + " FROM " + SELF_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?",
                new String[] {ownerPackageName})) {
            final List<E2eeContactKeysManager.E2eeSelfKey> result = new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                String deviceId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.DEVICE_ID));
                String accountId = c.getString(c.getColumnIndexOrThrow(E2eeContactKeys.ACCOUNT_ID));
                long timeUpdated = c.getLong(c.getColumnIndexOrThrow(E2eeContactKeys.TIME_UPDATED));
                byte[] keyValue = c.getBlob(c.getColumnIndexOrThrow(E2eeContactKeys.KEY_VALUE));
                int remoteVerificationState = c.getInt(c.getColumnIndexOrThrow(
                        E2eeContactKeys.REMOTE_VERIFICATION_STATE));
                result.add(new E2eeContactKeysManager.E2eeSelfKey(deviceId, accountId,
                        ownerPackageName, timeUpdated, keyValue, remoteVerificationState));
            }
            return result;
        }
    }

    /**
     * Inserts a new end-to-end encryption self key or updates an existing one and returns the
     * number of keys inserted/updated (should always be 0 or 1).
     */
    public int updateOrInsertSelfKey(byte[] keyValue, String deviceId, String accountId,
            String ownerPackageName, long timeUpdated) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "INSERT INTO " + SELF_KEYS_TABLE_NAME + " ("
                        + E2eeContactKeys.DEVICE_ID + ", "
                        + E2eeContactKeys.ACCOUNT_ID + ", "
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ", "
                        + E2eeContactKeys.KEY_VALUE + ", "
                        + E2eeContactKeys.TIME_UPDATED
                        + ") VALUES "
                        + "(?, ?, ?, ?, ?) "
                        + "ON CONFLICT ("
                        + E2eeContactKeys.DEVICE_ID + ", "
                        + E2eeContactKeys.ACCOUNT_ID + ", "
                        + E2eeContactKeys.OWNER_PACKAGE_NAME + ") "
                        + "DO UPDATE SET " + E2eeContactKeys.KEY_VALUE + " = ?, "
                        + E2eeContactKeys.TIME_UPDATED + " = ?")) {
            updateStatement.bindString(1, deviceId);
            updateStatement.bindString(2, accountId);
            updateStatement.bindString(3, ownerPackageName);
            updateStatement.bindBlob(4, keyValue);
            updateStatement.bindLong(5, timeUpdated);
            updateStatement.bindBlob(6, keyValue);
            updateStatement.bindLong(7, timeUpdated);
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Updates the end-to-end encryption self key remote verification state and returns the number
     * of keys updated (should always be 0 or 1).
     */
    public int updateSelfKeyRemoteVerificationState(String ownerPackageName, String deviceId,
            String accountId, int remoteVerificationState, long timeUpdated) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "UPDATE " + SELF_KEYS_TABLE_NAME
                        + " SET " + E2eeContactKeys.REMOTE_VERIFICATION_STATE + " = ?, "
                        + E2eeContactKeys.TIME_UPDATED + " = ?"
                        + " WHERE " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?"
                        + " AND " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?")) {
            updateStatement.bindLong(1, remoteVerificationState);
            updateStatement.bindLong(2, timeUpdated);
            updateStatement.bindString(3, ownerPackageName);
            updateStatement.bindString(4, deviceId);
            updateStatement.bindString(5, accountId);
            return updateStatement.executeUpdateDelete();
        }
    }

    /**
     * Removes a end-to-end encryption self key with given parameters.
     */
    public int removeSelfKey(String deviceId, String accountId, String ownerPackageName) {
        try (SQLiteStatement updateStatement = getWritableDatabase().compileStatement(
                "DELETE FROM " + SELF_KEYS_TABLE_NAME
                        + " WHERE " + E2eeContactKeys.DEVICE_ID + " = ?"
                        + " AND " + E2eeContactKeys.ACCOUNT_ID + " = ?"
                        + " AND " + E2eeContactKeys.OWNER_PACKAGE_NAME + " = ?")) {
            updateStatement.bindString(1, deviceId);
            updateStatement.bindString(2, accountId);
            updateStatement.bindString(3, ownerPackageName);
            return updateStatement.executeUpdateDelete();
        }
    }
}
