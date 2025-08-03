/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.services.telephony.rcs;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.os.RemoteException;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.TestExecutorService;
import com.android.services.telephony.rcs.validator.ValidationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class MessageTransportWrapperTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;

    private static final SipMessage TEST_MESSAGE = new SipMessage(
            "INVITE sip:callee@ex.domain.com SIP/2.0",
            "Via: SIP/2.0/UDP ex.place.com;branch=z9hG4bK776asdhds",
            new byte[0]);

    // Derived from TEST_MESSAGE above.
    private static final String TEST_TRANSACTION_ID = "z9hG4bK776asdhds";

    @Mock private ISipDelegateMessageCallback mDelegateMessageCallback;
    @Mock private TransportSipMessageValidator mTransportSipSessionValidator;
    @Mock private ISipDelegate mISipDelegate;

    // Test executor that just calls run on the Runnable provided in execute.
    private ScheduledExecutorService mExecutor = new TestExecutorService();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testImsConfigurationChanged() {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        // Ensure IMS config changes are propagated to the message tracker.
        tracker.onConfigurationChanged(c);
        verify(mTransportSipSessionValidator).onConfigurationChanged(c);
    }

    @SmallTest
    @Test
    public void testOpenTransport() {
        HashSet<String> allowedTags = new HashSet<>(1);
        allowedTags.add("testTag");
        HashSet<FeatureTagState> deniedTags = new HashSet<>(1);
        deniedTags.add(new FeatureTagState("testBadTag",
                SipDelegateManager.DENIED_REASON_INVALID));
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        // Ensure openTransport passes denied tags to the session tracker
        tracker.openTransport(mISipDelegate, allowedTags, deniedTags);
        verify(mTransportSipSessionValidator).onTransportOpened(allowedTags, deniedTags);
    }

    @SmallTest
    @Test
    public void testRegistrationStateChanged() throws Exception {
        ArraySet<String> callIds = new ArraySet<>(2);
        callIds.add("callId1");
        callIds.add("callId2");
        // empty registration state for testing
        DelegateRegistrationState state = new DelegateRegistrationState.Builder().build();
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());

        Consumer<Set<String>> callIdConsumer = trackerRegStateChanged(tracker, state);
        callIdConsumer.accept(callIds);
        // Verify that the pending call IDs are closed properly.
        for (String callId : callIds) {
            verify(mTransportSipSessionValidator).onSipSessionCleanup(callId);
            verify(mISipDelegate).cleanupSession(callId);
        }
    }

    @SmallTest
    @Test
    public void testCloseGracefully() throws Exception {
        int closingReason = DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE;
        int closedReason = DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED;
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();

        Boolean[] result = new Boolean[1];
        Consumer<Set<String>> callIdConsumer = closeTrackerGracefully(tracker, closingReason,
                closedReason, (r) -> result[0] = r);
        callIdConsumer.accept(Collections.emptySet());
        // Verify that the pending call IDs are closed properly.
        verify(mTransportSipSessionValidator, never()).onSipSessionCleanup(anyString());
        verify(mISipDelegate, never()).cleanupSession(anyString());
        // Result is true in the case that all call IDs were successfully closed.
        assertTrue(result[0]);
    }

    @SmallTest
    @Test
    public void testCloseGracefullyForceCloseCallIds() throws Exception {
        ArraySet<String> callIds = new ArraySet<>(2);
        callIds.add("callId1");
        callIds.add("callId2");
        int closingReason = DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE;
        int closedReason = DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED;
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());

        Boolean[] result = new Boolean[1];
        Consumer<Set<String>> callIdConsumer = closeTrackerGracefully(tracker, closingReason,
                closedReason, (r) -> result[0] = r);
        callIdConsumer.accept(callIds);
        // Verify that the pending call IDs are closed properly.
        for (String callId : callIds) {
            verify(mTransportSipSessionValidator).onSipSessionCleanup(callId);
            verify(mISipDelegate).cleanupSession(callId);
        }
        // Result is false in this case because there were still callIds left that were not
        // successfully closed.
        assertFalse(result[0]);
    }

    @SmallTest
    @Test
    public void testClose() throws Exception {
        ArraySet<String> callIds = new ArraySet<>(2);
        callIds.add("callId1");
        callIds.add("callId2");
        int closedReason = SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED;
        doReturn(callIds).when(mTransportSipSessionValidator).closeSessions(closedReason);
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());

        tracker.close(closedReason);
        // Verify that the pending call IDs are closed properly.
        for (String callId : callIds) {
            verify(mTransportSipSessionValidator).onSipSessionCleanup(callId);
            verify(mISipDelegate).cleanupSession(callId);
        }
    }

    @SmallTest
    @Test
    public void testDelegateConnectionSendOutgoingMessage() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();

        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        doReturn(ValidationResult.SUCCESS)
                .when(mTransportSipSessionValidator)
                .verifyOutgoingMessage(TEST_MESSAGE, 1 /*version*/);
        tracker.getDelegateConnection().sendMessage(TEST_MESSAGE, 1 /*version*/);
        verify(mISipDelegate).sendMessage(TEST_MESSAGE, 1 /*version*/);

        doThrow(new RemoteException()).when(mISipDelegate).sendMessage(any(), anyLong());
        tracker.getDelegateConnection().sendMessage(TEST_MESSAGE, 1 /*version*/);
        verify(mDelegateMessageCallback).onMessageSendFailure(any(),
                eq(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD));

        doReturn(new ValidationResult(
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED, ""))
                .when(mTransportSipSessionValidator)
                .verifyOutgoingMessage(TEST_MESSAGE, 1 /*version*/);
        tracker.getDelegateConnection().sendMessage(TEST_MESSAGE, 1 /*version*/);
        verify(mDelegateMessageCallback).onMessageSendFailure(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
    }

    @SmallTest
    @Test
    public void testDelegateConnectionNotifyMessageReceived() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        tracker.getDelegateConnection().notifyMessageReceived(TEST_TRANSACTION_ID);
        verify(mISipDelegate).notifyMessageReceived(TEST_TRANSACTION_ID);
        verify(mTransportSipSessionValidator).acknowledgePendingMessage(TEST_TRANSACTION_ID);
    }

    @SmallTest
    @Test
    public void testDelegateConnectionNotifyMessageReceiveError() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        tracker.getDelegateConnection().notifyMessageReceiveError(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        verify(mISipDelegate).notifyMessageReceiveError(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        verify(mTransportSipSessionValidator).notifyPendingMessageFailed(TEST_TRANSACTION_ID);
    }

    @SmallTest
    @Test
    public void testDelegateConnectionCloseSession() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        tracker.getDelegateConnection().cleanupSession("testCallId");
        verify(mISipDelegate).cleanupSession("testCallId");
        verify(mTransportSipSessionValidator).onSipSessionCleanup("testCallId");
    }

    @SmallTest
    @Test
    public void testDelegateOnMessageReceived() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());

        doReturn(ValidationResult.SUCCESS)
                .when(mTransportSipSessionValidator).verifyIncomingMessage(TEST_MESSAGE);
        tracker.getMessageCallback().onMessageReceived(TEST_MESSAGE);
        verify(mDelegateMessageCallback).onMessageReceived(TEST_MESSAGE);

        doThrow(new RemoteException()).when(mDelegateMessageCallback).onMessageReceived(any());
        tracker.getMessageCallback().onMessageReceived(TEST_MESSAGE);
        verify(mISipDelegate).notifyMessageReceiveError(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);

        doReturn(new ValidationResult(
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD, ""))
                .when(mTransportSipSessionValidator).verifyIncomingMessage(TEST_MESSAGE);
        tracker.getMessageCallback().onMessageReceived(TEST_MESSAGE);
        verify(mISipDelegate, times(2)).notifyMessageReceiveError(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);
    }

    @SmallTest
    @Test
    public void testDelegateOnMessageSent() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        tracker.getMessageCallback().onMessageSent(TEST_TRANSACTION_ID);
        verify(mTransportSipSessionValidator).acknowledgePendingMessage(TEST_TRANSACTION_ID);
        verify(mDelegateMessageCallback).onMessageSent(TEST_TRANSACTION_ID);
    }

    @SmallTest
    @Test
    public void testDelegateOnMessageSendFailure() throws Exception {
        MessageTransportWrapper tracker = createTestMessageTransportWrapper();
        tracker.openTransport(mISipDelegate, Collections.emptySet(), Collections.emptySet());
        tracker.getMessageCallback().onMessageSendFailure(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        verify(mTransportSipSessionValidator).notifyPendingMessageFailed(TEST_TRANSACTION_ID);
        verify(mDelegateMessageCallback).onMessageSendFailure(TEST_TRANSACTION_ID,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
    }

    private MessageTransportWrapper createTestMessageTransportWrapper() {
        return new MessageTransportWrapper(TEST_SUB_ID,
                mExecutor, mDelegateMessageCallback, mTransportSipSessionValidator);
    }

    private Consumer<Set<String>> trackerRegStateChanged(MessageTransportWrapper tracker,
            DelegateRegistrationState state) {
        ArrayList<Consumer<Set<String>>> consumerCaptor = new ArrayList<>(1);
        Mockito.doAnswer(it -> {
            // Capture the consumer here.
            consumerCaptor.add(it.getArgument(0));
            return null;
        }).when(mTransportSipSessionValidator).onRegistrationStateChanged(any(), eq(state));
        tracker.onRegistrationStateChanged(state);
        verify(mTransportSipSessionValidator).onRegistrationStateChanged(any(), eq(state));
        assertFalse(consumerCaptor.isEmpty());
        return consumerCaptor.get(0);
    }

    private Consumer<Set<String>> closeTrackerGracefully(MessageTransportWrapper tracker,
            int closingReason, int closedReason, Consumer<Boolean> resultConsumer) {
        ArrayList<Consumer<Set<String>>> consumerCaptor = new ArrayList<>(1);
        Mockito.doAnswer(it -> {
            // Capture the consumer here.
            consumerCaptor.add(it.getArgument(0));
            return null;
        }).when(mTransportSipSessionValidator).closeSessionsGracefully(any(), eq(closingReason),
                eq(closedReason));
        tracker.closeGracefully(closingReason, closedReason, resultConsumer);
        verify(mTransportSipSessionValidator).closeSessionsGracefully(any(), eq(closingReason),
                eq(closedReason));
        assertFalse(consumerCaptor.isEmpty());
        return consumerCaptor.get(0);
    }
}
