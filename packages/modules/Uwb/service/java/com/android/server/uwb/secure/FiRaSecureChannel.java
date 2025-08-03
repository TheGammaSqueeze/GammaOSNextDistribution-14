/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_ADF_SELECTED;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.discovery.Transport;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchCommand;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.GetDoResponse;
import com.android.server.uwb.secure.csml.SwapInAdfCommand;
import com.android.server.uwb.secure.csml.SwapInAdfResponse;
import com.android.server.uwb.secure.csml.SwapOutAdfCommand;
import com.android.server.uwb.secure.csml.SwapOutAdfResponse;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Set up the secure channel and handle the Tunnel data request.
 * For Tunnel data, simplex from Initiator is support. as the 'DISPATCH' limitation.
 */
@WorkerThread
public abstract class FiRaSecureChannel {
    private static final String LOG_TAG = "FiRaSecureChannel";

    private final Transport mTransport;
    protected final SecureElementChannel mSecureElementChannel;
    protected final RunningProfileSessionInfo mRunningProfileSessionInfo;
    protected SecureChannelCallback mSecureChannelCallback;
    @VisibleForTesting final Handler mWorkHandler;

    enum SetupError {
        INIT,
        SELECT_ADF,
        SWAP_IN_ADF,
        INITIATE_TRANSACTION,
        OPEN_SE_CHANNEL,
        DISPATCH,
        ADF_NOT_MATCHED,
    }

    enum Status {
        UNINITIALIZED,
        INITIALIZED,
        CHANNEL_OPENED,
        ADF_SELECTED,
        ESTABLISHED,
        TERMINATED,
        ABNORMAL,
    }

    static final int CMD_INIT = 0;
    static final int CMD_OPEN_CHANNEL = 1;
    static final int CMD_SELECT_ADF = 2;
    static final int CMD_INITIATE_TRANSACTION = 3;
    static final int CMD_SEND_OOB_DATA = 4;
    static final int CMD_PROCESS_RECEIVED_OOB_DATA = 5;
    static final int CMD_CLEAN_UP_TERMINATED_OR_ABORTED_CHANNEL = 6;

    static final int OOB_MSG_TYPE_APDU_COMMAND = 0;
    static final int OOB_MSG_TYPE_APDU_RESPONSE = 1;

    protected Status mStatus = Status.UNINITIALIZED;
    private Optional<byte[]> mDynamicSlotIdentifier = Optional.empty();

    FiRaSecureChannel(
            @NonNull SecureElementChannel secureElementChannel,
            @NonNull Transport transport,
            @NonNull Looper workLooper,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        this.mSecureElementChannel = secureElementChannel;
        this.mTransport = transport;
        this.mWorkHandler =
                new Handler(workLooper) {
                    @Override
                    public void handleMessage(Message msg) {
                        handleScMessage(msg);
                    }
                };
        this.mRunningProfileSessionInfo = runningProfileSessionInfo;
    }

    private final Transport.DataReceiver mDataReceiver =
            new Transport.DataReceiver() {
                @Override
                public void onDataReceived(@NonNull byte[] data) {
                    mWorkHandler.sendMessage(
                            mWorkHandler.obtainMessage(CMD_PROCESS_RECEIVED_OOB_DATA, data));
                }
            };

    protected void handleScMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CMD_INIT:
                mSecureElementChannel.init(
                        () -> {
                            // do nothing for ROLE_RESPONDER, wait cmd from remote device
                            if (doOpenSeChannelAfterInit()) {
                                mWorkHandler.sendMessage(
                                        mWorkHandler.obtainMessage(CMD_OPEN_CHANNEL));
                            }

                            mTransport.registerDataReceiver(mDataReceiver);
                            mStatus = Status.INITIALIZED;
                        });
                break;
            case CMD_SEND_OOB_DATA:
                byte[] payload = (byte[]) msg.obj;
                int msgType = msg.arg1;
                MessageType firaMsgType =
                        msgType == OOB_MSG_TYPE_APDU_COMMAND
                                ? MessageType.COMMAND : MessageType.COMMAND_RESPOND;
                mTransport.sendData(
                        firaMsgType,
                        payload,
                        new Transport.SendingDataCallback() {
                            @Override
                            public void onSuccess() {
                                // do nothing
                            }

                            @Override
                            public void onFailure() {
                                // TODO: retry to send it, end the session if it is failed many
                                // times.
                            }
                        });
                break;
            case CMD_PROCESS_RECEIVED_OOB_DATA:
                byte[] receivedData = (byte[]) msg.obj;
                processRemoteCommandOrResponse(receivedData);
                break;
            case CMD_CLEAN_UP_TERMINATED_OR_ABORTED_CHANNEL:
                mDynamicSlotIdentifier.ifPresent((slotId) -> swapOutAdf(slotId));

