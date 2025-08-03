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

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.INS_SELECT;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.P1_SELECT_BY_DEDICATED_FILE_NAME;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;

import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.Transport;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;

import java.io.IOException;

class ResponderSecureChannel extends FiRaSecureChannel {
    private static final String LOG_TAG = "ResponderSecureChannel";

    ResponderSecureChannel(
            @NonNull SecureElementChannel secureElementChannel,
            @NonNull Transport transport,
            @NonNull Looper workLooper,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(secureElementChannel, transport, workLooper, runningProfileSessionInfo);
    }

    @Override
    protected void handleScMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CMD_OPEN_CHANNEL:
                try {
                    ResponseApdu responseApdu = mSecureElementChannel.openChannelWithResponse();
                    if (responseApdu.getStatusWord() == SW_NO_ERROR.toInt()) {
                        if (mRunningProfileSessionInfo.secureBlob.isPresent()) {
                            if (!swapInAdf(
                                    mRunningProfileSessionInfo.secureBlob.get(),
                                    mRunningProfileSessionInfo.oidOfProvisionedAdf,
                                    mRunningProfileSessionInfo.controleeInfo.get().toBytes())) {
                                mSecureElementChannel.closeChannel();
                                throw new IllegalStateException("Error on swapping in ADF");
                            }
                        }
                        mStatus = Status.CHANNEL_OPENED;
                    } else {
                        throw new IllegalStateException(
                                String.valueOf(responseApdu.getStatusWord()));
                    }
                    mWorkHandler.sendMessage(
                            mWorkHandler.obtainMessage(
                                    CMD_SEND_OOB_DATA, OOB_MSG_TYPE_APDU_RESPONSE, 0,
                                    responseApdu.toByteArray()));
                } catch (IOException | IllegalStateException e) {
                    logw("Error on open channel: " + e);
                    mSecureChannelCallback.onSetUpError(SetupError.OPEN_SE_CHANNEL);
                    ResponseApdu responseApdu = ResponseApdu.SW_APPLET_SELECT_FAILED_APDU;
                    mWorkHandler.sendMessage(
                            mWorkHandler.obtainMessage(
                                    CMD_SEND_OOB_DATA, OOB_MSG_TYPE_APDU_RESPONSE, 0,
                                    responseApdu.toByteArray()));
                }
                // waiting for next request from the initiator.
                break;
            default:
                super.handleScMessage(msg);
        }
    }

    @Override
    protected boolean doOpenSeChannelAfterInit() {
        return false;
    }

    @Override
    protected boolean preprocessRemoteCommand(@NonNull byte[] data) {
        if (mStatus == Status.INITIALIZED && isSelectApdu(data)) {
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(CMD_OPEN_CHANNEL));

            return true;
        }
        return false;
    }

    private boolean isSelectApdu(@NonNull byte[] data) {
        try {
            CommandApdu command = CommandApdu.parse(data);
            return command.getCla() == (byte) 0x00
                    && command.getIns() == INS_SELECT
                    && command.getP1() == P1_SELECT_BY_DEDICATED_FILE_NAME;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    void tunnelToRemoteDevice(
            @NonNull byte[] data, @NonNull ExternalRequestCallback externalRequestCallback) {
        throw new IllegalStateException("tunnel is not supported for the Responder.");
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
}
