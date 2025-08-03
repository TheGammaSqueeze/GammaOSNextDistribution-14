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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapSimVcardManagerTest {

    private static final String TAG = BluetoothPbapSimVcardManagerTest.class.getSimpleName();

    @Spy
    BluetoothMethodProxy mPbapMethodProxy = BluetoothMethodProxy.getInstance();

    Context mContext;
    BluetoothPbapSimVcardManager mManager;

    private static final Uri WRONG_URI = Uri.parse("content://some/wrong/uri");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mPbapMethodProxy);
        mContext =  InstrumentationRegistry.getTargetContext();
        mManager = new BluetoothPbapSimVcardManager(mContext);
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testInit_whenUriIsUnsupported() {
        assertThat(mManager.init(WRONG_URI, null, null, null))
                .isFalse();
        assertThat(mManager.getErrorReason())
                .isEqualTo(BluetoothPbapSimVcardManager.FAILURE_REASON_UNSUPPORTED_URI);
    }

    @Test
    public void testInit_whenCursorIsNull() {
        doReturn(null).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        assertThat(mManager.init(BluetoothPbapSimVcardManager.SIM_URI, null, null, null))
                .isFalse();
        assertThat(mManager.getErrorReason())
                .isEqualTo(BluetoothPbapSimVcardManager.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO);
    }

    @Test
    public void testInit_whenCursorHasNoEntry() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(0);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        assertThat(mManager.init(BluetoothPbapSimVcardManager.SIM_URI, null, null, null))
                .isFalse();
        verify(cursor).close();
        assertThat(mManager.getErrorReason())
                .isEqualTo(BluetoothPbapSimVcardManager.FAILURE_REASON_NO_ENTRY);
    }

    @Test
    public void testInit_success() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(1);
        when(cursor.moveToFirst()).thenReturn(true);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        assertThat(mManager.init(BluetoothPbapSimVcardManager.SIM_URI, null, null, null))
                .isTrue();
        assertThat(mManager.getErrorReason()).isEqualTo(BluetoothPbapSimVcardManager.NO_ERROR);
    }

    @Test
    public void testCreateOneEntry_whenNotInitialized() {
        assertThat(mManager.createOneEntry(true)).isNull();
        assertThat(mManager.getErrorReason())
                .isEqualTo(BluetoothPbapSimVcardManager.FAILURE_REASON_NOT_INITIALIZED);
    }

    @Test
    public void testCreateOneEntry_success() {
        Cursor cursor = initManager();

        assertThat(mManager.createOneEntry(true)).isNotNull();
        assertThat(mManager.createOneEntry(false)).isNotNull();
        verify(cursor, times(2)).moveToNext();
    }

    @Test
    public void testTerminate() {
        Cursor cursor = initManager();
        mManager.terminate();

        verify(cursor).close();
    }

    @Test
    public void testGetCount_beforeInit() {
        assertThat(mManager.getCount()).isEqualTo(0);
    }

    @Test
    public void testGetCount_success() {
        final int count = 5;
        Cursor cursor = initManager();
        when(cursor.getCount()).thenReturn(count);

        assertThat(mManager.getCount()).isEqualTo(count);
    }

    @Test
    public void testIsAfterLast_beforeInit() {
        assertThat(mManager.isAfterLast()).isFalse();
    }

    @Test
    public void testIsAfterLast_success() {
        final boolean isAfterLast = true;
        Cursor cursor = initManager();
        when(cursor.isAfterLast()).thenReturn(isAfterLast);

        assertThat(mManager.isAfterLast()).isEqualTo(isAfterLast);
    }

    @Test
    public void testMoveToPosition_beforeInit() {
        try {
            mManager.moveToPosition(0, /*sortByAlphabet=*/ true);
            mManager.moveToPosition(0, /*sortByAlphabet=*/ false);
        } catch (Exception e) {
            assertWithMessage("This should not throw exception").fail();
        }
    }

    @Test
    public void testMoveToPosition_byAlphabeticalOrder_success() {
        Cursor cursor = initManager();
        List<String> nameList = Arrays.asList("D", "C", "A", "B");

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
        when(cursor.getString(anyInt())).then((Answer<String>) i -> {
            return nameList.get(currentPosition.get());
        });
        // Find first one in alphabetical order ("A")
        int position = 0;
        mManager.moveToPosition(position, /*sortByAlphabet=*/ true);

        assertThat(currentPosition.get()).isEqualTo(2);
    }

    @Test
    public void testMoveToPosition_notByAlphabeticalOrder_success() {
        Cursor cursor = initManager();
        int position = 3;

        mManager.moveToPosition(position, /*sortByAlphabet=*/ false);

        verify(cursor).moveToPosition(position);
    }

    @Test
    public void testGetSIMContactsSize() {
        final int count = 10;
        Cursor cursor = initManager();
        when(cursor.getCount()).thenReturn(count);

        assertThat(mManager.getSIMContactsSize()).isEqualTo(count);
        verify(cursor).close();
    }

    @Test
    public void testGetSIMPhonebookNameList_orderByIndexed() {
        String prevLocalPhoneName = BluetoothPbapService.getLocalPhoneName();
        try {
            final String localPhoneName = "test_local_phone_name";
            BluetoothPbapService.setLocalPhoneName(localPhoneName);
            Cursor cursor = initManager();
            List<String> nameList = Arrays.asList("D", "C", "A", "B");

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
            when(cursor.getString(anyInt())).then((Answer<String>) i -> {
                return nameList.get(currentPosition.get());
            });

            ArrayList<String> result = mManager.getSIMPhonebookNameList(
                    BluetoothPbapObexServer.ORDER_BY_INDEXED);

            ArrayList<String> expectedResult = new ArrayList<>();
            expectedResult.add(localPhoneName);
            expectedResult.addAll(nameList);

            assertThat(result).isEqualTo(expectedResult);
        } finally {
            BluetoothPbapService.setLocalPhoneName(prevLocalPhoneName);
        }
    }

    @Test
    public void testGetSIMPhonebookNameList_orderByAlphabet() {
        String prevLocalPhoneName = BluetoothPbapService.getLocalPhoneName();
        try {
            final String localPhoneName = "test_local_phone_name";
            BluetoothPbapService.setLocalPhoneName(localPhoneName);
            Cursor cursor = initManager();
            List<String> nameList = Arrays.asList("D", "C", "A", "B");

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
            when(cursor.getString(anyInt())).then((Answer<String>) i -> {
                return nameList.get(currentPosition.get());
            });

            List<String> result = mManager.getSIMPhonebookNameList(
                    BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL);

            List<String> expectedResult = new ArrayList<>(nameList);
            Collections.sort(expectedResult, String.CASE_INSENSITIVE_ORDER);
            expectedResult.add(0, localPhoneName);

            assertThat(result).isEqualTo(expectedResult);
        } finally {
            BluetoothPbapService.setLocalPhoneName(prevLocalPhoneName);
        }
    }

    @Test
    public void testGetSIMContactNamesByNumber() {
        Cursor cursor = initManager();
        List<String> nameList = Arrays.asList("A", "B", "C", "D");
        List<String> numberList = Arrays.asList(
                "000123456789",
                "123456789000",
                "000111111000",
                "123456789123");
        final String query = "000";

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
        when(cursor.getString(BluetoothPbapSimVcardManager.NAME_COLUMN_INDEX)).then(
                (Answer<String>) i -> {
                    return nameList.get(currentPosition.get());
                });
        when(cursor.getString(BluetoothPbapSimVcardManager.NUMBER_COLUMN_INDEX)).then(
                (Answer<String>) i -> {
                    return numberList.get(currentPosition.get());
                });

        // Find the names whose number ends with 'query', and then
        // also the names whose number starts with 'query'.
        List<String> result = mManager.getSIMContactNamesByNumber(query);
        List<String> expectedResult = Arrays.asList("B", "C", "A");
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void testComposeAndSendSIMPhonebookVcards_whenStartPointIsNotCorrect() {
        Operation operation = mock(Operation.class);
        final int incorrectStartPoint = 0; // Should be greater than zero

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookVcards(mContext,
                operation, incorrectStartPoint, 0, /*vcardType21=*/false, /*ownerVCard=*/null);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testComposeAndSendSIMPhonebookVcards_whenEndPointIsLessThanStartpoint() {
        Operation operation = mock(Operation.class);
        final int startPoint = 1;
        final int endPoint = 0; // Should be equal or greater than startPoint

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookVcards(mContext,
                operation, startPoint, endPoint, /*vcardType21=*/false, /*ownerVCard=*/null);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testComposeAndSendSIMPhonebookVcards_whenCursorInitFailed() {
        Operation operation = mock(Operation.class);
        final int startPoint = 1;
        final int endPoint = 1;
        doReturn(null).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookVcards(mContext,
                operation, startPoint, endPoint, /*vcardType21=*/false, /*ownerVCard=*/null);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testComposeAndSendSIMPhonebookVcards_success() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(10);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.isAfterLast()).thenReturn(false);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        final int startPoint = 1;
        final int endPoint = 1;
        final String testOwnerVcard = "owner_v_card";

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookVcards(mContext,
                operation, startPoint, endPoint, /*vcardType21=*/false, testOwnerVcard);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testComposeAndSendSIMPhonebookOneVcard_whenOffsetIsNotCorrect() {
        Operation operation = mock(Operation.class);
        final int offset = 0; // Should be greater than zero

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookOneVcard(mContext,
                operation, offset, /*vcardType21=*/false, /*ownerVCard=*/null,
                BluetoothPbapObexServer.ORDER_BY_INDEXED);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testComposeAndSendSIMPhonebookOneVcard_success() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(10);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.isAfterLast()).thenReturn(false);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        final int offset = 1;
        final String testOwnerVcard = "owner_v_card";

        int result = BluetoothPbapSimVcardManager.composeAndSendSIMPhonebookOneVcard(mContext,
                operation, offset, /*vcardType21=*/false, testOwnerVcard,
                BluetoothPbapObexServer.ORDER_BY_INDEXED);
        assertThat(result).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    private Cursor initManager() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(10);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.isAfterLast()).thenReturn(false);
        doReturn(cursor).when(mPbapMethodProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());
        mManager.init(BluetoothPbapSimVcardManager.SIM_URI, null, null, null);

        return cursor;
    }
}