                if (mSecureElementChannel.closeChannel()) {
                    mStatus = Status.INITIALIZED;
                    mSecureChannelCallback.onSeChannelClosed(/*withError=*/ false);
                } else {
                    logw("error happened on closing SE channel");
                    mStatus = Status.ABNORMAL;
                    mSecureChannelCallback.onSeChannelClosed(/*withError=*/ true);
                }

                break;
        }
    }

    protected abstract boolean doOpenSeChannelAfterInit();

    /**
     * Initiate the secure session set up.
     */
    public void init(@NonNull SecureChannelCallback secureChannelCallback) {
        if (mStatus == Status.ABNORMAL) {
            throw new IllegalStateException("fatal error, the session should be discarded");
        }
        mWorkHandler.sendMessage(mWorkHandler.obtainMessage(CMD_INIT));
        mSecureChannelCallback = secureChannelCallback;
    }

    /**
     * Swap in the ADF, this is optional, used only when the service profile is using the
     * dynamic slot.
     * @param secureBlob The secure BLOB contains the ADF OID and its encrypted content.
     */
    protected final boolean swapInAdf(
            @NonNull byte[] secureBlob,
            @NonNull ObjectIdentifier adfOid,
            @NonNull byte[] uwbControleeInfo) {
        SwapInAdfCommand swapInAdfCmd =
                SwapInAdfCommand.build(secureBlob, adfOid, uwbControleeInfo);
        try {
            SwapInAdfResponse response =
                    SwapInAdfResponse.fromResponseApdu(
                            mSecureElementChannel.transmit(swapInAdfCmd));
            if (!response.isSuccess() || response.slotIdentifier.isEmpty()) {
                throw new IllegalStateException(response.statusWord.toString());
            } else {
                mDynamicSlotIdentifier = response.slotIdentifier;
                return true;
            }
        } catch (IOException | IllegalStateException e) {
            logw("error on swapping in ADF: " + e);
        }
        return false;
    }

    private boolean swapOutAdf(@NonNull byte[] slotIdentifier) {
        SwapOutAdfCommand swapOutAdfCmd = SwapOutAdfCommand.build(slotIdentifier);
        try {
            SwapOutAdfResponse response =
                    SwapOutAdfResponse.fromResponseApdu(
                            mSecureElementChannel.transmit(swapOutAdfCmd));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.statusWord.toString());
            }
            mDynamicSlotIdentifier = Optional.empty();
        } catch (IOException | IllegalStateException e) {
            logw("Failed to swap out ADF with exception: " + e);
            return false;
        }
        return true;
    }

    protected boolean preprocessRemoteCommand(@NonNull byte[] data) {
        return false;
    }

    @VisibleForTesting
    void processRemoteCommandOrResponse(@NonNull byte[] data) {
        if (preprocessRemoteCommand(data)) {
            return;
        }

        try {
            if (!mSecureElementChannel.isOpened()) {
                throw new IllegalStateException("the SE is not opened to handle command.");
            }
            // otherwise, dispatch to FiRa applet
            DispatchCommand dispatchCommand = DispatchCommand.build(data);
            DispatchResponse response =
                    DispatchResponse.fromResponseApdu(
                            mSecureElementChannel.transmit(dispatchCommand));
            if (mStatus == Status.ESTABLISHED) {
                // send to initiator or responder
                mSecureChannelCallback.onDispatchResponseAvailable(response);
            } else {
                if (!response.isSuccess()) {
                    throw new IllegalStateException(
                            "Dispatch Command error: " + response.statusWord);
                }
                handleDispatchResponseForSc(response);
            }
        } catch (IOException | IllegalStateException e) {
            logw("Dispatch command failed for " + e);
            if (mStatus != Status.ESTABLISHED) {
                mSecureChannelCallback.onSetUpError(SetupError.DISPATCH);
                ResponseApdu responseApdu = ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU;
                mWorkHandler.sendMessage(
                        mWorkHandler.obtainMessage(CMD_SEND_OOB_DATA, responseApdu.toByteArray()));
            } else {
                // send the error to initiator or responder.
                mSecureChannelCallback.onDispatchCommandFailure();
            }
        }
    }

    private void handleDispatchResponseForSc(@NonNull DispatchResponse dispatchResponse) {
        Optional<DispatchResponse.OutboundData> outboundData = dispatchResponse.getOutboundData();
        if (outboundData.isPresent()) {
            if (outboundData.get().target == DispatchResponse.OUTBOUND_TARGET_REMOTE) {
                mWorkHandler.sendMessage(
                        mWorkHandler.obtainMessage(CMD_SEND_OOB_DATA, outboundData.get().data));
            } else {
                if (mStatus != Status.ESTABLISHED) {
                    logw(
                            "Session set up, ignore data to host, dup as SW "
                                    + DataTypeConversionUtil.byteArrayToHexString(
                                            outboundData.get().data));
                }
            }
        }
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_ADF_SELECTED:
                    logd("ADF selected");
                    DispatchResponse.AdfSelectedNotification adfSelected =
                            (DispatchResponse.AdfSelectedNotification) notification;
                    ObjectIdentifier selectedAdfOid = adfSelected.adfOid;
                    if (!mRunningProfileSessionInfo.oidOfProvisionedAdf
                            .equals(adfSelected.adfOid)) {
                        logw("The selected ADF doesn't match the provisioned ADF.");
                        mSecureChannelCallback.onSetUpError(SetupError.ADF_NOT_MATCHED);
                    } else {
                        mStatus = Status.ADF_SELECTED;
                    }
                    break;
                case NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED:
                    logd("SC established");
                    mStatus = Status.ESTABLISHED;
                    DispatchResponse.SecureChannelEstablishedNotification eNotification =
                            (DispatchResponse.SecureChannelEstablishedNotification) notification;
                    logd("defaultSessionId from notification: "
                            + eNotification.defaultSessionId);
                    Optional<Integer> defaultSessionId = Optional.empty();
                    if (eNotification.defaultSessionId.isEmpty()) {
                        defaultSessionId = readDefaultSessionId();
                    }
                    mSecureChannelCallback.onEstablished(defaultSessionId);
                    break;
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED:
                    cleanUpTerminatedOrAbortedSession();
                    break;
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    logd("RDS available and SC terminated automatically");
                    // see CSML 8.2.2.7.1.8 Table 64 - ADF Extended Options
                    // RDS available means the session is using the default session id and key
                    // Also the secure channel is terminated automatically.
                    DispatchResponse.RdsAvailableNotification rdsAvailableNotification =
                            (DispatchResponse.RdsAvailableNotification) notification;
                    mStatus = Status.TERMINATED;
                    mSecureChannelCallback.onRdsAvailableAndTerminated(
                            rdsAvailableNotification.sessionId);
                    break;
                default:
                    logw(
                            "Unexpected notification from dispatch response: "
                                    + notification.notificationEventId);
            }
        }
    }

    private Optional<Integer> readDefaultSessionId() {
        TlvDatum getSessionIdTlv = CsmlUtil.constructGetSessionIdGetDoTlv();
        GetDoCommand getSessionIdCommand = GetDoCommand.build(getSessionIdTlv);
        try {
            ResponseApdu responseApdu =
                    mSecureElementChannel.transmit(getSessionIdCommand);
            if (responseApdu != null && responseApdu.getStatusWord() == SW_NO_ERROR.toInt()) {
                TlvDatum sessionIdTlv = TlvParser.parseOneTlv(responseApdu.getResponseData());
                if (sessionIdTlv != null
                        && Objects.equals(sessionIdTlv.tag, CsmlUtil.SESSION_ID_TAG)) {
                    return Optional.of(
                            DataTypeConversionUtil.arbitraryByteArrayToI32(sessionIdTlv.value));
                }
            } else {
                throw new IllegalStateException("no valid APDU response.");
            }
        } catch (IOException | IllegalStateException e) {
            logw("error to getSessionId DO.");
        }
        return Optional.empty();
    }

    boolean isEstablished() {
        return mStatus == Status.ESTABLISHED;
    }

    void sendRawDataToRemote(@NonNull byte[] data) {
        mWorkHandler.sendMessage(mWorkHandler.obtainMessage(CMD_SEND_OOB_DATA, data));
    }

    void cleanUpTerminatedOrAbortedSession() {
        mWorkHandler.sendMessage(
                mWorkHandler.obtainMessage(CMD_CLEAN_UP_TERMINATED_OR_ABORTED_CHANNEL));
    }

    void sendLocalFiRaCommand(
            @NonNull FiRaCommand fiRaCommand,
            @NonNull ExternalRequestCallback externalRequestCallback) {
        sendLocalCommandApdu(fiRaCommand.getCommandApdu(), externalRequestCallback);
    }

    /**
     * Send the APDU to the FiRa applet through the channel.
     */
    void sendLocalCommandApdu(
            @NonNull CommandApdu commandApdu,
            @NonNull ExternalRequestCallback externalRequestCallback) {
        mWorkHandler.post(
                () -> {
                    try {
                        if (!mSecureElementChannel.isOpened()) {
                            throw new IllegalStateException("the OMAPI channel is not opened.");
                        }

                        ResponseApdu responseApdu = mSecureElementChannel.transmit(commandApdu);
                        if (responseApdu.getStatusWord() == SW_NO_ERROR.toInt()) {
                            externalRequestCallback.onSuccess(responseApdu.getResponseData());
                        } else {
                            logw("Applet failed to handle the APDU: " + commandApdu);
                            externalRequestCallback.onFailure();
                        }
                    } catch (IOException | IllegalStateException e) {
                        logw("sendLocalCommandApdu failed as: " + e);
                        externalRequestCallback.onFailure();
                    }
                });
    }

    abstract void tunnelToRemoteDevice(
            @NonNull byte[] data, @NonNull ExternalRequestCallback externalRequestCallback);

    void terminateLocally() {
        mWorkHandler.post(
                () -> {
                    if (mStatus != Status.ESTABLISHED) {
                        mSecureChannelCallback.onTerminated(/*withError=*/ false);
                        return;
                    }
                    // send terminate command to SE
                    // send GetDataDO - terminate session to local.
                    TlvDatum terminateSessionDo = CsmlUtil.constructTerminateSessionGetDoTlv();
                    GetDoCommand getDoCommand = GetDoCommand.build(terminateSessionDo);
                    try {
                        GetDoResponse response =
                                GetDoResponse.fromResponseApdu(
                                        mSecureElementChannel.transmit(getDoCommand));
                        if (response.isSuccess()) {
                            mSecureChannelCallback.onTerminated(/*withError=*/ false);
                            mStatus = Status.TERMINATED;
                        } else {
                            throw new IllegalStateException(
                                    "Terminate response error: " + response.statusWord);
                        }
                    } catch (IOException | IllegalStateException e) {
                        logw("Error happened on termination locally: " + e);
                        mStatus = Status.ABNORMAL;
                        mSecureChannelCallback.onTerminated(/*withError=*/ true);
                    }
                });
    }

    Status getStatus() {
        return mStatus;
    }

    interface SecureChannelCallback {
        /**
         * The secure session is set up. Ready to handle secure message exchanging.
         */
        void onEstablished(@NonNull Optional<Integer> defaultUniqueSessionId);

        /**
         * Error happens during the secure session set up.
         */
        void onSetUpError(SetupError error);

        /**
         * Received DispatchResponse which is for the DispatchCommand
         * received from the remote device after the secure channel setup.
         */
        void onDispatchResponseAvailable(DispatchResponse dispatchResponse);

        /**
         * The dispatch command wasn't handled correctly by the applet.
         */
        void onDispatchCommandFailure();

        /**
         * The Secure channel is terminated as response of  TERMINATE command.
         * If the channel is automatically terminated, this will not be called.
         */
        void onTerminated(boolean withError);

        /**
         * The secure element channel for the session  is closed.
         */
        void onSeChannelClosed(boolean withError);

        /**
         * The session is set up completed and terminated automatically.
         *
         * @param sessionId - the uwb session ID derived in the FiRa applet
         */
        void onRdsAvailableAndTerminated(int sessionId);
    }

    interface ExternalRequestCallback {
        /**
         * The request is handled correctly.
         */
        void onSuccess(@NonNull byte[] responseData);

        /**
         * The request cannot be handled.
         */
        void onFailure();
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
