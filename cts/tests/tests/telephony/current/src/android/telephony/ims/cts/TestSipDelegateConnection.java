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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateConnection;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.stub.DelegateConnectionMessageCallback;
import android.telephony.ims.stub.DelegateConnectionStateCallback;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestSipDelegateConnection implements DelegateConnectionStateCallback,
        DelegateConnectionMessageCallback {

    private interface ExceptionRunnable {
        void run() throws Exception;
    }

    private static final String LOG_TAG = "CtsImsSipDelegateC";

    public int destroyReason = -1;
    public SipDelegateConnection connection;
    public Set<FeatureTagState> deniedTags;
    public DelegateRegistrationState regState;
    public SipDelegateConfiguration sipConfig;
    public final DelegateRequest delegateRequest;

    private int mReceivedMessageErrorResponseReason = -1;
    private CountDownLatch mLatch;
    // Pair is <transactionId, error reason>
    private final LinkedBlockingQueue<SipMessage> mReceivedMessages = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Pair<String, Integer>> mSentMessageAcks =
            new LinkedBlockingQueue<>();

    public TestSipDelegateConnection(DelegateRequest request) {
        delegateRequest = request;
    }

    public void connect(SipDelegateManager manager) throws Exception {
        callUntilImsServiceIsAvailableNoReturn(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                        manager, (m) -> m.createSipDelegate(delegateRequest, Runnable::run, this,
                                this), ImsException.class,
                        "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
    }

    public void disconnect(SipDelegateManager manager, int reason) throws Exception {
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                manager, (m) -> m.destroySipDelegate(connection, reason),
                ImsException.class,
                "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
    }

    public void triggerFullNetworkRegistration(SipDelegateManager manager, int sipCode,
            String sipReason) throws Exception {
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                manager, (m) -> m.triggerFullNetworkRegistration(connection, sipCode, sipReason),
                ImsException.class,
                "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
    }

    @Override
    public void onMessageReceived(@NonNull SipMessage message) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onMessageReceived");
        mReceivedMessages.offer(message);
        if (mReceivedMessageErrorResponseReason > -1) {
            connection.notifyMessageReceiveError(message.getViaBranchParameter(),
                    mReceivedMessageErrorResponseReason);
        } else {
            connection.notifyMessageReceived(message.getViaBranchParameter());
        }
    }

    @Override
    public void onMessageSent(@NonNull String viaTransactionId) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onMessageSent");
        mSentMessageAcks.offer(new Pair<>(viaTransactionId, -1));
    }

    @Override
    public void onMessageSendFailure(@NonNull String viaTransactionId, int reason) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onMessageSendFailure");
        mSentMessageAcks.offer(new Pair<>(viaTransactionId, reason));
    }

    @Override
    public void onCreated(@NonNull SipDelegateConnection c) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onCreated");
        connection = c;
        mLatch.countDown();
    }

    @Override
    public void onFeatureTagStatusChanged(@NonNull DelegateRegistrationState registrationState,
            @NonNull Set<FeatureTagState> deniedFeatureTags) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onFeatureTagStatusChanged");
        regState = registrationState;
        deniedTags = deniedFeatureTags;
        mLatch.countDown();
    }

    @Override
    public void onConfigurationChanged(
            @NonNull SipDelegateConfiguration registeredSipConfig) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onConfigurationChanged");
        sipConfig = registeredSipConfig;
        mLatch.countDown();
    }

    @Override
    public void onDestroyed(int reason) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "onDestroyed");
        connection = null;
        destroyReason = reason;
        mLatch.countDown();
    }

    public void sendCleanupSession(String callId) {
        assertNotNull("SipDelegate was null when cleaning up session", connection);
        connection.cleanupSession(callId);
    }

    public void sendMessageAndVerifyCompletedSuccessfully(SipMessage messageToSend)
            throws Exception {
        assertNotNull("SipDelegate was null when sending message", connection);
        connection.sendMessage(messageToSend, sipConfig.getVersion());
        Pair<String, Integer> ack = mSentMessageAcks.poll(ImsUtils.TEST_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertNotNull(ack);
        assertEquals(messageToSend.getViaBranchParameter(), ack.first);
        assertNotNull(ack.second);
        assertEquals(-1, ack.second.intValue());
    }

    public void sendMessageAndVerifyFailure(SipMessage messageToSend, int expectedReason)
            throws Exception {
        assertNotNull("SipDelegate was null when sending message", connection);
        // send invalid version if it was not sent.
        long version = (sipConfig != null) ? sipConfig.getVersion() : -1;
        connection.sendMessage(messageToSend, version);
        Pair<String, Integer> ack = mSentMessageAcks.poll(ImsUtils.TEST_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertNotNull(ack);
        // TODO actually check this, but for now the platform can not inspect SipMessages and send
        // the real transaction ID. So, just ensure it is null.
        //assertEquals(ImsUtils.TEST_TRANSACTION_ID, ack.first);
        assertNotNull(ack.second);
        assertEquals(expectedReason, ack.second.intValue());
    }

    public void verifyMessageReceived(SipMessage messageToVerify)
            throws Exception {
        SipMessage m = mReceivedMessages.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(messageToVerify, m);
    }

    public void setReceivedMessageErrorResponseReason(int reason) {
        mReceivedMessageErrorResponseReason = reason;
    }

    public void verifyDelegateCreated() {
        assertNotNull("SipDelegate is null when it should have been created", connection);
    }

    public void verifyConfigEquals(SipDelegateConfiguration config) {
        assertNotNull("SIP configuration should not be null", sipConfig);
        assertEquals("IMS config version is not correct", config.getVersion(),
                sipConfig.getVersion());
        assertEquals(config, sipConfig);
    }

    public void verifyRegistrationStateRegistered() {
        verifyRegistrationStateRegistered(delegateRequest.getFeatureTags());
    }

    public void verifyRegistrationStateRegistered(Set<String> tags) {
        assertNotNull(regState);
        assertFalse("No registered features found",
                regState.getRegisteredFeatureTags().isEmpty());
        ArraySet<String> notRegistered = new ArraySet<>(tags);
        notRegistered.removeAll(regState.getRegisteredFeatureTags());
        assertTrue("Not all requested features were registered: " + notRegistered,
                notRegistered.isEmpty());
        assertTrue(regState.getDeregisteringFeatureTags().isEmpty());
        assertTrue(regState.getDeregisteredFeatureTags().isEmpty());
        assertTrue(regState.getRegisteringFeatureTags().isEmpty());
    }

    public void verifyRegistrationStateEmpty() {
        assertNotNull(regState);
        assertTrue(regState.getRegisteredFeatureTags().isEmpty());
        assertTrue(regState.getDeregisteringFeatureTags().isEmpty());
        assertTrue(regState.getDeregisteredFeatureTags().isEmpty());
        assertTrue(regState.getRegisteringFeatureTags().isEmpty());
    }

    public void verifyRegistrationStateEquals(DelegateRegistrationState s) {
        assertEquals("unexpected registered tags", s.getRegisteredFeatureTags(),
                regState.getRegisteredFeatureTags());
        assertEquals("unexpected deregistering tags", s.getDeregisteringFeatureTags(),
                regState.getDeregisteringFeatureTags());
        assertEquals("unexpected deregistered tags", s.getDeregisteredFeatureTags(),
                regState.getDeregisteredFeatureTags());
        assertEquals("unexpected registering tags", s.getRegisteringFeatureTags(),
                regState.getRegisteringFeatureTags());
    }

    public boolean verifyDeregisteringStateContains(String featureTag, int state) {
        for (FeatureTagState s : regState.getDeregisteringFeatureTags()) {
            if (s.getFeatureTag().equals(featureTag) && s.getState() == state) {
                return true;
            }
        }
        return false;
    }


    public void verifyNoneDenied() {
        assertNotNull(deniedTags);
        assertTrue(deniedTags.isEmpty());
    }

    public void verifyDenied(Set<FeatureTagState> denied) {
        assertNotNull(deniedTags);
        assertEquals(denied, deniedTags);
    }

    public void verifyAllDenied(int reason) {
        assertNotNull(deniedTags);
        // Ensure that if the request is empty, the denied tags are also empty.
        if (delegateRequest.getFeatureTags().isEmpty()) {
            assertTrue(deniedTags.isEmpty());
        }
        // All should be denied with the same reason.
        FeatureTagState incorrectReason = deniedTags.stream().filter((t) -> t.getState() != reason)
                .findAny().orElse(null);
        Set<String> deniedFeatures = deniedTags.stream().map(FeatureTagState::getFeatureTag)
                .collect(Collectors.toSet());
        assertNull(incorrectReason);

        Set<String> requestedTags = new ArraySet<>(delegateRequest.getFeatureTags());
        requestedTags.removeAll(deniedFeatures);
        assertTrue("Not all tags denied: " + requestedTags, requestedTags.isEmpty());
    }

    public void verifyDestroyed(int reason) {
        assertEquals(reason, destroyReason);
    }

    /**
     * Set the number of operations that are expected to happen. Use {@link #waitForCountDown(long)}
     * to wait for the operations to occur.
     */
    public void setOperationCountDownLatch(int operationCount) {
        if (ImsUtils.VDBG) Log.d(LOG_TAG, "setOperationCountDownLatch: " + operationCount);
        mLatch = new CountDownLatch(operationCount);
    }

    /**
     * Wait for the latch set in {@link #setOperationCountDownLatch(int)} to complete.
     * @param timeoutMs The time to wait before giving up.
     * @return {@code true} if the latch successfully counted down, {@code false} if time elaptsed
     * before it counted down.
     */
    public boolean waitForCountDown(long timeoutMs) {
        while (mLatch.getCount() > 0) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) { }
        }
        return true;
    }

    /**
     * Wait up to five seconds (retrying a command 1 time per second) until ImsExceptions due to the
     * ImsService not being available go away.
     */
    private void callUntilImsServiceIsAvailableNoReturn(ExceptionRunnable command)
            throws Exception {
        int retry = 0;
        while (retry < 5) {
            try {
                command.run();
                return;
            } catch (ImsException e) {
                // we want to absorb only the unavailable error, as telephony may still be
                // internally setting up. Any other type of ImsException is unexpected.
                if (e.getCode() != ImsException.CODE_ERROR_SERVICE_UNAVAILABLE) {
                    throw e;
                }
            }
            Thread.sleep(1000);
            retry++;
        }
    }
}
