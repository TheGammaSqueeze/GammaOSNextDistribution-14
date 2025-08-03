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

package com.android.cts.launchable.cloneprofile.contacts.app;

import static com.google.common.truth.Truth.assertThat;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class CloneContactsSharingTest {

    private static final String TEST_CONTACT_DISPLAY_NAME = "test_contact";
    private static final String TEST_CONTACT_PHONE = "0123456789";
    private static final String RAW_CONTACTS_ENDPOINT = "raw_contacts";
    private static final String CONTACTS_DATA_ENDPOINT = "data";
    private static final String TEST_ACCOUNT_TYPE = "test.com";
    private static final String TEST_ACCOUNT_NAME = "test@test.com";

    private Context mContext;
    private ContentResolver mContentResolver;
    private AccountManager mAccountManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mContentResolver = mContext.getContentResolver();
        mAccountManager = AccountManager.get(mContext);
    }

    /**
     * Returns argument from InstrumentationRegistry
     * @param testArgumentKey key name
     * @return value passed in argument or "" if not defined
     */
    private String getTestArgumentValueForGivenKey(String testArgumentKey) {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        String testArgumentValue = testArguments.getString(testArgumentKey, "");
        return testArgumentValue;
    }

    /**
     * Returns sample content values for a test raw contact
     * @param appendValue String value to be appended to the account name and custom ringtone
     *                    fields.
     */
    private ContentValues getTestContentValues(String appendValue) {
        if (appendValue == null) appendValue = "";
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "test" + appendValue + "@test.com");
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "test.com");
        values.put(ContactsContract.RawContacts.CUSTOM_RINGTONE, "custom" + appendValue);
        values.put(ContactsContract.RawContacts.STARRED, "1");
        return values;
    }

    private ContentValues[] getTestContentValues(int number) {
        ContentValues[] contentValues = new ContentValues[number];
        for (int i = 0; i < number; i++) {
            contentValues[i] = getTestContentValues((i + 1) + "");
        }
        return contentValues;
    }

    private String getCursorValue(Cursor c, String columnName) {
        return c.getString(c.getColumnIndex(columnName));
    }

    /**
     * Asserts that the expected cursor and actual cursor have the same values for the given
     * column names
     */
    private void assertContactsCursorEquals(Cursor expectedCursor, Cursor actualCursor,
            Set<String> columnNames) {
        assertThat(actualCursor).isNotNull();
        assertThat(expectedCursor).isNotNull();
        assertThat(actualCursor.getCount()).isEqualTo(expectedCursor.getCount());
        while (actualCursor.moveToNext()) {
            expectedCursor.moveToNext();
            for (String key: columnNames) {
                assertThat(getCursorValue(actualCursor, key))
                        .isEqualTo(getCursorValue(expectedCursor, key));
            }
        }
    }

    private Cursor queryContactsForTestAccount(Uri uri, String[] projection, String accountType) {
        String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
        String[] selectionArgs = new String[] {
                accountType
        };
        return mContentResolver.query(
                uri, projection, selection, selectionArgs,
                /* sortOrder */ null
        );
    }

    /**
     * Return the projected columns for all the raw contacts associated with the given account type
     * @param accountType account type that identifies all test raw_contacts
     * @param projection Array of projected columns to return. If this is null, only the _id column
     *                   will be returned
     */
    private Cursor getAllRawContactsForTestAccount(String accountType, String[] projection) {
        if (projection == null) {
            projection = new String[] {
                    ContactsContract.RawContacts._ID,
            };
        }
        return queryContactsForTestAccount(ContactsContract.RawContacts.CONTENT_URI, projection,
                accountType);
    }

    private Cursor getAllDataTableIds() {
        return mContentResolver.query(ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data._ID},
                null /* queryArgs */, null /* cancellationSignal */);
    }

    /**
     * Helper method to insert contacts data through {@link ContentResolver#applyBatch} operation.
     * It uses sample values to insert a test raw contact row, a row in the data table corresponding
     * to the test contact display name and one for the test phone number.
     * @return ContentProviderResults of each insert operation
     */
    private ContentProviderResult[] insertContactsDataThroughBatchOperations()
            throws RemoteException, OperationApplicationException, Resources.NotFoundException {

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,
                        CloneContactsSharingTest.TEST_ACCOUNT_TYPE)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME,
                        CloneContactsSharingTest.TEST_ACCOUNT_NAME)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        CloneContactsSharingTest.TEST_CONTACT_DISPLAY_NAME)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,
                        CloneContactsSharingTest.TEST_CONTACT_PHONE)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
        String authority = ContactsContract.AUTHORITY;
        return mContentResolver.applyBatch(authority, ops);
    }

    private void assertCloneProviderInsertContentProviderResults(
            ContentProviderResult[] contentProviderResults) {
        assertThat(contentProviderResults).isNotNull();
        for (ContentProviderResult contentProviderResult : contentProviderResults) {
            assertThat(contentProviderResult.exception).isNull();
            assertThat(contentProviderResult.uri).isNotNull();
            String uriEncodedPath = contentProviderResult.uri.getEncodedPath();
            if (uriEncodedPath.contains(RAW_CONTACTS_ENDPOINT)) {
                Uri expectedRawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                        .appendPath("0")
                        .build();
                assertThat(contentProviderResult.uri).isEqualTo(expectedRawContactsUri);
            } else if (uriEncodedPath.contains(CONTACTS_DATA_ENDPOINT)) {
                Uri expectedDataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
                        .appendPath("0")
                        .build();
                assertThat(contentProviderResult.uri).isEqualTo(expectedDataUri);
            }
        }
    }

    private void assertContactsNotSyncable(Account testAccount) {
        int isSyncable = ContentResolver.getIsSyncable(testAccount, ContactsContract.AUTHORITY);
        assertThat(isSyncable).isEqualTo(0);
    }

    @Test
    public void testCloneContactsProviderInsert_rawContacts_doesNotInsertActually() {
        Cursor resultsBeforeInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);

        // Insert new raw contact with sample content values
        Uri resultUri =
                mContentResolver.insert(ContactsContract.RawContacts.CONTENT_URI,
                        getTestContentValues(/* appendValues */ ""));

        // Here we expect a fakeUri returned to fail silently
        Uri expectedUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendPath("0")
                .build();
        assertThat(resultUri).isEqualTo(expectedUri);

        // Query for all raw contact ids that can be queried from the clone app and check no new
        // contact was added.
        Cursor resultsAfterInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);
        assertContactsCursorEquals(resultsBeforeInsert, resultsAfterInsert,
                new HashSet<>(List.of(ContactsContract.RawContacts._ID)));
    }

    @Test
    public void testCloneContactsProviderBulkInsert_rawContacts_noContactsInserted() {
        Cursor resultsBeforeInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);

        // Bulk insert new raw contacts with sample content values
        int bulkInsertResult =
                mContentResolver.bulkInsert(ContactsContract.RawContacts.CONTENT_URI,
                        getTestContentValues(2));

        // Assert that no contacts were inserted
        assertThat(bulkInsertResult).isEqualTo(0);

        // Query for all raw contact ids that can be queried from the clone app and check no new
        // contact was added.
        Cursor resultsAfterInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);
        assertContactsCursorEquals(resultsBeforeInsert, resultsAfterInsert,
                new HashSet<>(List.of(ContactsContract.RawContacts._ID)));
    }

    @Test
    public void testCloneContactsProviderApplyBatch_rawContacts_noContactsInserted()
            throws RemoteException, OperationApplicationException {

        // Query for all raw contacts with test account type and all the entries in the data table
        Cursor resultsBeforeInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);
        Cursor dataTableResultsBeforeInsert = getAllDataTableIds();

        // Insert contacts through clone contacts provider applyBatch operations
        ContentProviderResult[] contentProviderResults =
                insertContactsDataThroughBatchOperations();

        // Check results of inserting contacts through applyBatch
        assertCloneProviderInsertContentProviderResults(contentProviderResults);

        // Assert that no contacts were added as a result of the applyBatch through clone provider
        Cursor resultsAfterInsert = getAllRawContactsForTestAccount(TEST_ACCOUNT_TYPE,
                /* projection */ null);
        Cursor dataTableResultsAfterInsert = getAllDataTableIds();
        assertContactsCursorEquals(resultsBeforeInsert, resultsAfterInsert,
                new HashSet<>(List.of(ContactsContract.RawContacts._ID)));
        assertContactsCursorEquals(dataTableResultsBeforeInsert, dataTableResultsAfterInsert,
                new HashSet<>(List.of(ContactsContract.Data._ID)));
    }

    @Test
    public void testCloneContactsProviderUpdates_rawContactsUpdate_doesNotUpdateActually() {
        String testContactAccountType =
                getTestArgumentValueForGivenKey("test_contact_account_type");
        String testContactAccountName =
                getTestArgumentValueForGivenKey("test_contact_account_name");
        String testContactCustomRingtone =
                getTestArgumentValueForGivenKey("test_contact_custom_ringtone");

        // Query to fetch all raw_contact rows matching the test account type
        String[] projection = new String[] {
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.CUSTOM_RINGTONE
        };
        String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
        String[] selectionArgs = new String[] {
                testContactAccountType
        };
        Cursor resultsBeforeUpdate = getAllRawContactsForTestAccount(testContactAccountType,
                projection);

        // Build updated content values and query clone contacts provider to update some columns
        ContentValues updatedValues = new ContentValues();
        updatedValues.put(ContactsContract.RawContacts.ACCOUNT_TYPE,
                "updated_" + testContactAccountType);
        updatedValues.put(ContactsContract.RawContacts.ACCOUNT_NAME, testContactAccountName);
        updatedValues.put(ContactsContract.RawContacts.CUSTOM_RINGTONE,
                testContactCustomRingtone + "_updated");
        int update = mContentResolver.update(ContactsContract.RawContacts.CONTENT_URI,
                updatedValues, selection, selectionArgs);

        // Assert that no contacts were updated
        assertThat(update).isEqualTo(0);

        // Assert that the row is unaffected from the update
        Cursor resultsAfterUpdate = getAllRawContactsForTestAccount(testContactAccountType,
                projection);
        assertContactsCursorEquals(resultsBeforeUpdate, resultsAfterUpdate, updatedValues.keySet());
    }

    @Test
    public void testCloneContactsProviderDeletes_rawContactsDelete_doesNotDeleteActually() {
        String testContactAccountType =
                getTestArgumentValueForGivenKey("test_contact_account_type");

        // Query to fetch all raw_contact rows matching the test account type
        Cursor resultsBeforeDelete = getAllRawContactsForTestAccount(testContactAccountType,
                /* projection */ null);
        assertThat(resultsBeforeDelete).isNotNull();
        assertThat(resultsBeforeDelete.getCount()).isNotEqualTo(0);

        String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
        String[] selectionArgs = new String[] {
                testContactAccountType
        };
        int delete = mContentResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                selection, selectionArgs);

        // Assert that no contacts were updated
        assertThat(delete).isEqualTo(0);

        // Assert that the row is unaffected from the clone provider delete call
        Cursor resultsAfterDelete = getAllRawContactsForTestAccount(testContactAccountType,
                /* projection */ null);
        assertThat(resultsAfterDelete).isNotNull();
        assertThat(resultsAfterDelete.getCount()).isNotEqualTo(0);
        assertThat(resultsAfterDelete.getCount()).isEqualTo(resultsBeforeDelete.getCount());
    }

    @Test
    public void testCloneContactsProviderReads_rawContactsReads_redirectsToPrimary() {
        String testContactAccountType =
                getTestArgumentValueForGivenKey("test_contact_account_type");
        String testContactAccountName =
                getTestArgumentValueForGivenKey("test_contact_account_name");
        String testContactCustomRingtone =
                getTestArgumentValueForGivenKey("test_contact_custom_ringtone");

        // Make a query to fetch all raw contacts corresponding to the test account
        String[] projection = new String[] {
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.CUSTOM_RINGTONE
        };
        Cursor rawContactsForTestAccount = getAllRawContactsForTestAccount(
                testContactAccountType, projection);

        // Assert that the resulting cursor should contain only the raw contact that was inserted
        // and passed from the host side test.
        assertThat(rawContactsForTestAccount).isNotNull();
        assertThat(rawContactsForTestAccount.getCount()).isEqualTo(1);
        rawContactsForTestAccount.moveToFirst();
        assertThat(
                rawContactsForTestAccount.getString(
                        rawContactsForTestAccount.getColumnIndex(
                                ContactsContract.RawContacts.ACCOUNT_NAME)))
                .isEqualTo(testContactAccountName);
        assertThat(rawContactsForTestAccount.getString(
                rawContactsForTestAccount.getColumnIndex(
                        ContactsContract.RawContacts.ACCOUNT_TYPE)))
                .isEqualTo(testContactAccountType);
        assertThat(rawContactsForTestAccount.getString(
                rawContactsForTestAccount.getColumnIndex(
                        ContactsContract.RawContacts.CUSTOM_RINGTONE)))
                .isEqualTo(testContactCustomRingtone);
    }

    @Test
    public void testContactSyncsForCloneAccounts_syncsAreDisabled() {
        // Add test account with mock authenticator through account manager
        Account testAccount =
                new Account(TEST_ACCOUNT_NAME, TestAccountAuthenticator.TEST_ACCOUNT_TYPE);
        boolean result = mAccountManager.addAccountExplicitly(testAccount, /* password */ null,
                /* userData */ null);
        assertThat(result).isTrue();

        // Assert that contact syncs for clone profile accounts are disabled
        assertContactsNotSyncable(testAccount);

        // Assert that contact syncs are not allowed even after it is set as syncable through
        // content resolver APIs
        ContentResolver.setIsSyncable(testAccount, ContactsContract.AUTHORITY, /* syncable */ 1);
        assertContactsNotSyncable(testAccount);

        mAccountManager.removeAccountExplicitly(testAccount);
    }

    @Test
    public void testAccessManagedProfileContacts_contactReadSuccessfully() {
        String testContactAccountType =
                getTestArgumentValueForGivenKey("test_contact_account_type");
        String testContactAccountName =
                getTestArgumentValueForGivenKey("test_contact_account_name");
        String testContactPhoneNumber =
                getTestArgumentValueForGivenKey("test_contact_phone_number");

        String[] projection = new String[] {
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                Phone.NUMBER,
                Phone.RAW_CONTACT_ID
        };

        Cursor cursor = queryContactsForTestAccount(Phone.ENTERPRISE_CONTENT_URI, projection,
                testContactAccountType);

        // Assert that the resulting cursor should contain only the raw contact that was inserted
        // and passed from the host side test.
        assertThat(cursor).isNotNull();
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToFirst();
        assertThat(cursor.getString(
                        cursor.getColumnIndex(
                                ContactsContract.RawContacts.ACCOUNT_NAME)))
                .isEqualTo(testContactAccountName);
        assertThat(cursor.getString(
                cursor.getColumnIndex(
                        ContactsContract.RawContacts.ACCOUNT_TYPE)))
                .isEqualTo(testContactAccountType);
        assertThat(cursor.getString(
                cursor.getColumnIndex(
                        Phone.NUMBER)))
                .isEqualTo(testContactPhoneNumber);
    }

    /**
     * Checks that the cross-profile contact reads for managed-profile contacts are blocked
     */
    @Test
    public void testAccessManagedProfileContacts_contactsReadBlocked() {
        String testContactAccountType =
                getTestArgumentValueForGivenKey("test_contact_account_type");

        String[] projection = new String[] {
                Phone.NUMBER,
                Phone.RAW_CONTACT_ID
        };

        Cursor cursor = queryContactsForTestAccount(Phone.ENTERPRISE_CONTENT_URI, projection,
                testContactAccountType);
        assertThat(cursor).isNotNull();
        assertThat(cursor.getCount()).isEqualTo(0);
    }
}
