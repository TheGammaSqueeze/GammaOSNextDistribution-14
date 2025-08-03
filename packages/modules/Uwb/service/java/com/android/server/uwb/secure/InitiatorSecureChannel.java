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

import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.Transport;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.InitiateTransactionCommand;
import com.android.server.uwb.secure.csml.InitiateTransactionResponse;
import com.android.server.uwb.secure.csml.SelectAdfCommand;
import com.android.server.uwb.secure.csml.SelectAdfResponse;
import com.android.server.uwb.secure.csml.TunnelCommand;
import com.android.server.uwb.secure.csml.TunnelResponse;
import com.android.server.uwb.util.ObjectIdentifier;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

class InitiatorSecureChannel extends FiRaSecureChannel {
    private static final String LOG_TAG = "InitiatorSecureChannel";

    InitiatorSecureChannel(
            @NonNull SecureElementChannel secureElementChannel,
            @NonNull Transport transport,
            @NonNull Looper workLooper,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(secureElementChannel, transport, workLooper, runningProfileSessionInfo);
    }

    @Override
    protected boolean doOpenSeChannelAfterInit() {
        return true;
    }

    @Override
    protected void handleScMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CMD_OPEN_CHANNEL:
                if (mSecureElementChannel.openChannel()) {
                    if (mRunningProfileSessionInfo.secureBlob.isPresent()) {
                        if (!swapInAdf(
                                mRunningProfileSessionInfo.secureBlob.get(),
                                mRunningProfileSessionInfo.oidOfProvisionedAdf,
                                mRunningProfileSessionInfo.controleeInfo.get().toBytes())) {
                            mSecureChannelCallback.onSetUpError(SetupError.OPEN_SE_CHANNEL);
                            return;
                        }
                    }
                    mStatus = Status.CHANNEL_OPENED;
                } else {
                    mSecureChannelCallback.onSetUpError(SetupError.OPEN_SE_CHANNEL);
                    return;
                }
                mWorkHandler.sendMessage(mWorkHandler.obtainMessage(CMD_SELECT_ADF));
                break;
            case CMD_SELECT_ADF:
                if (selectAdf(mRunningProfileSessionInfo.oidOfProvisionedAdf)) {
                    mWorkHandler.sendMessage(mWorkHandler.obtainMessage(CMD_INITIATE_TRANSACTION));
                    mStatus = Status.ADF_SELECTED;
                } else {
                    mSecureChannelCallback.onSetUpError(SetupError.SELECT_ADF);
                }
                break;
            case CMD_INITIATE_TRANSACTION:
                Optional<Integer> uwbSessionId = mRunningProfileSessionInfo.sharedPrimarySessionId;

                execInitiateTransactionCmd(
                        mRunningProfileSessionInfo.selectableOidsOfResponder.get(), uwbSessionId);
                break;
            default:
                super.handleScMessage(msg);
        }
    }

    private boolean selectAdf(@NonNull ObjectIdentifier adfOid) {
        SelectAdfCommand selectAdfCmd = SelectAdfCommand.build(adfOid);
        try {
            SelectAdfResponse response =
                    SelectAdfResponse.fromResponseApdu(
                            mSecureElementChannel.transmit(selectAdfCmd));
            if (!response.isSuccess()) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private void execInitiateTransactionCmd(
            @NonNull List<ObjectIdentifier> adfOids, @NonNull Optional<Integer> uwbSessionId) {

        InitiateTransactionCommand initiateTransactionCmd;
        if (uwbSessionId.isPresent()) {
            initiateTransactionCmd =
                    InitiateTransactionCommand.buildForMulticast(adfOids, uwbSessionId.get());
        } else {
            initiateTransactionCmd = InitiateTransactionCommand.buildForUnicast(adfOids);
        }
        try {
            InitiateTransactionResponse response =
                    InitiateTransactionResponse.fromResponseApdu(
                            mSecureElementChannel.transmit(initiateTransactionCmd));
            if (!response.isSuccess()) {
                throw new IllegalStateException(
                        "INIT TRANSACTION: CMD error: " + response.statusWord);
            }
            // must have outbound data, otherwise the flow is stopped.
            if (response.outboundDataToRemoteApplet.isPresent()
                    && !response.outboundDataToRemoteApplet.isEmpty()) {
                mWorkHandler.sendMessage(
                        mWorkHandler.obtainMessage(
                                CMD_SEND_OOB_DATA, OOB_MSG_TYPE_APDU_COMMAND, 0,
                                response.outboundDataToRemoteApplet.get()));
            } else {
                throw new IllegalStateException("No outbound data for InitiateTransaction CMD");
            }
        } catch (IOException | IllegalStateException e) {
            mSecureChannelCallback.onSetUpError(SetupError.INITIATE_TRANSACTION);
        }
    }

    @Override
    void tunnelToRemoteDevice(
            @NonNull byte[] data, @NonNull ExternalRequestCallback externalRequestCallback) {
        mWorkHandler.post(
                () -> {
                    if (mStatus != Status.ESTABLISHED) {
                        externalRequestCallback.onFailure();
                        return;
                    }

                    TunnelCommand tunnelCmd = TunnelCommand.build(data);
                    try {
                        TunnelResponse response =
                                TunnelResponse.fromResponseApdu(
                                        mSecureElementChannel.transmit(tunnelCmd));
                        if (response.isSuccess() && response.outboundDataOrApdu.isPresent()) {
                            mWorkHandler.sendMessage(
                                    mWorkHandler.obtainMessage(
                                            CMD_SEND_OOB_DATA, OOB_MSG_TYPE_APDU_COMMAND, 0,
                                            response.outboundDataOrApdu.get()));
                            externalRequestCallback.onSuccess(new byte[0]);
                        } else {
                            throw new IllegalStateException(
                                    "Tunnel CMD error: " + response.statusWord);
                        }
                    } catch (IOException | IllegalStateException e) {
                        logw("Exception for TUNNEL command: " + e);
                        externalRequestCallback.onFailure();
                    }
                });
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
}
