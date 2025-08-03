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

package com.android.server.appsearch.contactsindexer;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.provider.ContactsContract;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ContactsProviderUtilTest extends FakeContactsProviderTestBase {
    @Test
    public void testGetUpdatedContactIds_getAll() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            expectedIds.add(String.valueOf(i));
            // Sleep for 2ms to ensure that each contact gets a distinct update timestamp
            Thread.sleep(2);
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ 0, ContactsProviderUtil.UPDATE_LIMIT_NONE, ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                mFakeContactsProvider.getMostRecentContactUpdateTimestampMillis());
        // TODO(b/228239000): make this assertion based on last-updated-ts instead of contact ID
        assertThat(ids).isEqualTo(expectedIds);
    }

    @Test
    public void testGetUpdatedContactIds_getNone() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ mFakeContactsProvider.getMostRecentContactUpdateTimestampMillis(),
                ContactsProviderUtil.UPDATE_LIMIT_NONE, ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                mFakeContactsProvider.getMostRecentContactUpdateTimestampMillis());
        assertThat(ids).isEmpty();
    }

    @Test
    public void testGetUpdatedContactIds() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        long firstUpdateTimestamp =
                mFakeContactsProvider.getMostRecentContactUpdateTimestampMillis();
        // Wait an additional 1ms here to avoid flaky tests. Otherwise, the first few contacts
        // inserted below might share the same timestamp as firstUpdateTimestamp, and when querying
        // CP2, we use "> timeFilter", and it caused test flakiness.
        Thread.sleep(/* millis= */ 1);
        List<String> expectedIds = new ArrayList<>();
        for (int i = 50; i < 100; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ firstUpdateTimestamp, ContactsProviderUtil.UPDATE_LIMIT_NONE,
                ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                mFakeContactsProvider.getMostRecentContactUpdateTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }

    @Test
    public void testGetDeletedContactIds_getAll() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        List<String> expectedIds = new ArrayList<>();
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ 0, ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                mFakeContactsProvider.getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }

    @Test
    public void testGetDeletedContactIds_getNone() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ mFakeContactsProvider.getMostRecentDeletedContactTimestampMillis(),
                ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                mFakeContactsProvider.getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEmpty();
    }

    @Test
    public void testGetDeletedContactIds() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }
        long firstDeleteTimestamp =
                mFakeContactsProvider.getMostRecentDeletedContactTimestampMillis();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 7; i < 50; i += 7) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ firstDeleteTimestamp, ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                mFakeContactsProvider.getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }
}
