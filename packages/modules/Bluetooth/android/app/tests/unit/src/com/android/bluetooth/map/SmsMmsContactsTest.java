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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import android.content.ContentResolver;
import android.database.MatrixCursor;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SmsMmsContactsTest {
    private static final long TEST_ID = 1;
    private static final String TEST_NAME = "test_name";
    private static final String TEST_PHONE_NUMBER = "111-1111-1111";
    private static final String TEST_PHONE = "test_phone";
    private static final String TEST_CONTACT_NAME_FILTER = "test_contact_name_filter";

    @Mock
    private ContentResolver mResolver;
    @Spy
    private BluetoothMethodProxy mMapMethodProxy = BluetoothMethodProxy.getInstance();

    private SmsMmsContacts mContacts;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mMapMethodProxy);
        mContacts = new SmsMmsContacts();
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void getPhoneNumberUncached_withNonEmptyCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"COL_ARRR_ID", "COL_ADDR_ADDR"});
        cursor.addRow(new Object[]{null, TEST_PHONE_NUMBER});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        assertThat(SmsMmsContacts.getPhoneNumberUncached(mResolver, TEST_ID)).isEqualTo(
                TEST_PHONE_NUMBER);
    }

    @Test
    public void getPhoneNumberUncached_withEmptyCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        assertThat(SmsMmsContacts.getPhoneNumberUncached(mResolver, TEST_ID)).isNull();
    }

    @Test
    public void fillPhoneCache() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"COL_ADDR_ID", "COL_ADDR_ADDR"});
        cursor.addRow(new Object[]{TEST_ID, TEST_PHONE_NUMBER});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        mContacts.fillPhoneCache(mResolver);

        assertThat(mContacts.getPhoneNumber(mResolver, TEST_ID)).isEqualTo(TEST_PHONE_NUMBER);
    }

    @Test
    public void fillPhoneCache_withNonNullPhoneNumbers() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"COL_ADDR_ID", "COL_ADDR_ADDR"});
        cursor.addRow(new Object[]{TEST_ID, TEST_PHONE_NUMBER});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        mContacts.fillPhoneCache(mResolver);
        assertThat(mContacts.getPhoneNumber(mResolver, TEST_ID)).isEqualTo(TEST_PHONE_NUMBER);
        doReturn(null).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());
        mContacts.fillPhoneCache(mResolver);

        assertThat(mContacts.getPhoneNumber(mResolver, TEST_ID)).isNull();
    }

    @Test
    public void clearCache() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"COL_ADDR_ID", "COL_ADDR_ADDR"});
        cursor.addRow(new Object[]{TEST_ID, TEST_PHONE_NUMBER});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());
        MapContact contact = MapContact.create(TEST_ID, TEST_PHONE);

        mContacts.mNames.put(TEST_PHONE, contact);
        mContacts.fillPhoneCache(mResolver);
        assertThat(mContacts.getPhoneNumber(mResolver, TEST_ID)).isEqualTo(TEST_PHONE_NUMBER);
        mContacts.clearCache();

        doReturn(null).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());
        assertThat(mContacts.mNames).isEmpty();
        assertThat(mContacts.getPhoneNumber(mResolver, TEST_ID)).isEqualTo(null);
    }

    @Test
    public void getContactNameFromPhone_withNonNullCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"COL_CONTACT_ID", "COL_CONTACT_NAME"});
        cursor.addRow(new Object[]{TEST_ID, TEST_NAME});
        doReturn(cursor).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        MapContact expected = MapContact.create(TEST_ID, TEST_NAME);
        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver,
                TEST_CONTACT_NAME_FILTER).toString()).isEqualTo(expected.toString());
    }

    @Test
    public void getContactNameFromPhone_withNullCursor() {
        doReturn(null).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver,
                TEST_CONTACT_NAME_FILTER)).isNull();
    }

    @Test
    public void getContactNameFromPhone_withNoParameterForContactNameFilter() {
        doReturn(null).when(mMapMethodProxy).contentResolverQuery(any(), any(), any(), any(),
                any(), any());

        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver)).isNull();
    }

    @Test
    public void getContactNameFromPhone_withNonNullContact_andZeroId() {
        long zeroId = 0;
        MapContact contact = MapContact.create(zeroId, TEST_PHONE);
        mContacts.mNames.put(TEST_PHONE, contact);

        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver,
                TEST_CONTACT_NAME_FILTER)).isNull();
    }

    @Test
    public void getContactNameFromPhone_withNonNullContact_andNullFilter() {
        MapContact contact = MapContact.create(TEST_ID, TEST_PHONE);
        mContacts.mNames.put(TEST_PHONE, contact);

        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver, null)).isEqualTo(
                contact);
    }

    @Test
    public void getContactNameFromPhone_withNonNullContact_andNonMatchingFilter() {
        MapContact contact = MapContact.create(TEST_ID, TEST_PHONE);
        mContacts.mNames.put(TEST_PHONE, contact);
        String nonMatchingFilter = "non_matching_filter";

        assertThat(mContacts.getContactNameFromPhone(TEST_PHONE, mResolver,
                nonMatchingFilter)).isNull();
    }
}
