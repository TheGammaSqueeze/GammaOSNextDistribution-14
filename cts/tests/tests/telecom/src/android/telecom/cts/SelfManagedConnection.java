/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.cts.TestUtils.InvokeCounter;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * CTS Test self-managed {@link Connection} implementation.
 */
public class SelfManagedConnection extends Connection {
    private static final String TAG = SelfManagedConnection.class.getSimpleName();
    InvokeCounter mCallAudioRouteInvokeCounter = new InvokeCounter("onCallAudioStateChanged");
    InvokeCounter mOnShowIncomingUiInvokeCounter = new InvokeCounter(
            "onShowIncomingUiInvokeCounter");
    InvokeCounter mCallEventCounter = new InvokeCounter("onCallEvent");
    InvokeCounter mHandoverCompleteCounter = new InvokeCounter("handoverCompleteCounter");
    CountDownLatch mOnAnswerLatch = new CountDownLatch(1);
    InvokeCounter mCallEndpointInvokeCounter = new InvokeCounter("onCallEndpointChanged");
    InvokeCounter mAvailableEndpointsInvokeCounter =
            new InvokeCounter("onAvailableCallEndpointsChanged");
    CountDownLatch mOnHoldLatch = new CountDownLatch(1);
    CountDownLatch mOnUnHoldLatch = new CountDownLatch(1);
    CountDownLatch mOnDisconnectLatch = new CountDownLatch(1);
    CountDownLatch mInCallServiceTrackingLatch = new CountDownLatch(1);
    boolean mIsTracked = false;
    boolean mIsAlternativeUiShowing = false;

    public static abstract class Listener {
        void onDestroyed(SelfManagedConnection connection) { };
    }

    private final boolean mIsIncomingCall;
    private final Listener mListener;

    public SelfManagedConnection(boolean isIncomingCall, Listener listener) {
        mIsIncomingCall = isIncomingCall;
        mListener = listener;
    }

    public boolean isIncomingCall() {
        return mIsIncomingCall;
    }

    public void disconnectAndDestroy() {
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        mListener.onDestroyed(this);
    }

    @Override
    public void onAnswer(int videoState) {
        this.setActive();
        mOnAnswerLatch.countDown();
    }

    @Override
    public void onAnswer() {
        this.setActive();
        mOnAnswerLatch.countDown();
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        mCallAudioRouteInvokeCounter.invoke(state);
    }

    @Override
    public void onUsingAlternativeUi(boolean isUsingAlternativeUi) {
        mIsAlternativeUiShowing = isUsingAlternativeUi;
        mInCallServiceTrackingLatch.countDown();
    }

    @Override
    public void onTrackedByNonUiService(boolean isTracked) {
        mIsTracked = isTracked;
        mInCallServiceTrackingLatch.countDown();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        disconnectAndDestroy();
        mOnDisconnectLatch.countDown();
    }

    @Override
    public void onShowIncomingCallUi() {
        mOnShowIncomingUiInvokeCounter.invoke();
    }

    @Override
    public void onHold() {
        this.setOnHold();
        mOnHoldLatch.countDown();
    }

    @Override
    public void onUnhold() {
        this.setActive();
        mOnUnHoldLatch.countDown();
    }

    @Override
    public void onCallEvent(String event, Bundle extras) {
        mCallEventCounter.invoke(event, extras);
    }

    @Override
    public void onHandoverComplete() {
        mHandoverCompleteCounter.invoke();
    }

    @Override
    public void onCallEndpointChanged(CallEndpoint endpoint) {
        Log.i(TAG, String.format("onCallEndpointChanged: endpoint=[%s]", endpoint));
        mCallEndpointInvokeCounter.invoke(endpoint);
    }

    @Override
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
        mAvailableEndpointsInvokeCounter.invoke(availableEndpoints);
    }

    public InvokeCounter getCallAudioStateChangedInvokeCounter() {
        return mCallAudioRouteInvokeCounter;
    }

    public InvokeCounter getOnShowIncomingUiInvokeCounter() {
        return mOnShowIncomingUiInvokeCounter;
    }

    public InvokeCounter getCallEventCounter() {
        return mCallEventCounter;
    }

    public InvokeCounter getHandoverCompleteCounter() {
        return mHandoverCompleteCounter;
    }

    public boolean waitOnAnswer() {
        mOnAnswerLatch = TestUtils.waitForLock(mOnAnswerLatch);
        return mOnAnswerLatch != null;
    }

    public boolean waitOnHold() {
        mOnHoldLatch = TestUtils.waitForLock(mOnHoldLatch);
        return mOnHoldLatch != null;
    }

    public boolean waitOnUnHold() {
        mOnUnHoldLatch = TestUtils.waitForLock(mOnUnHoldLatch);
        return mOnUnHoldLatch != null;
    }

    public boolean waitOnDisconnect() {
        mOnDisconnectLatch = TestUtils.waitForLock(mOnDisconnectLatch);
        return mOnDisconnectLatch != null;
    }

    public boolean waitOnInCallServiceTrackingChanged() {
        boolean result = TestUtils.waitForLatchCountDown(mInCallServiceTrackingLatch);
        mInCallServiceTrackingLatch = new CountDownLatch(1);
        return result;
    }

    public boolean isTracked() {
        return mIsTracked;
    }

    public boolean isAlternativeUiShowing() {
        return mIsAlternativeUiShowing;
    }

    public InvokeCounter getCallEndpointChangedInvokeCounter() {
        return mCallEndpointInvokeCounter;
    }

    public InvokeCounter getAvailableEndpointsChangedInvokeCounter() {
        return mAvailableEndpointsInvokeCounter;
    }
}
