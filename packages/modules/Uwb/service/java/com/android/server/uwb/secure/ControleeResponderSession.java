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

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.PutDoCommand;
import com.android.server.uwb.secure.csml.SessionData;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.Optional;

/**
 * The responder of dynamic STS session managed by the UWB controlee.
 */
public class ControleeResponderSession extends ResponderSession {
    private static final String LOG_TAG = "ControleeResponder";

    public ControleeResponderSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    @Override
    protected void handleFiRaSecureChannelEstablished() {
        super.handleFiRaSecureChannelEstablished();

        PutDoCommand putControleeInfoCommand = PutDoCommand.build(
                CsmlUtil.constructGetOrPutDoTlv(
                        new TlvDatum(CsmlUtil.CONTROLEE_INFO_DO_TAG,
                                mRunningProfileSessionInfo.controleeInfo.get().toBytes())));
        mFiRaSecureChannel.sendLocalFiRaCommand(putControleeInfoCommand,
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess(@NonNull byte[] responseData) {
                        logd("controlee info is sent to applet.");
                    }

                    @Override
                    public void onFailure() {
                        logw("failed to send controlee info to applet.");
                        terminateSession();
                        mSessionCallback.onSessionAborted();
                    }
                });
    }

    @Override
    protected boolean onDispatchResponseReceived(@NonNull DispatchResponse dispatchResponse) {
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
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
                logw("The default session Id is changed, which is not expected.");
            }
            mUniqueSessionId = Optional.of(rdsAvailable.sessionId);

            if (rdsAvailable.arbitraryData.isPresent()
                    && CsmlUtil.isSessionDataDo(rdsAvailable.arbitraryData.get())) {
                logd("SessionData is in RDS available notification.");
                mSessionData = SessionData.fromBytes(
                        TlvParser.parseOneTlv(rdsAvailable.arbitraryData.get()).value);
                mSessionCallback.onSessionDataReady(
                        mUniqueSessionId.get(),
                        Optional.of(mSessionData),
                        /* isSessionTerminated= */ false);
            } else {
                logd("try to read SessionData in applet.");
                GetDoCommand getSessionDataCommand =
                        GetDoCommand.build(CsmlUtil.constructSessionDataGetDoTlv());
                mFiRaSecureChannel.sendLocalFiRaCommand(getSessionDataCommand,
                        new FiRaSecureChannel.ExternalRequestCallback() {
                            @Override
                            public void onSuccess(byte[] responseData) {
                                logd("success to get session data from local FiRa applet.");
                                if (!CsmlUtil.isSessionDataDo(responseData)) {
                                    logw("session data is expected from applet.");
                                    terminateSession();
                                    mSessionCallback.onSessionAborted();
                                    return;
                                }
                                mSessionData = SessionData.fromBytes(
                                        TlvParser.parseOneTlv(responseData).value);
                                mSessionCallback.onSessionDataReady(
                                        mUniqueSessionId.get(),
                                        Optional.of(mSessionData),
                                        /* isSessionTerminated= */ false);
                            }

                            @Override
                            public void onFailure() {
                                logw("failed to get session data from applet.");
                                terminateSession();
                                mSessionCallback.onSessionAborted();
                            }
                        });
            }

            return true;
        }
        return false;
    }

    @Override
    protected void onUnsolicitedDataToHostReceived(@NonNull byte[] data) {
        logd("unsolicited data received: " + DataTypeConversionUtil.byteArrayToHexString(data));
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
