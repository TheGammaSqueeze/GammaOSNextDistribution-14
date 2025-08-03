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

package com.android.bluetooth.pbap;

import static android.provider.ContactsContract.Data.CONTACT_ID;
import static android.provider.ContactsContract.Data.DATA1;
import static android.provider.ContactsContract.Data.MIMETYPE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.database.MatrixCursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.vcard.VCardConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapUtilsTest {

    @Mock
    Context mContext;

    @Mock
    Resources mResources;

    @Spy
    BluetoothMethodProxy mProxy;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mProxy);

        when(mContext.getResources()).thenReturn(mResources);
        clearStaticFields();
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
        clearStaticFields();
    }

    @Test
    public void checkFieldUpdates_whenSizeAreDifferent_returnsTrue() {
        ArrayList<String> oldFields = new ArrayList<>(List.of("0", "1", "2", "3"));
        ArrayList<String> newFields = new ArrayList<>(List.of("0", "1", "2", "3", "4"));

        assertThat(BluetoothPbapUtils.checkFieldUpdates(oldFields, newFields)).isTrue();
    }

    @Test
    public void checkFieldUpdates_newFieldsHasItsOwnFields_returnsTrue() {
        ArrayList<String> oldFields = new ArrayList<>(List.of("0", "1", "2", "3"));
        ArrayList<String> newFields = new ArrayList<>(List.of("0", "1", "2", "5"));

        assertThat(BluetoothPbapUtils.checkFieldUpdates(oldFields, newFields)).isTrue();
    }

    @Test
    public void checkFieldUpdates_onlyNewFieldsIsNull_returnsTrue() {
        ArrayList<String> oldFields = new ArrayList<>(List.of("0", "1", "2", "3"));
        ArrayList<String> newFields = null;

        assertThat(BluetoothPbapUtils.checkFieldUpdates(oldFields, newFields)).isTrue();
    }

    @Test
    public void checkFieldUpdates_onlyOldFieldsIsNull_returnsTrue() {
        ArrayList<String> oldFields = null;
        ArrayList<String> newFields = new ArrayList<>(List.of("0", "1", "2", "3"));

        assertThat(BluetoothPbapUtils.checkFieldUpdates(oldFields, newFields)).isTrue();
    }

    @Test
    public void checkFieldUpdates_whenBothAreNull_returnsTrue() {
        ArrayList<String> oldFields = null;
        ArrayList<String> newFields = null;

        assertThat(BluetoothPbapUtils.checkFieldUpdates(oldFields, newFields)).isFalse();
    }

    @Test
    public void createFilteredVCardComposer_returnsNewVCardComposer() {
        byte[] filter = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        int vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;

        assertThat(BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, filter))
                .isNotNull();
    }

    @Test
    public void rolloverCounters() {
        BluetoothPbapUtils.sPrimaryVersionCounter = -1;
        BluetoothPbapUtils.sSecondaryVersionCounter = -1;

        BluetoothPbapUtils.rolloverCounters();

        assertThat(BluetoothPbapUtils.sPrimaryVersionCounter).isEqualTo(0);
        assertThat(BluetoothPbapUtils.sSecondaryVersionCounter).isEqualTo(0);
    }

    @Test
    public void setContactFields() {
        String contactId = "1358923";

        BluetoothPbapUtils.setContactFields(BluetoothPbapUtils.TYPE_NAME, contactId,
                "test_name");
        BluetoothPbapUtils.setContactFields(BluetoothPbapUtils.TYPE_PHONE, contactId,
                "0123456789");
        BluetoothPbapUtils.setContactFields(BluetoothPbapUtils.TYPE_EMAIL, contactId,
                "android@android.com");
        BluetoothPbapUtils.setContactFields(BluetoothPbapUtils.TYPE_ADDRESS, contactId,
                "SomeAddress");

        assertThat(BluetoothPbapUtils.sContactDataset.get(contactId)).isNotNull();
    }

    @Test
    public void fetchAndSetContacts_whenCursorIsNull_returnsMinusOne() {
        doReturn(null).when(mProxy).contentResolverQuery(
                any(), any(), any(), any(), any(), any());
        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            assertThat(BluetoothPbapUtils.fetchAndSetContacts(
                    mContext, handler, null, null, null, true))
                    .isEqualTo(-1);
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void fetchAndSetContacts_whenIsLoadTrue_returnsContactsSetSize() {
        MatrixCursor cursor = new MatrixCursor(new String[] {CONTACT_ID, MIMETYPE, DATA1});
        cursor.addRow(new Object[] {"id1", Phone.CONTENT_ITEM_TYPE, "01234567"});
        cursor.addRow(new Object[] {"id1", Email.CONTENT_ITEM_TYPE, "android@android.com"});
        cursor.addRow(new Object[] {"id1", StructuredPostal.CONTENT_ITEM_TYPE, "01234"});
        cursor.addRow(new Object[] {"id2", StructuredName.CONTENT_ITEM_TYPE, "And Roid"});
        cursor.addRow(new Object[] {null, null, null});

        doReturn(cursor).when(mProxy).contentResolverQuery(
                any(), any(), any(), any(), any(), any());
        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            boolean isLoad = true;
            assertThat(BluetoothPbapUtils.fetchAndSetContacts(
                    mContext, handler, null, null, null, isLoad))
                    .isEqualTo(2); // Two IDs exist in sContactSet.
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void fetchAndSetContacts_whenIsLoadFalse_returnsContactsSetSize() {
        MatrixCursor cursor = new MatrixCursor(new String[] {CONTACT_ID, MIMETYPE, DATA1});
        cursor.addRow(new Object[] {"id1", Phone.CONTENT_ITEM_TYPE, "01234567"});
        cursor.addRow(new Object[] {"id1", Email.CONTENT_ITEM_TYPE, "android@android.com"});
        cursor.addRow(new Object[] {"id1", StructuredPostal.CONTENT_ITEM_TYPE, "01234"});
        cursor.addRow(new Object[] {"id2", StructuredName.CONTENT_ITEM_TYPE, "And Roid"});
        cursor.addRow(new Object[] {null, null, null});

        doReturn(cursor).when(mProxy).contentResolverQuery(
                any(), any(), any(), any(), any(), any());
        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            boolean isLoad = false;
            assertThat(BluetoothPbapUtils.fetchAndSetContacts(
                    mContext, handler, null, null, null, isLoad))
                    .isEqualTo(2); // Two IDs exist in sContactSet.
            assertThat(BluetoothPbapUtils.sTotalFields).isEqualTo(1);
            assertThat(BluetoothPbapUtils.sTotalSvcFields).isEqualTo(1);
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void updateSecondaryVersionCounter_whenCursorIsNull_shouldNotCrash() {
        doReturn(null).when(mProxy).contentResolverQuery(
                any(), any(), any(), any(), any(), any());
        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            BluetoothPbapUtils.updateSecondaryVersionCounter(mContext, handler);
        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void updateSecondaryVersionCounter_whenContactsAreAdded() {
        MatrixCursor contactCursor = new MatrixCursor(
                new String[] {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP});
        contactCursor.addRow(new Object[] {"id1", Calendar.getInstance().getTimeInMillis()});
        contactCursor.addRow(new Object[] {"id2", Calendar.getInstance().getTimeInMillis()});
        contactCursor.addRow(new Object[] {"id3", Calendar.getInstance().getTimeInMillis()});
        contactCursor.addRow(new Object[] {"id4", Calendar.getInstance().getTimeInMillis()});
        doReturn(contactCursor).when(mProxy).contentResolverQuery(
                any(), eq(Contacts.CONTENT_URI), any(), any(), any(), any());

        MatrixCursor dataCursor = new MatrixCursor(new String[] {CONTACT_ID, MIMETYPE, DATA1});
        dataCursor.addRow(new Object[] {"id1", Phone.CONTENT_ITEM_TYPE, "01234567"});
        dataCursor.addRow(new Object[] {"id1", Email.CONTENT_ITEM_TYPE, "android@android.com"});
        dataCursor.addRow(new Object[] {"id1", StructuredPostal.CONTENT_ITEM_TYPE, "01234"});
        dataCursor.addRow(new Object[] {"id2", StructuredName.CONTENT_ITEM_TYPE, "And Roid"});
        doReturn(dataCursor).when(mProxy).contentResolverQuery(
                any(), eq(Data.CONTENT_URI), any(), any(), any(), any());

        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            BluetoothPbapUtils.updateSecondaryVersionCounter(mContext, handler);

            assertThat(BluetoothPbapUtils.sTotalContacts).isEqualTo(4);
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void updateSecondaryVersionCounter_whenContactsAreDeleted() {
        MatrixCursor contactCursor = new MatrixCursor(
                new String[] {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP});
        doReturn(contactCursor).when(mProxy).contentResolverQuery(
                any(), eq(Contacts.CONTENT_URI), any(), any(), any(), any());

        MatrixCursor dataCursor = new MatrixCursor(new String[] {CONTACT_ID, MIMETYPE, DATA1});
        doReturn(dataCursor).when(mProxy).contentResolverQuery(
                any(), eq(Data.CONTENT_URI), any(), any(), any(), any());

        HandlerThread handlerThread = new HandlerThread("BluetoothPbapUtilsTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try {
            BluetoothPbapUtils.sTotalContacts = 2;
            BluetoothPbapUtils.sContactSet.add("id1");
            BluetoothPbapUtils.sContactSet.add("id2");

            BluetoothPbapUtils.updateSecondaryVersionCounter(mContext, handler);

            assertThat(BluetoothPbapUtils.sTotalContacts).isEqualTo(0);
        } finally {
            handlerThread.quit();
        }
    }

    @Test
    public void updateSecondaryVersionCounter_whenContactsAreUpdated() {
        MatrixCursor contactCursor = new MatrixCursor(
                new String[] {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP});
        contactCursor.addRow(new Object[] {"id1", Calendar.getInstance().getTimeInMillis()});
        doReturn(contactCursor).when(mProxy).contentResolverQuery(
                any(), eq(Contacts.CONTENT_URI), any(), any(), any(), any());

        MatrixCursor dataCursor = new MatrixCursor(new String[] {CONTACT_ID, MIMETYPE, DATA1});
        dataCursor.addRow(new Object[] {"id1", Phone.CONTENT_ITEM_TYPE, "01234567"});
        dataCursor.addRow(new Object[] {"id1", Email.CONTENT_ITEM_TYPE, "android@android.com"});
        dataCursor.addRow(new Object[] {"id1", StructuredPostal.CONTENT_ITEM_TYPE, "01234"});
        dataCursor.addRow(new Object[] {"id1", StructuredName.CONTENT_ITEM_TYPE, "And Roid"});
        doReturn(dataCursor).when(mProxy).contentResolverQuery(
                any(), eq(Data.CONTENT_URI), any(), any(), any(), any());
        assertThat(BluetoothPbapUtils.sSecondaryVersionCounter).isEqualTo(0);

        BluetoothPbapUtils.sTotalContacts = 1;
        BluetoothPbapUtils.setContactFields(BluetoothPbapUtils.TYPE_NAME, "id1",
                "test_previous_name_before_update");

        BluetoothPbapUtils.updateSecondaryVersionCounter(mContext, null);

        assertThat(BluetoothPbapUtils.sSecondaryVersionCounter).isEqualTo(1);
    }

    private static void clearStaticFields() {
        BluetoothPbapUtils.sPrimaryVersionCounter = 0;
        BluetoothPbapUtils.sSecondaryVersionCounter = 0;
        BluetoothPbapUtils.sContactSet.clear();
        BluetoothPbapUtils.sContactDataset.clear();
        BluetoothPbapUtils.sTotalContacts = 0;
        BluetoothPbapUtils.sTotalFields = 0;
        BluetoothPbapUtils.sTotalSvcFields = 0;
        BluetoothPbapUtils.sContactsLastUpdated = 0;
    }
}
