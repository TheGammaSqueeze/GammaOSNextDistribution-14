/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.mapapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapIMProviderTest {

    private static final String TAG = "MapIMProviderTest";

    private static final String AUTHORITY = "com.test";
    private static final String ACCOUNT_ID = "12345";
    private static final String MESSAGE_ID = "987654321";
    private static final String FOLDER_ID = "6789";

    private Context mContext;

    @Spy
    private BluetoothMapIMProvider mProvider = new TestBluetoothMapIMProvider();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void attachInfo_whenProviderIsNotExported() throws Exception {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = false;

        assertThrows(SecurityException.class,
                () -> mProvider.attachInfo(mContext, providerInfo));
    }

    @Test
    public void attachInfo_whenNoPermission() throws Exception {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = "some.random.permission";

        assertThrows(SecurityException.class,
                () -> mProvider.attachInfo(mContext, providerInfo));
    }

    @Test
    public void attachInfo_success() throws Exception {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;

        try {
            mProvider.attachInfo(mContext, providerInfo);
        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }

    @Test
    public void getType() throws Exception {
        try {
            mProvider.getType(/*uri=*/ null);
        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }

    @Test
    public void delete_whenTableNameIsWrong() throws Exception {
        Uri uriWithWrongTable = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath("some_random_table_name")
                .appendPath(MESSAGE_ID)
                .build();

        // No rows are impacted.
        assertThat(mProvider.delete(uriWithWrongTable, /*where=*/null, /*selectionArgs=*/null))
                .isEqualTo(0);
    }

    @Test
    public void delete_success() throws Exception {
        Uri messageUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_MESSAGE)
                .appendPath(MESSAGE_ID)
                .build();

        mProvider.delete(messageUri, /*where=*/null, /*selectionArgs=*/null);
        verify(mProvider).deleteMessage(ACCOUNT_ID, MESSAGE_ID);
    }

    @Test
    public void insert_whenTableNameIsWrong() throws Exception {
        Uri uriWithWrongTable = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath("some_random_table_name")
                .build();
        ContentValues values = new ContentValues();
        values.put(BluetoothMapContract.MessageColumns.FOLDER_ID, Long.parseLong(FOLDER_ID));

        assertThat(mProvider.insert(uriWithWrongTable, values)).isNull();
    }

    @Test
    public void insert_success() throws Exception {
        Uri messageUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_MESSAGE)
                .build();

        ContentValues values = new ContentValues();
        values.put(BluetoothMapContract.MessageColumns.FOLDER_ID, Long.parseLong(FOLDER_ID));

        mProvider.insert(messageUri, values);
        verify(mProvider).insertMessage(ACCOUNT_ID, values);
    }

    @Test
    public void query_forAccountUri() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;
        mProvider.attachInfo(mContext, providerInfo);

        Uri accountUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(BluetoothMapContract.TABLE_ACCOUNT)
                .build();

        mProvider.query(accountUri, /*projection=*/ null, /*selection=*/ null,
                /*selectionArgs=*/ null, /*sortOrder=*/ null);
        verify(mProvider).queryAccount(any(), any(), any(), any());
    }

    @Test
    public void query_forMessageUri() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;
        mProvider.attachInfo(mContext, providerInfo);

        Uri messageUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_MESSAGE)
                .build();

        mProvider.query(messageUri, /*projection=*/ null, /*selection=*/ null,
                /*selectionArgs=*/ null, /*sortOrder=*/ null);
        verify(mProvider).queryMessage(eq(ACCOUNT_ID), any(), any(), any(), any());
    }

    @Test
    public void query_forConversationUri() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;
        mProvider.attachInfo(mContext, providerInfo);

        final long threadId = 1;
        final boolean read = true;
        final long periodEnd = 100;
        final long periodBegin = 10;
        final String searchString = "sample_search_query";

        Uri conversationUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_CONVERSATION)
                .appendQueryParameter(BluetoothMapContract.FILTER_ORIGINATOR_SUBSTRING,
                        searchString)
                .appendQueryParameter(BluetoothMapContract.FILTER_PERIOD_BEGIN,
                        Long.toString(periodBegin))
                .appendQueryParameter(BluetoothMapContract.FILTER_PERIOD_END,
                        Long.toString(periodEnd))
                .appendQueryParameter(BluetoothMapContract.FILTER_READ_STATUS,
                        Boolean.toString(read))
                .appendQueryParameter(BluetoothMapContract.FILTER_THREAD_ID,
                        Long.toString(threadId))
                .build();

        mProvider.query(conversationUri, /*projection=*/ null, /*selection=*/ null,
                /*selectionArgs=*/ null, /*sortOrder=*/ null);
        verify(mProvider).queryConversation(eq(ACCOUNT_ID), eq(threadId), eq(read), eq(periodEnd),
                eq(periodBegin), eq(searchString), any(), any());
    }

    @Test
    public void query_forConvoContactUri() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;
        mProvider.attachInfo(mContext, providerInfo);

        Uri convoContactUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_CONVOCONTACT)
                .build();

        mProvider.query(convoContactUri, /*projection=*/ null, /*selection=*/ null,
                /*selectionArgs=*/ null, /*sortOrder=*/ null);
        verify(mProvider).queryConvoContact(eq(ACCOUNT_ID), any(), any(), any(), any(), any());
    }

    @Test
    public void update_whenTableIsNull() {
        Uri uriWithoutTable = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .build();
        ContentValues values = new ContentValues();

        assertThrows(IllegalArgumentException.class,
                () -> mProvider.update(uriWithoutTable, values, /*selection=*/ null,
                        /*selectionArgs=*/ null));
    }

    @Test
    public void update_whenSelectionIsNotNull() {
        Uri uriWithTable = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_ACCOUNT)
                .build();
        ContentValues values = new ContentValues();

        String nonNullSelection = "id = 1234";

        assertThrows(IllegalArgumentException.class,
                () -> mProvider.update(uriWithTable, values, nonNullSelection,
                        /*selectionArgs=*/ null));
    }

    @Test
    public void update_forAccountUri_success() {
        Uri accountUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_ACCOUNT)
                .build();

        ContentValues values = new ContentValues();
        final int flagValue = 1;
        values.put(BluetoothMapContract.AccountColumns._ID, ACCOUNT_ID);
        values.put(BluetoothMapContract.AccountColumns.FLAG_EXPOSE, flagValue);

        mProvider.update(accountUri, values, /*selection=*/ null, /*selectionArgs=*/ null);
        verify(mProvider).updateAccount(ACCOUNT_ID, flagValue);
    }

    @Test
    public void update_forFolderUri() {
        Uri folderUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_FOLDER)
                .build();

        assertThat(mProvider.update(
                folderUri, /*values=*/ null, /*selection=*/ null, /*selectionArgs=*/ null))
                .isEqualTo(0);
    }

    @Test
    public void update_forConversationUri() {
        Uri folderUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_CONVERSATION)
                .build();

        assertThat(mProvider.update(
                folderUri, /*values=*/ null, /*selection=*/ null, /*selectionArgs=*/ null))
                .isEqualTo(0);
    }

    @Test
    public void update_forConvoContactUri() {
        Uri folderUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_CONVOCONTACT)
                .build();

        assertThat(mProvider.update(
                folderUri, /*values=*/ null, /*selection=*/ null, /*selectionArgs=*/ null))
                .isEqualTo(0);
    }

    @Test
    public void update_forMessageUri_success() {
        Uri accountUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_MESSAGE)
                .build();

        ContentValues values = new ContentValues();
        final boolean flagRead = true;
        values.put(BluetoothMapContract.MessageColumns._ID, MESSAGE_ID);
        values.put(BluetoothMapContract.MessageColumns.FOLDER_ID, Long.parseLong(FOLDER_ID));
        values.put(BluetoothMapContract.MessageColumns.FLAG_READ, flagRead);

        mProvider.update(accountUri, values, /*selection=*/ null, /*selectionArgs=*/ null);
        verify(mProvider).updateMessage(
                ACCOUNT_ID, Long.parseLong(MESSAGE_ID), Long.parseLong(FOLDER_ID), flagRead);
    }

    @Test
    public void call_whenMethodIsWrong() {
        String method = "some_random_method";
        assertThat(mProvider.call(method, /*arg=*/ null, /*extras=*/ null)).isNull();
    }

    @Test
    public void call_updateFolderMethod_whenExtrasDoesNotHaveAccountId() {
        Bundle extras = new Bundle();
        extras.putLong(BluetoothMapContract.EXTRA_UPDATE_FOLDER_ID, 12345);

        assertThat(mProvider.call(BluetoothMapContract.METHOD_UPDATE_FOLDER, /*arg=*/ null, extras))
                .isNull();
    }

    @Test
    public void call_updateFolderMethod_whenExtrasDoesNotHaveFolderId() {
        Bundle extras = new Bundle();
        extras.putLong(BluetoothMapContract.EXTRA_UPDATE_ACCOUNT_ID, 12345);

        assertThat(mProvider.call(BluetoothMapContract.METHOD_UPDATE_FOLDER, /*arg=*/ null, extras))
                .isNull();
    }

    @Test
    public void call_updateFolderMethod_success() {
        Bundle extras = new Bundle();
        extras.putLong(BluetoothMapContract.EXTRA_UPDATE_ACCOUNT_ID, Long.parseLong(ACCOUNT_ID));
        extras.putLong(BluetoothMapContract.EXTRA_UPDATE_FOLDER_ID, Long.parseLong(FOLDER_ID));

        mProvider.call(BluetoothMapContract.METHOD_UPDATE_FOLDER, /*arg=*/ null, extras);
        verify(mProvider).syncFolder(Long.parseLong(ACCOUNT_ID), Long.parseLong(FOLDER_ID));
    }

    @Test
    public void call_setOwnerStatusMethod_success() {
        final int presenceState = 1;
        final String presenceStatus = Integer.toString(3); // 0x0000 to 0x00FF
        final long lastActive = Instant.now().toEpochMilli();
        final int chatState = 5; // 0x0000 to 0x00FF
        final String convoId = Integer.toString(7);

        Bundle extras = new Bundle();
        extras.putInt(BluetoothMapContract.EXTRA_PRESENCE_STATE, presenceState);
        extras.putString(BluetoothMapContract.EXTRA_PRESENCE_STATUS, presenceStatus);
        extras.putLong(BluetoothMapContract.EXTRA_LAST_ACTIVE, lastActive);
        extras.putInt(BluetoothMapContract.EXTRA_CHAT_STATE, chatState);
        extras.putString(BluetoothMapContract.EXTRA_CONVERSATION_ID, convoId);

        mProvider.call(BluetoothMapContract.METHOD_SET_OWNER_STATUS, /*arg=*/ null, extras);
        verify(mProvider).setOwnerStatus(presenceState, presenceStatus, lastActive, chatState,
                convoId);
    }

    @Test
    public void call_setBluetoothStateMethod_success() {
        final boolean state = true;

        Bundle extras = new Bundle();
        extras.putBoolean(BluetoothMapContract.EXTRA_BLUETOOTH_STATE, state);

        mProvider.call(BluetoothMapContract.METHOD_SET_BLUETOOTH_STATE, /*arg=*/ null, extras);
        verify(mProvider).setBluetoothStatus(state);
    }

    @Test
    public void shutdown() {
        try {
            mProvider.shutdown();
        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }

    @Test
    public void getAccountId_whenNotEnoughPathSegments() {
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> BluetoothMapEmailProvider.getAccountId(uri));
    }

    @Test
    public void getAccountId_success() {
        Uri messageUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ACCOUNT_ID)
                .appendPath(BluetoothMapContract.TABLE_MESSAGE)
                .appendPath(MESSAGE_ID)
                .build();

        assertThat(BluetoothMapEmailProvider.getAccountId(messageUri)).isEqualTo(ACCOUNT_ID);
    }

    @Test
    public void onAccountChanged() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;

        ContentResolver resolver = mock(ContentResolver.class);
        Context context = spy(new ContextWrapper(mContext));
        doReturn(resolver).when(context).getContentResolver();
        mProvider.attachInfo(context, providerInfo);

        Uri expectedUri;

        expectedUri = BluetoothMapContract.buildAccountUri(AUTHORITY);
        mProvider.onAccountChanged(null);
        verify(resolver).notifyChange(expectedUri, null);

        Mockito.clearInvocations(resolver);
        String accountId = "32608910";
        expectedUri = BluetoothMapContract.buildAccountUriwithId(AUTHORITY, accountId);
        mProvider.onAccountChanged(accountId);
        verify(resolver).notifyChange(expectedUri, null);
    }

    @Test
    public void onContactChanged() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;

        ContentResolver resolver = mock(ContentResolver.class);
        Context context = spy(new ContextWrapper(mContext));
        doReturn(resolver).when(context).getContentResolver();
        mProvider.attachInfo(context, providerInfo);

        Uri expectedUri;

        expectedUri = BluetoothMapContract.buildConvoContactsUri(AUTHORITY);
        mProvider.onContactChanged(null,null);
        verify(resolver).notifyChange(expectedUri, null);

        Mockito.clearInvocations(resolver);
        String accountId = "32608910";
        expectedUri = BluetoothMapContract.buildConvoContactsUri(AUTHORITY, accountId);
        mProvider.onContactChanged(accountId, null);
        verify(resolver).notifyChange(expectedUri, null);

        Mockito.clearInvocations(resolver);
        String contactId = "23623";
        expectedUri = BluetoothMapContract.buildConvoContactsUriWithId(
                AUTHORITY, accountId, contactId);
        mProvider.onContactChanged(accountId, contactId);
        verify(resolver).notifyChange(expectedUri, null);
    }

    @Test
    public void onMessageChanged() {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        providerInfo.exported = true;
        providerInfo.writePermission = android.Manifest.permission.BLUETOOTH_MAP;

        ContentResolver resolver = mock(ContentResolver.class);
        Context context = spy(new ContextWrapper(mContext));
        doReturn(resolver).when(context).getContentResolver();
        mProvider.attachInfo(context, providerInfo);

        Uri expectedUri;

        expectedUri = BluetoothMapContract.buildMessageUri(AUTHORITY);
        mProvider.onMessageChanged(null, null);
        verify(resolver).notifyChange(expectedUri, null);

        Mockito.clearInvocations(resolver);
        String accountId = "32608910";
        expectedUri = BluetoothMapContract.buildMessageUri(AUTHORITY, accountId);
        mProvider.onMessageChanged(accountId, null);
        verify(resolver).notifyChange(expectedUri, null);

        Mockito.clearInvocations(resolver);
        String messageId = "23623";
        expectedUri = BluetoothMapContract.buildMessageUriWithId(
                AUTHORITY, accountId, messageId);
        mProvider.onMessageChanged(accountId, messageId);
        verify(resolver).notifyChange(expectedUri, null);
    }

    @Test
    public void createContentValues_throwsIAE_forUnknownDataType() {
        Set<Map.Entry<String, Object>> valueSet = new HashSet<>();
        Map<String, String> keyMap = new HashMap<>();

        String key = "test_key";
        Uri unknownTypeObject = Uri.parse("http://www.google.com");
        valueSet.add(new AbstractMap.SimpleEntry<String, Object>(key, unknownTypeObject));

        try {
            mProvider.createContentValues(valueSet, keyMap);
            assertWithMessage("IllegalArgumentException should be thrown.").fail();
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void createContentValues_success() {
        Map<String, String> keyMap = new HashMap<>();
        String key = "test_key";
        String convertedKey = "test_converted_key";
        keyMap.put(key, convertedKey);

        Object[] valuesToTest = new Object[] {
                null, true, (byte) 0x01, new byte[] {0x01, 0x02},
                0.01, 0.01f, 123, 12345L, (short) 10, "testString"
        };

        for (Object value : valuesToTest) {
            Log.d(TAG, "value=" + value);

            Set<Map.Entry<String, Object>> valueSet = new HashSet<>();
            valueSet.add(new AbstractMap.SimpleEntry<String, Object>(key, value));
            ContentValues contentValues = mProvider.createContentValues(valueSet, keyMap);

            assertThat(contentValues.get(convertedKey)).isEqualTo(value);
        }
    }

    public static class TestBluetoothMapIMProvider extends BluetoothMapIMProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        protected Uri getContentUri() {
            return null;
        }

        @Override
        protected int deleteMessage(String accountId, String messageId) {
            return 0;
        }

        @Override
        protected String insertMessage(String accountId, ContentValues values) {
            return null;
        }

        @Override
        protected Cursor queryAccount(String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return null;
        }

        @Override
        protected Cursor queryMessage(String accountId, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override
        protected Cursor queryConversation(String accountId, Long threadId, Boolean read,
                Long periodEnd, Long periodBegin, String searchString, String[] projection,
                String sortOrder) {
            return null;
        }

        @Override
        protected Cursor queryConvoContact(String accountId, Long contactId, String[] projection,
                String selection, String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override
        protected int updateAccount(String accountId, Integer flagExpose) {
            return 0;
        }

        @Override
        protected int updateMessage(String accountId, Long messageId, Long folderId,
                Boolean flagRead) {
            return 0;
        }

        @Override
        protected int syncFolder(long accountId, long folderId) {
            return 0;
        }

        @Override
        protected int setOwnerStatus(int presenceState, String presenceStatus, long lastActive,
                int chatState, String convoId) {
            return 0;
        }

        @Override
        protected int setBluetoothStatus(boolean bluetoothState) {
            return 0;
        }
    }
}
