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

import static com.android.internal.telephony.TelephonyStatsLog.SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.BinderCacheManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipDialogState;
import android.telephony.ims.SipDialogStateCallback;
import android.telephony.ims.SipMessage;
import android.telephony.ims.aidl.IImsRcsController;
import android.util.ArraySet;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.ISipDialogStateCallback;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.metrics.RcsStats;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SipSessionTrackerTest {

    private class DialogAttributes {
        public final String branchId;
        public final String callId;
        public final String fromHeader;
        public final String fromTag;
        public final String toUri;
        public final String toHeader;
        private final String mFromUri;
        // This may be populated later.
        public String toTag;

        DialogAttributes() {
            branchId = getNextString();
            callId = getNextString();
            mFromUri = generateRandomSipUri();
            fromHeader = generateContactUri(mFromUri);
            fromTag = getNextString();
            toUri = generateRandomSipUri();
            toHeader = generateContactUri(toUri);
        }

        private DialogAttributes(String branchId, String callId, String fromUri,
                String fromTag, String toUri, String toTag) {
            this.branchId = branchId;
            this.callId = callId;
            this.mFromUri = fromUri;
            this.fromHeader = generateContactUri(fromUri);
            this.fromTag = fromTag;
            this.toUri = toUri;
            this.toHeader = generateContactUri(toUri);
            this.toTag = toTag;
        }

        public void setToTag() {
            if (toTag == null) {
                toTag = getNextString();
            }
        }

        public DialogAttributes fromExisting() {
            return new DialogAttributes(branchId, callId, mFromUri, fromTag, toUri, null);
        }

        public DialogAttributes invertFromTo() {
            return new DialogAttributes(branchId, callId, toUri, fromTag, mFromUri, toTag);
        }
    }

    // Keep track of the string entry so we can generate unique strings.
    private int mStringEntryCounter = 0;
    private SipSessionTracker mTrackerUT;
    private static final int TEST_SUB_ID = 1;
    private static final String TEST_INVITE_SIP_METHOD = "INVITE";
    private static final int TEST_SIP_RESPONSE_CODE = 200;
    private static final int TEST_SIP_CLOSE_RESPONSE_CODE = 0;

    @Mock private RcsStats mRcsStats;
    private boolean mUpdatedState = false;
    private SipDialogStateCallback mCallback;
    private SipDelegateManager mSipManager;
    private ISipDialogStateCallback mCbBinder;
    IImsRcsController mMockImsRcsInterface;
    BinderCacheManager<ITelephony> mBinderCache;
    BinderCacheManager<IImsRcsController> mRcsBinderCache;

    @Before
    public void setUp() throws Exception {
        mStringEntryCounter = 0;
        MockitoAnnotations.initMocks(this);
        mTrackerUT = new SipSessionTracker(TEST_SUB_ID, mRcsStats);
        mMockImsRcsInterface = mock(IImsRcsController.class);
        mBinderCache = mock(BinderCacheManager.class);
        mRcsBinderCache = mock(BinderCacheManager.class);
        doReturn(mMockImsRcsInterface).when(mRcsBinderCache)
                .listenOnBinder(any(), any(Runnable.class));
        doReturn(mMockImsRcsInterface).when(mRcsBinderCache)
                .removeRunnable(any(SipDialogStateCallback.class));
        doReturn(mMockImsRcsInterface).when(mRcsBinderCache).getBinder();
    }

    @Test
    public void testMetricsEndedGracefullyBye() {
        DialogAttributes attr = new DialogAttributes();
        // INVITE
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        filterMessage(inviteRequest, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);

        // confirmed dialog
        attr.setToTag();
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attr);
        filterMessage(inviteConfirm, attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);

        // Gracefully Ended
        SipMessage inviteClose = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr);
        filterMessage(inviteClose, attr);

        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr);

        // verify Metrics information
        verify(mRcsStats).onSipTransportSessionClosed(eq(TEST_SUB_ID), eq(attr.callId),
                eq(TEST_SIP_CLOSE_RESPONSE_CODE), eq(true));
    }

    @Test
    public void testMetricsCloseCleanupSession() {
        //mTrackerUT.setRcsStats(mRcsStats);
        DialogAttributes attr = new DialogAttributes();
        // INVITE A -> B
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        filterMessage(inviteRequest, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);

        // confirmed dialog
        attr.setToTag();
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attr);
        filterMessage(inviteConfirm, attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);

        //forcefully close session
        mTrackerUT.cleanupSession(attr.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());

        // verify Metrics information
        verify(mRcsStats).onSipTransportSessionClosed(eq(TEST_SUB_ID), eq(attr.callId),
                eq(TEST_SIP_CLOSE_RESPONSE_CODE), eq(false));
    }

    @Test
    public void testMetricsCloseClearAllSessions() {
        //mTrackerUT.setRcsStats(mRcsStats);
        DialogAttributes attr = new DialogAttributes();

        // INVITE
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        filterMessage(inviteRequest, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);

        // confirmed dialog
        attr.setToTag();
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attr);
        filterMessage(inviteConfirm, attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);

        //forcefully close session
        mTrackerUT.clearAllSessions();
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());

        // verify Metrics information
        verify(mRcsStats).onSipTransportSessionClosed(eq(TEST_SUB_ID), eq(attr.callId),
                eq(TEST_SIP_CLOSE_RESPONSE_CODE), eq(false));
    }

    @Test
    public void testEarlyDialogToConfirmed() {
        DialogAttributes attr = new DialogAttributes();
        // INVITE A -> B
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        filterMessage(inviteRequest, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);
        // 100 TRYING A <- proxy
        SipMessage inviteTrying = generateSipResponse("100", "Trying", attr);
        filterMessage(inviteTrying, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);
        // INVITE proxy -> B
        // (BOB generates To tag)
        attr.setToTag();
        // 180 RINGING proxy <- B
        // 180 RINGING A <- proxy
        SipMessage inviteRinging = generateSipResponse("180", "Ringing", attr);
        filterMessage(inviteRinging, attr);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attr);
        // User answers phone
        // 200 OK proxy <- B
        // 200 OK A <- proxy
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attr);
        filterMessage(inviteConfirm, attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);
    }

    @Test
    public void testForkDialog() {
        DialogAttributes attrB1 = new DialogAttributes();
        // INVITE A -> B
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attrB1);
        filterMessage(inviteRequest, attrB1);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attrB1);
        // INVITE proxy -> B
        // (BOB generates To tag)
        attrB1.setToTag();
        // 180 RINGING proxy <- B1
        // 180 RINGING A <- proxy
        SipMessage inviteRingingB1 = generateSipResponse("180", "Ringing", attrB1);
        filterMessage(inviteRingingB1, attrB1);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attrB1);
        // Now get another RINGING indication from another device associated with the same user.
        // 180 RINGING proxy <- B2
        // 180 RINGING A <- proxy
        DialogAttributes attrB2 = attrB1.fromExisting();
        // set different To tag
        attrB2.setToTag();
        SipMessage inviteRingingB2 = generateSipResponse("180", "Ringing", attrB2);
        filterMessage(inviteRingingB2, attrB2);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attrB1, attrB2);
        // User answers B1
        // 200 OK proxy <- B1
        // 200 OK A <- proxy
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attrB1);
        filterMessage(inviteConfirm, attrB1);
        verifyContainsCallIds(mTrackerUT.getEarlyDialogs(), attrB2);
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attrB1);
        // Receive indication that B2 is terminated because user answered on B1
        // 487 A <- proxy
        SipMessage terminatedResponse = generateSipResponse("487",
                "Request Terminated", attrB2);
        filterMessage(terminatedResponse, attrB2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attrB1);
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attrB2);
        SipMessage byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attrB1);
        // Send BYE request for the open dialog.
        filterMessage(byeRequest, attrB1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attrB1, attrB2);
        // Clean up the session and ensure the close dialog is completely removed from the tracker.
        mTrackerUT.cleanupSession(attrB1.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
    }

    @Test
    public void testCloseLocalDialog() {
        DialogAttributes attr = new DialogAttributes();
        attr.setToTag();
        createConfirmedDialog(attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);

        // Send BYE request for a dialog that was started locally and ensure that we see the call id
        // move to the closed list.
        SipMessage byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr);
        filterMessage(byeRequest, attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr);
        // Clean up the session and ensure the close dialog is completely removed from the tracker.
        mTrackerUT.cleanupSession(attr.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
    }

    @Test
    public void testAcceptContactFts() {
        DialogAttributes attr = new DialogAttributes();
        attr.setToTag();
        SipMessage inviteRequest = generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                attr);
        // add accept contact header
        inviteRequest = new SipMessage(inviteRequest.getStartLine(),
                inviteRequest.getHeaderSection() + "\nAccept-Contact:*;+test",
                new byte[0]);
        filterMessage(inviteRequest, attr);
        assertTrue(mTrackerUT.getCallIdsAssociatedWithFeatureTag(Collections.singleton("+test"))
                .contains(attr.callId));
    }

    @Test
    public void testCloseRemoteDialog() {
        DialogAttributes remoteAttr = new DialogAttributes();
        remoteAttr.setToTag();
        createConfirmedDialog(remoteAttr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), remoteAttr);

        // Send BYE request on a dialog that was started from the remote party.
        DialogAttributes localAttr = remoteAttr.invertFromTo();
        SipMessage byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, localAttr);
        filterMessage(byeRequest, localAttr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), remoteAttr);
        // Clean up the session and ensure the dialog is completely removed from the tracker.
        mTrackerUT.cleanupSession(remoteAttr.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
    }

    @Test
    public void testCleanupConfirmedDialog() {
        DialogAttributes attr = new DialogAttributes();
        attr.setToTag();
        createConfirmedDialog(attr);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);
        // Clean up the session and ensure the dialog is completely removed from the tracker.
        mTrackerUT.cleanupSession(attr.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
    }

    @Test
    public void testMultipleDialogs() {
        DialogAttributes attr1 = new DialogAttributes();
        createConfirmedDialog(attr1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1);
        // add a second dialog
        DialogAttributes attr2 = new DialogAttributes();
        createConfirmedDialog(attr2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1, attr2);
        // Send BYE request on dialogs
        SipMessage byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr1);
        filterMessage(byeRequest, attr1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr2);
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr1);
        mTrackerUT.cleanupSession(attr1.callId);
        // Send BYE request on dialogs
        byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr2);
        filterMessage(byeRequest, attr2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr2);
        mTrackerUT.cleanupSession(attr2.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
    }

    @Test
    public void testAcknowledgeMessageFailed() {
        DialogAttributes attr = new DialogAttributes();
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        mTrackerUT.filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, inviteRequest);
        // Do not acknowledge the request and ensure that the operation has not been applied yet.
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        // send message ack failed event, the operation shouldn't have been applied
        mTrackerUT.pendingMessageFailed(attr.branchId);
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
    }

    @Test
    public void testAcknowledgeBatchEvents() {
        DialogAttributes attr = new DialogAttributes();
        SipMessage inviteRequest = generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD, attr);
        attr.setToTag();
        SipMessage inviteConfirm = generateSipResponse("200", "OK", attr);
        // We unexpectedly received two filter requests for the same branchId without
        // acknowledgePendingMessage being called in between. Ensure that when it is called, it
        // applies both operations.
        mTrackerUT.filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, inviteRequest);
        mTrackerUT.filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, inviteConfirm);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        // we should skip right to confirmed as both operations run back-to-back
        mTrackerUT.acknowledgePendingMessage(attr.branchId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr);
    }

    @Test
    public void testActiveDialogsChanged() throws ImsException {
        sipDialogStateCallback();

        // first dialog
        DialogAttributes attr1 = new DialogAttributes();
        createConfirmedDialog(attr1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1);

        verifyConfirmedStates(true);

        // add a second dialog
        DialogAttributes attr2 = new DialogAttributes();
        createConfirmedDialog(attr2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1, attr2);
        verifyConfirmedStates(true);

        // Send BYE request on first dialog
        SipMessage byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr1);
        filterMessage(byeRequest, attr1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr2);
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr1);
        mTrackerUT.cleanupSession(attr1.callId);
        verifyConfirmedStates(true);

        // Send BYE request on second dialog
        byeRequest = generateSipRequest(SipMessageUtils.BYE_SIP_METHOD, attr2);
        filterMessage(byeRequest, attr2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getClosedDialogs(), attr2);
        mTrackerUT.cleanupSession(attr2.callId);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
        verifyConfirmedStates(false);
        unRegisterCallback();
    }

    @Test
    public void testActiveSipDialogsChangedClearAll() throws ImsException {
        sipDialogStateCallback();

        // first dialog
        DialogAttributes attr1 = new DialogAttributes();
        createConfirmedDialog(attr1);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1);
        verifyConfirmedStates(true);

        // add a second dialog
        DialogAttributes attr2 = new DialogAttributes();
        createConfirmedDialog(attr2);
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        verifyContainsCallIds(mTrackerUT.getConfirmedDialogs(), attr1, attr2);
        verifyConfirmedStates(true);

        // cleanAllSessions
        mTrackerUT.clearAllSessions();
        assertTrue(mTrackerUT.getEarlyDialogs().isEmpty());
        assertTrue(mTrackerUT.getConfirmedDialogs().isEmpty());
        assertTrue(mTrackerUT.getClosedDialogs().isEmpty());
        verifyConfirmedStates(false);
        unRegisterCallback();
    }

    private void sipDialogStateCallback() throws ImsException {
        mCallback = new SipDialogStateCallback() {
            @Override
            public void onActiveSipDialogsChanged(List<SipDialogState> dialogs) {
                mUpdatedState = isSipDialogActiveState(dialogs);
            }

            @Override
            public void onError() { }
        };
        registerCallback();
    }

    private void verifyConfirmedStates(boolean currentState) {
        List<SipDialogState> dialogStates = new ArrayList<>();
        for (SipDialog d : (ArraySet<SipDialog>) mTrackerUT.getTrackedDialogs()) {
            SipDialogState dialog = new SipDialogState.Builder(d.getState()).build();
            dialogStates.add(dialog);
        }
        try {
            mCbBinder.onActiveSipDialogsChanged(dialogStates);
        } catch (RemoteException e) {
            //onActiveSipDialogsChanged error
        }
        assertEquals(currentState, mUpdatedState);
    }

    private void registerCallback() throws ImsException {
        // Capture the Runnable that was registered.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        // Capture the ISipDialogStateCallback that was registered.
        ArgumentCaptor<ISipDialogStateCallback> callbackCaptor =
                ArgumentCaptor.forClass(ISipDialogStateCallback.class);

        Context context = PhoneFactory.getDefaultPhone().getContext();
        mSipManager = new SipDelegateManager(context,
                        TEST_SUB_ID, mRcsBinderCache, mBinderCache);

        mSipManager.registerSipDialogStateCallback(Runnable::run, mCallback);

        verify(mRcsBinderCache).listenOnBinder(any(), runnableCaptor.capture());
        try {
            verify(mMockImsRcsInterface).registerSipDialogStateCallback(
                    eq(TEST_SUB_ID), callbackCaptor.capture());
        } catch (RemoteException e) {
            //registerSipDialogStateCallback error
        }
        mCbBinder = callbackCaptor.getValue();
    }

    private void unRegisterCallback() {
        try {
            mSipManager.unregisterSipDialogStateCallback(mCallback);
        } catch (ImsException e) {
            //unregisterSipDialogStateCallback error
        }
    }

    private boolean isSipDialogActiveState(List<SipDialogState> dialogs) {
        int confirmedSize = dialogs.stream().filter(
                d -> d.getState() == SipDialogState.STATE_CONFIRMED)
                .collect(Collectors.toSet()).size();
        if (confirmedSize > 0) {
            return true;
        }
        return false;
    }

    private void filterMessage(SipMessage m, DialogAttributes attr) {
        mTrackerUT.filterSipMessage(
                SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, m);
        mTrackerUT.acknowledgePendingMessage(attr.branchId);
    }
    private void verifyContainsCallIds(Set<SipDialog> callIdSet, DialogAttributes... attrs) {
        Set<String> callIds = Arrays.stream(attrs).map(a -> a.callId).collect(
                Collectors.toSet());
        assertTrue(callIdSet.stream().map(SipDialog::getCallId).collect(Collectors.toSet())
                .containsAll(callIds));
    }

    private SipMessage generateSipRequest(String requestMethod,
            DialogAttributes attr) {
        return SipMessageUtils.generateSipRequest(requestMethod, attr.fromHeader, attr.toHeader,
                attr.toUri, attr.branchId, attr.callId, attr.fromTag, attr.toTag);
    }
    private SipMessage generateSipResponse(String statusCode, String statusString,
            DialogAttributes attr) {
        return SipMessageUtils.generateSipResponse(statusCode, statusString, attr.fromHeader,
                attr.toHeader, attr.branchId, attr.callId, attr.fromTag, attr.toTag);
    }

    private String generateContactUri(String sipUri) {
        Uri uri = Uri.parse(sipUri);
        assertNotNull(uri);
        String[] user = uri.getSchemeSpecificPart().split("@", 2);
        assertNotNull(user);
        assertEquals(2, user.length);
        return user[0] + " <" + sipUri + ">";
    }

    private String generateRandomSipUri() {
        return "sip:" + getNextString() + "@" + SipMessageUtils.BASE_ADDRESS;
    }

    private void createConfirmedDialog(DialogAttributes attr) {
        // INVITE ALICE -> BOB
        SipMessage inviteRequest = generateSipRequest(
                SipMessageUtils.INVITE_SIP_METHOD,
                attr);
        filterMessage(inviteRequest, attr);
        attr.setToTag();
        // skip to confirmed state for test.
        SipMessage inviteConfirm = generateSipResponse("200", "OK",
                attr);
        filterMessage(inviteConfirm, attr);
    }

    private String getNextString() {
        // Get a string representation of the entry counter
        byte[] idByteArray = ByteBuffer.allocate(4).putInt(mStringEntryCounter++).array();
        return Base64.encodeToString(idByteArray,
                Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }
}
