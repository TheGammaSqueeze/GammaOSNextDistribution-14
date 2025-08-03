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

package android.telecom.cts;

import static android.telecom.CallAttributes.AUDIO_CALL;
import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;
import static android.telecom.CallAttributes.VIDEO_CALL;
import static android.telecom.cts.TestUtils.PACKAGE;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.server.telecom.flags.Flags;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class tests calls added with the API
 * {@link TelecomManager#addCall(CallAttributes,
 * Executor,
 * OutcomeReceiver,
 * CallControlCallback,
 * CallEventCallback)
 * }
 * and controlled by {@link CallControl} or {@link CallControlCallback}.
 */
public class TransactionalApisTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = TransactionalApisTest.class.getSimpleName();
    private static final String TEST_NAME_1 = "Alan Turing";
    private static final Uri TEST_URI_1 = Uri.parse("tel:123-TEST");
    private static final String TEST_NAME_2 = "Mike Tyson";
    private static final Uri TEST_URI_2 = Uri.parse("tel:456-TEST");
    private static final String TEL_CLEAN_STUCK_CALLS_CMD = "telecom cleanup-stuck-calls";
    private static final String CALL_CHANNEL_ID = "t_test_call_channel";
    private static final String CALL_CHANNEL_NAME = "Transactional Test Call Channel";
    private static final String FAKE_INTENT_ACTION = "action new t-call";
    private static final int NOTIFICATION_ID = 2;
    // CallControl
    private static final String SET_ACTIVE = "SetActive";
    private static final String ANSWER = "Answer";
    private static final String SET_INACTIVE = "SetInactive";
    private static final String DISCONNECT = "Disconnect";
    private static final String SET_MUTE_STATE = "RequestMuteState";
    private static final String REQUEST_VIDEO_STATE = "RequestVideoState";

    // CallControlCallback
    private static final String ON_SET_ACTIVE = "OnSetActive";
    private static final String ON_ANSWER = "OnAnswer";
    private static final String ON_SET_INACTIVE = "OnSetInactive";
    private static final String ON_DISCONNECT = "OnDisconnect";

    // Fail messages
    private static final String FAIL_MSG_CALL_CONTROL_NULL =
            "onResult: callControl object is null";
    private static final String FAIL_MSG_OUTCOME_RECEIVER =
            "failed to receive OutcomeReceiver#onResult";
    private static final String FAIL_MSG_DURING_CLEANUP =
            "exception thrown while cleaning up tests";
    private static final String FAIL_MSG_ON_CALL_ENDPOINT_UPDATE =
            "CallEndpoint was not updated at all or in time";
    private static final String FAIL_MSG_ON_AVAILABLE_ENDPOINTS_UPDATE =
            "onAvailable CallEndpoint was not updated at all or in time";
    private static final String FAIL_MSG_ON_MUTE_STATE_CHANGED =
            "Mute state was not updated at all or in time";

    // inner classes

    /**
     * simulates a VoIP app construct of a Call object that accepts every
     * {@link CallControlCallback}
     */
    public class TelecomCtsVoipCall {
        private static final String TAG = "TelecomCtsVoipCall";
        private final String mCallId;
        private String mTelecomCallId = "";
        CallControl mCallControl;
        public boolean mCompletionResponse = Boolean.TRUE;

        // callback verifiers
        public boolean mWasOnSetActiveCalled = false;
        public boolean mWasOnSetInactiveCalled = false;
        public boolean mWasOnAnswerCalled = false;
        public boolean mWasOnDisconnectCalled = false;

        TelecomCtsVoipCall(String id) {
            mCallId = id;
            mEvents.mCallId = id;
        }

        public String getTelecomCallId() {
            return mTelecomCallId;
        }

        public void onAddCallControl(@NonNull CallControl callControl) {
            mCallControl = callControl;
            mTelecomCallId = callControl.getCallId().toString();
        }

        public android.telecom.cts.TelecomCtsVoipCall.CallEvent mEvents =
                new android.telecom.cts.TelecomCtsVoipCall.CallEvent();

        public CallControlCallback mHandshakes = new CallControlCallback() {
            @Override
            public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onSetActive: callId=[%s]", mCallId));
                mWasOnSetActiveCalled = true;
                wasCompleted.accept(mCompletionResponse);
            }

            @Override
            public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onSetInactive: callId=[%s]", mCallId));
                mWasOnSetInactiveCalled = true;
                wasCompleted.accept(mCompletionResponse);
            }

            @Override
            public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onAnswer: callId=[%s]", mCallId));
                mWasOnAnswerCalled = true;
                wasCompleted.accept(mCompletionResponse);
            }

            @Override
            public void onDisconnect(@NonNull DisconnectCause cause,
                    @NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onDisconnect: callId=[%s]", mCallId));
                mWasOnDisconnectCalled = true;
                wasCompleted.accept(mCompletionResponse);
            }

            @Override
            public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onCallStreamingStarted: callId=[%s]", mCallId));
            }
        };

        public void setClientResponse(boolean willComplete) {
            mCompletionResponse = willComplete;
        }

        public void resetAllCallbackVerifiers() {
            mCompletionResponse = Boolean.TRUE;
            mWasOnSetActiveCalled = false;
            mWasOnSetInactiveCalled = false;
            mWasOnAnswerCalled = false;
            mWasOnDisconnectCalled = false;
            mEvents.resetAllCallbackVerifiers();
        }
    }

    // Call constants
    public final PhoneAccountHandle DEFAULT_T_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, PACKAGE), "1");

    public final PhoneAccountHandle NO_VIDEO_CAPABILITIES_T_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, PACKAGE), "2");

    public final PhoneAccount DEFAULT_TRANSACTIONAL_ACCOUNT =
            PhoneAccount.builder(DEFAULT_T_HANDLE, "TransactionalApisTest default acct")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                            | PhoneAccount.CAPABILITY_VIDEO_CALLING
                            | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                    ).build();

    public final PhoneAccount NO_VIDEO_CAPABILITIES_ACCOUNT =
            PhoneAccount.builder(NO_VIDEO_CAPABILITIES_T_HANDLE,
                            "TransactionalApisTest no video acct")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                    ).build();

    CallAttributes mOutgoingCallAttributes = new CallAttributes.Builder(DEFAULT_T_HANDLE,
            DIRECTION_OUTGOING,
            TEST_NAME_1, TEST_URI_1)
            .setCallType(CallAttributes.AUDIO_CALL)
            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
            .build();

    CallAttributes mIncomingCallAttributes = new CallAttributes.Builder(DEFAULT_T_HANDLE,
            DIRECTION_INCOMING,
            TEST_NAME_2, TEST_URI_2)
            .setCallType(CallAttributes.AUDIO_CALL)
            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
            .build();

    private static final String OUTGOING_CALL_ID = "1";
    private static final String INCOMING_CALL_ID = "2";

    private final TelecomCtsVoipCall mCall1 = new TelecomCtsVoipCall(OUTGOING_CALL_ID);
    private final TelecomCtsVoipCall mCall2 = new TelecomCtsVoipCall(INCOMING_CALL_ID);
    private NotificationManager mNotificationManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) return;
        mNotificationManager =  mContext.getSystemService(NotificationManager.class);
        NewOutgoingCallBroadcastReceiver.reset();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        mTelecomManager.registerPhoneAccount(DEFAULT_TRANSACTIONAL_ACCOUNT);
        configureNotificationChannel();
        cleanup();
    }

    @Override
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown");
        if (mShouldTestTelecom) {
            cleanup();
            try {
                mNotificationManager.deleteNotificationChannel(CALL_CHANNEL_ID);
            } catch (Exception e) {
                // If there is an issue with deleting the notification channel, this should not
                // cause the Telecom test to fail.
                Log.w(TAG, String.format("tearDown: hit exception=[%s] while trying to delete"
                        + " Telecom CTS Notification Channel", e));
            }
        }
        super.tearDown();
    }

    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.CallAttributes"})
    public void testCallAttributesHelpers() {
        if (!mShouldTestTelecom) {
            return;
        }
        assertFalse(mOutgoingCallAttributes.equals(mIncomingCallAttributes));
    }

    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.CallAttributes",
            "android.telecom.CallAttributes#getPhoneAccountHandle",
            "android.telecom.CallAttributes#getCallType",
            "android.telecom.CallAttributes#getCallCapabilities",
            "android.telecom.CallAttributes#getDisplayName",
            "android.telecom.CallAttributes#getDirection",
            "android.telecom.CallAttributes#getAddress"})
    public void testCallAttributesGetters() {
        if (!mShouldTestTelecom) {
            return;
        }
        // outgoing call
        assertEquals(DEFAULT_T_HANDLE, mOutgoingCallAttributes.getPhoneAccountHandle());
        assertEquals(AUDIO_CALL, mOutgoingCallAttributes.getCallType());
        assertEquals(CallAttributes.SUPPORTS_SET_INACTIVE,
                mOutgoingCallAttributes.getCallCapabilities());
        assertEquals(TEST_NAME_1, mOutgoingCallAttributes.getDisplayName());
        assertEquals(DIRECTION_OUTGOING, mOutgoingCallAttributes.getDirection());
        assertEquals(TEST_URI_1, mOutgoingCallAttributes.getAddress());

        // incoming call
        assertEquals(DEFAULT_T_HANDLE, mIncomingCallAttributes.getPhoneAccountHandle());
        assertEquals(AUDIO_CALL, mIncomingCallAttributes.getCallType());
        assertEquals(CallAttributes.SUPPORTS_SET_INACTIVE,
                mIncomingCallAttributes.getCallCapabilities());
        assertEquals(TEST_NAME_2, mIncomingCallAttributes.getDisplayName());
        assertEquals(DIRECTION_INCOMING, mIncomingCallAttributes.getDirection());
        assertEquals(TEST_URI_2, mIncomingCallAttributes.getAddress());
    }

    /**
     * Ensure early failure for TelecomManager#addCall whenever a null argument is passed in.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall"})
    public void testAddCallWithNullArgument() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            mTelecomManager.addCall(null, Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(CallControl result) {
                    fail("test should never execute onResult");
                }
            }, mCall1.mHandshakes, mCall1.mEvents);
            fail("test should have thrown an exception already");
        } catch (Exception e) {
            // test passes
        }
    }

    /**
     * Ensure the state transitions of a successful outgoing call are correct.
     * State Transitions:  New -> * Connecting * -> Active -> Disconnecting -> Disconnected
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#disconnect"})
    public void testAddOutgoingCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link Call.Details#PROPERTY_IS_TRANSACTIONAL} is properly set to true when a
     * transactional outgoing call has been added and set active.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#disconnect"})
    public void testAddOutgoingCallHasPropertyIsTransactional() {
        if (!mShouldTestTelecom || !Flags.voipAppActionsSupport()) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            // Ensure that the call has PROPERTY_IS_TRANSACTIONAL:
            Call call = getLastAddedCall();
            assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_IS_TRANSACTIONAL));
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  New -> * Ringing -> Active * -> Disconnecting -> Disconnected
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#disconnect"})
    public void testAddIncomingCallAndSetActive() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link Call.Details#PROPERTY_IS_TRANSACTIONAL} is properly set to true when a
     * transactional incoming call has been added and set active.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#disconnect"})
    public void testAddIncomingCallHasPropertyIsTransactional() {
        if (!mShouldTestTelecom || !Flags.voipAppActionsSupport()) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            // Ensure that the call has PROPERTY_IS_TRANSACTIONAL:
            Call call = getLastAddedCall();
            assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_IS_TRANSACTIONAL));
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  New -> * Ringing -> Answered* -> Disconnecting -> Disconnected
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#answer",
            "android.telecom.CallControl#disconnect"})
    public void testAddIncomingCallAndAnswer() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            callControlAction(ANSWER, mCall1, AUDIO_CALL);
            assertNumCalls(getInCallService(), 1);
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  Created -> Ringing -> Disconnected -> Destroyed
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect"})
    public void testRejectIncomingCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_RINGING, getLastAddedCall().getState());
            try {
                callControlAction(DISCONNECT, mCall1, DisconnectCause.ERROR);
                fail("testRejectIncomingCall: forced fail b/c IllegalArgumentException not thrown");
            } catch (IllegalArgumentException e) {
                assertNotNull(e);
            }
            callControlAction(DISCONNECT, mCall1, DisconnectCause.REJECTED);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure transactional calls can transition from inactive to active multiple times
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#setInactive",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#disconnect"})
    public void testToggleActiveAndInactive() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            // set the call active
            callControlAction(SET_ACTIVE, mCall1);
            assertCallState(getLastAddedCall(), Call.STATE_ACTIVE);
            // toggle hold
            callControlAction(SET_INACTIVE, mCall1);
            assertCallState(getLastAddedCall(), Call.STATE_HOLDING);
            callControlAction(SET_ACTIVE, mCall1);
            assertCallState(getLastAddedCall(), Call.STATE_ACTIVE);
            callControlAction(SET_INACTIVE, mCall1);
            assertCallState(getLastAddedCall(), Call.STATE_HOLDING);
            // disconnect
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Test the scenario where an outgoing call starts toggles the video state.
     */
    public void testTransactionalVideoStateChanges() {
        if (!mShouldTestTelecom || !Flags.transactionalVideoState()) {
            return;
        }
        try {
            startCallWithAttributesAndVerify(new CallAttributes.Builder(DEFAULT_T_HANDLE,
                    DIRECTION_OUTGOING, TEST_NAME_1, TEST_URI_1)
                    .setCallType(CallAttributes.AUDIO_CALL)
                    .setCallCapabilities(CallAttributes.SUPPORTS_VIDEO_CALLING)
                    .build(), mCall1);
            // set the call active
            callControlAction(SET_ACTIVE, mCall1);
            waitUntilVideoStateIs(AUDIO_CALL, mCall1);
            // change the video state to VIDEO
            callControlAction(REQUEST_VIDEO_STATE, mCall1, VIDEO_CALL);
            waitUntilVideoStateIs(CallAttributes.VIDEO_CALL, mCall1);
            // change the video state back to AUDIO
            callControlAction(REQUEST_VIDEO_STATE, mCall1, AUDIO_CALL);
            waitUntilVideoStateIs(AUDIO_CALL, mCall1);
            // disconnect
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Common fail case: Test the scenario where an outgoing call that does not set the
     * {@link CallAttributes.CallCapability#SUPPORTS_VIDEO_CALLING}
     */
    public void testTransactionalVideoStateChanges_withoutVideoCapabilitiesForCall() {
        if (!mShouldTestTelecom || !Flags.transactionalVideoState()) {
            return;
        }
        try {
            startCallWithAttributesAndVerify(
                    new CallAttributes.Builder(DEFAULT_T_HANDLE, DIRECTION_OUTGOING,
                            TEST_NAME_1, TEST_URI_1)
                            .setCallType(CallAttributes.AUDIO_CALL)
                            .setCallCapabilities(0 /* purposely do not add VIDEO capabilities */)
                            .build(), mCall1);
            // set the call active
            callControlAction(SET_ACTIVE, mCall1);
            waitUntilVideoStateIs(AUDIO_CALL, mCall1);
            final CountDownLatch latch = new CountDownLatch(1);
            // request the video state change
            mCall1.mCallControl.requestVideoState(CallAttributes.VIDEO_CALL, Runnable::run,
                    new OutcomeReceiver<Void, CallException>() {
                        @Override
                        public void onResult(Void result) {
                        }

                        @Override
                        public void onError(CallException exception) {
                            latch.countDown();
                        }
                    });
            // wait for the latch to count down signaling the onError was called
            assertOnErrorWasReceived(latch);
            // disconnect
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Common fail case: Test the scenario where an application failed to register a PhoneAccount
     * with {@link PhoneAccount#CAPABILITY_SUPPORTS_VIDEO_CALLING} and requests video state changes
     * via {@link android.telecom.CallControl#requestVideoState(int, Executor, OutcomeReceiver)}
     */
    public void testTransactionalVideoStateChanges_withoutVideoCapabilitiesInAccount() {
        if (!mShouldTestTelecom || !Flags.transactionalVideoState()) {
            return;
        }
        try {
            mTelecomManager.registerPhoneAccount(NO_VIDEO_CAPABILITIES_ACCOUNT);
            startCallWithAttributesAndVerify(
                    new CallAttributes.Builder(NO_VIDEO_CAPABILITIES_T_HANDLE, DIRECTION_OUTGOING,
                            TEST_NAME_1, TEST_URI_1)
                            .setCallType(CallAttributes.AUDIO_CALL)
                            .setCallCapabilities(CallAttributes.SUPPORTS_VIDEO_CALLING)
                            .build(), mCall1);
            // set the call active
            callControlAction(SET_ACTIVE, mCall1);
            waitUntilVideoStateIs(AUDIO_CALL, mCall1);
            final CountDownLatch latch = new CountDownLatch(1);
            // request the video state change
            mCall1.mCallControl.requestVideoState(CallAttributes.VIDEO_CALL, Runnable::run,
                    new OutcomeReceiver<Void, CallException>() {
                        @Override
                        public void onResult(Void result) {
                        }

                        @Override
                        public void onError(CallException exception) {
                            latch.countDown();
                        }
                    });
            // wait for the latch to count down signaling the onError was called
            assertOnErrorWasReceived(latch);
            // disconnect
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    private void waitUntilVideoStateIs(int videoState, TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return videoState;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.getVideoState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "Video State is not desired state");
    }

    /**
     * Calls that do not have the {@link CallAttributes#SUPPORTS_SET_INACTIVE} and call
     * {@link CallControl#setInactive(Executor, OutcomeReceiver)} should always result in an
     * OutcomeReceiver#onError with CallException#CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallAttributes#SUPPORTS_STREAM",
            "android.telecom.CallAttributes#SUPPORTS_SET_INACTIVE",
            "android.telecom.CallException(java.lang.String, int)"})
    public void testCallDoesNotSupportHoldResultsInOnError() {
        if (!mShouldTestTelecom) {
            return;
        }
        final CallAttributes cannotSetInactiveAttributes =
                new CallAttributes.Builder(DEFAULT_T_HANDLE, DIRECTION_OUTGOING,
                        TEST_NAME_1, TEST_URI_1)
                        .setCallCapabilities(CallAttributes.SUPPORTS_STREAM)
                        .build();

        assertEquals(CallAttributes.SUPPORTS_STREAM,
                cannotSetInactiveAttributes.getCallCapabilities());

        CallException cannotSetInactiveException = new CallException("call does not support hold",
                CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL);
        try {
            cleanup();
            startCallWithAttributesAndVerify(cannotSetInactiveAttributes, mCall1);
            mCall1.mCallControl.setInactive(Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(Void result) {
                    fail("testCannotSetInactiveExpectFail:"
                            + " onResult should not be called");
                }

                @Override
                public void onError(CallException exception) {
                    assertEquals(cannotSetInactiveException.getCode(),
                            exception.getCode());
                }
            });
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Calling any {@link CallControl} API after calling
     * {@link CallControl#disconnect(DisconnectCause, Executor, OutcomeReceiver)} will always
     * result in OutcomeReceiver#onError.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallControl#setActive"})
    public void testUsingCallControlAfterDisconnect() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);

            mCall1.mCallControl.setActive(Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(Void result) {
                    fail("testUsingCallControlAfterDisconnect:"
                            + " onResult should not be called");
                }

                @Override
                public void onError(CallException exception) {
                }
            });
        } finally {
            cleanup();
        }
    }


    /**
     * Ensure {@link CallControlCallback#onDisconnect(DisconnectCause, Consumer)}
     * is being called and destroying the call.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControlCallback#onDisconnect"})
    public void testAddIncomingCallAndRejectWithCallEventCallback() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            mCall1.resetAllCallbackVerifiers();
            assertFalse(mCall1.mWasOnDisconnectCalled);
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            Call call = getLastAddedCall();
            call.reject(Call.REJECT_REASON_DECLINED);
            assertNumCalls(getInCallService(), 0);
            assertTrue(mCall1.mWasOnDisconnectCalled);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallControlCallback#onAnswer(int, Consumer)} is being called
     * and setting the call to active.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControlCallback#onAnswer"})
    public void testAddIncomingCallOnAnswer() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            Call call = getLastAddedCall();
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return Call.STATE_ACTIVE;
                }

                @Override
                public Object actual() {
                    return getCallWithId(mCall1.getTelecomCallId()).getState();
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "call not set to active");
            call.disconnect();
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure when a client rejects CallControlCallback#onAnswer, the call is disconnected.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControlCallback#onAnswer"})
    public void testAddIncomingCallOnAnswer_RejectCallback() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            Call call = getLastAddedCall();
            // reject the next CallControlCallback
            mCall1.setClientResponse(Boolean.FALSE);
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            // assert the CallControlCallback#onAnswer was called
            verifyCallControlCallback(ON_ANSWER, mCall1,
                    "onAnswer CallControlCallback was never called");
            assertNumCalls_OrICSUnbound(getInCallService(), 0); // If the ICS is already
            // unbound, this is another signal that all calls have disconnected.
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure when a client rejects CallControlCallback#onSetActive, the call is still in an
     * inactive state.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControlCallback#onSetActive"})
    public void testOngoingCall_RejectSetActiveCallback() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            // set the call active and then place on hold/inactive
            callControlAction(SET_ACTIVE, mCall1);
            callControlAction(SET_INACTIVE, mCall1);
            // reject the next CallControlCallback
            mCall1.setClientResponse(Boolean.FALSE);
            Call call = getLastAddedCall();
            call.unhold(); // calls CallControlCallback#onSetActive
            // assert CallControlCallback#onSetActive was called
            verifyCallControlCallback(ON_SET_ACTIVE, mCall1,
                    "onSetActive CallControlCallback was never called");
            assertCallState(call, Call.STATE_HOLDING);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }


    /**
     * Test two transactional sequential calls transition to the correct states.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl"})
    public void testCallStatesForTwoLiveTransactionalCalls() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();

            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);

            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());

            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall2);
            callControlAction(SET_ACTIVE, mCall2);
            assertNumCalls(getInCallService(), 2);

            Call call1 = getCallWithId(mCall1.getTelecomCallId());
            Call call2 = getCallWithId(mCall2.getTelecomCallId());

            assertEquals(Call.STATE_HOLDING, call1.getState());
            assertEquals(Call.STATE_ACTIVE, call2.getState());

            callControlAction(DISCONNECT, mCall2);
            assertNumCalls(getInCallService(), 1);

            assertEquals(Call.STATE_HOLDING, call1.getState());

            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Test 1 sim call and 1 transactional call
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl"})
    public void testSimCallAndTransactionalCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        try {
            cleanup();
            // conditionally register
            registerSimAccountIfNeeded();
            // start a sim call and set it active
            MockConnection simConnection = placeSimCallAndSetActive();
            // start a Transactional SM call
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall2);
            // set the Transactional call to active
            callControlAction(SET_ACTIVE, mCall2);
            // check call states
            assertConnectionState(simConnection, Connection.STATE_HOLDING);
            assertCallState(getCallWithId(mCall2.getTelecomCallId()), Call.STATE_ACTIVE);
            // disconnect the Transactional call
            callControlAction(DISCONNECT, mCall2);
            // check the call states
            assertConnectionState(simConnection, Connection.STATE_HOLDING);
            // disconnect the sim call
            simConnection.onDisconnect();
        } finally {
            runWithShellPermissionIdentity(() -> {
                mTelecomManager.unregisterPhoneAccount(
                        TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle());
            });
            cleanup();
        }
    }

    private void registerSimAccountIfNeeded() {
        // if the account is not present
        if (mTelecomManager.getPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle())
                == null) {
            // register
            runWithShellPermissionIdentity(() -> {
                mTelecomManager.registerPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT);
            });
        }
    }

    /**
     * test {@link CallEventCallback#onCallEndpointChanged(CallEndpoint)} is called and provides a
     * non-null {@link CallEndpoint}.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallEventCallback#onCallEndpointChanged"})
    public void testOnChangedCallEndpoint() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertNull(mCall1.mEvents.getCurrentCallEndpoint());
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyCallEndpointIsNotNull(mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallEventCallback#onAvailableCallEndpointsChanged(List)} is called and provides a
     * list of non-null {@link CallEndpoint}s.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallEventCallback#onAvailableCallEndpointsChanged"})
    public void testOnAvailableCallEndpointsChanged() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertNull(mCall1.mEvents.getAvailableEndpoints());
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyOnAvailableEndpointsIsNotNull(mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallEventCallback#onMuteStateChanged(boolean)} is called properly relays the
     * changes to the audio mute state.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallEventCallback#onMuteStateChanged"})
    public void testGetMuteState() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            mCall1.resetAllCallbackVerifiers();
            assertFalse(mCall1.mEvents.mWasMuteStateChangedCalled);
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyMuteStateCallbackWasCalled(true, mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }


    /**
     * test {@link CallControl#requestMuteState(boolean, Executor, OutcomeReceiver)} changes the
     * mute state of a call.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallControl#setActive",
            "android.telecom.CallControl#requestMuteState",
            "android.telecom.CallEventCallback#onMuteStateChanged"})
    public void testRequestMuteState() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            mCall1.resetAllCallbackVerifiers();
            assertFalse(mCall1.mEvents.mWasMuteStateChangedCalled);
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            callControlAction(SET_MUTE_STATE, mCall1, true /* isMuted */);
            waitUntilMuteStateIs(true /* isMuted */, mCall1);
            callControlAction(SET_MUTE_STATE, mCall1, false /* isMuted */);
            waitUntilMuteStateIs(false /* isMuted */, mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallControl#sendEvent(String, Bundle)} does not throw an exception when given
     * an event without a Bundle value.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallControl#sendEvent"})
    public void testSendCallEvent() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            TestParcelable originalParcelable = createTestParcelable();
            mCall1.mCallControl.sendEvent(OTT_TEST_EVENT_NAME,
                    createTestBundle(originalParcelable));
            // verify the event was received
            mOnConnectionEventCounter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
            String event = (String) (mOnConnectionEventCounter.getArgs(0)[1]);
            Bundle extras = (Bundle) (mOnConnectionEventCounter.getArgs(0)[2]);
            assertEquals(OTT_TEST_EVENT_NAME, event);
            verifyTestBundle(extras, originalParcelable);
            mOnConnectionEventCounter.reset();
            // disconnect
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallEventCallback#onEvent(String, Bundle)} is called when an InCallService
     * creates a new event.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallEventCallback#onEvent"})
    public void testOnCallEvent() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertNull(mCall1.mEvents.mLastEventReceived);
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            // simulate an InCallService sending a call event
            TestParcelable originalParcelable = createTestParcelable();
            Bundle testBundle = createTestBundle(originalParcelable);
            getLastAddedCall().sendCallEvent(OTT_TEST_EVENT_NAME, testBundle);
            // wait for the onEvent to be called
            waitUntilConditionIsTrueOrTimeout(
                    new Condition() {
                        @Override
                        public Object expected() {
                            return true;
                        }

                        @Override
                        public Object actual() {
                            Pair<String, Bundle> lastEvent = mCall1.mEvents.mLastEventReceived;
                            if ((lastEvent != null
                                    && OTT_TEST_EVENT_NAME.equals(lastEvent.first))) {
                                verifyTestBundle(lastEvent.second, originalParcelable);
                                return true;
                            }
                            return false;
                        }
                    },
                    WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "onEvent was not called with correct Bundle");

        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallControl#requestCallEndpointChange(CallEndpoint, Executor, OutcomeReceiver)}
     * can switch {@link CallEndpoint}s if there is another endpoint available.  This test will not
     * request an endpoint change if the device only has a single endpoint.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall",
            "android.telecom.CallControl#disconnect",
            "android.telecom.CallControl#requestCallEndpointChange"})
    public void testRequestCallEndpointChangeViaCallControl() {
        if (!mShouldTestTelecom) {
            return;
        }

        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);

            // wait on handlers
            TestUtils.waitOnAllHandlers(getInstrumentation());

            // query the current endpoints
            List<CallEndpoint> endpoints = mCall1.mEvents.getAvailableEndpoints();

            // if another endpoint is available, request a switch
            if (endpoints != null && endpoints.size() > 1) {
                // verify there is at least one endpoint that is non-null
                verifyCallEndpointIsNotNull(mCall1);
                int startingEndpointType = mCall1.mEvents
                        .getCurrentCallEndpoint().getEndpointType();

                // iterate the other endpoints until an endpoint other than the startingEndpointType
                // is found
                CallEndpoint anotherEndpoint = null;
                for (CallEndpoint endpoint : endpoints) {
                    if (endpoint != null
                            && endpoint.getEndpointType() != startingEndpointType) {
                        anotherEndpoint = endpoint;
                        break;
                    }
                }
                // CallControl#requestCallEndpointChange
                requestAndAssertEndpointChange(mCall1, anotherEndpoint);
                assertNotNull(anotherEndpoint);
                assertEndpointType(getInCallService(), anotherEndpoint.getEndpointType());
                // disconnect the call
                callControlAction(DISCONNECT, mCall1);
            }
        } finally {
            cleanup();
        }
    }

    public void verifyCallEndpointIsNotNull(TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.getCurrentCallEndpoint() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_CALL_ENDPOINT_UPDATE);
    }

    public void verifyOnAvailableEndpointsIsNotNull(TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.getAvailableEndpoints() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_AVAILABLE_ENDPOINTS_UPDATE);
    }

    public void verifyMuteStateCallbackWasCalled(boolean expected, TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.mWasMuteStateChangedCalled;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_MUTE_STATE_CHANGED);
    }


    private void waitUntilMuteStateIs(boolean expected, TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, expected
                        ? "Wanted mute state to be <MUTED> but never reached the <MUTE> state" :
                        "Wanted mute state to be <UN-MUTED> but never reached <UN-MUTED> state");
    }

    public void requestAndAssertEndpointChange(TelecomCtsVoipCall call, CallEndpoint endpoint) {
        final CountDownLatch latch = new CountDownLatch(1);
        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);

        CallControl callControl = call.mCallControl;
        if (callControl == null) {
            fail("callControl object is null");
            return;
        }

        call.mCallControl.requestCallEndpointChange(endpoint, Runnable::run, outcome);

        assertOnResultWasReceived(latch);
    }

    // waits for the latch to countdown or times out and fails
    public void assertOnResultWasReceived(CountDownLatch latch) {
        Log.i(TAG, "assertOnResultWasReceived: waiting for latch");
        try {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail(FAIL_MSG_OUTCOME_RECEIVER);
            }

        } catch (InterruptedException ie) {
            fail(FAIL_MSG_OUTCOME_RECEIVER);
        }
    }

    public void assertOnErrorWasReceived(CountDownLatch latch) {
        Log.i(TAG, "assertOnErrorWasReceived: waiting for latch");
        try {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail("failed to receive onError");
            }

        } catch (InterruptedException ie) {
            fail("failed to receive onError");
        }
    }

    public String startCallWithAttributesAndVerify(CallAttributes attributes,
            TelecomCtsVoipCall call) {
        final CountDownLatch latch = new CountDownLatch(1);
        postCallNotification(); // required in order to maintain foreground service delegation
        mTelecomManager.addCall(attributes, Runnable::run, new OutcomeReceiver<>() {
            @Override
            public void onResult(CallControl callControl) {
                Log.i(TAG, "onResult: adding callControl to callObject");

                if (callControl == null) {
                    fail(FAIL_MSG_CALL_CONTROL_NULL);
                }

                call.onAddCallControl(callControl);
                latch.countDown();
            }

            @Override
            public void onError(CallException exception) {
                Log.i(TAG, "testRegisterApp: onError");
            }
        }, call.mHandshakes, call.mEvents);

        assertOnResultWasReceived(latch);

        return call.mCallControl.getCallId().toString();
    }

    public void callControlAction(String action, TelecomCtsVoipCall call, Object... objects) {
        final CountDownLatch latch = new CountDownLatch(1);
        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);
        DisconnectCause disconnectCause = new DisconnectCause(DisconnectCause.LOCAL);

        CallControl callControl = call.mCallControl;
        if (callControl == null) {
            fail("callControl object is null");
            return;
        }

        switch (action) {
            case SET_ACTIVE:
                call.mCallControl.setActive(Runnable::run, outcome);
                break;
            case ANSWER:
                int videoState = AUDIO_CALL;
                if (isArgumentAvailable(objects)) {
                    videoState = (int) objects[0];
                }
                call.mCallControl.answer(videoState, Runnable::run, outcome);
                break;
            case SET_INACTIVE:
                call.mCallControl.setInactive(Runnable::run, outcome);
                break;
            case SET_MUTE_STATE:
                boolean isMuted = false;
                if (isArgumentAvailable(objects)) {
                    isMuted = (boolean) objects[0];
                }
                call.mCallControl.requestMuteState(isMuted, Runnable::run, outcome);
                break;
            case REQUEST_VIDEO_STATE:
                int requestedVideoState = AUDIO_CALL;
                if (isArgumentAvailable(objects)) {
                    requestedVideoState = (int) objects[0];
                }
                call.mCallControl.requestVideoState(requestedVideoState, Runnable::run, outcome);
                break;
            case DISCONNECT:
                if (isArgumentAvailable(objects)) {
                    disconnectCause = new DisconnectCause((int) objects[0]);
                }
                call.mCallControl.disconnect(disconnectCause, Runnable::run, outcome);
                break;
            default:
                fail("should never reach the default case");
        }

        assertOnResultWasReceived(latch);
    }

    private boolean isArgumentAvailable(Object... objects) {
        return objects != null && objects.length >= 1;
    }

    @NonNull
    private Call getLastAddedCall() {
        waitOnInCallService();
        waitOnCallToBeAdded();
        return getInCallService().getLastCall();
    }

    // Due to timing issues, we need to wait for the MockInCall service to add the call to its call
    // list. This reduces flake.
    public void waitOnCallToBeAdded() {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return getInCallService().getLastCall() != null;
            }
        }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "MockInCallService failed to get non-null Call");
    }

    @NonNull
    private Call getCallWithId(String id) {
        waitOnInCallService();
        Call call = getInCallService().getCallWithId(id);
        assertNotNull(call);
        return call;
    }

    private MockConnection placeSimCallAndSetActive() {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
        placeAndVerifyCall(extras);
        MockConnection conn = verifyConnectionForOutgoingCall();
        conn.setActive();
        assertConnectionState(conn, Connection.STATE_ACTIVE);
        return conn;
    }

    public void verifyCallControlCallback(String callback, TelecomCtsVoipCall call,
            String errorMessage) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        switch (callback){
                            case ON_SET_ACTIVE:
                                return call.mWasOnSetActiveCalled;
                            case ON_SET_INACTIVE:
                                return call.mWasOnSetInactiveCalled;
                            case ON_ANSWER:
                                return call.mWasOnAnswerCalled;
                            case ON_DISCONNECT:
                                return call.mWasOnDisconnectCalled;
                            default:
                                throw new IllegalArgumentException(
                                        "verifyCallControlCallback: undefined callback "
                                                + callback);
                        }
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, errorMessage);
        // The call should go back to completing transactions, otherwise the call can go into a
        // state where it rejects all CallControlCallbacks which is most likely unwanted
        call.setClientResponse(Boolean.TRUE);
    }

    private void cleanup() {
        Log.i(TAG, "cleanup: method running");
        try {
            // clear the posted notification
            mNotificationManager.cancel(NOTIFICATION_ID);
            // ensure the call objects default to completing transactions
            mCall1.resetAllCallbackVerifiers();
            mCall2.resetAllCallbackVerifiers();
            // if the CallControl object is still available, we should send the signal to disconnect
            // because its possible the InCallService is already unbound.
            safelyDisconnect(mCall1);
            safelyDisconnect(mCall2);
            // It's possible that they MockInCallService either has not bound yet or is already
            // unbond. Therefore, the block below might not run.
            if (mInCallCallbacks.getService() != null) {
                mInCallCallbacks.getService().disconnectAllCalls();
                mInCallCallbacks.getService().clearCallList();
            }
            // In the event the ICS was not able to disconnect any stuck calls, the last hope is to
            // run the telecom cleanup command.
            TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
        } catch (Exception e) {
            Log.i(TAG, FAIL_MSG_DURING_CLEANUP);
        }
    }

    // send the client side signal to disconnect the call if the call control object is available
    private void safelyDisconnect(TelecomCtsVoipCall call) {
        if (call != null && call.mCallControl != null) {
            call.mCallControl.disconnect(new DisconnectCause(DisconnectCause.LOCAL), Runnable::run,
                    new OutcomeReceiver<Void, CallException>() {
                        @Override
                        public void onResult(Void result) {
                            // pass through
                        }
                    });
        }
    }

    // necessary step in order to start posting notifications
    private void configureNotificationChannel() {
        NotificationChannel callsChannel = new NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(callsChannel);
    }

    // Starting in Android U, Telecom is expecting VoIP clients to post a notification in order to
    // grant Foreground Service Delegation. Failing to do so can cause unwanted behavior like
    // suppressing audio.
    private void postCallNotification() {
        Person person = new Person.Builder().setName(TEST_NAME_1).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(FAKE_INTENT_ACTION), PendingIntent.FLAG_IMMUTABLE);
        Notification callNot = new Notification.Builder(mContext, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_24dp)
                .setStyle(Notification.CallStyle.forOngoingCall(person, pendingIntent))
                .setFullScreenIntent(pendingIntent, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, callNot);
    }
}
