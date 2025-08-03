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

package com.android.bluetooth.sap;

import android.hardware.radio.sap.ISap;
import android.hardware.radio.sap.ISapCallback;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SapRiilReceiver is the AIDL implementation of ISapRilReceiver
 */
public class SapRilReceiver implements ISapRilReceiver {
    private static final String TAG = "SapRilReceiver";
    public static final boolean DEBUG = true;
    public static final boolean VERBOSE = true;

    // todo: add support for slot2 and slot3
    private static final String HAL_INSTANCE_NAME = ISap.DESCRIPTOR + "/slot1";

    SapCallback mSapCallback;
    volatile ISap mSapProxy = null;
    final Object mSapProxyLock = new Object();
    final AtomicLong mSapProxyCookie = new AtomicLong(0);
    final SapProxyDeathRecipient mSapProxyDeathRecipient;

    private Handler mSapServerMsgHandler = null;
    private Handler mSapServiceHandler = null;

    public static final int RIL_MAX_COMMAND_BYTES = (8 * 1024);
    public byte[] buffer = new byte[RIL_MAX_COMMAND_BYTES];

    /**
     * TRANSFER_APDU_REQ from SAP 1.1 spec 5.1.6
     *
     * @param serial  Id to match req-resp. Resp must include same serial.
     * @param type    APDU command type
     * @param command CommandAPDU/CommandAPDU7816 parameter depending on type
     */
    @Override
    public void apduReq(int serial, int type, byte[] command) throws android.os.RemoteException {
        mSapProxy.apduReq(serial, type, command);
    }

    /**
     * CONNECT_REQ from SAP 1.1 spec 5.1.1
     *
     * @param serial          Id to match req-resp. Resp must include same serial.
     * @param maxMsgSizeBytes MaxMsgSize to be used for SIM Access Profile connection
     */
    @Override
    public void connectReq(int serial, int maxMsgSizeBytes) throws android.os.RemoteException {
        mSapProxy.connectReq(serial, maxMsgSizeBytes);
    }

    /**
     * DISCONNECT_REQ from SAP 1.1 spec 5.1.3
     *
     * @param serial Id to match req-resp. Resp must include same serial.
     */
    @Override
    public void disconnectReq(int serial) throws android.os.RemoteException {
        mSapProxy.disconnectReq(serial);
    }

    /**
     * POWER_SIM_OFF_REQ and POWER_SIM_ON_REQ from SAP 1.1 spec 5.1.10 + 5.1.12
     *
     * @param serial  Id to match req-resp. Resp must include same serial.
     * @param powerOn true for on, false for off
     */
    @Override
    public void powerReq(int serial, boolean powerOn) throws android.os.RemoteException {
        mSapProxy.powerReq(serial, powerOn);
    }

    /**
     * RESET_SIM_REQ from SAP 1.1 spec 5.1.14
     *
     * @param serial Id to match req-resp. Resp must include same serial.
     */
    @Override
    public void resetSimReq(int serial) throws android.os.RemoteException {
        mSapProxy.resetSimReq(serial);
    }

    /**
     * Set callback that has response and unsolicited indication functions
     *
     * @param sapCallback Object containing response and unosolicited indication callbacks
     */
    @Override
    public void setCallback(android.hardware.radio.sap.ISapCallback sapCallback)
            throws android.os.RemoteException {
        Log.e(TAG, "setCallback should never be called");
    }

    /**
     * SET_TRANSPORT_PROTOCOL_REQ from SAP 1.1 spec 5.1.20
     *
     * @param serial           Id to match req-resp. Resp must include same serial.
     * @param transferProtocol Transport Protocol
     */
    @Override
    public void setTransferProtocolReq(int serial, int transferProtocol)
            throws android.os.RemoteException {
        mSapProxy.setTransferProtocolReq(serial, transferProtocol);
    }

    /**
     * TRANSFER_ATR_REQ from SAP 1.1 spec 5.1.8
     *
     * @param serial Id to match req-resp. Resp must include same serial.
     */
    @Override
    public void transferAtrReq(int serial) throws android.os.RemoteException {
        mSapProxy.transferAtrReq(serial);
    }

    /**
     * TRANSFER_CARD_READER_STATUS_REQ from SAP 1.1 spec 5.1.17
     *
     * @param serial Id to match req-resp. Resp must include same serial.
     */
    @Override
    public void transferCardReaderStatusReq(int serial) throws android.os.RemoteException {
        mSapProxy.transferCardReaderStatusReq(serial);
    }

    @Override
    public int getInterfaceVersion() {
        Log.e(TAG, "getInterfaceVersion should never be called");
        return 0;
    }

    @Override
    public String getInterfaceHash() {
        Log.e(TAG, "getInterfaceHash should never be called");
        return "";
    }

    @Override
    public android.os.IBinder asBinder() {
        Log.e(TAG, "asBinder should never be called");
        return null;
    }

