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

package com.android.server.telecom.transactionalVoipApp;

import android.os.Bundle;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallControl;
import android.telecom.CallEventCallback;
import android.telecom.DisconnectCause;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

public class MyVoipCall implements CallControlCallback, CallEventCallback {

    private static final String TAG = "MyVoipCall";
    private final String mCallId;
    public CallControl mCallControl;
    public CallEndpoint mCurrentEndpoint;
    public CallEndpoint mEarpieceEndpoint;
    public CallEndpoint mSpeakerEndpoint;
    public CallEndpoint mBluetoothEndpoint;
    List<CallEndpoint> mAvailableEndpoint = new ArrayList<>();

    MyVoipCall(String id) {
        mCallId = id;
    }

    public void onAddCallControl(@NonNull CallControl callControl) {
        mCallControl = callControl;
    }

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
    public void onDisconnect(@NonNull DisconnectCause cause,
            @NonNull Consumer<Boolean> wasCompleted) {
        Log.i(TAG, String.format("onDisconnect: callId=[%s]", mCallId));
        wasCompleted.accept(Boolean.TRUE);
    }

    @Override
    public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
        Log.i(TAG, String.format("onCallStreamingStarted: callId=[%s]", mCallId));
        wasCompleted.accept(Boolean.TRUE);
    }

    @Override
    public void onCallStreamingFailed(int reason) {
        Log.i(TAG, String.format("onCallStreamingFailed: id=[%s], reason=[%d]", mCallId, reason));
    }

    @Override
    public void onEvent(String event, Bundle extras) {
        Log.i(TAG, String.format("onEvent: id=[%s], event=[%s], extras=[%s]",
                mCallId, event, extras));
    }

    @Override
    public void onCallEndpointChanged(@NonNull CallEndpoint newCallEndpoint) {
        Log.i(TAG, String.format("onCallEndpointChanged: endpoint=[%s]", newCallEndpoint));
        mCurrentEndpoint = newCallEndpoint;
    }

    @Override
    public void onAvailableCallEndpointsChanged(
            @NonNull List<CallEndpoint> availableEndpoints) {
        Log.i(TAG, String.format("onAvailableCallEndpointsChanged: callId=[%s]", mCallId));
        for (CallEndpoint endpoint : availableEndpoints) {
            Log.i(TAG, String.format("endpoint=[%s]", endpoint));
            if (endpoint != null && endpoint.getEndpointType() == CallEndpoint.TYPE_EARPIECE) {
                mEarpieceEndpoint = endpoint;
            }
            if (endpoint != null && endpoint.getEndpointType() == CallEndpoint.TYPE_SPEAKER) {
                mSpeakerEndpoint = endpoint;
            }
            if (endpoint != null && endpoint.getEndpointType() == CallEndpoint.TYPE_BLUETOOTH) {
                mBluetoothEndpoint = endpoint;
            }
        }
        mAvailableEndpoint = availableEndpoints;
    }

    @Override
    public void onMuteStateChanged(boolean isMuted) {
    }
}
