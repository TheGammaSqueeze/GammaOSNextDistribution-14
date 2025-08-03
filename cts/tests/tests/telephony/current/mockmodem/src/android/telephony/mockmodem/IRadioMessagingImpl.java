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

package android.telephony.mockmodem;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.messaging.IRadioMessaging;
import android.hardware.radio.messaging.IRadioMessagingIndication;
import android.hardware.radio.messaging.IRadioMessagingResponse;
import android.hardware.radio.messaging.SendSmsResult;
import android.os.RemoteException;
import android.support.annotation.GuardedBy;
import android.util.ArraySet;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class IRadioMessagingImpl extends IRadioMessaging.Stub {
    private static final String TAG = "MRMSG";

    private final MockModemService mService;
    private IRadioMessagingResponse mRadioMessagingResponse;
    private IRadioMessagingIndication mRadioMessagingIndication;

    @GuardedBy("mGsmBroadcastConfigSet")
    private final Set<Integer> mGsmBroadcastConfigSet = new ArraySet<Integer>();

    @GuardedBy("mCdmaBroadcastConfigSet")
    private final Set<Integer> mCdmaBroadcastConfigSet = new ArraySet<Integer>();

    private CopyOnWriteArrayList<CallBackWithExecutor> mBroadcastCallbacks =
            new CopyOnWriteArrayList<>();

    private MockModemConfigInterface mMockModemConfigInterface;
    private int mSubId;
    private String mTag;
    private final MockMessagingService mMockMessagingService;

    public interface BroadcastCallback {
        void onGsmBroadcastActivated();
        void onCdmaBroadcastActivated();
    }

    public static class CallBackWithExecutor {
        public Executor mExecutor;
        public BroadcastCallback mCallback;

        public CallBackWithExecutor(Executor executor, BroadcastCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }
    }

    public IRadioMessagingImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;
        mMockMessagingService = new MockMessagingService(instanceId);
    }

    // Implementation of IRadioMessaging functions
    @Override
    public void setResponseFunctions(
            IRadioMessagingResponse radioMessagingResponse,
            IRadioMessagingIndication radioMessagingIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioMessagingResponse = radioMessagingResponse;
        mRadioMessagingIndication = radioMessagingIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(int serial, boolean success, String ackPdu) {
        Log.d(mTag, "acknowledgeIncomingGsmSmsWithPdu");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeIncomingGsmSmsWithPduResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to acknowledgeIncomingGsmSmsWithPdu from AIDL. Exception" + ex);
        }
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(
            int serial, android.hardware.radio.messaging.CdmaSmsAck smsAck) {
        Log.d(mTag, "acknowledgeLastIncomingCdmaSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeLastIncomingCdmaSmsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to acknowledgeLastIncomingCdmaSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void acknowledgeLastIncomingGsmSms(int serial, boolean success, int cause) {
        Log.d(mTag, "acknowledgeLastIncomingGsmSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeLastIncomingGsmSmsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to acknowledgeLastIncomingGsmSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deleteSmsOnRuim(int serial, int index) {
        Log.d(mTag, "deleteSmsOnRuim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.deleteSmsOnRuimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to deleteSmsOnRuim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deleteSmsOnSim(int serial, int index) {
        Log.d(mTag, "deleteSmsOnSim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.deleteSmsOnSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to deleteSmsOnSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaBroadcastConfig(int serial) {
        Log.d(mTag, "getCdmaBroadcastConfig");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.getCdmaBroadcastConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCdmaBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getGsmBroadcastConfig(int serial) {
        Log.d(mTag, "getGsmBroadcastConfig");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.getGsmBroadcastConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getGsmBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSmscAddress(int serial) {
        Log.d(mTag, "getSmscAddress");

        String smsc = mMockMessagingService.getSmscAddress();
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioMessagingResponse.getSmscAddressResponse(rsp, smsc);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSmscAddress from AIDL. Exception" + ex);
        }
    }

    @Override
    public void reportSmsMemoryStatus(int serial, boolean available) {
        Log.d(mTag, "reportSmsMemoryStatus");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.reportSmsMemoryStatusResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to reportSmsMemoryStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
        // TODO
    }

    @Override
    public void sendCdmaSms(int serial, android.hardware.radio.messaging.CdmaSmsMessage sms) {
        Log.d(mTag, "sendCdmaSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendCdmaSmsResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendCdmaSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendCdmaSmsExpectMore(
            int serial, android.hardware.radio.messaging.CdmaSmsMessage sms) {
        Log.d(mTag, "sendCdmaSmsExpectMore");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendCdmaSmsExpectMoreResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendCdmaSmsExpectMore from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendImsSms(int serial, android.hardware.radio.messaging.ImsSmsMessage message) {
        Log.d(mTag, "sendImsSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendImsSmsResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendImsSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendSms(int serial, android.hardware.radio.messaging.GsmSmsMessage message) {
        Log.d(mTag, "sendSms");

        android.hardware.radio.messaging.SendSmsResult sms = new SendSmsResult();
        sms.messageRef = 0;
        sms.ackPDU = "ack";
        sms.errorCode = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioMessagingResponse.sendSmsResponse(rsp, sms);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendSmsExpectMore(
            int serial, android.hardware.radio.messaging.GsmSmsMessage message) {
        Log.d(mTag, "sendSmsExpectMore");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendSmsExpectMoreResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendSmsExpectMore from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaBroadcastActivation(int serial, boolean activate) {
        Log.d(mTag, "setCdmaBroadcastActivation, activate = " + activate);

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.NONE);
        try {
            mRadioMessagingResponse.setCdmaBroadcastActivationResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCdmaBroadcastActivation from AIDL. Exception" + ex);
        }
        if (activate) {
            for (CallBackWithExecutor callbackWithExecutor : mBroadcastCallbacks) {
                callbackWithExecutor.mExecutor.execute(
                        () -> callbackWithExecutor.mCallback.onCdmaBroadcastActivated());
            }
        }
    }

    @Override
    public void setCdmaBroadcastConfig(
            int serial, android.hardware.radio.messaging.CdmaBroadcastSmsConfigInfo[] configInfo) {
        Log.d(mTag, "setCdmaBroadcastConfig");

        int error = RadioError.NONE;
        if (configInfo == null) {
            error = RadioError.INVALID_ARGUMENTS;
        } else {
            synchronized (mCdmaBroadcastConfigSet) {
                mCdmaBroadcastConfigSet.clear();
                for (int i = 0; i < configInfo.length; i++) {
                    Log.d(mTag, "configInfo serviceCategory" + configInfo[i].serviceCategory);
                    mCdmaBroadcastConfigSet.add(configInfo[i].serviceCategory);
                }
            }
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial, error);
        try {
            mRadioMessagingResponse.setCdmaBroadcastConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCdmaBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setGsmBroadcastActivation(int serial, boolean activate) {
        Log.d(mTag, "setGsmBroadcastActivation, activate = " + activate);

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.NONE);
        try {
            mRadioMessagingResponse.setGsmBroadcastActivationResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setGsmBroadcastActivation from AIDL. Exception" + ex);
        }
        if (activate) {
            for (CallBackWithExecutor callbackWithExecutor : mBroadcastCallbacks) {
                callbackWithExecutor.mExecutor.execute(
                        () -> callbackWithExecutor.mCallback.onGsmBroadcastActivated());
            }
        }
    }

    @Override
    public void setGsmBroadcastConfig(
            int serial, android.hardware.radio.messaging.GsmBroadcastSmsConfigInfo[] configInfo) {
        Log.d(mTag, "setGsmBroadcastConfig");

        int error = RadioError.NONE;
        if (configInfo == null) {
            error = RadioError.INVALID_ARGUMENTS;
        } else {
            synchronized (mGsmBroadcastConfigSet) {
                mGsmBroadcastConfigSet.clear();
                for (int i = 0; i < configInfo.length; i++) {
                    int startId = configInfo[i].fromServiceId;
                    int endId = configInfo[i].toServiceId;
                    boolean selected = configInfo[i].selected;
                    Log.d(
                            mTag,
                            "configInfo from: "
                                    + startId
                                    + ", to: "
                                    + endId
                                    + ", selected: "
                                    + selected);
                    if (selected) {
                        for (int j = startId; j <= endId; j++) {
                            mGsmBroadcastConfigSet.add(j);
                        }
                    }
                }
            }
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial, error);
        try {
            mRadioMessagingResponse.setGsmBroadcastConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setGsmBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSmscAddress(int serial, String smsc) {
        Log.d(mTag, "setSmscAddress");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.setSmscAddressResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSmscAddress from AIDL. Exception" + ex);
        }
    }

    @Override
    public void writeSmsToRuim(
            int serial, android.hardware.radio.messaging.CdmaSmsWriteArgs cdmaSms) {
        Log.d(mTag, "writeSmsToRuim");

        int index = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.writeSmsToRuimResponse(rsp, index);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to writeSmsToRuim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void writeSmsToSim(
            int serial, android.hardware.radio.messaging.SmsWriteArgs smsWriteArgs) {
        Log.d(mTag, "writeSmsToSim");

        int index = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.writeSmsToSimResponse(rsp, index);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to writeSmsToSim from AIDL. Exception" + ex);
        }
    }

    public void cdmaNewSms(android.hardware.radio.messaging.CdmaSmsMessage msg) {
        Log.d(mTag, "cdmaNewSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.cdmaNewSms(RadioIndicationType.UNSOLICITED, msg);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to cdmaNewSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void cdmaRuimSmsStorageFull() {
        Log.d(mTag, "cdmaRuimSmsStorageFull");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.cdmaRuimSmsStorageFull(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to cdmaRuimSmsStorageFull indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void newBroadcastSms(byte[] data) {
        Log.d(mTag, "newBroadcastSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newBroadcastSms(RadioIndicationType.UNSOLICITED, data);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to newBroadcastSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void newSms(byte[] pdu) {
        Log.d(mTag, "newSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSms(RadioIndicationType.UNSOLICITED, pdu);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to newSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void newSmsOnSim(int recordNumber) {
        Log.d(mTag, "newSmsOnSim");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSmsOnSim(
                        RadioIndicationType.UNSOLICITED, recordNumber);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to newSmsOnSim indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void newSmsStatusReport(byte[] pdu) {
        Log.d(mTag, "newSmsStatusReport");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSmsStatusReport(RadioIndicationType.UNSOLICITED, pdu);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to newSmsStatusReport indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    public void simSmsStorageFull() {
        Log.d(mTag, "simSmsStorageFull");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.simSmsStorageFull(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to simSmsStorageFull indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioMessagingIndication");
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioMessaging.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioMessaging.VERSION;
    }

    public Set<Integer> getGsmBroadcastConfigSet() {
        synchronized (mGsmBroadcastConfigSet) {
            Log.d(mTag, "getBroadcastConfigSet. " + mGsmBroadcastConfigSet);
            return mGsmBroadcastConfigSet;
        }
    }

    public Set<Integer> getCdmaBroadcastConfigSet() {
        synchronized (mCdmaBroadcastConfigSet) {
            Log.d(mTag, "getBroadcastConfigSet. " + mCdmaBroadcastConfigSet);
            return mCdmaBroadcastConfigSet;
        }
    }

    public void registerBroadcastCallback(CallBackWithExecutor callback) {
        mBroadcastCallbacks.add(callback);
    }
    public void unregisterBroadcastCallback(CallBackWithExecutor callback) {
        mBroadcastCallbacks.remove(callback);
    }
}
