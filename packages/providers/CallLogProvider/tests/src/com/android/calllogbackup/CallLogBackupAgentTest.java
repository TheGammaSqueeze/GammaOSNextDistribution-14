/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.calllogbackup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.calllogbackup.CallLogBackupAgent.Call;
import com.android.calllogbackup.CallLogBackupAgent.CallLogBackupState;

import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Test cases for {@link com.android.providers.contacts.CallLogBackupAgent}
 */
@SmallTest
public class CallLogBackupAgentTest extends AndroidTestCase {
    static final String TELEPHONY_COMPONENT
            = "com.android.phone/com.android.services.telephony.TelephonyConnectionService";
    static final String TEST_PHONE_ACCOUNT_HANDLE_SUB_ID = "666";
    static final int TEST_PHONE_ACCOUNT_HANDLE_SUB_ID_INT = 666;
    static final String TEST_PHONE_ACCOUNT_HANDLE_ICC_ID = "891004234814455936F";

    public int backupRestoreLoggerSuccessCount = 0;
    public int backupRestoreLoggerFailCount = 0;

    @Mock DataInput mDataInput;
    @Mock DataOutput mDataOutput;
    @Mock BackupDataOutput mBackupDataOutput;
    @Mock Cursor mCursor;

    private CallLogBackupAgent.BackupRestoreEventLoggerProxy mBackupRestoreEventLoggerProxy =
            new CallLogBackupAgent.BackupRestoreEventLoggerProxy() {
        @Override
        public void logItemsBackedUp(String dataType, int count) {
            backupRestoreLoggerSuccessCount += count;
        }

        @Override
        public void logItemsBackupFailed(String dataType, int count, String error) {
            backupRestoreLoggerFailCount += count;
        }

        @Override
        public void logItemsRestored(String dataType, int count) {
            backupRestoreLoggerSuccessCount += count;
        }

        @Override
        public void logItemsRestoreFailed(String dataType, int count, String error) {
            backupRestoreLoggerFailCount += count;
        }
    };

    CallLogBackupAgent mCallLogBackupAgent;

