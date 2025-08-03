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

import static com.android.bluetooth.pbap.BluetoothPbapCallLogComposer.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO;
import static com.android.bluetooth.pbap.BluetoothPbapCallLogComposer.FAILURE_REASON_NOT_INITIALIZED;
import static com.android.bluetooth.pbap.BluetoothPbapCallLogComposer.FAILURE_REASON_NO_ENTRY;
import static com.android.bluetooth.pbap.BluetoothPbapCallLogComposer.FAILURE_REASON_UNSUPPORTED_URI;
import static com.android.bluetooth.pbap.BluetoothPbapCallLogComposer.NO_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;

import androidx.test.InstrumentationRegistry;
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
public class BluetoothPbapCallLogComposerTest {

    private static final Uri CALL_LOG_URI = CallLog.Calls.CONTENT_URI;

    // Note: These variables are used intentionally put as null,
    //       since the values are not at all used inside BluetoothPbapCallLogComposer.init().
    private static final String SELECTION = null;
    private static final String[] SELECTION_ARGS = null;
    private static final String SORT_ORDER = null;

    private BluetoothPbapCallLogComposer mComposer;

    @Spy
    BluetoothMethodProxy mPbapCallProxy = BluetoothMethodProxy.getInstance();

    @Mock
    Cursor mMockCursor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mPbapCallProxy);

        doReturn(mMockCursor).when(mPbapCallProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());
        final int validRowCount = 5;
        when(mMockCursor.getCount()).thenReturn(validRowCount);
        when(mMockCursor.moveToFirst()).thenReturn(true);

        mComposer = new BluetoothPbapCallLogComposer(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testInit_success() {
        assertThat(mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER))
                .isTrue();
        assertThat(mComposer.getErrorReason()).isEqualTo(NO_ERROR);
    }

    @Test
    public void testInit_failWhenUriIsNotSupported() {
        final Uri uriOtherThanCallLog = Uri.parse("content://not/a/call/log/uri");
        assertThat(uriOtherThanCallLog).isNotEqualTo(CALL_LOG_URI);

        assertThat(mComposer.init(uriOtherThanCallLog, SELECTION, SELECTION_ARGS, SORT_ORDER))
                .isFalse();
        assertThat(mComposer.getErrorReason()).isEqualTo(FAILURE_REASON_UNSUPPORTED_URI);
    }

    @Test
    public void testInit_failWhenCursorIsNull() {
        doReturn(null).when(mPbapCallProxy)
                .contentResolverQuery(any(), any(), any(), any(), any(), any());

        assertThat(mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER))
                .isFalse();
        assertThat(mComposer.getErrorReason())
                .isEqualTo(FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO);
    }

    @Test
    public void testInit_failWhenCursorRowCountIsZero() {
        when(mMockCursor.getCount()).thenReturn(0);

        assertThat(mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER))
                .isFalse();
        assertThat(mComposer.getErrorReason()).isEqualTo(FAILURE_REASON_NO_ENTRY);
        verify(mMockCursor).close();
    }

    @Test
    public void testInit_failWhenCursorMoveToFirstFails() {
        when(mMockCursor.moveToFirst()).thenReturn(false);

        assertThat(mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER))
                .isFalse();
        assertThat(mComposer.getErrorReason()).isEqualTo(FAILURE_REASON_NO_ENTRY);
        verify(mMockCursor).close();
    }

    @Test
    public void testCreateOneEntry_success() {
        mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER);

        assertThat(mComposer.createOneEntry(true)).isNotEmpty();
        assertThat(mComposer.getErrorReason()).isEqualTo(NO_ERROR);
        verify(mMockCursor).moveToNext();
    }

    @Test
    public void testCreateOneEntry_failWhenNotInitialized() {
        assertThat(mComposer.createOneEntry(true)).isNull();
        assertThat(mComposer.getErrorReason()).isEqualTo(FAILURE_REASON_NOT_INITIALIZED);
    }

    @Test
    public void testComposeVCardForPhoneOwnNumber() {
        final int testPhoneType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
        final String testPhoneName = "test_phone_name";
        final String testPhoneNumber = "0123456789";

        assertThat(BluetoothPbapCallLogComposer.composeVCardForPhoneOwnNumber(
                testPhoneType, testPhoneName, testPhoneNumber, /*vcardVer21=*/ true))
                .contains(testPhoneNumber);
    }

    @Test
    public void testTerminate() {
        mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER);

        mComposer.terminate();
        verify(mMockCursor).close();
    }

    @Test
    public void testFinalize() {
        mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER);

        mComposer.finalize();
        verify(mMockCursor).close();
    }

    @Test
    public void testGetCount_success() {
        mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER);
        final int cursorRowCount = 15;
        when(mMockCursor.getCount()).thenReturn(cursorRowCount);

        assertThat(mComposer.getCount()).isEqualTo(cursorRowCount);
    }

    @Test
    public void testGetCount_returnsZeroWhenNotInitialized() {
        assertThat(mComposer.getCount()).isEqualTo(0);
    }

    @Test
    public void testIsAfterLast_success() {
        mComposer.init(CALL_LOG_URI, SELECTION, SELECTION_ARGS, SORT_ORDER);
        final boolean cursorIsAfterLast = true;
        when(mMockCursor.isAfterLast()).thenReturn(cursorIsAfterLast);

        assertThat(mComposer.isAfterLast()).isEqualTo(cursorIsAfterLast);
    }

    @Test
    public void testIsAfterLast_returnsFalseWhenNotInitialized() {
        assertThat(mComposer.isAfterLast()).isEqualTo(false);
    }
}