    final class SapProxyDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            // Deal with service going away
            Log.d(TAG, "serviceDied");
            // todo: temp hack to send delayed message so that rild is back up by then
            mSapServerMsgHandler.sendMessageDelayed(
                    mSapServerMsgHandler.obtainMessage(SapServer.SAP_PROXY_DEAD, (long) 0),
                    SapServer.ISAP_GET_SERVICE_DELAY_MILLIS);
        }
    }

    private void sendSapMessage(SapMessage sapMessage) {
        if (sapMessage.getMsgType() < SapMessage.ID_RIL_BASE) {
            sendClientMessage(sapMessage);
        } else {
            sendRilIndMessage(sapMessage);
        }
    }

    private void removeOngoingReqAndSendMessage(int token, SapMessage sapMessage) {
        Integer reqType = SapMessage.sOngoingRequests.remove(token);
        if (VERBOSE) {
            Log.d(TAG, "removeOngoingReqAndSendMessage: token " + token + " reqType " + (
                    reqType == null ? "null" : SapMessage.getMsgTypeName(reqType)));
        }
        sendSapMessage(sapMessage);
    }

    class SapCallback extends ISapCallback.Stub {
        @Override
        public void connectResponse(int token, int sapConnectRsp, int maxMsgSize) {
            Log.d(TAG, "connectResponse: token " + token + " sapConnectRsp " + sapConnectRsp
                    + " maxMsgSize " + maxMsgSize);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_CONNECT_RESP);
            sapMessage.setConnectionStatus(sapConnectRsp);
            if (sapConnectRsp == SapMessage.CON_STATUS_ERROR_MAX_MSG_SIZE_UNSUPPORTED) {
                sapMessage.setMaxMsgSize(maxMsgSize);
            }
            sapMessage.setResultCode(SapMessage.INVALID_VALUE);
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void disconnectResponse(int token) {
            Log.d(TAG, "disconnectResponse: token " + token);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_DISCONNECT_RESP);
            sapMessage.setResultCode(SapMessage.INVALID_VALUE);
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void disconnectIndication(int token, int disconnectType) {
            Log.d(TAG,
                    "disconnectIndication: token " + token + " disconnectType " + disconnectType);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_RIL_UNSOL_DISCONNECT_IND);
            sapMessage.setDisconnectionType(disconnectType);
            sendSapMessage(sapMessage);
        }

        @Override
        public void apduResponse(int token, int resultCode, byte[] apduRsp) {
            Log.d(TAG, "apduResponse: token " + token);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_TRANSFER_APDU_RESP);
            sapMessage.setResultCode(resultCode);
            if (resultCode == SapMessage.RESULT_OK) {
                sapMessage.setApduResp(apduRsp);
            }
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void transferAtrResponse(int token, int resultCode, byte[] atr) {
            Log.d(TAG, "transferAtrResponse: token " + token + " resultCode " + resultCode);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_TRANSFER_ATR_RESP);
            sapMessage.setResultCode(resultCode);
            if (resultCode == SapMessage.RESULT_OK) {
                sapMessage.setAtr(atr);
            }
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void powerResponse(int token, int resultCode) {
            Log.d(TAG, "powerResponse: token " + token + " resultCode " + resultCode);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            Integer reqType = SapMessage.sOngoingRequests.remove(token);
            if (VERBOSE) {
                Log.d(TAG, "powerResponse: reqType " + (reqType == null ? "null"
                        : SapMessage.getMsgTypeName(reqType)));
            }
            SapMessage sapMessage;
            if (reqType == SapMessage.ID_POWER_SIM_OFF_REQ) {
                sapMessage = new SapMessage(SapMessage.ID_POWER_SIM_OFF_RESP);
            } else if (reqType == SapMessage.ID_POWER_SIM_ON_REQ) {
                sapMessage = new SapMessage(SapMessage.ID_POWER_SIM_ON_RESP);
            } else {
                return;
            }
            sapMessage.setResultCode(resultCode);
            sendSapMessage(sapMessage);
        }

        @Override
        public void resetSimResponse(int token, int resultCode) {
            Log.d(TAG, "resetSimResponse: token " + token + " resultCode " + resultCode);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_RESET_SIM_RESP);
            sapMessage.setResultCode(resultCode);
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void statusIndication(int token, int status) {
            Log.d(TAG, "statusIndication: token " + token + " status " + status);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_STATUS_IND);
            sapMessage.setStatusChange(status);
            sendSapMessage(sapMessage);
        }

        @Override
        public void transferCardReaderStatusResponse(int token, int resultCode,
                int cardReaderStatus) {
            Log.d(TAG,
                    "transferCardReaderStatusResponse: token " + token + " resultCode " + resultCode
                            + " cardReaderStatus " + cardReaderStatus);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_TRANSFER_CARD_READER_STATUS_RESP);
            sapMessage.setResultCode(resultCode);
            if (resultCode == SapMessage.RESULT_OK) {
                sapMessage.setCardReaderStatus(cardReaderStatus);
            }
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public void errorResponse(int token) {
            Log.d(TAG, "errorResponse: token " + token);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            // Since ERROR_RESP isn't supported by createUnsolicited(), keeping behavior same here
            // SapMessage sapMessage = new SapMessage(SapMessage.ID_ERROR_RESP);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_RIL_UNKNOWN);
            sendSapMessage(sapMessage);
        }

        @Override
        public void transferProtocolResponse(int token, int resultCode) {
            Log.d(TAG, "transferProtocolResponse: token " + token + " resultCode " + resultCode);
            SapService.notifyUpdateWakeLock(mSapServiceHandler);
            SapMessage sapMessage = new SapMessage(SapMessage.ID_SET_TRANSPORT_PROTOCOL_RESP);
            sapMessage.setResultCode(resultCode);
            removeOngoingReqAndSendMessage(token, sapMessage);
        }

        @Override
        public String getInterfaceHash() {
            return ISapCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ISapCallback.VERSION;
        }
    }

    @Override
    public Object getSapProxyLock() {
        return mSapProxyLock;
    }

    @Override
    public boolean isProxyValid() {
        // Only call when synchronized with getSapProxyLock
        return mSapProxy != null;
    }

    /**
     * Check if AIDL is supported
     */
    public static boolean isAidlSupported() {
        return SdkLevel.isAtLeastU() && ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    /**
     * Obtain a valid sapProxy
     */
    public ISap getSapProxy() {
        synchronized (mSapProxyLock) {
            if (mSapProxy != null) {
                return mSapProxy;
            }

            try {
                IBinder service = ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME);
                mSapProxy = ISap.Stub.asInterface(service);
                if (mSapProxy != null) {
                    service.linkToDeath(mSapProxyDeathRecipient,
                            /* flags= */ 0);
                    mSapProxy.setCallback(mSapCallback);
                } else {
                    Log.e(TAG, "getSapProxy: mSapProxy == null");
                }
            } catch (RemoteException | RuntimeException e) {
                mSapProxy = null;
                Log.e(TAG, "getSapProxy: exception: " + e);
            }

            if (mSapProxy == null) {
                // if service is not up, treat it like death notification to try to get service
                // again
                mSapServerMsgHandler.sendMessageDelayed(
                        mSapServerMsgHandler.obtainMessage(SapServer.SAP_PROXY_DEAD,
                                mSapProxyCookie.get()), SapServer.ISAP_GET_SERVICE_DELAY_MILLIS);
            }
            return mSapProxy;
        }
    }

    @Override
    public void resetSapProxy() {
        synchronized (mSapProxyLock) {
            if (DEBUG) Log.d(TAG, "resetSapProxy :" + mSapProxy);
            if (mSapProxy == null) {
                return;
            }
            if (mSapProxy.asBinder() == null) {
                Log.e(TAG, "asdf asBinder is null");
            }
            mSapProxy.asBinder().unlinkToDeath(mSapProxyDeathRecipient, /* flags= */ 0);
            mSapProxy = null;
        }
    }

    public SapRilReceiver(Handler sapServerMsgHandler, Handler sapServiceHandler) {
        mSapServerMsgHandler = sapServerMsgHandler;
        mSapServiceHandler = sapServiceHandler;
        mSapCallback = new SapCallback();
        mSapProxyDeathRecipient = new SapProxyDeathRecipient();
        synchronized (mSapProxyLock) {
            mSapProxy = getSapProxy();
        }
    }

    @Override
    public void notifyShutdown() {
        if (DEBUG) {
            Log.i(TAG, "notifyShutdown()");
        }
        synchronized (mSapProxyLock) {
            // If we are already shutdown, don't bother sending a notification.
            if (mSapProxy != null) {
                sendShutdownMessage();
            }
            resetSapProxy();

            // todo: rild should be back up since the message was sent with a delay. this is
            // a hack.
            getSapProxy();
        }
    }

    @Override
    public void sendRilConnectMessage() {
        if (mSapServerMsgHandler != null) {
            mSapServerMsgHandler.sendEmptyMessage(SapServer.SAP_MSG_RIL_CONNECT);
        }
    }

    /**
     * Send reply (solicited) message from the RIL to the Sap Server Handler Thread
     *
     * @param sapMsg The message to send
     */
    private void sendClientMessage(SapMessage sapMsg) {
        Message newMsg = mSapServerMsgHandler.obtainMessage(SapServer.SAP_MSG_RFC_REPLY, sapMsg);
        mSapServerMsgHandler.sendMessage(newMsg);
    }

    /**
     * Send a shutdown signal to SapServer to indicate the
     */
    private void sendShutdownMessage() {
        if (mSapServerMsgHandler != null) {
            mSapServerMsgHandler.sendEmptyMessage(SapServer.SAP_RIL_SOCK_CLOSED);
        }
    }

    /**
     * Send indication (unsolicited) message from RIL to the Sap Server Handler Thread
     *
     * @param sapMsg The message to send
     */
    private void sendRilIndMessage(SapMessage sapMsg) {
        Message newMsg = mSapServerMsgHandler.obtainMessage(SapServer.SAP_MSG_RIL_IND, sapMsg);
        mSapServerMsgHandler.sendMessage(newMsg);
    }

    AtomicLong getSapProxyCookie() {
        return mSapProxyCookie;
    }
}
