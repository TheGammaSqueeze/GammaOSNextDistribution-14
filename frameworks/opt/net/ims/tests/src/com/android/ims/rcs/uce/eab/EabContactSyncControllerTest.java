/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.eab;

import static android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.test.mock.MockContentResolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.rule.provider.ProviderTestRule;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EabContactSyncControllerTest extends ImsTestBase {
    private static final String TAG = "EabContactDataSyncServiceTest";

    FakeContactProvider mFakeContactProvider = new FakeContactProvider();

    @Rule
    public ProviderTestRule mProviderTestRule = new ProviderTestRule.Builder(
            EabProvider.class, EabProvider.AUTHORITY).build();

    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mSharedPreferencesEditor;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockContentResolver mockContentResolver =
                (MockContentResolver) mProviderTestRule.getResolver();
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = ContactsContract.AUTHORITY;
        mFakeContactProvider.attachInfo(mContext, providerInfo);
        mockContentResolver.addProvider(providerInfo.authority, mFakeContactProvider);
        doReturn("com.android.phone.tests").when(mContext).getPackageName();

        doReturn(mProviderTestRule.getResolver()).when(mContext).getContentResolver();

        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(0L).when(mSharedPreferences).getLong(anyString(), anyInt());
        doReturn(mSharedPreferencesEditor).when(mSharedPreferences).edit();
        doReturn(mSharedPreferencesEditor).when(mSharedPreferencesEditor).putLong(anyString(),
                anyLong());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mFakeContactProvider.clearData();
        mContext.getContentResolver().delete(EabProvider.CONTACT_URI, null, null);
    }

    @Test
    public void testContactDeletedCase() {
        insertContactToEabProvider(1, 2, 3, "123456");
        insertDeletedContactToContactProvider(1, 1);

        new EabContactSyncController().syncContactToEabProvider(mContext);

        Cursor result = mProviderTestRule.getResolver().query(
                EabProvider.CONTACT_URI,
                null,
                null,
                null);
        assertEquals(0, result.getCount());
    }

    @Test
    public void testMultipleContactsDeletedCase() {
        // Insert 3 contacts in EabProvider
        insertContactToEabProvider(1, 1, 1, "123456");
        insertContactToEabProvider(2, 2, 2, "1234567");
        insertContactToEabProvider(3, 3, 3, "12345678");
        // Insert 2 deleted contacts
        insertDeletedContactToContactProvider(1, 1);
        insertDeletedContactToContactProvider(2, 1);
        // Keep id:3 in contact provider
        insertContactToContactProvider(3, 3, 3, "12345678");

        new EabContactSyncController().syncContactToEabProvider(mContext);

        // Make sure only 1 contact in Eab DB
        Cursor result = mProviderTestRule.getResolver().query(
                EabProvider.CONTACT_URI,
                null,
                null,
                null);
        assertEquals(1, result.getCount());
    }

    @Test
    public void testPhoneNumberDeletedCase() {
        insertContactToEabProvider(1, 1, 2, "123456");
        insertContactToEabProvider(1, 1, 3, "1234567");
        insertContactToEabProvider(1, 1, 4, "12345678");
        // Delete phone number 12345678
        insertContactToContactProvider(1, 1, 2, "123456");
        insertContactToContactProvider(1, 1, 3, "1234567");

        new EabContactSyncController().syncContactToEabProvider(mContext);

        Cursor result = mProviderTestRule.getResolver().query(
                EabProvider.CONTACT_URI,
                null,
                null,
                null);
        assertEquals(2, result.getCount());
    }

    @Test
    public void testPhoneNumberUpdatedCase() {
        insertContactToEabProvider(1, 1, 2, "123456");
        insertContactToEabProvider(1, 1, 3, "1234567");
        insertContactToEabProvider(1, 1, 4, "12345678");
        // Update phone number to 1,2,3
        insertContactToContactProvider(1, 1, 2, "1");
        insertContactToContactProvider(1, 1, 3, "2");
        insertContactToContactProvider(1, 1, 4, "3");

        new EabContactSyncController().syncContactToEabProvider(mContext);

        Cursor result = mProviderTestRule.getResolver().query(
                EabProvider.CONTACT_URI,
                null,
                null,
                null,
                EabProvider.ContactColumns.DATA_ID);
        result.moveToFirst();
        assertEquals(3, result.getCount());
        assertEquals("1",
                result.getString(result.getColumnIndex(EabProvider.ContactColumns.PHONE_NUMBER)));
        result.moveToNext();
        assertEquals("2",
                result.getString(result.getColumnIndex(EabProvider.ContactColumns.PHONE_NUMBER)));
        result.moveToNext();
        assertEquals("3",
                result.getString(result.getColumnIndex(EabProvider.ContactColumns.PHONE_NUMBER)));
    }

    private void insertDeletedContactToContactProvider(int contactId, int timestamp) {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.DeletedContacts.CONTACT_ID, contactId);
        values.put(ContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP, timestamp);
        mContext.getContentResolver().insert(
                ContactsContract.DeletedContacts.CONTENT_URI, values);
    }

    private void insertContactToContactProvider(
            int contactId, int rawContactId, int dataId, String number) {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.Data._ID, dataId);
        values.put(EabProvider.ContactColumns.CONTACT_ID, contactId);
        values.put(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, number);
        values.put(ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP, 1);

        mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
    }

    private void insertContactToEabProvider(int contactId,
            int rawContactId, int dataId, String phoneNumber) {
        ContentValues values = new ContentValues();
        values.put(EabProvider.ContactColumns.CONTACT_ID, contactId);
        values.put(EabProvider.ContactColumns.RAW_CONTACT_ID, rawContactId);
        values.put(EabProvider.ContactColumns.DATA_ID, dataId);
        values.put(EabProvider.ContactColumns.PHONE_NUMBER, phoneNumber);
        mContext.getContentResolver().insert(EabProvider.CONTACT_URI, values);
    }

    /**
     * Create a fake contact provider that store ContentValues in hashmap when invoke insert()
     * and convert to cursor when invoke query()
     */
    public static class FakeContactProvider extends ContentProvider {
        private final HashMap<Uri, List<ContentValues>> mFakeProviderData = new HashMap<>();

        public FakeContactProvider() {
        }

        public void clearData() {
            mFakeProviderData.clear();
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return convertContentValuesToCursor(mFakeProviderData.get(uri));
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return null;
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            List<ContentValues> allDataList =
                    mFakeProviderData.computeIfAbsent(uri, k -> new ArrayList<>());
            allDataList.add(new ContentValues(values));
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values,
                @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }

        private Cursor convertContentValuesToCursor(List<ContentValues> valuesList) {
            if (valuesList != null) {
                MatrixCursor result =
                        new MatrixCursor(valuesList.get(0).keySet().toArray(new String[0]));
                for (ContentValues contentValue : valuesList) {
                    MatrixCursor.RowBuilder builder = result.newRow();
                    for (String key : contentValue.keySet()) {
                        builder.add(key, contentValue.get(key));
                    }
                }
                return result;
            } else {
                return new MatrixCursor(new String[0]);
            }
        }
    }
}