    MockitoHelper mMockitoHelper = new MockitoHelper();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mMockitoHelper.setUp(getClass());
        // Since we're testing a system app, AppDataDirGuesser doesn't find our
        // cache dir, so set it explicitly.
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        mCallLogBackupAgent = new CallLogBackupAgent();
        mCallLogBackupAgent.setBackupRestoreEventLoggerProxy(mBackupRestoreEventLoggerProxy);
    }

    @Override
    public void tearDown() throws Exception {
        mMockitoHelper.tearDown();
    }

    @Override
    public Context getTestContext() {
        return InstrumentationRegistry.getContext();
    }

    public void testReadState_NoCall() throws Exception {
        when(mDataInput.readInt()).thenThrow(new EOFException());

        CallLogBackupState state = mCallLogBackupAgent.readState(mDataInput);

        assertEquals(state.version, CallLogBackupAgent.VERSION_NO_PREVIOUS_STATE);
        assertEquals(state.callIds.size(), 0);
    }

    public void testReadState_OneCall() throws Exception {
        when(mDataInput.readInt()).thenReturn(
                1 /* version */,
                1 /* size */,
                101 /* call-ID */ );

        CallLogBackupState state = mCallLogBackupAgent.readState(mDataInput);

        assertEquals(1, state.version);
        assertEquals(1, state.callIds.size());
        assertTrue(state.callIds.contains(101));
    }

    /**
     * Verifies that attempting to restore from a version newer than what the backup agent defines
     * will result in no restored rows.
     */
    public void testRestoreFromHigherVersion() throws Exception {
        // The backup format is not well structured, and consists of a bunch of persisted bytes, so
        // making the mock data is a bit gross.
        BackupDataInput backupDataInput = Mockito.mock(BackupDataInput.class);
        when(backupDataInput.getKey()).thenReturn("1");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(new ByteArrayOutputStream());
        dataOutputStream.writeInt(10000); // version
        byte[] data = byteArrayOutputStream.toByteArray();
        when(backupDataInput.getDataSize()).thenReturn(data.length);
        when(backupDataInput.readEntityData(any(), anyInt(), anyInt())).thenAnswer(
                new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        byte[] bytes = invocation.getArgument(0);
                        System.arraycopy(data, 0, bytes, 0, data.length);
                        return null;
                    }
                }
        );

        // Well, this is awkward.  BackupDataInput has no way to get the number of data elements
        // it contains.  So we'll mock out "readNextHeader" to emulate there being some non-zero
        // number of items to restore.
        final int[] executionLimit = {1};
        when(backupDataInput.readNextHeader()).thenAnswer(
                new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        executionLimit[0]--;
                        return executionLimit[0] >= 0;
                    }
                }
        );

        mCallLogBackupAgent.onRestore(backupDataInput, Integer.MAX_VALUE, null);

        assertEquals(1, backupRestoreLoggerFailCount);
        assertEquals(0, backupRestoreLoggerSuccessCount);
    }

    public void testReadState_MultipleCalls() throws Exception {
        when(mDataInput.readInt()).thenReturn(
                1 /* version */,
                2 /* size */,
                101 /* call-ID */,
                102 /* call-ID */);

        CallLogBackupState state = mCallLogBackupAgent.readState(mDataInput);

        assertEquals(1, state.version);
        assertEquals(2, state.callIds.size());
        assertTrue(state.callIds.contains(101));
        assertTrue(state.callIds.contains(102));
    }

    public void testWriteState_NoCalls() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();

        mCallLogBackupAgent.writeState(mDataOutput, state);

        InOrder inOrder = Mockito.inOrder(mDataOutput);
        inOrder.verify(mDataOutput).writeInt(CallLogBackupAgent.VERSION);
        inOrder.verify(mDataOutput).writeInt(0 /* size */);
    }

    public void testWriteState_OneCall() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        state.callIds.add(101);

        mCallLogBackupAgent.writeState(mDataOutput, state);

        InOrder inOrder = Mockito.inOrder(mDataOutput);
        inOrder.verify(mDataOutput).writeInt(CallLogBackupAgent.VERSION);
        inOrder.verify(mDataOutput).writeInt(1);
        inOrder.verify(mDataOutput).writeInt(101 /* call-ID */);
    }

    public void testWriteState_MultipleCalls() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        state.callIds.add(101);
        state.callIds.add(102);
        state.callIds.add(103);

        mCallLogBackupAgent.writeState(mDataOutput, state);

        InOrder inOrder = Mockito.inOrder(mDataOutput);
        inOrder.verify(mDataOutput).writeInt(CallLogBackupAgent.VERSION);
        inOrder.verify(mDataOutput).writeInt(3 /* size */);
        inOrder.verify(mDataOutput).writeInt(101 /* call-ID */);
        inOrder.verify(mDataOutput).writeInt(102 /* call-ID */);
        inOrder.verify(mDataOutput).writeInt(103 /* call-ID */);
    }

    public void testRunBackup_NoCalls() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        List<Call> calls = new LinkedList<>();

        mCallLogBackupAgent.runBackup(state, mBackupDataOutput, calls);

        // Ensure the {@link BackupRestoreEventLogger} is not notified as no calls were backed up:
        assertEquals(backupRestoreLoggerSuccessCount, 0);
        assertEquals(backupRestoreLoggerFailCount, 0);

        Mockito.verifyNoMoreInteractions(mBackupDataOutput);
    }

    public void testRunBackup_OneNewCall_ErrorAddingCall() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        List<Call> calls = new LinkedList<>();
        calls.add(makeCall(101, 0L, 0L, "555-5555"));

        // Throw an exception when the call is added to the backup:
        when(mBackupDataOutput.writeEntityData(any(byte[].class), anyInt()))
                .thenThrow(IOException.class);
        mCallLogBackupAgent.runBackup(state, mBackupDataOutput, calls);

        // Ensure the {@link BackupRestoreEventLogger} is informed of the failed backed up call:
        assertEquals(backupRestoreLoggerSuccessCount, 0);
        assertEquals(backupRestoreLoggerFailCount, 1);
    }

    public void testRunBackup_OneNewCall_NullBackupDataOutput() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        List<Call> calls = new LinkedList<>();
        calls.add(makeCall(101, 0L, 0L, "555-5555"));

        // Invoke runBackup() with a null value for BackupDataOutput causing an exception:
        mCallLogBackupAgent.runBackup(state, null, calls);

        // Ensure the {@link BackupRestoreEventLogger} is informed of the failed backed up call:
        assertEquals(backupRestoreLoggerSuccessCount, 0);
        assertEquals(backupRestoreLoggerFailCount, 1);
    }

    public void testRunBackup_OneNewCall() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        List<Call> calls = new LinkedList<>();
        calls.add(makeCall(101, 0L, 0L, "555-5555"));
        mCallLogBackupAgent.runBackup(state, mBackupDataOutput, calls);

        // Ensure the {@link BackupRestoreEventLogger} is informed of the backed up call:
        assertEquals(backupRestoreLoggerSuccessCount, 1);
        assertEquals(backupRestoreLoggerFailCount, 0);

        verify(mBackupDataOutput).writeEntityHeader(eq("101"), Matchers.anyInt());
        verify(mBackupDataOutput).writeEntityData((byte[]) Matchers.any(), Matchers.anyInt());
    }

    /*
        Test PhoneAccountHandle Migration process during back up
     */
    public void testReadCallFromCursorForPhoneAccountMigrationBackup() throws Exception {
        Map<Integer, String> subscriptionInfoMap = new HashMap<>();
        subscriptionInfoMap.put(TEST_PHONE_ACCOUNT_HANDLE_SUB_ID_INT,
                TEST_PHONE_ACCOUNT_HANDLE_ICC_ID);
        mCallLogBackupAgent.mSubscriptionInfoMap = subscriptionInfoMap;

        // Mock telephony component name and expect the Sub ID is converted to Icc ID
        // and the pending status is 1 when backup
        mockCursor(mCursor, true);
        Call call = mCallLogBackupAgent.readCallFromCursor(mCursor);
        assertEquals(TEST_PHONE_ACCOUNT_HANDLE_ICC_ID, call.accountId);
        assertEquals(1, call.isPhoneAccountMigrationPending);

        // Mock non-telephony component name and expect the Sub ID not converted to Icc ID
        // and pending status is 0 when backup.
        mockCursor(mCursor, false);
        call = mCallLogBackupAgent.readCallFromCursor(mCursor);
        assertEquals(TEST_PHONE_ACCOUNT_HANDLE_SUB_ID, call.accountId);
        assertEquals(0, call.isPhoneAccountMigrationPending);
    }

    public void testReadCallFromCursor_WithNullAccountComponentName() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME);
    }

    public void testReadCallFromCursor_WithNullNumber() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.NUMBER);
    }

    public void testReadCallFromCursor_WithNullPostDialDigits() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.POST_DIAL_DIGITS);
    }

    public void testReadCallFromCursor_WithNullViaNumber() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.VIA_NUMBER);
    }

    public void testReadCallFromCursor_WithNullPhoneAccountId() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.PHONE_ACCOUNT_ID);
    }

    public void testReadCallFromCursor_WithNullCallAccountAddress() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.PHONE_ACCOUNT_ADDRESS);
    }

    public void testReadCallFromCursor_WithNullCallScreeningAppName() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.CALL_SCREENING_APP_NAME);
    }

    public void testReadCallFromCursor_WithNullCallScreeningComponentName() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME);
    }

    public void testReadCallFromCursor_WithNullMissedReason() throws Exception {
        testReadCallFromCursor_WithNullField(CallLog.Calls.MISSED_REASON);
    }

    private void testReadCallFromCursor_WithNullField(String field) throws Exception {
        Map<Integer, String> subscriptionInfoMap = new HashMap<>();
        subscriptionInfoMap.put(TEST_PHONE_ACCOUNT_HANDLE_SUB_ID_INT,
            TEST_PHONE_ACCOUNT_HANDLE_ICC_ID);
        mCallLogBackupAgent.mSubscriptionInfoMap = subscriptionInfoMap;

        //read from cursor and not throw exception
        mockCursorWithNullFields(mCursor, field);
        Call call = mCallLogBackupAgent.readCallFromCursor(mCursor);
    }

    public void testRunBackup_MultipleCall() throws Exception {
        CallLogBackupState state = new CallLogBackupState();
        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        List<Call> calls = new LinkedList<>();
        calls.add(makeCall(101, 0L, 0L, "555-1234"));
        calls.add(makeCall(102, 0L, 0L, "555-5555"));

        mCallLogBackupAgent.runBackup(state, mBackupDataOutput, calls);

        // Ensure the {@link BackupRestoreEventLogger} is informed of the 2 backed up calls:
        assertEquals(backupRestoreLoggerSuccessCount, 2);
        assertEquals(backupRestoreLoggerFailCount, 0);

        InOrder inOrder = Mockito.inOrder(mBackupDataOutput);
        inOrder.verify(mBackupDataOutput).writeEntityHeader(eq("101"), Matchers.anyInt());
        inOrder.verify(mBackupDataOutput).
                writeEntityData((byte[]) Matchers.any(), Matchers.anyInt());
        inOrder.verify(mBackupDataOutput).writeEntityHeader(eq("102"), Matchers.anyInt());
        inOrder.verify(mBackupDataOutput).
                writeEntityData((byte[]) Matchers.any(), Matchers.anyInt());
    }

    public void testRunBackup_PartialMultipleCall() throws Exception {
        CallLogBackupState state = new CallLogBackupState();

        state.version = CallLogBackupAgent.VERSION;
        state.callIds = new TreeSet<>();
        state.callIds.add(101);

        List<Call> calls = new LinkedList<>();
        calls.add(makeCall(101, 0L, 0L, "555-1234"));
        calls.add(makeCall(102, 0L, 0L, "555-5555"));

        mCallLogBackupAgent.runBackup(state, mBackupDataOutput, calls);

        // Ensure the {@link BackupRestoreEventLogger} is informed of the 2 backed up calls:
        assertEquals(backupRestoreLoggerSuccessCount, 2);
        assertEquals(backupRestoreLoggerFailCount, 0);

        InOrder inOrder = Mockito.inOrder(mBackupDataOutput);
        inOrder.verify(mBackupDataOutput).writeEntityHeader(eq("102"), Matchers.anyInt());
        inOrder.verify(mBackupDataOutput).
                writeEntityData((byte[]) Matchers.any(), Matchers.anyInt());
    }

    private static void mockCursor(Cursor cursor, boolean isTelephonyComponentName) {
        when(cursor.moveToNext()).thenReturn(true).thenReturn(false);

        int CALLS_ID_COLUMN_INDEX = 1;
        int CALL_ID = 9;
        when(cursor.getColumnIndex(CallLog.Calls._ID)).thenReturn(CALLS_ID_COLUMN_INDEX);
        when(cursor.getInt(CALLS_ID_COLUMN_INDEX)).thenReturn(CALL_ID);

        int CALLS_DATE_COLUMN_INDEX = 2;
        long CALL_DATE = 20991231;
        when(cursor.getColumnIndex(CallLog.Calls.DATE)).thenReturn(CALLS_DATE_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DATE_COLUMN_INDEX)).thenReturn(CALL_DATE);

        int CALLS_DURATION_COLUMN_INDEX = 3;
        long CALL_DURATION = 987654321;
        when(cursor.getColumnIndex(CallLog.Calls.DURATION)).thenReturn(
                CALLS_DURATION_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DURATION_COLUMN_INDEX)).thenReturn(CALL_DURATION);

        int CALLS_NUMBER_COLUMN_INDEX = 4;
        String CALL_NUMBER = "6316056461";
        when(cursor.getColumnIndex(CallLog.Calls.NUMBER)).thenReturn(
                CALLS_NUMBER_COLUMN_INDEX);
        when(cursor.getString(CALLS_NUMBER_COLUMN_INDEX)).thenReturn(CALL_NUMBER);

        int CALLS_POST_DIAL_DIGITS_COLUMN_INDEX = 5;
        String CALL_POST_DIAL_DIGITS = "54321";
        when(cursor.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)).thenReturn(
                CALLS_POST_DIAL_DIGITS_COLUMN_INDEX);
        when(cursor.getString(CALLS_POST_DIAL_DIGITS_COLUMN_INDEX)).thenReturn(
                CALL_POST_DIAL_DIGITS);

        int CALLS_VIA_NUMBER_COLUMN_INDEX = 6;
        String CALL_VIA_NUMBER = "via_number";
        when(cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER)).thenReturn(
                CALLS_VIA_NUMBER_COLUMN_INDEX);
        when(cursor.getString(CALLS_VIA_NUMBER_COLUMN_INDEX)).thenReturn(
                CALL_VIA_NUMBER);

        int CALLS_TYPE_COLUMN_INDEX = 7;
        int CALL_TYPE = CallLog.Calls.OUTGOING_TYPE;
        when(cursor.getColumnIndex(CallLog.Calls.TYPE)).thenReturn(CALLS_TYPE_COLUMN_INDEX);
        when(cursor.getInt(CALLS_TYPE_COLUMN_INDEX)).thenReturn(CALL_TYPE);

        int CALLS_NUMBER_PRESENTATION_COLUMN_INDEX = 8;
        int CALL_NUMBER_PRESENTATION = CallLog.Calls.PRESENTATION_ALLOWED;
        when(cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)).thenReturn(
                CALLS_NUMBER_PRESENTATION_COLUMN_INDEX);
        when(cursor.getInt(CALLS_NUMBER_PRESENTATION_COLUMN_INDEX)).thenReturn(
                CALL_NUMBER_PRESENTATION);

        int CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX = 9;
        String CALL_ACCOUNT_COMPONENT_NAME = "NON_TELEPHONY_COMPONENT_NAME";
        if (isTelephonyComponentName) {
            CALL_ACCOUNT_COMPONENT_NAME = TELEPHONY_COMPONENT;
        }
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)).thenReturn(
                CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX);
        when(cursor.getString(CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_COMPONENT_NAME);

        int CALLS_ACCOUNT_ID_COLUMN_INDEX = 10;
        String CALL_ACCOUNT_ID = TEST_PHONE_ACCOUNT_HANDLE_SUB_ID;
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)).thenReturn(
                CALLS_ACCOUNT_ID_COLUMN_INDEX);
        when(cursor.getString(CALLS_ACCOUNT_ID_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_ID);

        int CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX = 11;
        String CALL_ACCOUNT_ADDRESS = "CALL_ACCOUNT_ADDRESS";
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ADDRESS)).thenReturn(
                CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX);
        when(cursor.getString(CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_ADDRESS);

        int CALLS_DATA_USAGE_COLUMN_INDEX = 12;
        long CALL_DATA_USAGE = 987654321;
        when(cursor.getColumnIndex(CallLog.Calls.DATA_USAGE)).thenReturn(
                CALLS_DATA_USAGE_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DATA_USAGE_COLUMN_INDEX)).thenReturn(CALL_DATA_USAGE);

        int CALLS_FEATURES_COLUMN_INDEX = 13;
        int CALL_FEATURES = 777;
        when(cursor.getColumnIndex(CallLog.Calls.FEATURES)).thenReturn(
                CALLS_FEATURES_COLUMN_INDEX);
        when(cursor.getInt(CALLS_FEATURES_COLUMN_INDEX)).thenReturn(CALL_FEATURES);

        int CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX = 14;
        int CALL_ADD_FOR_ALL_USERS = 1;
        when(cursor.getColumnIndex(CallLog.Calls.ADD_FOR_ALL_USERS)).thenReturn(
                CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX);
        when(cursor.getInt(CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX)).thenReturn(
                CALL_ADD_FOR_ALL_USERS);

        int CALLS_BLOCK_REASON_COLUMN_INDEX = 15;
        int CALL_BLOCK_REASON = CallLog.Calls.BLOCK_REASON_NOT_BLOCKED;
        when(cursor.getColumnIndex(CallLog.Calls.BLOCK_REASON)).thenReturn(
                CALLS_BLOCK_REASON_COLUMN_INDEX);
        when(cursor.getInt(CALLS_BLOCK_REASON_COLUMN_INDEX)).thenReturn(
                CALL_BLOCK_REASON);

        int CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX = 16;
        String CALL_CALL_SCREENING_APP_NAME = "CALL_CALL_SCREENING_APP_NAME";
        when(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME)).thenReturn(
                CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX);
        when(cursor.getString(CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX)).thenReturn(
                CALL_CALL_SCREENING_APP_NAME);

        int CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX = 17;
        String CALL_CALL_SCREENING_COMPONENT_NAME = "CALL_CALL_SCREENING_COMPONENT_NAME";
        when(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)).thenReturn(
                CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX);
        when(cursor.getString(CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                CALL_CALL_SCREENING_COMPONENT_NAME);

        int CALLS_MISSED_REASON_COLUMN_INDEX = 18;
        String CALL_MISSED_REASON = "CALL_MISSED_REASON";
        when(cursor.getColumnIndex(CallLog.Calls.MISSED_REASON)).thenReturn(
                CALLS_MISSED_REASON_COLUMN_INDEX);
        when(cursor.getString(CALLS_MISSED_REASON_COLUMN_INDEX)).thenReturn(
                CALL_MISSED_REASON);

        int CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX = 19;
        int CALL_IS_PHONE_ACCOUNT_MIGRATION_PENDING = 0;
        when(cursor.getColumnIndex(CallLog.Calls.IS_PHONE_ACCOUNT_MIGRATION_PENDING)).thenReturn(
                CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX);
        when(cursor.getInt(CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX)).thenReturn(
                CALL_IS_PHONE_ACCOUNT_MIGRATION_PENDING);
    }

    //sets up the mock cursor with specified column data (string) set to null
    private static void mockCursorWithNullFields(Cursor cursor, String columnToNullify) {
        when(cursor.moveToNext()).thenReturn(true).thenReturn(false);

        int CALLS_ID_COLUMN_INDEX = 1;
        int CALL_ID = 9;
        when(cursor.getColumnIndex(CallLog.Calls._ID)).thenReturn(CALLS_ID_COLUMN_INDEX);
        when(cursor.getInt(CALLS_ID_COLUMN_INDEX)).thenReturn(CALL_ID);

        int CALLS_DATE_COLUMN_INDEX = 2;
        long CALL_DATE = 20991231;
        when(cursor.getColumnIndex(CallLog.Calls.DATE)).thenReturn(CALLS_DATE_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DATE_COLUMN_INDEX)).thenReturn(CALL_DATE);

        int CALLS_DURATION_COLUMN_INDEX = 3;
        long CALL_DURATION = 987654321;
        when(cursor.getColumnIndex(CallLog.Calls.DURATION)).thenReturn(
            CALLS_DURATION_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DURATION_COLUMN_INDEX)).thenReturn(CALL_DURATION);

        int CALLS_NUMBER_COLUMN_INDEX = 4;
        String CALL_NUMBER = "6316056461";
        when(cursor.getColumnIndex(CallLog.Calls.NUMBER)).thenReturn(
            CALLS_NUMBER_COLUMN_INDEX);
        if (CallLog.Calls.NUMBER.equals(columnToNullify)) {
            when(cursor.getString(CALLS_NUMBER_COLUMN_INDEX)).thenReturn(null);
        } else {
            when(cursor.getString(CALLS_NUMBER_COLUMN_INDEX)).thenReturn(CALL_NUMBER);
        }

        int CALLS_POST_DIAL_DIGITS_COLUMN_INDEX = 5;
        String CALL_POST_DIAL_DIGITS = "54321";
        when(cursor.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)).thenReturn(
            CALLS_POST_DIAL_DIGITS_COLUMN_INDEX);
        if (CallLog.Calls.POST_DIAL_DIGITS.equals(columnToNullify)) {
            when(cursor.getString(CALLS_POST_DIAL_DIGITS_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_POST_DIAL_DIGITS_COLUMN_INDEX)).thenReturn(
                CALL_POST_DIAL_DIGITS);
        }

        int CALLS_VIA_NUMBER_COLUMN_INDEX = 6;
        String CALL_VIA_NUMBER = "via_number";
        when(cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER)).thenReturn(
            CALLS_VIA_NUMBER_COLUMN_INDEX);
        if (CallLog.Calls.VIA_NUMBER.equals(columnToNullify)) {
            when(cursor.getString(CALLS_VIA_NUMBER_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_VIA_NUMBER_COLUMN_INDEX)).thenReturn(
                CALL_VIA_NUMBER);
        }

        int CALLS_TYPE_COLUMN_INDEX = 7;
        int CALL_TYPE = CallLog.Calls.OUTGOING_TYPE;
        when(cursor.getColumnIndex(CallLog.Calls.TYPE)).thenReturn(CALLS_TYPE_COLUMN_INDEX);
        when(cursor.getInt(CALLS_TYPE_COLUMN_INDEX)).thenReturn(CALL_TYPE);

        int CALLS_NUMBER_PRESENTATION_COLUMN_INDEX = 8;
        int CALL_NUMBER_PRESENTATION = CallLog.Calls.PRESENTATION_ALLOWED;
        when(cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)).thenReturn(
            CALLS_NUMBER_PRESENTATION_COLUMN_INDEX);
        when(cursor.getInt(CALLS_NUMBER_PRESENTATION_COLUMN_INDEX)).thenReturn(
            CALL_NUMBER_PRESENTATION);

        int CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX = 9;
        String CALL_ACCOUNT_COMPONENT_NAME = TELEPHONY_COMPONENT;
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)).thenReturn(
            CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX);
        if (CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME.equals(columnToNullify)) {
            when(cursor.getString(CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_COMPONENT_NAME);
        }

        int CALLS_ACCOUNT_ID_COLUMN_INDEX = 10;
        String CALL_ACCOUNT_ID = TEST_PHONE_ACCOUNT_HANDLE_SUB_ID;
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)).thenReturn(
            CALLS_ACCOUNT_ID_COLUMN_INDEX);
        if (CallLog.Calls.PHONE_ACCOUNT_ID.equals(columnToNullify)) {
            when(cursor.getString(CALLS_ACCOUNT_ID_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_ACCOUNT_ID_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_ID);
        }

        int CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX = 11;
        String CALL_ACCOUNT_ADDRESS = "CALL_ACCOUNT_ADDRESS";
        when(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ADDRESS)).thenReturn(
            CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX);
        if (CallLog.Calls.PHONE_ACCOUNT_ADDRESS.equals(columnToNullify)) {
            when(cursor.getString(CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_ACCOUNT_ADDRESS_COLUMN_INDEX)).thenReturn(
                CALL_ACCOUNT_ADDRESS);
        }

        int CALLS_DATA_USAGE_COLUMN_INDEX = 12;
        long CALL_DATA_USAGE = 987654321;
        when(cursor.getColumnIndex(CallLog.Calls.DATA_USAGE)).thenReturn(
            CALLS_DATA_USAGE_COLUMN_INDEX);
        when(cursor.getLong(CALLS_DATA_USAGE_COLUMN_INDEX)).thenReturn(CALL_DATA_USAGE);

        int CALLS_FEATURES_COLUMN_INDEX = 13;
        int CALL_FEATURES = 777;
        when(cursor.getColumnIndex(CallLog.Calls.FEATURES)).thenReturn(
            CALLS_FEATURES_COLUMN_INDEX);
        when(cursor.getInt(CALLS_FEATURES_COLUMN_INDEX)).thenReturn(CALL_FEATURES);

        int CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX = 14;
        int CALL_ADD_FOR_ALL_USERS = 1;
        when(cursor.getColumnIndex(CallLog.Calls.ADD_FOR_ALL_USERS)).thenReturn(
            CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX);
        when(cursor.getInt(CALLS_ADD_FOR_ALL_USERS_COLUMN_INDEX)).thenReturn(
            CALL_ADD_FOR_ALL_USERS);

        int CALLS_BLOCK_REASON_COLUMN_INDEX = 15;
        int CALL_BLOCK_REASON = CallLog.Calls.BLOCK_REASON_NOT_BLOCKED;
        when(cursor.getColumnIndex(CallLog.Calls.BLOCK_REASON)).thenReturn(
            CALLS_BLOCK_REASON_COLUMN_INDEX);
        when(cursor.getInt(CALLS_BLOCK_REASON_COLUMN_INDEX)).thenReturn(
            CALL_BLOCK_REASON);

        int CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX = 16;
        String CALL_CALL_SCREENING_APP_NAME = "CALL_CALL_SCREENING_APP_NAME";
        when(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME)).thenReturn(
            CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX);
        if (CallLog.Calls.CALL_SCREENING_APP_NAME.equals(columnToNullify)) {
            when(cursor.getString(CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_CALL_SCREENING_APP_NAME_COLUMN_INDEX)).thenReturn(
                CALL_CALL_SCREENING_APP_NAME);
        }

        int CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX = 17;
        String CALL_CALL_SCREENING_COMPONENT_NAME = "CALL_CALL_SCREENING_COMPONENT_NAME";
        when(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)).thenReturn(
            CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX);
        if (CallLog.Calls.CALL_SCREENING_COMPONENT_NAME.equals(columnToNullify)) {
            when(cursor.getString(CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_CALL_SCREENING_COMPONENT_NAME_COLUMN_INDEX)).thenReturn(
                CALL_CALL_SCREENING_COMPONENT_NAME);
        }

        int CALLS_MISSED_REASON_COLUMN_INDEX = 18;
        String CALL_MISSED_REASON = "CALL_MISSED_REASON";
        when(cursor.getColumnIndex(CallLog.Calls.MISSED_REASON)).thenReturn(
            CALLS_MISSED_REASON_COLUMN_INDEX);
        if (CallLog.Calls.MISSED_REASON.equals(columnToNullify)) {
            when(cursor.getString(CALLS_MISSED_REASON_COLUMN_INDEX)).thenReturn(
                null);
        } else {
            when(cursor.getString(CALLS_MISSED_REASON_COLUMN_INDEX)).thenReturn(
                CALL_MISSED_REASON);
        }

        int CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX = 19;
        int CALL_IS_PHONE_ACCOUNT_MIGRATION_PENDING = 0;
        when(cursor.getColumnIndex(CallLog.Calls.IS_PHONE_ACCOUNT_MIGRATION_PENDING)).thenReturn(
            CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX);
        when(cursor.getInt(CALLS_IS_PHONE_ACCOUNT_MIGRATION_PENDING_COLUMN_INDEX)).thenReturn(
            CALL_IS_PHONE_ACCOUNT_MIGRATION_PENDING);
    }

    private static Call makeCall(int id, long date, long duration, String number) {
        Call c = new Call();
        c.id = id;
        c.date = date;
        c.duration = duration;
        c.number = number;
        c.accountComponentName = "account-component";
        c.accountId = "account-id";
        return c;
    }

}
