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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapVcardManagerTest {

    private static final String TAG = BluetoothPbapVcardManagerTest.class.getSimpleName();

    @Spy
    BluetoothMethodProxy mPbapMethodProxy = BluetoothMethodProxy.getInstance();

    Context mContext;
    BluetoothPbapVcardManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mPbapMethodProxy);
        mContext = InstrumentationRegistry.getTargetContext();
        mManager = new BluetoothPbapVcardManager(mContext);
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testGetOwnerPhoneNumberVcard_whenUseProfileForOwnerVcard() {
        BluetoothPbapConfig.setIncludePhotosInVcard(true);

        assertThat(mManager.getOwnerPhoneNumberVcard(/*vcardType21=*/true, /*filter=*/null))
                .isNotNull();
    }

    @Test
    public void testGetOwnerPhoneNumberVcard_whenNotUseProfileForOwnerVcard() {
        BluetoothPbapConfig.setIncludePhotosInVcard(false);

        assertThat(mManager.getOwnerPhoneNumberVcard(/*vcardType21=*/true, /*filter=*/null))
                .isNotNull();
    }

    @Test
    public void testGetPhonebookSize_whenTypeIsPhonebook() {
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        // 5 distinct contact IDs.
        final List<Integer> contactIdsWithDuplicates = Arrays.asList(0, 1, 1, 2, 2, 3, 3, 4, 4);

        // Implement Cursor iteration
        final int size = contactIdsWithDuplicates.size();
        AtomicInteger currentPosition = new AtomicInteger(0);
        when(cursor.moveToPosition(anyInt())).then((Answer<Boolean>) i -> {
            int position = i.getArgument(0);
            currentPosition.set(position);
            return true;
        });
        when(cursor.moveToNext()).then((Answer<Boolean>) i -> {
            int pos = currentPosition.addAndGet(1);
            return pos < size;
        });
        when(cursor.getLong(anyInt())).then((Answer<Long>) i -> {
            return (long) contactIdsWithDuplicates.get(currentPosition.get());
        });

        // 5 distinct contact IDs + self (which is only included for phonebook)
        final int expectedSize = 5 + 1;

        assertThat(mManager.getPhonebookSize(BluetoothPbapObexServer.ContentType.PHONEBOOK, null))
                .isEqualTo(expectedSize);
    }

    @Test
    public void testGetPhonebookSize_whenTypeIsFavorites() {
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        // 5 distinct contact IDs.
        final List<Integer> contactIdsWithDuplicates = Arrays.asList(
                0, 1, 1, 2,     // starred
                2, 3, 3, 4, 4   // not starred
        );

        // Implement Cursor iteration
        final int starredSize = 4;
        AtomicInteger currentPosition = new AtomicInteger(0);
        when(cursor.moveToPosition(anyInt())).then((Answer<Boolean>) i -> {
            int position = i.getArgument(0);
            currentPosition.set(position);
            return true;
        });
        when(cursor.moveToNext()).then((Answer<Boolean>) i -> {
            int pos = currentPosition.addAndGet(1);
            return pos < starredSize;
        });
        when(cursor.getLong(anyInt())).then((Answer<Long>) i -> {
            return (long) contactIdsWithDuplicates.get(currentPosition.get());
        });

        // Among 4 starred contact Ids, there are 3 distinct contact IDs
        final int expectedSize = 3;

        assertThat(mManager.getPhonebookSize(BluetoothPbapObexServer.ContentType.FAVORITES, null))
                .isEqualTo(expectedSize);
    }

    @Test
    public void testGetPhonebookSize_whenTypeIsSimPhonebook() {
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());
        final int expectedSize = 10;
        when(cursor.getCount()).thenReturn(expectedSize);
        BluetoothPbapSimVcardManager simVcardManager = mock(BluetoothPbapSimVcardManager.class);

        assertThat(mManager.getPhonebookSize(BluetoothPbapObexServer.ContentType.SIM_PHONEBOOK,
                simVcardManager)).isEqualTo(expectedSize);
        verify(simVcardManager).getSIMContactsSize();
    }

    @Test
    public void testGetPhonebookSize_whenTypeIsHistory() {
        final int historySize = 10;
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(historySize);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        assertThat(mManager.getPhonebookSize(
                BluetoothPbapObexServer.ContentType.INCOMING_CALL_HISTORY, null))
                .isEqualTo(historySize);
    }

    @Test
    public void testLoadCallHistoryList() {
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        List<String> nameList = Arrays.asList("A", "B", "", "");
        List<String> numberList = Arrays.asList("0000", "1111", "2222", "3333");
        List<Integer> presentationAllowedList = Arrays.asList(
                CallLog.Calls.PRESENTATION_ALLOWED,
                CallLog.Calls.PRESENTATION_ALLOWED,
                CallLog.Calls.PRESENTATION_ALLOWED,
                CallLog.Calls.PRESENTATION_UNKNOWN); // The number "3333" should not be shown.

        List<String> expectedResult = Arrays.asList(
                "A", "B", "2222", mContext.getString(R.string.unknownNumber));

        // Implement Cursor iteration
        final int size = nameList.size();
        AtomicInteger currentPosition = new AtomicInteger(0);
        when(cursor.moveToFirst()).then((Answer<Boolean>) i -> {
            currentPosition.set(0);
            return true;
        });
        when(cursor.isAfterLast()).then((Answer<Boolean>) i -> {
            return currentPosition.get() >= size;
        });
        when(cursor.moveToNext()).then((Answer<Boolean>) i -> {
            int pos = currentPosition.addAndGet(1);
            return pos < size;
        });
        when(cursor.getString(BluetoothPbapVcardManager.CALLS_NAME_COLUMN_INDEX))
                .then((Answer<String>) i -> {
            return nameList.get(currentPosition.get());
        });
        when(cursor.getString(BluetoothPbapVcardManager.CALLS_NUMBER_COLUMN_INDEX))
                .then((Answer<String>) i -> {
            return numberList.get(currentPosition.get());
        });
        when(cursor.getInt(BluetoothPbapVcardManager.CALLS_NUMBER_PRESENTATION_COLUMN_INDEX))
                .then((Answer<Integer>) i -> {
            return presentationAllowedList.get(currentPosition.get());
        });

        assertThat(mManager.loadCallHistoryList(
                BluetoothPbapObexServer.ContentType.INCOMING_CALL_HISTORY))
                .isEqualTo(expectedResult);
    }

    @Test
    public void testGetPhonebookNameList() {
        final String localPhoneName = "test_local_phone_name";
        BluetoothPbapService.setLocalPhoneName(localPhoneName);

        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        List<String> nameList = Arrays.asList("A", "B", "C", "");
        List<Integer> contactIdList = Arrays.asList(0, 1, 2, 3);

        List<String> expectedResult = Arrays.asList(
                localPhoneName,
                "A,0",
                "B,1",
                "C,2",
                mContext.getString(android.R.string.unknownName) + ",3");

        // Implement Cursor iteration
        final int size = nameList.size();
        AtomicInteger currentPosition = new AtomicInteger(0);
        when(cursor.moveToPosition(anyInt())).then((Answer<Boolean>) i -> {
            int position = i.getArgument(0);
            currentPosition.set(position);
            return true;
        });
        when(cursor.moveToNext()).then((Answer<Boolean>) i -> {
            int pos = currentPosition.addAndGet(1);
            return pos < size;
        });

        final int contactIdColumn = 0;
        final int nameColumn = 1;
        when(cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)).thenReturn(contactIdColumn);
        when(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)).thenReturn(nameColumn);

        when(cursor.getLong(contactIdColumn)).then((Answer<Long>) i -> {
            return (long) contactIdList.get(currentPosition.get());
        });
        when(cursor.getString(nameColumn)).then((Answer<String>) i -> {
            return nameList.get(currentPosition.get());
        });

        assertThat(mManager.getPhonebookNameList(BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL))
                .isEqualTo(expectedResult);
    }

    @Test
    public void testGetContactNamesByNumber_whenNumberIsNull() {
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        List<String> nameList = Arrays.asList("A", "B", "C", "");
        List<Integer> contactIdList = Arrays.asList(0, 1, 2, 3);

        List<String> expectedResult = Arrays.asList(
                "A,0",
                "B,1",
                "C,2",
                mContext.getString(android.R.string.unknownName) + ",3");

        // Implement Cursor iteration
        final int size = nameList.size();
        AtomicInteger currentPosition = new AtomicInteger(0);
        when(cursor.moveToPosition(anyInt())).then((Answer<Boolean>) i -> {
            int position = i.getArgument(0);
            currentPosition.set(position);
            return true;
        });
        when(cursor.moveToNext()).then((Answer<Boolean>) i -> {
            int pos = currentPosition.addAndGet(1);
            return pos < size;
        });

        final int contactIdColumn = 0;
        final int nameColumn = 1;
        when(cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)).thenReturn(contactIdColumn);
        when(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)).thenReturn(nameColumn);

        when(cursor.getLong(contactIdColumn)).then((Answer<Long>) i -> {
            return (long) contactIdList.get(currentPosition.get());
        });
        when(cursor.getString(nameColumn)).then((Answer<String>) i -> {
            return nameList.get(currentPosition.get());
        });

        assertThat(mManager.getContactNamesByNumber(null))
                .isEqualTo(expectedResult);
    }

    @Test
    public void testStripTelephoneNumber() {
        final String separator = System.getProperty("line.separator");
        final String vCard = "SomeRandomLine" + separator + "TEL:+1-(588)-328-382" + separator;
        final String expectedResult = "SomeRandomLine" + separator + "TEL:+1588328382" + separator;

        assertThat(mManager.stripTelephoneNumber(vCard)).isEqualTo(expectedResult);
    }

    @Test
    public void getNameFromVCard() {
        final String separator = System.getProperty("line.separator");
        String vCard = "N:Test Name" + separator
                + "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator;

        assertThat(BluetoothPbapVcardManager.getNameFromVCard(vCard)).isEqualTo("Test Name");
    }
}
