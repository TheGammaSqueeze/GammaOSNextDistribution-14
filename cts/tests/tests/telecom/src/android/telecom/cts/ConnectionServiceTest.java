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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.*;

import static com.android.compatibility.common.util.BlockedNumberUtil.deleteBlockedNumber;
import static com.android.compatibility.common.util.BlockedNumberUtil.insertBlockedNumber;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.provider.BlockedNumberContract;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.CallScreeningService.CallResponse;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test some additional {@link ConnectionService} and {@link Connection} APIs not already covered
 * by other tests.
 */
public class ConnectionServiceTest extends BaseTelecomTestWithMockServices {

    private static final Uri SELF_MANAGED_TEST_ADDRESS =
            Uri.fromParts("sip", "call1@test.com", null);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testAddExistingConnection() {
        if (!mShouldTestTelecom) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            placeAndVerifyCall();
            verifyConnectionForOutgoingCall();

            // Add second connection (add existing connection)
            final MockConnection connection = new MockConnection();
            connection.setOnHold();
            CtsConnectionService.addExistingConnectionToTelecom(TEST_PHONE_ACCOUNT_HANDLE,
                            connection);
            assertNumCalls(mInCallCallbacks.getService(), 2);
            mInCallCallbacks.lock.drainPermits();
            final Call call = mInCallCallbacks.getService().getLastCall();
            assertCallState(call, Call.STATE_HOLDING);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testAddExistingConnection_invalidPhoneAccountPackageName() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Add second connection (add existing connection)
        final MockConnection connection = new MockConnection();
        connection.setOnHold();
        ComponentName invalidName = new ComponentName("com.android.phone",
                "com.android.services.telephony.TelephonyConnectionService");
        // This command will fail and a SecurityException will be thrown by Telecom. The Exception
        // will then be absorbed by the ConnectionServiceAdapter.
        runWithShellPermissionIdentity(() ->
                CtsConnectionService.addExistingConnectionToTelecom(
                        new PhoneAccountHandle(invalidName, "Test"), connection));
        // Make sure that only the original Call exists.
        assertNumCalls(mInCallCallbacks.getService(), 1);
        mInCallCallbacks.lock.drainPermits();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);
    }

    public void testAddExistingConnection_invalidPhoneAccountAccountId() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Add second connection (add existing connection)
        final MockConnection connection = new MockConnection();
        connection.setOnHold();
        ComponentName validName = new ComponentName(PACKAGE, COMPONENT);
        // This command will fail because the PhoneAccount is not registered to Telecom currently.
        runWithShellPermissionIdentity(() ->
                CtsConnectionService.addExistingConnectionToTelecom(
                        new PhoneAccountHandle(validName, "Invalid Account Id"), connection));
        // Make sure that only the original Call exists.
        assertNumCalls(mInCallCallbacks.getService(), 1);
        mInCallCallbacks.lock.drainPermits();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);
    }

    public void testVoipAudioModePropagation() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        MockConnection connection = verifyConnectionForOutgoingCall();
        connection.setAudioModeIsVoip(true);
        waitOnAllHandlers(getInstrumentation());

        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return AudioManager.MODE_IN_COMMUNICATION;
                    }

                    @Override
                    public Object actual() {
                        return audioManager.getMode();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "wait for mode in-communication"
        );

        connection.setAudioModeIsVoip(false);
        waitOnAllHandlers(getInstrumentation());
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return AudioManager.MODE_IN_CALL;
                    }

                    @Override
                    public Object actual() {
                        return audioManager.getMode();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "wait for mode in-call"
        );
    }

    public void testConnectionServiceFocusGainedWithNoConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // WHEN place a managed call
        placeAndVerifyCall();

        // THEN managed connection service has gained the focus
        assertTrue(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_GAINED));
    }

    public void testConnectionServiceFocusGainedWithSameConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // GIVEN a managed call
        placeAndVerifyCall();
        Connection outgoing = verifyConnectionForOutgoingCall();
        outgoing.setActive();
        assertTrue(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_GAINED));
        assertCallState(mInCallCallbacks.getService().getLastCall(), Call.STATE_ACTIVE);

        // WHEN place another call has the same ConnectionService as the existing call
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // THEN the ConnectionService has not gained the focus again
        assertFalse(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_GAINED));
        // and the ConnectionService didn't lose the focus
        assertFalse(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_LOST));
    }

    public void testConnectionServiceFocusGainedWithDifferentConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // GIVEN an existing managed call
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall().setActive();
        assertTrue(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_GAINED));

        // WHEN a self-managed call is coming
        SelfManagedConnection selfManagedConnection =
                addIncomingSelfManagedCall(TEST_SELF_MANAGED_HANDLE_1, SELF_MANAGED_TEST_ADDRESS);

        // THEN the managed ConnectionService has lost the focus
        assertTrue(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_LOST));
        // and the self-managed ConnectionService has gained the focus
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.FOCUS_GAINED_LOCK));

        // Wait for the internal handlers to set the self-managed call to active. Otherwise, the
        // call will get stuck in telecom if the set-active command gets run after the initial
        // disconnect command.
        TestUtils.waitOnAllHandlers(getInstrumentation());
        // Disconnected the self-managed call
        selfManagedConnection.disconnectAndDestroy();
    }

    private SelfManagedConnection addIncomingSelfManagedCall(
            PhoneAccountHandle pah, Uri address) {

        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager, pah, address);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);

        // Active the call
        connection.setActive();

        return connection;
    }

    public void testCallDirectionIncoming() {
        if (!mShouldTestTelecom) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            // Need to add a call to ensure ConnectionService is up and bound.
            placeAndVerifyCall();
            verifyConnectionForOutgoingCall().setActive();

            final MockConnection connection = new MockConnection();
            connection.setActive();
            connection.setCallDirection(Call.Details.DIRECTION_INCOMING);
            assertEquals(connection.getCallDirection(), Call.Details.DIRECTION_INCOMING);
            CtsConnectionService.addExistingConnectionToTelecom(TEST_PHONE_ACCOUNT_HANDLE,
                    connection);
            assertNumCalls(mInCallCallbacks.getService(), 2);
            mInCallCallbacks.lock.drainPermits();
            final Call call = mInCallCallbacks.getService().getLastCall();
            assertCallState(call, Call.STATE_ACTIVE);
            assertEquals(Call.Details.DIRECTION_INCOMING, call.getDetails().getCallDirection());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

    }

    public void testCallFilteringCompletionInfoParcelable() {
        Connection.CallFilteringCompletionInfo info = new Connection.CallFilteringCompletionInfo(
                false /* isBlocked */,
                false /* isInContacts */,
                null /* callResponse */,
                null /* callScreeningComponent */
        );
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        Connection.CallFilteringCompletionInfo info2 =
                Connection.CallFilteringCompletionInfo.CREATOR.createFromParcel(p);

        assertEquals(info.isBlocked(), info2.isBlocked());
        assertEquals(info.isInContacts(), info2.isInContacts());
        assertEquals(info.getCallResponse(), info2.getCallResponse());
        assertEquals(info.getCallScreeningComponent(), info2.getCallScreeningComponent());
    }

    public void testCallFilteringCompleteSignalNotInContacts() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        MockCallScreeningService.enableService(mContext);
        try {
            CallScreeningService.CallResponse response =
                    new CallScreeningService.CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .setSilenceCall(false)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .setShouldScreenCallViaAudioProcessing(false)
                            .setCallComposerAttachmentsToShow(
                                    CallResponse.CALL_COMPOSER_ATTACHMENT_PRIORITY
                                            | CallResponse.CALL_COMPOSER_ATTACHMENT_SUBJECT)
                            .build();
            MockCallScreeningService.setCallbacks(createCallbackForCsTest(response));

            addAndVerifyNewIncomingCall(createTestNumber(), null);
            MockConnection connection = verifyConnectionForIncomingCall();

            Object[] callFilteringCompleteInvocations =
                    connection.getInvokeCounter(MockConnection.ON_CALL_FILTERING_COMPLETED)
                            .getArgs(0);
            Connection.CallFilteringCompletionInfo completionInfo =
                    (Connection.CallFilteringCompletionInfo) callFilteringCompleteInvocations[0];

            assertFalse(completionInfo.isBlocked());
            assertFalse(completionInfo.isInContacts());
            assertEquals(response, completionInfo.getCallResponse());
            assertEquals(PACKAGE, completionInfo.getCallScreeningComponent().getPackageName());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            MockCallScreeningService.disableService(mContext);
        }
    }

    public void testCallFilteringCompleteSignalInContacts() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        Uri testNumber = createTestNumber();
        Uri contactUri = TestUtils.insertContact(mContext.getContentResolver(),
                testNumber.getSchemeSpecificPart());
        MockCallScreeningService.enableService(mContext);
        try {
            CallScreeningService.CallResponse response =
                    new CallScreeningService.CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .setSilenceCall(false)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .setShouldScreenCallViaAudioProcessing(false)
                            .setCallComposerAttachmentsToShow(
                                    CallResponse.CALL_COMPOSER_ATTACHMENT_PRIORITY
                                            | CallResponse.CALL_COMPOSER_ATTACHMENT_SUBJECT)
                            .build();
            MockCallScreeningService.setCallbacks(createCallbackForCsTest(response));

            assertEquals(CallResponse.CALL_COMPOSER_ATTACHMENT_PRIORITY
                    | CallResponse.CALL_COMPOSER_ATTACHMENT_SUBJECT,
                    response.getCallComposerAttachmentsToShow());
            addAndVerifyNewIncomingCall(testNumber, null);

            MockConnection connection = verifyConnectionForIncomingCall();

            Object[] callFilteringCompleteInvocations =
                    connection.getInvokeCounter(MockConnection.ON_CALL_FILTERING_COMPLETED)
                            .getArgs(0);
            Connection.CallFilteringCompletionInfo completionInfo =
                    (Connection.CallFilteringCompletionInfo) callFilteringCompleteInvocations[0];

            assertFalse(completionInfo.isBlocked());
            assertTrue(completionInfo.isInContacts());
            assertEquals(response, completionInfo.getCallResponse());
            assertEquals(PACKAGE, completionInfo.getCallScreeningComponent().getPackageName());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            TestUtils.deleteContact(mContext.getContentResolver(), contactUri);
            MockCallScreeningService.disableService(mContext);
        }
    }

    private Uri blockNumber(Uri phoneNumberUri) {
        Uri number = insertBlockedNumber(mContext, phoneNumberUri.getSchemeSpecificPart());
        if (number == null) {
            fail("Failed to insert into blocked number provider");
        }
        return number;
    }

    private int unblockNumber(Uri uri) {
        return deleteBlockedNumber(mContext, uri);
    }

    /**
     * Tests {@link CallLog.Calls.BLOCKED_TYPE} call log to ensure that blocked
     * numbers incoming calls should be logged to platform call log provider.
     */
    @CddTest(requirement = "7.4.1.1/C-1-4")
    public void testCallLogForBlockedNumberIncomingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        //Check if blocking numbers is supported for the current user
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(mContext)) {
            return;
        }

        Uri blockedUri = null;

        try {
            TestUtils.executeShellCommand(getInstrumentation(), "telecom stop-block-suppression");
            Uri testNumberUri = createTestNumber();
            blockedUri = blockNumber(testNumberUri);

            final Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, testNumberUri);
            mTelecomManager.addNewIncomingCall(TEST_PHONE_ACCOUNT_HANDLE, extras);

            // Setup content observer to notify us when we call log entry is added.
            CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

            // Blocked number incoming call should be in disconnected state
            final MockConnection connection = verifyConnectionForIncomingCall();
            assertConnectionState(connection, Connection.STATE_DISCONNECTED);
            assertNull(mInCallCallbacks.getService());

            // Wait for the content observer to report that we have gotten a new call log entry.
            callLogEntryLatch.await(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Query the latest entry into the call log.
            Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    null, null, null, CallLog.Calls._ID + " DESC limit 1;");
            int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
            int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
            int reasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
            if (callsCursor.moveToNext()) {
                assertEquals(testNumberUri.getSchemeSpecificPart(),
                        callsCursor.getString(numberIndex));
                assertEquals(CallLog.Calls.BLOCKED_TYPE, callsCursor.getInt(callTypeIndex));
                assertEquals(CallLog.Calls.BLOCK_REASON_BLOCKED_NUMBER,
                        callsCursor.getInt(reasonIndex));
            } else {
                fail("No call log entry found for blocked number");
            }
        } catch (InterruptedException ie) {
            fail("Failed to get blocked number call log");
        } finally {
            if (blockedUri != null) {
                unblockNumber(blockedUri);
            }
        }
    }

    private MockCallScreeningService.CallScreeningServiceCallbacks createCallbackForCsTest(
            CallScreeningService.CallResponse response) {
        return new MockCallScreeningService.CallScreeningServiceCallbacks() {
            @Override
            public void onScreenCall(Call.Details callDetails) {

                getService().respondToCall(callDetails, response);
            }
        };
    }

    public void testCallDirectionOutgoing() {
        if (!mShouldTestTelecom) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            // Ensure CS is up and bound.
            placeAndVerifyCall();
            verifyConnectionForOutgoingCall().setActive();

            final MockConnection connection = new MockConnection();
            connection.setActive();
            connection.setCallDirection(Call.Details.DIRECTION_OUTGOING);
            assertEquals(connection.getCallDirection(), Call.Details.DIRECTION_OUTGOING);
            connection.setConnectTimeMillis(1000L);
            assertEquals(1000L, connection.getConnectTimeMillis());
            connection.setConnectionStartElapsedRealtimeMillis(100L);
            assertEquals(100L, connection.getConnectionStartElapsedRealtimeMillis());

            CtsConnectionService.addExistingConnectionToTelecom(TEST_PHONE_ACCOUNT_HANDLE,
                    connection);
            assertNumCalls(mInCallCallbacks.getService(), 2);
            mInCallCallbacks.lock.drainPermits();
            final Call call = mInCallCallbacks.getService().getLastCall();
            assertCallState(call, Call.STATE_ACTIVE);
            assertEquals(Call.Details.DIRECTION_OUTGOING, call.getDetails().getCallDirection());
            assertEquals(1000L, call.getDetails().getConnectTimeMillis());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testGetAllConnections() {
        if (!mShouldTestTelecom) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            // Add first connection (outgoing call)
            placeAndVerifyCall();
            final Connection connection1 = verifyConnectionForOutgoingCall();

            Collection<Connection> connections =
                    CtsConnectionService.getAllConnectionsFromTelecom();
            assertEquals(1, connections.size());
            assertTrue(connections.contains(connection1));
            // Need to move this to active since we reject the 3rd incoming call below if this is in
            // dialing state (b/23428950).
            connection1.setActive();
            assertCallState(mInCallCallbacks.getService().getLastCall(), Call.STATE_ACTIVE);

            // Add second connection (add existing connection)
            final Connection connection2 = new MockConnection();
            connection2.setActive();
            CtsConnectionService.addExistingConnectionToTelecom(TEST_PHONE_ACCOUNT_HANDLE,
                            connection2);
            assertNumCalls(mInCallCallbacks.getService(), 2);
            mInCallCallbacks.lock.drainPermits();
            connections = CtsConnectionService.getAllConnectionsFromTelecom();
            assertEquals(2, connections.size());
            assertTrue(connections.contains(connection2));

            // Add third connection (incoming call)
            addAndVerifyNewIncomingCall(createTestNumber(), null);
            final Connection connection3 = verifyConnectionForIncomingCall();
            connections = CtsConnectionService.getAllConnectionsFromTelecom();
            assertEquals(3, connections.size());
            assertTrue(connections.contains(connection3));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
}
