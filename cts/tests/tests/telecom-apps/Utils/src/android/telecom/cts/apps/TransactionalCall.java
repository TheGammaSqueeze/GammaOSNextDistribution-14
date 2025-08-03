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

package android.telecom.cts.apps;

import android.content.Context;
import android.os.Bundle;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

public class TransactionalCall {
    private static final String TAG = TransactionalCall.class.getSimpleName();
    private String mId = "";
    private Context mContext;
    private boolean mIsOutgoingCall;
    private CallResources mCallResources;
    private final PhoneAccountHandle mPhoneAccountHandle;
    private CallControl mCallControl;

    public CallResources getCallResources() {
        return mCallResources;
    }

    public boolean isOutgoingCall() {
        return mIsOutgoingCall;
    }

    public TransactionalCall(Context context, CallResources callResources) {
        mContext = context;
        mIsOutgoingCall = callResources.getCallAttributes().getDirection()
                == CallAttributes.DIRECTION_OUTGOING;
        mCallResources = callResources;
        mPhoneAccountHandle = callResources.getCallAttributes().getPhoneAccountHandle();
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public CallControl getCallControl() {
        return mCallControl;
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccountHandle;
    }

    public void setCallControl(CallControl callControl) {
        mCallControl = callControl;
    }

    public void setCallControlAndId(CallControl callControl) {
        mCallControl = callControl;
        mId = callControl.getCallId().toString();
        mCallResources.setCallId(mId);
        Log.i(TAG, String.format("setCallControlAndId: id=[%s], cc=[%s]", mId, callControl));
    }

    public void setActive(LatchedOutcomeReceiver outcome, Bundle extras ){
        if (mIsOutgoingCall) {
            Log.i(TAG, "transitionCallStateTo: setActive");
            mCallControl.setActive(Runnable::run, outcome);
        } else {
            Log.i(TAG, "transitionCallStateTo: setAnswer");
            int videoState = CallAttributes.AUDIO_CALL;
            if (CallControlExtras.hasVideoStateExtra(extras)) {
                CallControlExtras.getVideoStateFromExtras(extras);
            }
            mCallControl.answer(videoState, Runnable::run, outcome);
            mCallResources.updateNotificationToOngoing(mContext);
        }
    }

    public void setInactive(LatchedOutcomeReceiver outcome){
        Log.i(TAG, "transitionCallStateTo: setInactive");
        mCallControl.setInactive(Runnable::run, outcome);
    }

    public void disconnect(LatchedOutcomeReceiver outcome, Bundle extras ) {
        Log.i(TAG, "transitionCallStateTo: disconnect");
        int disconnectCause = DisconnectCause.LOCAL;
        if (CallControlExtras.hasDisconnectCauseExtra(extras)) {
            disconnectCause = CallControlExtras.getDisconnectCauseFromExtras(
                    extras);
        }
        mCallControl.disconnect(
                new DisconnectCause(disconnectCause),
                Runnable::run,
                outcome);
        mCallResources.destroyResources(mContext);
    }

    public CallControlCallback mHandshakes = new CallControlCallback() {
        @Override
        public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
            Log.i(TAG, "onSetInactive: stopped recording ; callId"+ mId);
        }

        @Override
        public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
            mCallResources.updateNotificationToOngoing(mContext);
            wasCompleted.accept(true);
            Log.i(TAG, "onAnswer: started recording; callId" + mId);
        }

        @Override
        public void onDisconnect(@NonNull DisconnectCause cause,
                @NonNull Consumer<Boolean> wasCompleted) {
            mCallResources.destroyResources(mContext);
            wasCompleted.accept(true);
        }

        @Override
        public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
        }
    };

    public TransactionalCallEvents mEvents = new TransactionalCallEvents();
}
