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

package com.android.server.uwb.secure;

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.iso7816.StatusWord;

import java.util.Optional;

/**
 * Dynamic STS session for responder.
 */
public abstract class ResponderSession extends SecureSession {
    private static final String LOG_TAG = "ResponderSession";

    ResponderSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    /**
     * Process the dispatch response if it is expected.
     * @return true if the response is expected and processed, false otherwise.
     */
    protected abstract boolean onDispatchResponseReceived(
            @NonNull DispatchResponse dispatchResponse);

    protected abstract void onUnsolicitedDataToHostReceived(@NonNull byte[] data);

    @Override
    protected final void handleDispatchCommandFailure() {
        // can do nothing for responder. ignore it.
        logw("a dispatch command wasn't handled correctly.");
    }

    @Override
    protected final void handleDispatchResponse(@NonNull DispatchResponse dispatchResponse) {
        if (!dispatchResponse.statusWord.equals(StatusWord.SW_NO_ERROR)) {
            logw("Wrong DispatchResponse sw: " + dispatchResponse.statusWord);
            terminateSession();
            mSessionCallback.onSessionAborted();
            return;
        }
        // once session is aborted, nothing else in the response.
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED:
                    mFiRaSecureChannel.cleanUpTerminatedOrAbortedSession();
                    mSessionCallback.onSessionAborted();
                    return;
                default:
                    logw("unhandled notification from dispatch response: "
                            + notification.notificationEventId);
                    break;
            }
        }

        Optional<DispatchResponse.OutboundData> outboundData = dispatchResponse.getOutboundData();
        if (outboundData.isPresent()
                && outboundData.get().target == DispatchResponse.OUTBOUND_TARGET_REMOTE) {
            logd("send response back to remote.");
            mFiRaSecureChannel.sendRawDataToRemote(outboundData.get().data);
        }
        if (onDispatchResponseReceived(dispatchResponse)) {
            return;
        }

        if (outboundData.isPresent()
                && outboundData.get().target == DispatchResponse.OUTBOUND_TARGET_HOST) {
            onUnsolicitedDataToHostReceived(outboundData.get().data);
        }
    }

    @Override
    protected void handleFiRaSecureChannelEstablished() {
        // do nothing, wait for request from initiator.
        // TODO: add a time out protection
    }

    @Override
    public final void terminateSession() {
        mWorkHandler.post(() -> mFiRaSecureChannel.terminateLocally());
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
}
