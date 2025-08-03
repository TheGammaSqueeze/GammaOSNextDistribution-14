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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.telephony.ims.DelegateMessageCallback;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.DelegateStateCallback;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipMessage;
import android.telephony.ims.stub.SipDelegate;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestSipDelegate implements SipDelegate {
    private static final String LOG_TAG = "CtsImsSipDelegate";

    public final int subId;
    public final DelegateRequest delegateRequest;
    private final DelegateStateCallback mStateCallback;
    private final DelegateMessageCallback mMessageCallback;

    private final LinkedBlockingQueue<SipMessage> mIncomingMessages = new LinkedBlockingQueue<>();
    // Pair is <transactionId, error reason>
    private final LinkedBlockingQueue<Pair<String, Integer>> mReceivedMessageAcks =
            new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> mCleanupSipSessionRequests =
            new LinkedBlockingQueue<>();
    private int mSendMessageDenyReason = -1;

    public TestSipDelegate(int sub, DelegateRequest request, DelegateStateCallback cb,
            DelegateMessageCallback mc) {
        subId = sub;
        delegateRequest = request;
        mStateCallback = cb;
        mMessageCallback = mc;
    }

    @Override
    public void sendMessage(@NonNull SipMessage message, long configVersion) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "sendMessage");
        mIncomingMessages.offer(message);
        if (mSendMessageDenyReason > -1) {
            mMessageCallback.onMessageSendFailure(message.getViaBranchParameter(),
                    mSendMessageDenyReason);
        } else {
            mMessageCallback.onMessageSent(message.getViaBranchParameter());
        }
    }

    @Override
    public void cleanupSession(@NonNull String callId) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "CleanSession");
        mCleanupSipSessionRequests.offer(callId);
    }

    @Override
    public void notifyMessageReceived(@NonNull String viaTransactionId) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "notifyMessageReceived");
        mReceivedMessageAcks.offer(new Pair<>(viaTransactionId, -1));
    }

    @Override
    public void notifyMessageReceiveError(@NonNull String viaTransactionId, int reason) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "notifyMessageReceiveError");
        mReceivedMessageAcks.offer(new Pair<>(viaTransactionId, reason));
    }

    public void verifyMessageSend(SipMessage messageToVerify) throws Exception {
        SipMessage m = mIncomingMessages.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(messageToVerify, m);
    }

    public void verifyCleanupSession(String... callIdsToVerify) throws Exception {
        Set<String> ids = new ArraySet<>(callIdsToVerify);
        for (int i = 0; i < callIdsToVerify.length; i++) {
            String requestedCallId = mCleanupSipSessionRequests.poll(ImsUtils.TEST_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertTrue(ids.contains(requestedCallId));
            ids.remove(requestedCallId);
        }
        assertTrue(ids.isEmpty());
    }

    public void setSendMessageDenyReason(int reason) {
        mSendMessageDenyReason = reason;
    }

    public void receiveMessageAndVerifyReceivedCalled(SipMessage m) throws Exception {
        mMessageCallback.onMessageReceived(m);
        Pair<String, Integer> transactionAndIdPair = mReceivedMessageAcks.poll(
                ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(m.getViaBranchParameter(), transactionAndIdPair.first);
        assertNotNull(transactionAndIdPair.second);
        assertEquals(-1, transactionAndIdPair.second.intValue());
    }

    public void receiveMessageAndVerifyReceiveErrorCalled(SipMessage m, int reason)
            throws Exception {
        mMessageCallback.onMessageReceived(m);
        Pair<String, Integer> transactionAndIdPair = mReceivedMessageAcks.poll(
                ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(m.getViaBranchParameter(), transactionAndIdPair.first);
        assertNotNull(transactionAndIdPair.second);
        assertEquals(reason, transactionAndIdPair.second.intValue());
    }

    public void notifyImsRegistrationUpdate(DelegateRegistrationState state) {
        mStateCallback.onFeatureTagRegistrationChanged(state);
    }

    public void notifyConfigurationUpdate(SipDelegateConfiguration config) {
        mStateCallback.onConfigurationChanged(config);
    }

    public void notifyOnCreated(Set<FeatureTagState> deniedTags) {
        mStateCallback.onCreated(this, deniedTags);
    }

    public void notifyOnDestroyed(int reason) {
        mStateCallback.onDestroyed(reason);
    }
}
