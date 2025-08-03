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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.OUTBOUND_TARGET_HOST;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.PutDoCommand;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.Optional;

/**
 * The initiator of dynamic STS session managed by the UWB controller.
 */
public class ControllerInitiatorSession extends InitiatorSession {
    private static final String LOG_TAG = "ControllerInitiator";

    public ControllerInitiatorSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
        mIsController = true;
    }

    private void sendGetControleeInfoCommand() {
        GetDoCommand getControleeInfoCommand = GetDoCommand.build(
                CsmlUtil.constructGetDoTlv(CsmlUtil.CONTROLEE_INFO_DO_TAG));
        tunnelData(MSG_ID_GET_CONTROLEE_INFO,
                getControleeInfoCommand.getCommandApdu().getEncoded());
    }

    @Override
    protected void handleFiRaSecureChannelEstablished() {
        sendGetControleeInfoCommand();
    }

    @Override
    protected void handleTunnelDataFailure(int msgId, @NonNull TunnelDataFailReason failReason) {
        switch (msgId) {
            case MSG_ID_GET_CONTROLEE_INFO:
                // fall through
            case MSG_ID_PUT_SESSION_DATA:
                // simply abort the session.
                logw(
                        "terminate session as tunnel data was failed: "
                                + failReason
                                + " for msg: "
                                + msgId);
                mFiRaSecureChannel.terminateLocally();
                mSessionCallback.onSessionAborted();
                break;
            default:
                logd("unknown failure response for tunnel message: " + msgId);
                break;
        }
    }

    @Override
    protected boolean handleTunnelDataResponseReceived(
            int msgId, @NonNull DispatchResponse response) {
        switch (msgId) {
            case MSG_ID_GET_CONTROLEE_INFO:
                return handleTunnelGetControleeInfoResponse(response);
            case MSG_ID_PUT_SESSION_DATA:
                return handleTunnelPutSessionDataResponse(response);
            default:
                logd("unknown response for tunnel message: " + msgId);
                break;
        }
        return false;
    }

    private boolean handleTunnelGetControleeInfoResponse(@NonNull DispatchResponse response) {
        try {
            if (response.getOutboundData().isEmpty()) {
                throw new IllegalStateException("data response is expected for GetControleeInfo.");
            }
            DispatchResponse.OutboundData outboundData = response.getOutboundData().get();
            if (outboundData.target != OUTBOUND_TARGET_HOST
                    || !CsmlUtil.isControleeInfoDo(outboundData.data)) {
                throw new IllegalStateException("controlee info from the response is not valid.");
            }
            ControleeInfo controleeInfo = ControleeInfo.fromBytes(outboundData.data);

            mSessionData = CsmlUtil.generateSessionData(
                    mRunningProfileSessionInfo.uwbCapability,
                    controleeInfo,
                    mRunningProfileSessionInfo.sharedPrimarySessionId,
                    mRunningProfileSessionInfo.sharedPrimarySessionKeyInfo,
                    mUniqueSessionId.get(),
                    !mIsDefaultUniqueSessionId);

            PutDoCommand putSessionDataCommand = PutDoCommand.build(
                    CsmlUtil.constructGetOrPutDoTlv(
                            new TlvDatum(CsmlUtil.SESSION_DATA_DO_TAG, mSessionData.toBytes())));
            tunnelData(MSG_ID_PUT_SESSION_DATA,
                    putSessionDataCommand.getCommandApdu().getEncoded());
        } catch (IllegalStateException e) {
            logw("unexpected response for getControleeInfo" + e);
            terminateSession();
            mSessionCallback.onSessionAborted();
        }
        return true;
    }

    private boolean handleTunnelPutSessionDataResponse(@NonNull DispatchResponse response) {
        Optional<DispatchResponse.OutboundData> outboundData = response.getOutboundData();
        if (!response.statusWord.equals(StatusWord.SW_NO_ERROR)
                || outboundData.isEmpty()
                || (!StatusWord.SW_NO_ERROR.equals(
                        StatusWord.fromInt(DataTypeConversionUtil.arbitraryByteArrayToI32(
                                outboundData.get().data))))) {
            logw("unexpected response for tunnel putSessionData.");
            terminateSession();
            mSessionCallback.onSessionAborted();
            return true;
        }
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        for (DispatchResponse.Notification notification : response.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable = (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                default:
                    logw(
                            "Unexpected notification from dispatch response: "
                                    + notification.notificationEventId);
            }
        }

        if (rdsAvailable != null) {
            if (mIsDefaultUniqueSessionId && mUniqueSessionId.get() != rdsAvailable.sessionId) {
                logw("default session Id is changed, which is not expected.");
            }
            mUniqueSessionId = Optional.of(rdsAvailable.sessionId);
            mSessionCallback.onSessionDataReady(
                    rdsAvailable.sessionId,
                    Optional.of(mSessionData),
                    /*isSessionTerminated=*/ false);
        } else {
            // push session data to FiRa applet if there is no RDS available notification;
            // FiRa applet didn't send RDS to SUS.
            PutDoCommand putSessionDataCommand = PutDoCommand.build(
                    new TlvDatum(CsmlUtil.SESSION_DATA_DO_TAG,
                            mSessionData.toBytes()));

            mFiRaSecureChannel.sendLocalCommandApdu(
                    putSessionDataCommand.getCommandApdu(),
                    new FiRaSecureChannel.ExternalRequestCallback() {
                        @Override
                        public void onSuccess(byte[] responseData) {
                            logd("success to send session data to local FiRa applet.");
                            mSessionCallback.onSessionDataReady(
                                    mUniqueSessionId.get(),
                                    Optional.of(mSessionData),
                                    /*isTerminatedSession=*/ false);

                        }

                        @Override
                        public void onFailure() {
                            logw("failed to put session data to applet.");
                            terminateSession();
                            mSessionCallback.onSessionAborted();
                        }
                    });
        }

        return true;
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
