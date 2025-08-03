/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.internal.telephony.TelephonyStatsLog.SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING;
import static com.android.internal.telephony.TelephonyStatsLog.SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.TestExecutorService;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.services.telephony.rcs.validator.IncomingTransportStateValidator;
import com.android.services.telephony.rcs.validator.OutgoingTransportStateValidator;
import com.android.services.telephony.rcs.validator.ValidationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(AndroidJUnit4.class)
public class TransportSipMessageValidatorTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;
    private static final int TEST_CONFIG_VERSION = 1;
    private static final SipMessage TEST_MESSAGE = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            // Typical Via
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: testid\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142",
            new byte[0]);

    @Mock
    private SipSessionTracker mSipSessionTracker;
    @Mock
    private IncomingTransportStateValidator mIncomingStateValidator;
    @Mock
    private OutgoingTransportStateValidator mOutgoingStateValidator;
    @Mock
    private RcsStats mRcsStats;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testTransportOpening() {
        TestExecutorService executor = new TestExecutorService();
        TransportSipMessageValidator tracker = getTestTracker(executor);
        tracker.onTransportOpened(Collections.emptySet(), Collections.emptySet());
        verify(mOutgoingStateValidator).open(Collections.emptySet(), Collections.emptySet());
        verify(mIncomingStateValidator).open();
        // Incoming messages are already verified
        assertTrue(isIncomingTransportOpen(tracker));
        // IMS config needs to be sent before outgoing messages can be verified.
        assertFalse(isOutgoingTransportOpen(tracker));
        tracker.onConfigurationChanged(getConfigBuilder(TEST_CONFIG_VERSION).build());
        // Incoming messages are already verified
        // Config set, transport is now open.
        assertTrue(isIncomingTransportOpen(tracker));
        assertTrue(isOutgoingTransportOpen(tracker));
    }

    @Test
    public void testTransportOpenConfigChange() {
        TestExecutorService executor = new TestExecutorService();
        TransportSipMessageValidator tracker = openTransport(executor);

        // Update IMS config version and send a message with an outdated version.
        tracker.onConfigurationChanged(getConfigBuilder(TEST_CONFIG_VERSION + 1).build());
        assertEquals(SipDelegateManager.MESSAGE_FAILURE_REASON_STALE_IMS_CONFIGURATION,
                verifyOutgoingTransportClosed(tracker));
    }

    @Test
    public void testMetricsResponse() {
        String testSipInviteMethod = "INVITE";
        String testSipMessageMethod = "MESSAGE";
        int testSipResponseCode = 200;
        int testMessageError = 0;

        TestExecutorService executor = new TestExecutorService();
        TransportSipMessageValidator tracker = openTransport(executor);
        // Since the incoming/outgoing messages were verified, there should have been two calls
        // to filter the message.
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, TEST_MESSAGE);
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING, TEST_MESSAGE);

        assertTrue(tracker.verifyOutgoingMessage(generateSipRequest("INVITE",
                "testId1"), TEST_CONFIG_VERSION).isValidated);
        verify(mRcsStats).onSipMessageRequest(eq("testId1"),
                eq(testSipInviteMethod),
                eq(SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING));

        assertTrue(tracker.verifyOutgoingMessage(generateSipRequest("MESSAGE",
                "testId2"), TEST_CONFIG_VERSION).isValidated);
        verify(mRcsStats).onSipMessageRequest(eq("testId2"),
                eq(testSipMessageMethod),
                eq(SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING));

        assertTrue(tracker.verifyIncomingMessage(
                 generateSipResponse("200", "OK", "testId2"))
                 .isValidated);
        verify(mRcsStats).onSipMessageResponse(eq(TEST_SUB_ID), eq("testId2"),
                eq(testSipResponseCode), eq(testMessageError));
    }

    @Test
    public void testSessionTrackerFiltering() {
        TestExecutorService executor = new TestExecutorService();
        TransportSipMessageValidator tracker = openTransport(executor);
        // Since the incoming/outgoing messages were verified, there should have been two calls
        // to filter the message.
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, TEST_MESSAGE);
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING, TEST_MESSAGE);
        // ensure pass through methods are working
        tracker.acknowledgePendingMessage("abc");
        verify(mSipSessionTracker).acknowledgePendingMessage("abc");
        tracker.notifyPendingMessageFailed("abc");
        verify(mSipSessionTracker).pendingMessageFailed("abc");
        tracker.onSipSessionCleanup("abc");
        verify(mSipSessionTracker).cleanupSession("abc");
        // Now have validators return a non-successful result for validation and the tracker should
        // not get the indication to filter the message.
        doReturn(new ValidationResult(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                "")).when(mOutgoingStateValidator).validate(any());
        doReturn(new ValidationResult(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                "")).when(mIncomingStateValidator).validate(any());
        assertFalse(tracker.verifyIncomingMessage(TEST_MESSAGE).isValidated);
        assertFalse(tracker.verifyOutgoingMessage(TEST_MESSAGE, TEST_CONFIG_VERSION).isValidated);
        // The number of times the filter method was called should still only be two after these
        // messages were not validated.
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, TEST_MESSAGE);
        verify(mSipSessionTracker).filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING, TEST_MESSAGE);
    }


    @Test
    public void testTransportClosingGracefullyNoPendingSessions() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        doReturn(Collections.emptySet()).when(mSipSessionTracker).getTrackedDialogs();
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.closeSessionsGracefully((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Since there are no pending call ids, this should be completed with no call ids pending.
        assertEquals(0, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void testTransportClosingGracefullyCloseCallIds() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<SipDialog> dialogs = new ArraySet<>();
        dialogs.add(SipDialog.fromSipMessage(TEST_MESSAGE));
        doReturn(dialogs).when(mSipSessionTracker).getTrackedDialogs();
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.closeSessionsGracefully((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Before executor executes, outgoing messages will be restricted due to pending call ids.
        assertTrue(l.getCount() >= 1);
        assertTrue(pendingCallIds.isEmpty());
        assertTrue(isIncomingTransportOpen(tracker));
        verify(mOutgoingStateValidator).restrict(anyInt());
        // pretend a sip message has been acknowledged, which closed pending call id. Since there
        // are no more pending call ids, the transport should move to closed.
        dialogs.clear();
        tracker.acknowledgePendingMessage("blah");
        assertEquals(0, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void testTransportClosingGracefullyThenForceClose() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<SipDialog> dialogs = new ArraySet<>();
        dialogs.add(SipDialog.fromSipMessage(TEST_MESSAGE));
        doReturn(dialogs).when(mSipSessionTracker).getTrackedDialogs();
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.closeSessionsGracefully((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Before executor executes, outgoing messages will be restricted due to pending call ids.
        assertTrue(l.getCount() >= 1);
        assertTrue(pendingCallIds.isEmpty());
        assertTrue(isIncomingTransportOpen(tracker));
        verify(mOutgoingStateValidator).restrict(anyInt());
        // force close amd ensure pending close is
        assertTrue(tracker.closeSessions(
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED).isEmpty());
        assertEquals(0, l.getCount());
        assertEquals(pendingCallIds, pendingCallIds);
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void testTransportClosingGracefullyTimeout() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<SipDialog> dialogs = new ArraySet<>();
        dialogs.add(SipDialog.fromSipMessage(TEST_MESSAGE));
        doReturn(dialogs).when(mSipSessionTracker).getTrackedDialogs();
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.closeSessionsGracefully((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Before executor executes, outgoing messages will be restricted due to pending call ids.
        assertTrue(l.getCount() >= 1);
        assertTrue(pendingCallIds.isEmpty());
        assertTrue(isIncomingTransportOpen(tracker));
        verify(mOutgoingStateValidator).restrict(anyInt());
        // Process timeout event - pending call id should be passed to transport.
        executor.executePending();
        assertEquals(0, l.getCount());
        assertTrue(pendingCallIds.contains("testid"));
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void testTransportClosingGracefullyCleanup() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<SipDialog> dialogs = new ArraySet<>();
        dialogs.add(SipDialog.fromSipMessage(TEST_MESSAGE));
        doReturn(dialogs).when(mSipSessionTracker).getTrackedDialogs();
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.closeSessionsGracefully((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Before executor executes, outgoing messages will be restricted due to pending call ids.
        assertTrue(l.getCount() >= 1);
        assertTrue(pendingCallIds.isEmpty());
        assertTrue(isIncomingTransportOpen(tracker));
        verify(mOutgoingStateValidator).restrict(anyInt());
        // Mock cleanupSession event was called for pending callId
        dialogs.clear();
        tracker.onSipSessionCleanup("abc");
        assertEquals(0, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void testTransportClosingForcefully() {
        TestExecutorService executor = new TestExecutorService();
        TransportSipMessageValidator tracker = openTransport(executor);

        tracker.closeSessions(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);

        // All messages will be rejected.
        verify(mOutgoingStateValidator).close(anyInt());
        verify(mIncomingStateValidator).close(anyInt());
    }

    @Test
    public void setRegStateChangedNoPendingCallIds() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        doReturn(Collections.emptySet()).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        // no feature tags are deregistering/deregistered, should return immediately
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        verify(mOutgoingStateValidator, times(2)).restrictFeatureTags(Collections.emptySet());
        assertTrue(pendingCallIds.isEmpty());
        assertEquals(0, l.getCount());
    }

    @Test
    public void setRegStateChangedPendingCallIdsMultipleCalls() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<String> callIds = new ArraySet<>(1);
        callIds.add("abc");
        doReturn(callIds).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(2);
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(2, l.getCount());
        // If called again, the previous request will complete with no call ids
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(1, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        // Simulate timeout - we should get callback with the pending call ids.
        executor.executePending();
        assertEquals(0, l.getCount());
        assertEquals(callIds, pendingCallIds);
    }

    @Test
    public void setRegStateChangedPendingCallIdsTimeout() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<String> callIds = new ArraySet<>(1);
        callIds.add("abc");
        doReturn(callIds).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(1, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        // Simulate timeout - we should get callback with the pending call ids.
        executor.executePending();
        assertEquals(0, l.getCount());
        assertEquals(callIds, pendingCallIds);
    }

    @Test
    public void setRegStateChangedPendingCallIdsResolved() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<String> callIds = new ArraySet<>(1);
        callIds.add("abc");
        doReturn(callIds).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(1, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        // Simulate ack pending SIP session, which has cleared pending call id
        doReturn(Collections.emptySet()).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        tracker.acknowledgePendingMessage("blah");
        assertEquals(0, l.getCount());
        assertEquals(callIds, pendingCallIds);
    }

    @Test
    public void setRegStateChangedPendingCallIdsChanged() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<String> callIds = new ArraySet<>(1);
        callIds.add("abc");
        doReturn(callIds).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(1, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        // Simulate pending callIds changed to add another session.
        callIds.add("def");
        executor.executePending();
        assertEquals(0, l.getCount());
        assertEquals(callIds, pendingCallIds);
    }

    @Test
    public void setRegStateRegThenClose() {
        TestExecutorService executor = new TestExecutorService(true /*wait*/);
        TransportSipMessageValidator tracker = openTransport(executor);

        ArraySet<String> callIds = new ArraySet<>(1);
        callIds.add("abc");
        doReturn(callIds).when(mSipSessionTracker)
                .getCallIdsAssociatedWithFeatureTag(any());
        ArraySet<String> pendingCallIds = new ArraySet<>();
        CountDownLatch l = new CountDownLatch(1);
        tracker.onRegistrationStateChanged((ids) -> {
            pendingCallIds.addAll(ids);
            l.countDown();
        }, getTestRegistrationState());
        assertEquals(1, l.getCount());
        assertTrue(pendingCallIds.isEmpty());
        // If close is called during pending reg state change, it should be completed with no
        // pending call IDs (close will take care of closing everything).
        ArraySet<SipDialog> dialogs = new ArraySet<>();
        dialogs.add(SipDialog.fromSipMessage(TEST_MESSAGE));
        doReturn(dialogs).when(mSipSessionTracker).getTrackedDialogs();
        tracker.closeSessions(SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);
        assertEquals(0, l.getCount());
        assertEquals(Collections.emptySet(), pendingCallIds);
    }

    private SipDelegateConfiguration.Builder getConfigBuilder(int version) {
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        return new SipDelegateConfiguration.Builder(version,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr);
    }

    private boolean isIncomingTransportOpen(TransportSipMessageValidator tracker) {
        return tracker.verifyIncomingMessage(TEST_MESSAGE).isValidated;
    }

    private boolean isOutgoingTransportOpen(TransportSipMessageValidator tracker) {
        return tracker.verifyOutgoingMessage(TEST_MESSAGE, TEST_CONFIG_VERSION).isValidated;
    }

    private int verifyOutgoingTransportClosed(TransportSipMessageValidator tracker) {
        ValidationResult result = tracker.verifyOutgoingMessage(TEST_MESSAGE, TEST_CONFIG_VERSION);
        assertFalse(result.isValidated);
        return result.restrictedReason;
    }

    private DelegateRegistrationState getTestRegistrationState() {
        return new DelegateRegistrationState.Builder().build();
    }

    private TransportSipMessageValidator openTransport(ScheduledExecutorService executor) {
        TransportSipMessageValidator tracker = getTestTracker(executor);
        tracker.onTransportOpened(Collections.emptySet(), Collections.emptySet());
        tracker.onConfigurationChanged(getConfigBuilder(TEST_CONFIG_VERSION).build());
        tracker.onRegistrationStateChanged((ignore) -> {}, getTestRegistrationState());
        // Config set + IMS reg state sent, transport is now open.
        assertTrue(isIncomingTransportOpen(tracker));
        assertTrue(isOutgoingTransportOpen(tracker));
        return tracker;
    }

    private TransportSipMessageValidator getTestTracker(ScheduledExecutorService executor) {
        doReturn(ValidationResult.SUCCESS).when(mOutgoingStateValidator).validate(any());
        doReturn(ValidationResult.SUCCESS).when(mIncomingStateValidator).validate(any());
        doReturn(mIncomingStateValidator).when(mIncomingStateValidator).andThen(any());
        return new TransportSipMessageValidator(TEST_SUB_ID, executor, mSipSessionTracker,
                mOutgoingStateValidator, mIncomingStateValidator, mRcsStats);
    }

    private SipMessage generateSipResponse(String statusCode, String statusString, String callId) {
        String fromHeader = "Alice <sip:alice@atlanta.com>;tag=1928301774";
        String toHeader = "Bob <sip:bob@biloxi.com>";
        String branchId = "AAAA";
        String fromTag = "tag=1928301774";
        String toTag = "";
        return SipMessageUtils.generateSipResponse(statusCode, statusString, fromHeader,
            toHeader, branchId, callId, fromTag, toTag);
    }

    private SipMessage generateSipRequest(String requestMethod, String callId) {
        String fromHeader = "Alice <sip:alice@atlanta.com>;tag=1928301774";
        String toHeader = "Bob <sip:bob@biloxi.com>";
        String branchId = "AAAA";
        String fromTag = "tag=1928301774";
        String toTag = "";
        String toUri = "sip:bob@biloxi.com";
        return SipMessageUtils.generateSipRequest(requestMethod, fromHeader, toHeader,
                toUri, branchId, callId, fromTag, toTag);
    }
}
