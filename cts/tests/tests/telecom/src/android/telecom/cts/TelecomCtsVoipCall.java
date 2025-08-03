/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.DisconnectCause;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class TelecomCtsVoipCall {

    private static final String TAG = "TelecomCtsVoipCall";
    private final String mCallId;
    private String mTelecomCallId = "";
    CallControl mCallControl;

    TelecomCtsVoipCall(String id) {
        mCallId = id;
        mEvents.mCallId = id;
    }

    public ParcelUuid getTelecomCallId() {
        return mCallControl.getCallId();
    }

    public void onAddCallControl(@NonNull CallControl callControl) {
        mCallControl = callControl;
        mTelecomCallId = callControl.getCallId().toString();
    }

    public CallControlCallback mHandshakes = new CallControlCallback() {
        @Override
        public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onSetActive: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onSetInactive: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onAnswer: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onDisconnect(DisconnectCause disconnectCause, Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onDisconnect: callId=[%s], disconnectCause=[%s]",
                    mCallId, disconnectCause));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onCallStreamingStarted: callId=[%s]", mCallId));
        }
    };
    public CallEvent mEvents = new CallEvent();

    public void resetAllCallbackVerifiers() {
        mEvents.resetAllCallbackVerifiers();
    }

    public static class CallEvent implements CallEventCallback {
        public String mCallId = "";
        private CallEndpoint mCallEndpoint;
        private List<CallEndpoint> mAvailableEndpoints;
        private boolean mIsMuted = false;
        private int mVideoState = CallAttributes.AUDIO_CALL;
        public boolean mWasMuteStateChangedCalled = false;
        public boolean mWasVideoStateChangedCalled = false;
        public Pair<String, Bundle> mLastEventReceived = null;

        @Override
        public void onCallEndpointChanged(@NonNull CallEndpoint newCallEndpoint) {
            Log.i(TAG, String.format("onCallEndpointChanged: endpoint=[%s]", newCallEndpoint));
            mCallEndpoint = newCallEndpoint;
        }

        @Override
        public void onAvailableCallEndpointsChanged(
                @NonNull List<CallEndpoint> availableEndpoints) {
            Log.i(TAG, String.format("onAvailableCallEndpointsChanged: callId=[%s]", mCallId));
            for (CallEndpoint endpoint : availableEndpoints) {
                Log.i(TAG, String.format("endpoint=[%s]", endpoint));
            }
            mAvailableEndpoints = availableEndpoints;
        }

        @Override
        public void onMuteStateChanged(boolean isMuted) {
            mIsMuted = isMuted;
            mWasMuteStateChangedCalled = true;
        }

        @Override
        public void onVideoStateChanged(int videoState) {
            mVideoState = videoState;
            mWasVideoStateChangedCalled = true;
        }

        @Override
        public void onCallStreamingFailed(int reason) {
            Log.i(TAG, String.format("onCallStreamingFailed: callId=[%s], reason=[%s]", mCallId,
                    reason));
        }

        @Override
        public void onEvent(String event, Bundle extras) {
            Log.i(TAG, String.format("onEvent: callId=[%s], event=[%s]", mCallId, event));
            mLastEventReceived = new Pair<>(event, extras);
        }

        public void resetAllCallbackVerifiers() {
            mWasVideoStateChangedCalled = false;
            mWasMuteStateChangedCalled = false;
            mLastEventReceived = null;
        }

        public CallEndpoint getCurrentCallEndpoint() {
            return mCallEndpoint;
        }

        public List<CallEndpoint> getAvailableEndpoints() {
            return mAvailableEndpoints;
        }

        public boolean isMuted() {
            return mIsMuted;
        }
        public int getVideoState() {return mVideoState;}
    }

    public static class LatchedOutcomeReceiver implements OutcomeReceiver<Void, CallException> {
        CountDownLatch mCountDownLatch;

        public LatchedOutcomeReceiver(CountDownLatch latch) {
            mCountDownLatch = latch;
        }

        @Override
        public void onResult(Void result) {
            Log.i(TAG, "latch is counting down");
            mCountDownLatch.countDown();
        }

        @Override
        public void onError(@NonNull CallException error) {
            Log.i(TAG, String.format("onError: code=[%d]", error.getCode()));
            OutcomeReceiver.super.onError(error);
        }
    }
}
