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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_CONTROLEE_INFO_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.PutDoCommand;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.Optional;

/**
 * The responder of dynamic STS session managed by the UWB controller.
 */
public class ControllerResponderSession extends ResponderSession {
    private static final String LOG_TAG = "ControllerResponder";

    public ControllerResponderSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
        mIsController = true;
    }

    @Override
    protected boolean onDispatchResponseReceived(@NonNull DispatchResponse dispatchResponse) {
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        DispatchResponse.ControleeInfoAvailableNotification controleeInfoAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable = (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                case NOTIFICATION_EVENT_ID_CONTROLEE_INFO_AVAILABLE:
                    controleeInfoAvailable =
                            (DispatchResponse.ControleeInfoAvailableNotification) notification;
                    break;
                default:
                    logw(
                            "Unexpected notification from dispatch response: "
                                    + notification.notificationEventId);
            }
        }
        if (controleeInfoAvailable != null) {
            handleControleeInfoAvailable(controleeInfoAvailable);
            return true;
        }
        if (rdsAvailable != null) {
            handleRdsAvailable(rdsAvailable);
            return true;
        }
        return false;
    }

    private void handleControleeInfoAvailable(
            @NonNull DispatchResponse.ControleeInfoAvailableNotification controleeInfoAvailable) {
        if (CsmlUtil.isControleeInfoDo(controleeInfoAvailable.arbitraryData)) {
            ControleeInfo controleeInfo =
                    ControleeInfo.fromBytes(TlvParser.parseOneTlv(
                            controleeInfoAvailable.arbitraryData).value);
            generateAndPutSessionDataToApplet(controleeInfo);
        } else {
            logd("try to get ControleeInfo from applet.");
            GetDoCommand getControleeInfoCommand =
                    GetDoCommand.build(CsmlUtil.constructGetDoTlv(CsmlUtil.CONTROLEE_INFO_DO_TAG));
            mFiRaSecureChannel.sendLocalFiRaCommand(getControleeInfoCommand,
                    new FiRaSecureChannel.ExternalRequestCallback() {
                        @Override
                        public void onSuccess(@NonNull byte[] responseData) {
                            ControleeInfo controleeInfo =
                                    ControleeInfo.fromBytes(
                                            TlvParser.parseOneTlv(responseData).value);
                            generateAndPutSessionDataToApplet(controleeInfo);
                        }

                        @Override
                        public void onFailure() {
                            logw("ControleeInfo is not available in applet.");
                            terminateSession();
                            mSessionCallback.onSessionAborted();
                        }
                    });
        }
    }

    private void generateAndPutSessionDataToApplet(@NonNull ControleeInfo controleeInfo) {
        try {
            mSessionData = CsmlUtil.generateSessionData(
                    mRunningProfileSessionInfo.uwbCapability,
                    controleeInfo,
                    mRunningProfileSessionInfo.sharedPrimarySessionId,
                    mRunningProfileSessionInfo.sharedPrimarySessionKeyInfo,
                    mUniqueSessionId.get(),
                    !mIsDefaultUniqueSessionId);
        } catch (IllegalStateException e) {
            logw("Uwb capability may not be compatible: " + e);
            terminateSession();
            mSessionCallback.onSessionAborted();
        }
        // put session data

        PutDoCommand putSessionDataCommand =
                PutDoCommand.build(CsmlUtil.constructGetOrPutDoTlv(
                        new TlvDatum(CsmlUtil.SESSION_DATA_DO_TAG, mSessionData.toBytes())));
        mFiRaSecureChannel.sendLocalFiRaCommand(putSessionDataCommand,
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess(@NonNull byte[] responseData) {
                        // do nothing, wait 'GetSessionData' command from remote.
                    }

                    @Override
                    public void onFailure() {
                        logw("failed to put session data to applet.");
                        terminateSession();
                        mSessionCallback.onSessionAborted();
                    }
                });
    }

    private void handleRdsAvailable(DispatchResponse.RdsAvailableNotification rdsAvailable) {
        if (mSessionData == null) {
            logw("session data is not available.");
            terminateSession();
            mSessionCallback.onSessionAborted();
            return;
        }
        if (mUniqueSessionId.isPresent() && mUniqueSessionId.get() != rdsAvailable.sessionId) {
            logw("unique session id was present, shouldn't be updated as "
                    + rdsAvailable.sessionId);
            mUniqueSessionId = Optional.of(rdsAvailable.sessionId);
        }
        mSessionCallback.onSessionDataReady(mUniqueSessionId.get(),
                Optional.of(mSessionData), /*isSessionTerminated=*/ false);
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
