/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.util.LogUtil;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DeletedContacts;
import android.util.Log;

import java.util.List;
import java.util.Objects;

/**
 * Helper class to query Contacts Provider (CP2).
 *
 * @hide
 */
public final class ContactsProviderUtil {
    private static final String TAG = "ContactsProviderHelper";

    public static final int UPDATE_LIMIT_NONE = -1;

    // static final string for querying CP2
    private static final String UPDATE_SINCE = Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + ">?";
    private static final String UPDATE_ORDER_BY = Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " DESC";
    private static final String[] UPDATE_SELECTION = new String[]{
            Contacts._ID,
            Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
    };
    private static final String DELETION_SINCE = DeletedContacts.CONTACT_DELETED_TIMESTAMP + ">?";
    private static final String[] DELETION_SELECTION = new String[]{
            DeletedContacts.CONTACT_ID,
            DeletedContacts.CONTACT_DELETED_TIMESTAMP,
    };

    private ContactsProviderUtil() {
    }

    static long getLastUpdatedTimestamp(@NonNull Cursor cursor) {
        Objects.requireNonNull(cursor);
        int index = cursor.getColumnIndex(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP);
        return index != -1 ? cursor.getLong(index) : 0;
    }

    /**
     * Gets the ids for deleted contacts from certain timestamp.
     *
     * @param sinceFilter timestamp (milliseconds since epoch) from which ids of deleted contacts
     *                    should be returned.
     * @param contactIds  the Set passed in to hold the deleted contacts.
     * @return the timestamp for the contact most recently deleted.
     */
    static public long getDeletedContactIds(@NonNull Context context, long sinceFilter,
            @NonNull List<String> contactIds, @Nullable ContactsUpdateStats updateStats) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(contactIds);

        String[] selectionArgs = new String[]{Long.toString(sinceFilter)};
        long newTimestamp = sinceFilter;
        Cursor cursor = null;
        try {
            // TODO(b/203605504) We could optimize the query by setting the sortOrder:
            //  LAST_DELETED_TIMESTAMP DESC. This way the 1st contact would have the last deleted
            //  timestamp.
            cursor =
                    context.getContentResolver().query(
                            DeletedContacts.CONTENT_URI,
                            DELETION_SELECTION,
                            DELETION_SINCE,
                            selectionArgs,
                            /*sortOrder=*/ null);

            if (cursor == null) {
                Log.e(TAG,
                        "Could not fetch deleted contacts - no contacts provider present?");
                if (updateStats != null) {
                    updateStats.mDeleteStatuses.add(
                            ContactsUpdateStats.ERROR_CODE_CP2_NULL_CURSOR);
                }
                return newTimestamp;
            }

            int contactIdIndex = cursor.getColumnIndex(DeletedContacts.CONTACT_ID);
            int timestampIndex = cursor.getColumnIndex(DeletedContacts.CONTACT_DELETED_TIMESTAMP);
            long rows = 0;
            while (cursor.moveToNext()) {
                contactIds.add(String.valueOf(cursor.getLong(contactIdIndex)));
                // We still get max value between those two here just in case cursor.getLong
                // returns something unexpected(e.g. somehow it returns an invalid value like
                // -1 or 0 due to an invalid index).
                newTimestamp = Math.max(newTimestamp, cursor.getLong(timestampIndex));
                ++rows;
            }
            if (LogUtil.DEBUG) {
                Log.d(TAG, "Got " + rows + " deleted contacts since " + sinceFilter);
            }
        } catch (SecurityException |
                 SQLiteException |
                 NullPointerException |
                 NoClassDefFoundError e) {
            Log.e(TAG, "ContentResolver.query failed to get latest deleted contacts.", e);
            if (updateStats != null) {
                updateStats.mDeleteStatuses.add(
                        ContactsUpdateStats.ERROR_CODE_CP2_RUNTIME_EXCEPTION);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return newTimestamp;
    }

    /**
     * Returns a list of IDs, within given limit, of contacts updated since given timestamp.
     *
     * @param sinceFilter timestamp (milliseconds since epoch) from which ids of recently updated
     *                    contacts should be returned.
     * @param contactIds  the Set passed in to hold the recently updated contacts.
     * @param limit       the maximum number of contacts fetched from CP2. No limit will be set if
     *                    the value is {@link ContactsIndexerConfig#UPDATE_LIMIT_NONE}.
     * @return the timestamp for the contact most recently updated.
     */
    public static long getUpdatedContactIds(@NonNull Context context, long sinceFilter, int limit,
            @NonNull List<String> contactIds, @Nullable ContactsUpdateStats updateStats) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(contactIds);

        long newTimestamp = sinceFilter;
        String[] selectionArgs = new String[]{Long.toString(sinceFilter)};
        // We only get the contacts from the default directory, e.g. the non-invisibles.
        Uri.Builder contactsUriBuilder = Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY,
                String.valueOf(ContactsContract.Directory.DEFAULT));
        String orderBy = null;
        if (limit >= 0) {
            contactsUriBuilder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                    String.valueOf(limit));
            orderBy = UPDATE_ORDER_BY;
        }
        try (Cursor cursor = context.getContentResolver().query(
                contactsUriBuilder.build(),
                UPDATE_SELECTION,
                UPDATE_SINCE, selectionArgs,
                orderBy)) {
            if (cursor == null) {
                Log.w(TAG, "Failed to get a list of contacts updated since " + sinceFilter);
                if (updateStats != null) {
                    updateStats.mUpdateStatuses.add(
                            ContactsUpdateStats.ERROR_CODE_CP2_NULL_CURSOR);
                }
                return newTimestamp;
            }

            int contactIdIndex = cursor.getColumnIndex(Contacts._ID);
            int timestampIndex = cursor.getColumnIndex(
                    Contacts.CONTACT_LAST_UPDATED_TIMESTAMP);
            int numContacts = 0;
            while (cursor.moveToNext()) {
                // Just in case the LIMIT parameter doesn't work in the query to CP2.
                if (limit >= 0 && numContacts >= limit) {
                    break;
                }

                long contactId = cursor.getLong(contactIdIndex);
                contactIds.add(String.valueOf(contactId));
                numContacts++;
                newTimestamp = Math.max(newTimestamp, cursor.getLong(timestampIndex));
            }

            if (LogUtil.DEBUG) {
                Log.v(TAG, "Returning " + numContacts + " updated contacts since " + sinceFilter);
            }
        } catch (SecurityException |
                 SQLiteException |
                 NullPointerException |
                 NoClassDefFoundError e) {
            Log.e(TAG, "ContentResolver.query failed to get latest updated contacts.", e);
            // TODO(b/222126568) consider throwing an exception here. And in the caller it can
            //  still catch the exception, and based on the states(e.g. whether we query CP2
            //  successfully before and need to remove some contacts), caller can choose to keep
            //  doing the update or not.
            if (updateStats != null) {
                updateStats.mUpdateStatuses.add(
                        ContactsUpdateStats.ERROR_CODE_CP2_RUNTIME_EXCEPTION);
            }
        }

        return newTimestamp;
    }
}