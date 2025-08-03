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
import android.hardware.radio.voice.EmergencyNumber;
import android.hardware.radio.voice.IRadioVoice;
import android.hardware.radio.voice.IRadioVoiceIndication;
import android.hardware.radio.voice.IRadioVoiceResponse;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.mockmodem.MockVoiceService.MockCallInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IRadioVoiceImpl extends IRadioVoice.Stub {
    private static final String TAG = "MRVOICE";

    public static final int LATCH_EMERGENCY_DIAL = 0;
    public static final int LATCH_GET_LAST_CALL_FAIL_CAUSE = 1;
    private static final int LATCH_MAX = 2;

    private final CountDownLatch[] mLatches = new CountDownLatch[LATCH_MAX];

    private final MockModemService mService;
    private IRadioVoiceResponse mRadioVoiceResponse;
    private IRadioVoiceIndication mRadioVoiceIndication;
    private MockModemConfigInterface mMockModemConfigInterface;
    private final Object mCacheUpdateMutex;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private int mSubId;
    private String mTag;

    // ***** Events
    static final int EVENT_CALL_STATE_CHANGED = 1;
    static final int EVENT_CURRENT_CALLS_RESPONSE = 2;
    static final int EVENT_CALL_INCOMING = 3;
    static final int EVENT_RINGBACK_TONE = 4;

    // ***** Cache of modem attributes/status
    private ArrayList<MockCallInfo> mCallList;
    private ArrayList<Integer> mGetCurrentCallReqList;

    public IRadioVoiceImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;
        mCacheUpdateMutex = new Object();
        mHandlerThread = new HandlerThread(mTag);
        mHandlerThread.start();
        mHandler = new IRadioVoiceHandler(mHandlerThread.getLooper());
        mGetCurrentCallReqList = new ArrayList<Integer>();

        // Register events
        mMockModemConfigInterface.registerForCallStateChanged(
                mSubId, mHandler, EVENT_CALL_STATE_CHANGED, null);
        mMockModemConfigInterface.registerForCurrentCallsResponse(
                mSubId, mHandler, EVENT_CURRENT_CALLS_RESPONSE, null);
        mMockModemConfigInterface.registerForCallIncoming(
                mSubId, mHandler, EVENT_CALL_INCOMING, null);
        mMockModemConfigInterface.registerRingbackTone(mSubId, mHandler, EVENT_RINGBACK_TONE, null);

        for (int i = 0; i < LATCH_MAX; i++) {
            mLatches[i] = new CountDownLatch(1);
        }
    }

    /** Handler class to handle callbacks */
    private final class IRadioVoiceHandler extends Handler {
        IRadioVoiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_CALL_STATE_CHANGED:
                        Log.d(mTag, "Received EVENT_CALL_STATE_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mCallList = (ArrayList<MockCallInfo>) ar.result;
                            Log.i(mTag, "num of calls: " + mCallList.size());
                            callStateChanged();
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_CURRENT_CALLS_RESPONSE:
                        Log.d(mTag, "Received EVENT_CURRENT_CALLS_RESPONSE");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            final ArrayList<MockCallInfo> callList =
                                    (ArrayList<MockCallInfo>) ar.result;
                            synchronized (callList) {
                                mCallList = callList;
                                Log.i(
                                        mTag,
                                        "num of calls: "
                                                + mCallList.size()
                                                + ", num of getCurrentCalls requests: "
                                                + mGetCurrentCallReqList.size());
                                for (int i = 0; i < mGetCurrentCallReqList.size(); i++) {
                                    getCurrentCallsRespnose(
                                            mGetCurrentCallReqList.get(i).intValue());
                                }
                                mGetCurrentCallReqList.clear();
                            }
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_CALL_INCOMING:
                        Log.d(mTag, "Received EVENT_CALL_INCOMING");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            MockCallInfo callInfo = (MockCallInfo) ar.result;
                            Log.i(mTag, "Incoming call id = " + callInfo.getCallId());
                            boolean hasCdmaSignalInfoRecord =
                                    (callInfo.getCdmaSignalInfoRecord() != null) ? true : false;
                            callRing(!hasCdmaSignalInfoRecord, callInfo.getCdmaSignalInfoRecord());
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_RINGBACK_TONE:
                        Log.d(mTag, "Received EVENT_RINGBACK_TONE");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            boolean ringbackToneState = (boolean) ar.result;
                            Log.i(mTag, "ringbackToneState = " + ringbackToneState);
                            indicateRingbackTone(ringbackToneState);
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                }
            }
        }
    }

    private int convertCallState(int callstate) {
        int state = -1;

        switch (callstate) {
            case MockCallInfo.CALL_STATE_ACTIVE:
                state = android.hardware.radio.voice.Call.STATE_ACTIVE;
                break;

            case MockCallInfo.CALL_STATE_HOLDING:
                state = android.hardware.radio.voice.Call.STATE_HOLDING;
                break;

            case MockCallInfo.CALL_STATE_DIALING:
                state = android.hardware.radio.voice.Call.STATE_DIALING;
                break;

            case MockCallInfo.CALL_STATE_ALERTING:
                state = android.hardware.radio.voice.Call.STATE_ALERTING;
                break;

            case MockCallInfo.CALL_STATE_INCOMING:
                state = android.hardware.radio.voice.Call.STATE_INCOMING;
                break;

            case MockCallInfo.CALL_STATE_WAITING:
                state = android.hardware.radio.voice.Call.STATE_WAITING;
                break;

            default:
                Log.e(mTag, "Unknown call state = " + callstate);
                break;
        }

        return state;
    }

    private android.hardware.radio.voice.Call[] fillUpCurrentCallsRespnose() {
        int numOfCalls = 0;
        android.hardware.radio.voice.Call[] calls = null;

        if (mCallList != null) {
            numOfCalls = mCallList.size();
        }

        if (mMockModemConfigInterface != null
                && mMockModemConfigInterface.getNumberOfCalls(mSubId, mTag) == numOfCalls) {
            calls = new android.hardware.radio.voice.Call[numOfCalls];

            if (calls != null) {
                for (int i = 0; i < numOfCalls; i++) {
                    calls[i] = new android.hardware.radio.voice.Call();
                    if (calls[i] != null) {
                        calls[i].state = convertCallState(mCallList.get(i).getCallState());
                        calls[i].index = mCallList.get(i).getCallId();
                        calls[i].toa = mCallList.get(i).getCallToa();
                        calls[i].isMpty = mCallList.get(i).isMpty();
                        calls[i].isMT = mCallList.get(i).isMT();
                        calls[i].als = mCallList.get(i).getCallAls();
                        calls[i].isVoice = mCallList.get(i).isVoice();
                        calls[i].isVoicePrivacy = mCallList.get(i).isVoicePrivacy();
                        calls[i].number = mCallList.get(i).getNumber();
                        calls[i].numberPresentation = mCallList.get(i).getNumberPresentation();
                        calls[i].uusInfo = mCallList.get(i).getUusInfo();
                        calls[i].audioQuality = mCallList.get(i).getAudioQuality();
                        calls[i].forwardedNumber = mCallList.get(i).getForwardedNumber();
                    } else {
                        Log.e(
                                mTag,
                                "Failed to allocate memory for call " + i + "/" + numOfCalls + ".");
                        break;
                    }
                }
            } else {
                Log.e(mTag, "Failed to allocate memory for calls.");
            }
        } else {
            Log.e(mTag, "mMockModemConfigInterface != null or num of calls isn't matched.");
        }

        return calls;
    }

    private void getCurrentCallsRespnose(int serial) {
        int responseError = RadioError.NONE;
        android.hardware.radio.voice.Call[] calls = fillUpCurrentCallsRespnose();

        if (calls == null) {
            responseError = RadioError.INTERNAL_ERR;
            Log.e(mTag, "calls == null!!");
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.getCurrentCallsResponse(rsp, calls);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCurrentCalls from AIDL. Exception" + ex);
        }
    }

    // Implementation of IRadioVoice functions
    @Override
    public void setResponseFunctions(
            IRadioVoiceResponse radioVoiceResponse, IRadioVoiceIndication radioVoiceIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioVoiceResponse = radioVoiceResponse;
        mRadioVoiceIndication = radioVoiceIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void acceptCall(int serial) {
        Log.d(mTag, "acceptCall");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret = mMockModemConfigInterface.acceptVoiceCall(mSubId, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: accept request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.acceptCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to acceptCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void cancelPendingUssd(int serial) {
        Log.d(mTag, "cancelPendingUssd");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.cancelPendingUssdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to cancelPendingUssd from AIDL. Exception" + ex);
        }
    }

    @Override
    public void conference(int serial) {
        Log.d(mTag, "conference");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.conferenceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to conference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void dial(int serial, android.hardware.radio.voice.Dial dialInfo) {
        Log.d(mTag, "dial");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret =
                    mMockModemConfigInterface.dialVoiceCall(
                            mSubId, dialInfo.address, dialInfo.clir, dialInfo.uusInfo, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: dial request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.dialResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to dial from AIDL. Exception" + ex);
        }
    }

    @Override
    public void emergencyDial(
            int serial,
            android.hardware.radio.voice.Dial dialInfo,
            int categories,
            String[] urns,
            int routing,
            boolean hasKnownUserIntentEmergency,
            boolean isTesting) {
        Log.d(mTag, "emergencyDial");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret =
                    mMockModemConfigInterface.dialEccVoiceCall(
                            mSubId, dialInfo.address, categories, urns, routing, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: dial request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.emergencyDialResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to emergencyDial from AIDL. Exception" + ex);
        }

        countDownLatch(LATCH_EMERGENCY_DIAL);
        resetLatch(LATCH_GET_LAST_CALL_FAIL_CAUSE);
    }

    @Override
    public void exitEmergencyCallbackMode(int serial) {
        Log.d(mTag, "exitEmergencyCallbackMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.exitEmergencyCallbackModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to exitEmergencyCallbackMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void explicitCallTransfer(int serial) {
        Log.d(mTag, "explicitCallTransfer");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.explicitCallTransferResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to explicitCallTransfer from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCallForwardStatus(
            int serial, android.hardware.radio.voice.CallForwardInfo callInfo) {
        Log.d(mTag, "getCallForwardStatus");

        android.hardware.radio.voice.CallForwardInfo[] callForwardInfos =
                new android.hardware.radio.voice.CallForwardInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getCallForwardStatusResponse(rsp, callForwardInfos);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCallForwardStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCallWaiting(int serial, int serviceClass) {
        Log.d(mTag, "getCallWaiting");

        boolean enable = false;
        int rspServiceClass = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getCallWaitingResponse(rsp, enable, rspServiceClass);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCallWaiting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getClip(int serial) {
        Log.d(mTag, "getClip");

        int status = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getClipResponse(rsp, status);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getClip from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getClir(int serial) {
        Log.d(mTag, "getClir");

        int n = 0;
        int m = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getClirResponse(rsp, n, m);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getClir from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCurrentCalls(int serial) {
        Log.d(mTag, "getCurrentCalls");

        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            Integer request = Integer.valueOf(serial);

            synchronized (mCacheUpdateMutex) {
                if (mGetCurrentCallReqList != null && request != null) {
                    mGetCurrentCallReqList.add(request);
                    Log.d(mTag, "Add GetCurrentCallReq");
                } else {
                    Log.e(mTag, "Failed: mGetCurrentCallReqList == null or request == null");
                    responseError = RadioError.INTERNAL_ERR;
                }
            }

            if (responseError == RadioError.NONE) {
                boolean ret = mMockModemConfigInterface.getCurrentCalls(mSubId, mTag);

                if (!ret) {
                    Log.e(mTag, "Failed: getCurrentCalls request failed");
                    responseError = RadioError.INTERNAL_ERR;
                }
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        if (responseError != RadioError.NONE) {
            android.hardware.radio.voice.Call[] calls = new android.hardware.radio.voice.Call[0];
            RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
            try {
                mRadioVoiceResponse.getCurrentCallsResponse(rsp, calls);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to getCurrentCalls from AIDL. Exception" + ex);
            }
        }
    }

    @Override
    public void getLastCallFailCause(int serial) {
        Log.d(mTag, "getLastCallFailCause");
        LastCallFailCauseInfo failCauseInfo = null;
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            failCauseInfo = mMockModemConfigInterface.getLastCallFailCause(mSubId, mTag);

            if (failCauseInfo == null) {
                Log.e(mTag, "Failed: get last call fail cause request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.getLastCallFailCauseResponse(rsp, failCauseInfo);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getLastCallFailCause from AIDL. Exception" + ex);
        }
        countDownLatch(LATCH_GET_LAST_CALL_FAIL_CAUSE);
    }

    @Override
    public void getMute(int serial) {
        Log.d(mTag, "getMute");
        int responseError = RadioError.NONE;
        boolean muteMode = false;

        if (mMockModemConfigInterface != null) {
            muteMode = mMockModemConfigInterface.getVoiceMuteMode(mSubId, mTag);
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.getMuteResponse(rsp, muteMode);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getMute from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getPreferredVoicePrivacy(int serial) {
        Log.d(mTag, "getPreferredVoicePrivacy");

        boolean enable = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getPreferredVoicePrivacyResponse(rsp, enable);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getPreferredVoicePrivacy from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getTtyMode(int serial) {
        Log.d(mTag, "getTtyMode");

        int mode = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getTtyModeResponse(rsp, mode);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getTtyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void handleStkCallSetupRequestFromSim(int serial, boolean accept) {
        Log.d(mTag, "handleStkCallSetupRequestFromSim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.handleStkCallSetupRequestFromSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to handleStkCallSetupRequestFromSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangup(int serial, int gsmIndex) {
        Log.d(mTag, "hangup");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret = mMockModemConfigInterface.hangupVoiceCall(mSubId, gsmIndex, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: hangup request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.hangupConnectionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to hangup from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangupForegroundResumeBackground(int serial) {
        Log.d(mTag, "hangupForegroundResumeBackground");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.hangupForegroundResumeBackgroundResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to hangupForegroundResumeBackground from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangupWaitingOrBackground(int serial) {
        Log.d(mTag, "hangupWaitingOrBackground");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.hangupWaitingOrBackgroundResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to hangupWaitingOrBackground from AIDL. Exception" + ex);
        }
    }

    @Override
    public void isVoNrEnabled(int serial) {
        Log.d(mTag, "isVoNrEnabled");

        boolean enable = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.isVoNrEnabledResponse(rsp, enable);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to isVoNrEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void rejectCall(int serial) {
        Log.d(mTag, "rejectCall");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret = mMockModemConfigInterface.rejectVoiceCall(mSubId, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: reject request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.rejectCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to rejectCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
        // TODO
    }

    @Override
    public void sendBurstDtmf(int serial, String dtmf, int on, int off) {
        Log.d(mTag, "sendBurstDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendBurstDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendBurstDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendCdmaFeatureCode(int serial, String featureCode) {
        Log.d(mTag, "sendCdmaFeatureCode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendCdmaFeatureCodeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendCdmaFeatureCode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendDtmf(int serial, String s) {
        Log.d(mTag, "sendDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendUssd(int serial, String ussd) {
        Log.d(mTag, "sendUssd");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendUssdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendUssd from AIDL. Exception" + ex);
        }
    }

    @Override
    public void separateConnection(int serial, int gsmIndex) {
        Log.d(mTag, "separateConnection");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.separateConnectionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to separateConnection from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCallForward(int serial, android.hardware.radio.voice.CallForwardInfo callInfo) {
        Log.d(mTag, "setCallForward");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setCallForwardResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCallForward from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCallWaiting(int serial, boolean enable, int serviceClass) {
        Log.d(mTag, "setCallWaiting");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setCallWaitingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCallWaiting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setClir(int serial, int status) {
        Log.d(mTag, "setClir");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setClirResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setClir from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setMute(int serial, boolean enable) {
        Log.d(mTag, "setMute");
        int responseError = RadioError.NONE;

        if (mMockModemConfigInterface != null) {
            boolean ret = mMockModemConfigInterface.setVoiceMuteMode(mSubId, enable, mTag);

            if (!ret) {
                Log.e(mTag, "Failed: setMute request failed");
                responseError = RadioError.INTERNAL_ERR;
            }
        } else {
            Log.e(mTag, "Failed: mMockModemConfigInterface == null");
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioVoiceResponse.setMuteResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setMute from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setPreferredVoicePrivacy(int serial, boolean enable) {
        Log.d(mTag, "setPreferredVoicePrivacy");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setPreferredVoicePrivacyResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setPreferredVoicePrivacy from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setTtyMode(int serial, int mode) {
        Log.d(mTag, "setTtyMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setTtyModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setTtyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setVoNrEnabled(int serial, boolean enable) {
        Log.d(mTag, "setVoNrEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setVoNrEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setVoNrEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startDtmf(int serial, String s) {
        Log.d(mTag, "startDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.startDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopDtmf(int serial) {
        Log.d(mTag, "stopDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.stopDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to stopDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void switchWaitingOrHoldingAndActive(int serial) {
        Log.d(mTag, "switchWaitingOrHoldingAndActive");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.switchWaitingOrHoldingAndActiveResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to switchWaitingOrHoldingAndActive from AIDL. Exception" + ex);
        }
    }

    public void callRing(boolean isGsm, android.hardware.radio.voice.CdmaSignalInfoRecord record) {
        Log.d(mTag, "callRing");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.callRing(RadioIndicationType.UNSOLICITED, isGsm, record);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to callRing indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void callStateChanged() {
        Log.d(mTag, "callStateChanged");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.callStateChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to callStateChanged indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void cdmaCallWaiting(android.hardware.radio.voice.CdmaCallWaiting callWaitingRecord) {
        Log.d(mTag, "cdmaCallWaiting");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaCallWaiting(
                        RadioIndicationType.UNSOLICITED, callWaitingRecord);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to cdmaCallWaiting indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void cdmaInfoRec(android.hardware.radio.voice.CdmaInformationRecord[] records) {
        Log.d(mTag, "cdmaInfoRec");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaInfoRec(RadioIndicationType.UNSOLICITED, records);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to cdmaInfoRec indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void cdmaOtaProvisionStatus(int status) {
        Log.d(mTag, "cdmaOtaProvisionStatus");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaOtaProvisionStatus(
                        RadioIndicationType.UNSOLICITED, status);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to cdmaOtaProvisionStatus indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void currentEmergencyNumberList(
            android.hardware.radio.voice.EmergencyNumber[] emergencyNumberList) {
        Log.d(mTag, "currentEmergencyNumberList");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.currentEmergencyNumberList(
                        RadioIndicationType.UNSOLICITED, emergencyNumberList);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to currentEmergencyNumberList indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void enterEmergencyCallbackMode() {
        Log.d(mTag, "enterEmergencyCallbackMode");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.enterEmergencyCallbackMode(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to enterEmergencyCallbackMode indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void exitEmergencyCallbackMode() {
        Log.d(mTag, "exitEmergencyCallbackMode");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.exitEmergencyCallbackMode(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to exitEmergencyCallbackMode indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void indicateRingbackTone(boolean start) {
        Log.d(mTag, "indicateRingbackTone");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.indicateRingbackTone(RadioIndicationType.UNSOLICITED, start);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to indicateRingbackTone indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void onSupplementaryServiceIndication(
            android.hardware.radio.voice.StkCcUnsolSsResult ss) {
        Log.d(mTag, "onSupplementaryServiceIndication");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.onSupplementaryServiceIndication(
                        RadioIndicationType.UNSOLICITED, ss);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to onSupplementaryServiceIndication indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void onUssd(int modeType, String msg) {
        Log.d(mTag, "onUssd");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.onUssd(RadioIndicationType.UNSOLICITED, modeType, msg);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to onUssd indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void resendIncallMute() {
        Log.d(mTag, "resendIncallMute");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.resendIncallMute(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to resendIncallMute indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void srvccStateNotify(int state) {
        Log.d(mTag, "srvccStateNotify");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.srvccStateNotify(RadioIndicationType.UNSOLICITED, state);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to srvccStateNotify indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void stkCallControlAlphaNotify(String alpha) {
        Log.d(mTag, "stkCallControlAlphaNotify");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.stkCallControlAlphaNotify(
                        RadioIndicationType.UNSOLICITED, alpha);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to stkCallControlAlphaNotify indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void stkCallSetup(long timeout) {
        Log.d(mTag, "stkCallSetup");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.stkCallSetup(RadioIndicationType.UNSOLICITED, timeout);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to stkCallSetup indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioVoiceIndication");
        }
    }

    public void notifyEmergencyNumberList(String[] numbers) {
        if (numbers == null || numbers.length == 0) return;

        EmergencyNumber[] emergencyNumberList = new EmergencyNumber[numbers.length];

        for (int i = 0; i < numbers.length; i++) {
            EmergencyNumber number = new EmergencyNumber();
            number.number = numbers[i];
            number.mcc = "310";
            number.mnc = "";
            String urn = "sip:" + numbers[i] + "@test.3gpp.com";
            number.urns = new String[] { urn };
            number.sources = EmergencyNumber.SOURCE_MODEM_CONFIG;
            emergencyNumberList[i] = number;
        }

        currentEmergencyNumberList(emergencyNumberList);
    }


    private void countDownLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex].countDown();
        }
    }

    private void resetLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
    }

    /**
     * Waits for the event of voice service.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     * @return {@code true} if the event happens.
     */
    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLatches) {
                latch = mLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Latch " + latchIndex + " took "
                    + (System.currentTimeMillis() - startTime) + " ms to count down.");
        } catch (InterruptedException e) {
            Log.e(TAG, "Waiting latch " + latchIndex + " interrupted, e=" + e);
        }
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /**
     * Resets the CountDownLatches.
     */
    public void resetAllLatchCountdown() {
        synchronized (mLatches) {
            for (int i = 0; i < LATCH_MAX; i++) {
                mLatches[i] = new CountDownLatch(1);
            }
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioVoice.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioVoice.VERSION;
    }
}
