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

package com.android.providers.contactkeys;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.E2eeContactKeysManager;
import android.provider.E2eeContactKeysManager.E2eeContactKeys;
import android.provider.ContactsContract;
import android.provider.Flags;

import com.android.internal.annotations.VisibleForTesting;
import com.android.providers.contactkeys.util.E2eeContactKeysPermissions;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to a database of end-to-end encryption contact keys.
 */
@FlaggedApi(Flags.FLAG_USER_KEYS)
public class E2eeContactKeysProvider extends ContentProvider {

    /** The authority for the end-to-end encryption contact keys provider. */
    public static final String AUTHORITY = "com.android.contactkeys.contactkeysprovider";

    private static final String READ_PERMISSION = "android.permission.READ_CONTACTS";
    private static final String WRITE_PERMISSION = "android.permission.WRITE_CONTACTS";
    private static final String UPDATE_VERIFICATION_STATE_PERMISSION =
            "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS";

    /** Special timeUpdated value used when this data is stripped. */
    private static final long STRIPPED_TIME_UPDATED = -1;

    @VisibleForTesting
    E2eeContactKeysDatabaseHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new E2eeContactKeysDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        switch (method) {
            case E2eeContactKeys.UPDATE_OR_INSERT_CONTACT_KEY_METHOD -> {
                return updateOrInsertE2eeContactKey(extras);
            }
            case E2eeContactKeys.GET_CONTACT_KEY_METHOD -> {
                return getE2eeContactKey(extras);
            }
            case E2eeContactKeys.GET_ALL_CONTACT_KEYS_METHOD -> {
                return getAllE2eeContactKeys(extras);
            }
            case E2eeContactKeys.GET_OWNER_CONTACT_KEYS_METHOD -> {
                return getOwnerE2eeContactKeys(extras);
            }
            case E2eeContactKeys.UPDATE_CONTACT_KEY_LOCAL_VERIFICATION_STATE_METHOD -> {
                return updateE2eeContactKeyLocalVerificationState(extras);
            }
            case E2eeContactKeys.UPDATE_CONTACT_KEY_REMOTE_VERIFICATION_STATE_METHOD -> {
                return updateE2eeContactKeyRemoteVerificationState(extras);
            }
            case E2eeContactKeys.REMOVE_CONTACT_KEY_METHOD -> {
                return removeE2eeContactKey(extras);
            }
            case E2eeContactKeys.UPDATE_OR_INSERT_SELF_KEY_METHOD -> {
                return updateOrInsertE2eeSelfKey(extras);
            }
            case E2eeContactKeys.UPDATE_SELF_KEY_REMOTE_VERIFICATION_STATE_METHOD -> {
                return updateE2eeSelfKeyRemoteVerificationState(extras);
            }
            case E2eeContactKeys.GET_SELF_KEY_METHOD -> {
                return getE2eeSelfKey(extras);
            }
            case E2eeContactKeys.GET_ALL_SELF_KEYS_METHOD -> {
                return getAllE2eeSelfKeys();
            }
            case E2eeContactKeys.GET_OWNER_SELF_KEYS_METHOD -> {
                return getOwnerE2eeSelfKeys();
            }
            case E2eeContactKeys.REMOVE_SELF_KEY_METHOD -> {
                return removeE2eeSelfKey(extras);
            }
        }
        throw new IllegalArgumentException("Unexpected method " + method);
    }

    /**
     * Inserts a new entry into the end-to-end encryption contact keys table or updates one if it
     * already exists.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and a
     * boolean value being true if the row was added or updated, false otherwise.
     */
    private Bundle updateOrInsertE2eeContactKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final Bundle response = new Bundle();

        final String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        final byte[] keyValue = extras.getByteArray(E2eeContactKeys.KEY_VALUE);

        Bundle contactInfo = getContactBasicInfoFromCP2(lookupKey);
        String displayName = contactInfo.getString(E2eeContactKeys.DISPLAY_NAME);
        String number = contactInfo.getString(E2eeContactKeys.PHONE_NUMBER);
        String emailAddress = contactInfo.getString(E2eeContactKeys.EMAIL_ADDRESS);

        String callerPackageName = getCallingPackage();
        long timeUpdated = System.currentTimeMillis();

        int rowsUpdateOrInserted = mDbHelper.updateOrInsertContactKey(lookupKey, keyValue,
                deviceId, accountId, callerPackageName, timeUpdated, displayName, number,
                emailAddress);

        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsUpdateOrInserted > 0);

        return response;
    }

    /**
     * Retrieves an end-to-end encryption contact key entry given the lookupKey, deviceId and the
     * inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEY} as a key and a
     * parcelable {@link E2eeContactKeysManager.E2eeContactKey} as a value
     */
    private Bundle getE2eeContactKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);

        final String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        String callerPackageName = getCallingPackage();

        E2eeContactKeysManager.E2eeContactKey contactKey = mDbHelper.getContactKey(lookupKey,
                callerPackageName, deviceId, accountId);

        final Bundle response = new Bundle();
        response.putParcelable(E2eeContactKeys.KEY_CONTACT_KEY, contactKey);

        return response;
    }

    /**
     * Retrieves all end-to-end encryption contact key entries given the lookupKey. All keys that
     * belong to apps visible to the caller will be returned.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEYS} as a key and
     * a parcelable list of {@link E2eeContactKeysManager.E2eeContactKey} as a value
     */
    private Bundle getAllE2eeContactKeys(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);
        String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);

        final Bundle response = new Bundle();

        List<E2eeContactKeysManager.E2eeContactKey> contactKeys =
                mDbHelper.getAllContactKeys(lookupKey);

        contactKeys = filterVisibleContactKeys(contactKeys);
        contactKeys = getStrippedContactKeys(contactKeys);

        response.putParcelableList(E2eeContactKeys.KEY_CONTACT_KEYS, contactKeys);

        return response;
    }

    /**
     * Retrieves all end-to-end encryption contact key entries given the lookupKey. All keys that
     * belong to the caller will be returned.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEYS} as a key and
     * a parcelable list of {@link E2eeContactKeysManager.E2eeContactKey} as a value
     */
    private Bundle getOwnerE2eeContactKeys(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);
        String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);

        final Bundle response = new Bundle();

        String callerPackageName = getCallingPackage();
        List<E2eeContactKeysManager.E2eeContactKey> contactKeys =
                mDbHelper.getContactKeysForOwnerPackageName(lookupKey, callerPackageName);

        response.putParcelableList(E2eeContactKeys.KEY_CONTACT_KEYS, contactKeys);

        return response;
    }

    /**
     * Updates an end-to-end encryption contact key entry
     * {@link E2eeContactKeys.LOCAL_VERIFICATION_STATE} given the lookupKey, deviceId, accountId
     * and the inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and
     * a boolean value being true if the row was updated, false otherwise.
     */
    private Bundle updateE2eeContactKeyLocalVerificationState(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        final int localVerificationState =
                extras.getInt(E2eeContactKeys.LOCAL_VERIFICATION_STATE);
        final long timeUpdated = System.currentTimeMillis();
        final String callerPackageName = getCallingPackage();

        int rowsUpdated;
        if (extras.containsKey(E2eeContactKeys.OWNER_PACKAGE_NAME)) {
            final String ownerPackageName = extras.getString(E2eeContactKeys.OWNER_PACKAGE_NAME);
            E2eeContactKeysPermissions.enforceVisibility(getContext(),
                    callerPackageName, ownerPackageName);
            E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(),
                    UPDATE_VERIFICATION_STATE_PERMISSION);
            rowsUpdated = mDbHelper.updateContactKeyLocalVerificationState(lookupKey,
                    ownerPackageName, deviceId, accountId, localVerificationState, timeUpdated);
        } else {
            rowsUpdated = mDbHelper.updateContactKeyLocalVerificationState(lookupKey,
                    callerPackageName, deviceId, accountId, localVerificationState, timeUpdated);
        }

        final Bundle response = new Bundle();
        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsUpdated > 0);
        return response;
    }

    /**
     * Updates an end-to-end encryption contact key entry
     * {@link E2eeContactKeys.REMOTE_VERIFICATION_STATE} given the lookupKey, deviceId, accountId
     * and the inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and
     * a boolean value being true if the row was updated, false otherwise.
     */
    private Bundle updateE2eeContactKeyRemoteVerificationState(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        final int remoteVerificationState =
                extras.getInt(E2eeContactKeys.REMOTE_VERIFICATION_STATE);
        final long timeUpdated = System.currentTimeMillis();
        final String callerPackageName = getCallingPackage();

        int rowsUpdated;
        if (extras.containsKey(E2eeContactKeys.OWNER_PACKAGE_NAME)) {
            final String ownerPackageName = extras.getString(E2eeContactKeys.OWNER_PACKAGE_NAME);
            E2eeContactKeysPermissions.enforceVisibility(getContext(),
                    callerPackageName, ownerPackageName);
            E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(),
                    UPDATE_VERIFICATION_STATE_PERMISSION);
            rowsUpdated = mDbHelper.updateContactKeyRemoteVerificationState(lookupKey,
                    ownerPackageName, deviceId, accountId, remoteVerificationState, timeUpdated);
        } else {
            rowsUpdated = mDbHelper.updateContactKeyRemoteVerificationState(lookupKey,
                    callerPackageName, deviceId, accountId, remoteVerificationState, timeUpdated);
        }
        final Bundle response = new Bundle();
        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsUpdated > 0);
        return response;
    }

    /**
     * Removes an end-to-end encryption contact key entry given the lookupKey, deviceId, accountId
     * and the inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and
     * a boolean value being true if the row was removed, false otherwise.
     */
    private Bundle removeE2eeContactKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String lookupKey = extras.getString(E2eeContactKeys.LOOKUP_KEY);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        String callerPackageName = getCallingPackage();

        int rowsRemoved = mDbHelper.removeContactKey(lookupKey, deviceId, accountId,
                callerPackageName);

        final Bundle response = new Bundle();
        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsRemoved > 0);
        return response;
    }

    /**
     * Inserts a new entry into the end-to-end encryption self keys table or updates one if it
     * already exists.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and
     * a boolean value being true if the row was added or updated, false otherwise.
     */
    private Bundle updateOrInsertE2eeSelfKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        final byte[] keyValue = extras.getByteArray(E2eeContactKeys.KEY_VALUE);

        String callerPackageName = getCallingPackage();
        long timeUpdated = System.currentTimeMillis();

        int rowsUpdateOrInserted = mDbHelper.updateOrInsertSelfKey(keyValue, deviceId, accountId,
                callerPackageName, timeUpdated);

        final Bundle response = new Bundle();
        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS,
                rowsUpdateOrInserted > 0);
        return response;
    }

    /**
     * Updates an end-to-end encryption self key entry
     * {@link E2eeContactKeys.REMOTE_VERIFICATION_STATE} given the deviceId, accountId and the
     * inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and
     * a boolean value being true if the row was updated, false otherwise.
     */
    private Bundle updateE2eeSelfKeyRemoteVerificationState(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        final int remoteVerificationState =
                extras.getInt(E2eeContactKeys.REMOTE_VERIFICATION_STATE);
        final long timeUpdated = System.currentTimeMillis();
        final String callerPackageName = getCallingPackage();

        int rowsUpdated;
        if (extras.containsKey(E2eeContactKeys.OWNER_PACKAGE_NAME)) {
            final String ownerPackageName = extras.getString(E2eeContactKeys.OWNER_PACKAGE_NAME);
            E2eeContactKeysPermissions.enforceVisibility(getContext(),
                    callerPackageName, ownerPackageName);
            E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(),
                    UPDATE_VERIFICATION_STATE_PERMISSION);
            rowsUpdated = mDbHelper.updateSelfKeyRemoteVerificationState(ownerPackageName,
                    deviceId, accountId, remoteVerificationState, timeUpdated);
        } else {
            rowsUpdated = mDbHelper.updateSelfKeyRemoteVerificationState(callerPackageName,
                    deviceId, accountId, remoteVerificationState, timeUpdated);
        }

        final Bundle response = new Bundle();
        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsUpdated > 0);
        return response;
    }

    /**
     * Retrieves an end-to-end encryption self key entry given the deviceId, accountId and the
     * inferred package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEY} as a key and a
     * parcelable {@link E2eeContactKeysManager.E2eeContactKey} as a value
     */
    private Bundle getE2eeSelfKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);
        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        String callerPackageName = getCallingPackage();

        E2eeContactKeysManager.E2eeSelfKey selfKey = mDbHelper.getSelfKey(deviceId, accountId,
                callerPackageName);

        final Bundle response = new Bundle();
        response.putParcelable(E2eeContactKeys.KEY_CONTACT_KEY, selfKey);

        return response;
    }

    /**
     * Retrieves all end-to-end encryption self key entries that belong to apps visible to the
     * caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEYS} as a key and
     * a parcelable list of {@link E2eeContactKeysManager.E2eeSelfKey} as a value
     */
    private Bundle getAllE2eeSelfKeys() {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);

        final Bundle response = new Bundle();

        List<E2eeContactKeysManager.E2eeSelfKey> selfKeys = mDbHelper.getAllSelfKeys();

        selfKeys = filterVisibleSelfKeys(selfKeys);
        selfKeys = getStrippedSelfKeys(selfKeys);

        response.putParcelableList(E2eeContactKeys.KEY_CONTACT_KEYS, selfKeys);

        return response;
    }

    /**
     * Retrieves all end-to-end encryption self key entries that are owned by the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_CONTACT_KEYS} as a key and
     * a parcelable list of {@link E2eeContactKeysManager.E2eeContactKey} as a value
     */
    private Bundle getOwnerE2eeSelfKeys() {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);

        final Bundle response = new Bundle();

        String callerPackageName = getCallingPackage();
        List<E2eeContactKeysManager.E2eeSelfKey> selfKeys =
                mDbHelper.getSelfKeysForOwnerPackageName(callerPackageName);

        response.putParcelableList(E2eeContactKeys.KEY_CONTACT_KEYS, selfKeys);

        return response;
    }

    /**
     * Removes an end-to-end encryption self key entry
     * {@link E2eeContactKeys.REMOTE_VERIFICATION_STATE} given the deviceId and the inferred
     * package name of the caller.
     *
     * @return Bundle with a {@link E2eeContactKeys.KEY_UPDATED_ROWS} as a key and a
     * boolean value being true if the row was removed, false otherwise.
     */
    private Bundle removeE2eeSelfKey(Bundle extras) {
        E2eeContactKeysPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);

        final String deviceId = extras.getString(E2eeContactKeys.DEVICE_ID);
        final String accountId = extras.getString(E2eeContactKeys.ACCOUNT_ID);
        String callerPackageName = getCallingPackage();

        int rowsRemoved = mDbHelper.removeSelfKey(deviceId, accountId, callerPackageName);

        final Bundle response = new Bundle();

        response.putBoolean(E2eeContactKeys.KEY_UPDATED_ROWS, rowsRemoved > 0);

        return response;
    }

    /**
     * Retrieves contact basic info - given the lookupKey of the contact, returns display name,
     * phone number and email address.
     *
     * @return Bundle with a {@link E2eeContactKeys.DISPLAY_NAME},
     * {@link E2eeContactKeys.PHONE_NUMBER}
     * and {@link E2eeContactKeys.EMAIL_ADDRESS} as keys and their corresponding
     * values.
     */
    private Bundle getContactBasicInfoFromCP2(String lookupKey) {
        Bundle bundle = new Bundle();
        final long identity = Binder.clearCallingIdentity();
        try {
            Bundle nameAndNumberBundle = getDisplayNameAndNumber(lookupKey);
            bundle.putString(E2eeContactKeys.DISPLAY_NAME,
                    nameAndNumberBundle.getString(E2eeContactKeys.DISPLAY_NAME));
            bundle.putString(E2eeContactKeys.PHONE_NUMBER,
                    nameAndNumberBundle.getString(E2eeContactKeys.PHONE_NUMBER));
            bundle.putString(E2eeContactKeys.EMAIL_ADDRESS, getEmail(lookupKey));
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return bundle;
    }

    /**
     * Returns display name and phone number given the lookupKey of the contact.
     *
     * @return Bundle with a {@link E2eeContactKeys.DISPLAY_NAME}
     * and {@link E2eeContactKeys.PHONE_NUMBER} as keys and their corresponding values.
     */
    private Bundle getDisplayNameAndNumber(String lookupKey) {
        Bundle bundle = new Bundle();
        Cursor c = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[] {ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.Data.LOOKUP_KEY + " = ?",
                new String[] { lookupKey }, null);
        if (c.moveToFirst()) {
            bundle.putString(E2eeContactKeys.DISPLAY_NAME,
                    c.getString(c.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME)));
            bundle.putString(E2eeContactKeys.PHONE_NUMBER,
                    c.getString(c.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER)));
        }
        c.close();
        return bundle;
    }

    /**
     * Returns email address given the lookupKey of the contact.
     *
     * @return Bundle with a {@link E2eeContactKeys.EMAIL_ADDRESS} as key and an
     * email address as a value.
     */
    private String getEmail(String lookupKey) {
        String email = null;
        Cursor c = getContext().getContentResolver().query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[] { ContactsContract.CommonDataKinds.Email.ADDRESS },
                ContactsContract.Data.LOOKUP_KEY + " = ?",
                new String[] { lookupKey }, null);
        if (c.moveToFirst()) {
            email = c.getString(c.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Email.ADDRESS));
        }
        c.close();

        return email;
    }

    /**
     * Returns end-to-end encryption contact keys owned by the packages that are visible to the
     * caller.
     * This is verified by checking that the caller package can query the package owning the key.
     */
    private List<E2eeContactKeysManager.E2eeContactKey> filterVisibleContactKeys(
            List<E2eeContactKeysManager.E2eeContactKey> contactKeys) {
        List<E2eeContactKeysManager.E2eeContactKey> visibleContactKeys =
                new ArrayList<>(contactKeys.size());
        PackageManager packageManager = getContext().getPackageManager();
        String callingPackageName = getCallingPackage();
        for (E2eeContactKeysManager.E2eeContactKey contactKey : contactKeys) {
            try {
                String targetPackageName = contactKey.getOwnerPackageName();
                if (packageManager.canPackageQuery(callingPackageName, targetPackageName)) {
                    visibleContactKeys.add(contactKey);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // The caller doesn't have visibility of the owner package
            }
        }
        return visibleContactKeys;
    }

    /**
     * Removes deviceId, timeUpdated, and key value data from end-to-end encryption contact keys.
     */
    private static List<E2eeContactKeysManager.E2eeContactKey> getStrippedContactKeys(
            List<E2eeContactKeysManager.E2eeContactKey> visibleContactKeys) {
        List<E2eeContactKeysManager.E2eeContactKey> strippedContactKeys = new ArrayList<>(
                visibleContactKeys.size());
        for (E2eeContactKeysManager.E2eeContactKey key : visibleContactKeys) {
            E2eeContactKeysManager.E2eeContactKey strippedContactKey =
                    new E2eeContactKeysManager.E2eeContactKey(null, key.getAccountId(),
                            key.getOwnerPackageName(), STRIPPED_TIME_UPDATED, null,
                            key.getLocalVerificationState(), key.getRemoteVerificationState(),
                            key.getDisplayName(), key.getPhoneNumber(), key.getEmailAddress());
            strippedContactKeys.add(strippedContactKey);
        }
        return strippedContactKeys;
    }

    /**
     * Returns end-to-end encryption self keys owned by the packages that are visible to the caller.
     * This is verified by checking that the caller package can query the package owning the key.
     */
    private List<E2eeContactKeysManager.E2eeSelfKey> filterVisibleSelfKeys(
            List<E2eeContactKeysManager.E2eeSelfKey> selfKeys) {
        List<E2eeContactKeysManager.E2eeSelfKey> visibleSelfKeys =
                new ArrayList<>(selfKeys.size());
        PackageManager packageManager = getContext().getPackageManager();
        String callingPackageName = getCallingPackage();
        for (E2eeContactKeysManager.E2eeSelfKey selfKey : selfKeys) {
            try {
                String targetPackageName = selfKey.getOwnerPackageName();
                if (packageManager.canPackageQuery(callingPackageName, targetPackageName)) {
                    visibleSelfKeys.add(selfKey);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // The caller doesn't have visibility of the owner package
            }
        }
        return visibleSelfKeys;
    }


    /**
     * Removes deviceId, timeUpdated, and key value data from end-to-end encryption self keys.
     */
    private static List<E2eeContactKeysManager.E2eeSelfKey> getStrippedSelfKeys(
            List<E2eeContactKeysManager.E2eeSelfKey> visibleSelfKeys) {
        List<E2eeContactKeysManager.E2eeSelfKey> strippedSelfKeys = new ArrayList<>(
                visibleSelfKeys.size());
        for (E2eeContactKeysManager.E2eeSelfKey key : visibleSelfKeys) {
            E2eeContactKeysManager.E2eeSelfKey strippedContactKey =
                    new E2eeContactKeysManager.E2eeSelfKey(null, key.getAccountId(),
                            key.getOwnerPackageName(), STRIPPED_TIME_UPDATED, null,
                            key.getRemoteVerificationState());
            strippedSelfKeys.add(strippedContactKey);
        }
        return strippedSelfKeys;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // Not implemented
        return null;
    }

    @Override
    public String getType(Uri uri) {
        // Not implemented
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Not implemented
        return null;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        // Not implemented
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        // Not implemented
        return 0;
    }
}