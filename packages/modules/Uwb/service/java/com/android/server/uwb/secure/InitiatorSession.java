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
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.iso7816.TlvDatum;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Dynamic STS session for initiator.
 */
public abstract class InitiatorSession extends SecureSession {
    private static final String LOG_TAG = "InitiatorSession";
    private static final int TUNNEL_TIMEOUT_MILLIS = 2000;

    protected static final int MSG_ID_GET_CONTROLEE_INFO = 0;
    protected static final int MSG_ID_PUT_CONTROLEE_INFO = 1;
    protected static final int MSG_ID_GET_SESSION_DATA = 2;
    protected static final int MSG_ID_PUT_SESSION_DATA = 3;

    private final Deque<TunnelMessageRequest> mPendingTunnelRequests = new ArrayDeque<>();

    InitiatorSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    /**
     * Handle the dispatch response.
     * @param response The DispatchResponse for the Tunnel message.
     * @return true if the response is expected for the message, false otherwise.
     */
    protected abstract boolean handleTunnelDataResponseReceived(
            int msgId, @NonNull DispatchResponse response);

    protected abstract void handleTunnelDataFailure(
            int msgId, @NonNull TunnelDataFailReason failReason);

    @Override
    protected void handleDispatchCommandFailure() {
        if (!mPendingTunnelRequests.isEmpty()) {
            // we assume the unhandled dispatch command is for the tunnel request.
            TunnelMessageRequest request = mPendingTunnelRequests.removeFirst();
            logw("The response from peer device is not handled for request: " + request.mMsgId);
            handleTunnelDataFailure(request.mMsgId, TunnelDataFailReason.REMOTE);
            mWorkHandler.removeCallbacks(request.mTimeoutRunnable);
        }
    }

    @Override
    protected void handleDispatchResponse(@NonNull DispatchResponse dispatchResponse) {
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED:
                    mFiRaSecureChannel.cleanUpTerminatedOrAbortedSession();
                    mSessionCallback.onSessionAborted();
                    return;
                default:
                    // do nothing, continue.
            }
        }
        if (!mPendingTunnelRequests.isEmpty()) {
            TunnelMessageRequest request = mPendingTunnelRequests.peekFirst();
            if (handleTunnelDataResponseReceived(request.mMsgId, dispatchResponse)) {
                logd("The response is expected for msgId: " + request.mMsgId);
                mWorkHandler.removeCallbacks(request.mTimeoutRunnable);
                mPendingTunnelRequests.removeFirst();
                return;
            } else {
                logw("The response is not expected for msgId: " + request.mMsgId);
            }
        }

        Optional<DispatchResponse.OutboundData> outboundData = dispatchResponse.getOutboundData();
        if (outboundData.isPresent()) {
            if (outboundData.get().target == DispatchResponse.OUTBOUND_TARGET_REMOTE) {
                mFiRaSecureChannel.sendRawDataToRemote(outboundData.get().data);
            } else {
                onUnsolicitedDataToHostReceived(outboundData.get().data);
            }
        }
    }

    protected void onUnsolicitedDataToHostReceived(@NonNull byte[] data) {
        // do nothing for now.
    }

    /**
     * tunnel terminate cmd to the remote device if the session is terminated manually.
     */
    private void terminateRemoteSession() {
        logd("send terminate session to remote device.");
        TlvDatum terminateSessionDo = CsmlUtil.constructTerminateSessionGetDoTlv();
        GetDoCommand getDoCommand = GetDoCommand.build(terminateSessionDo);
        // do not expect any response from the remote.
        mFiRaSecureChannel.tunnelToRemoteDevice(
                getDoCommand.getCommandApdu().getEncoded(),
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess(@NonNull byte[] responseData) {
                        // do nothing.
                    }

                    @Override
                    public void onFailure() {
                        logw("failed to send the terminate session cmd to remote device");
                        // do nothing.
                    }
                });
    }

    protected final void tunnelData(int msgId, @NonNull byte[] data) {
        mFiRaSecureChannel.tunnelToRemoteDevice(
                data,
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess(@NonNull byte[] responseData) {
                        TunnelMessageRequest tunnelMessageRequest = new TunnelMessageRequest(msgId);
                        mPendingTunnelRequests.addLast(tunnelMessageRequest);
                        logd("message: " + msgId + " is send out, waiting for response.");
                        mWorkHandler.postDelayed(
                                tunnelMessageRequest.mTimeoutRunnable, TUNNEL_TIMEOUT_MILLIS);
                    }

                    @Override
                    public void onFailure() {
                        handleTunnelDataFailure(msgId, TunnelDataFailReason.LOCAL);
                    }
                });
    }

    @Override
    public final void terminateSession() {
        mWorkHandler.post(
                () -> {
                    if (mFiRaSecureChannel.isEstablished()) {
                        terminateRemoteSession();
                    }
                    mFiRaSecureChannel.terminateLocally();
                });
    }

    private class TunnelMessageRequest {
        private final int mMsgId;
        private final Runnable mTimeoutRunnable;

        TunnelMessageRequest(int msgId) {
            this.mMsgId = msgId;
            mTimeoutRunnable =
                    () -> {
                        logd("tunnel data timeout for msg: " + msgId);
                        if (mPendingTunnelRequests.isEmpty()) {
                            return;
                        }
                        mPendingTunnelRequests.removeFirst();
                        handleTunnelDataFailure(this.mMsgId, TunnelDataFailReason.TIMEOUT);
                    };
        }
    }

    enum TunnelDataFailReason {
        TIMEOUT,
        REMOTE,
        LOCAL,
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
}
