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

import static android.telephony.mockmodem.MockSimService.COMMAND_GET_RESPONSE;
import static android.telephony.mockmodem.MockSimService.COMMAND_READ_BINARY;
import static android.telephony.mockmodem.MockSimService.EF_GID1;
import static android.telephony.mockmodem.MockSimService.EF_ICCID;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.sim.CardStatus;
import android.hardware.radio.sim.Carrier;
import android.hardware.radio.sim.CarrierRestrictions;
import android.hardware.radio.sim.IRadioSim;
import android.hardware.radio.sim.IRadioSimIndication;
import android.hardware.radio.sim.IRadioSimResponse;
import android.hardware.radio.sim.SimRefreshResult;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.mockmodem.MockModemConfigBase.SimInfoChangedResult;
import android.telephony.mockmodem.MockSimService.SimAppData;
import android.util.Log;

import java.util.ArrayList;
import com.android.internal.telephony.flags.Flags;

public class IRadioSimImpl extends IRadioSim.Stub {
    private static final String TAG = "MRSIM";
    private final MockModemService mService;
    private IRadioSimResponse mRadioSimResponse;
    private IRadioSimIndication mRadioSimIndication;
    private MockModemConfigInterface mMockModemConfigInterface;
    private Object mCacheUpdateMutex;
    private final Handler mHandler;
    private int mSubId;
    private String mTag;

    // ***** Events
    static final int EVENT_SIM_CARD_STATUS_CHANGED = 1;
    static final int EVENT_SIM_APP_DATA_CHANGED = 2;
    static final int EVENT_SIM_INFO_CHANGED = 3;

    // ***** Cache of modem attributes/status
    private CardStatus mCardStatus;
    private ArrayList<SimAppData> mSimAppList;

    private Carrier[] mCarrierList;
    private int mCarrierRestrictionStatus = CarrierRestrictions.CarrierRestrictionStatus.UNKNOWN;

    private int mMultiSimPolicy =
            android.hardware.radio.sim.SimLockMultiSimPolicy.NO_MULTISIM_POLICY;
    private CarrierRestrictions mCarrierRestrictionRules;

    public IRadioSimImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;
        mCardStatus = new CardStatus();
        mCacheUpdateMutex = new Object();
        mHandler = new IRadioSimHandler();

        // Register events
        mMockModemConfigInterface.registerForCardStatusChanged(
                mSubId, mHandler, EVENT_SIM_CARD_STATUS_CHANGED, null);

        // Register events
        mMockModemConfigInterface.registerForSimAppDataChanged(
                mSubId, mHandler, EVENT_SIM_APP_DATA_CHANGED, null);

        // Register events
        mMockModemConfigInterface.registerForSimInfoChanged(
                mSubId, mHandler, EVENT_SIM_INFO_CHANGED, null);
    }

    /** Handler class to handle callbacks */
    private final class IRadioSimHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_SIM_CARD_STATUS_CHANGED:
                        Log.d(mTag, "Received EVENT_SIM_CARD_STATUS_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mCardStatus = (CardStatus) ar.result;
                            Log.i(mTag, "Sim card status: " + mCardStatus);
                            simStatusChanged();
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;

                    case EVENT_SIM_APP_DATA_CHANGED:
                        Log.d(mTag, "Received EVENT_SIM_APP_DATA_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mSimAppList = (ArrayList<SimAppData>) ar.result;
                            if (mSimAppList != null) {
                                Log.i(mTag, "number of SIM app data: " + mSimAppList.size());
                            } else {
                                Log.e(mTag, "mSimAppList = null");
                            }
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;

                    case EVENT_SIM_INFO_CHANGED:
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            SimInfoChangedResult simInfoChangeResult =
                                    (SimInfoChangedResult) ar.result;
                            Log.d(mTag, "Received EVENT_SIM_INFO_CHANGED: " + simInfoChangeResult);
                            SimRefreshResult simRefreshResult = new SimRefreshResult();
                            switch (simInfoChangeResult.mSimInfoType) {
                                case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                                case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                                    if (simRefreshResult != null) {
                                        simRefreshResult.type =
                                                SimRefreshResult.TYPE_SIM_FILE_UPDATE;
                                        simRefreshResult.efId = simInfoChangeResult.mEfId;
                                        simRefreshResult.aid = simInfoChangeResult.mAid;
                                        simRefresh(simRefreshResult);
                                    }
                                    break;
                            }
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                }
            }
        }
    }

    // Implementation of IRadioSim functions
    @Override
    public void setResponseFunctions(
            IRadioSimResponse radioSimResponse, IRadioSimIndication radioSimIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioSimResponse = radioSimResponse;
        mRadioSimIndication = radioSimIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
        // TODO
        // acknowledgeRequest(in int serial);
    }

    @Override
    public void areUiccApplicationsEnabled(int serial) {
        Log.d(mTag, "areUiccApplicationsEnabled");

        boolean enabled = true;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.areUiccApplicationsEnabledResponse(rsp, enabled);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to areUiccApplicationsEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void changeIccPin2ForApp(int serial, String oldPin2, String newPin2, String aid) {
        Log.d(mTag, "changeIccPin2ForApp");
        // TODO: cache value

        int remainingRetries = 3;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.changeIccPin2ForAppResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to changeIccPin2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void changeIccPinForApp(int serial, String oldPin, String newPin, String aid) {
        Log.d(mTag, "changeIccPinForApp");
        // TODO: cache value

        int remainingRetries = 3;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.changeIccPinForAppResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to changeIccPinForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void enableUiccApplications(int serial, boolean enable) {
        Log.d(mTag, "enableUiccApplications");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.enableUiccApplicationsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to enableUiccApplications from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAllowedCarriers(int serial) {
        Log.d(mTag, "getAllowedCarriers");
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        android.hardware.radio.sim.CarrierRestrictions carriers =
                new android.hardware.radio.sim.CarrierRestrictions();
        if (mCarrierList == null || mCarrierList.length < 1) {
            carriers.allowedCarriers = new Carrier[0];
        } else {
            carriers.allowedCarriers = mCarrierList;
        }
        carriers.excludedCarriers = new Carrier[0];
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            carriers.status = mCarrierRestrictionStatus;
        }
        if (Flags.carrierRestrictionRulesEnhancement() && mCarrierRestrictionRules != null) {
            Log.e(mTag, "Updating carrierInfo information in CarrierRestrictions");
            carriers.allowedCarrierInfoList = mCarrierRestrictionRules.allowedCarrierInfoList;
            carriers.excludedCarrierInfoList = mCarrierRestrictionRules.excludedCarrierInfoList;
        }
        try {
            mRadioSimResponse.getAllowedCarriersResponse(rsp, carriers, mMultiSimPolicy);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getAllowedCarriers from AIDL. Exception" + ex);
        } finally {
            // resetting the mCarrierRestrictionRules
            mCarrierRestrictionRules = null;
            mMultiSimPolicy = android.hardware.radio.sim.SimLockMultiSimPolicy.NO_MULTISIM_POLICY;
        }
    }

    @Override
    public void getCdmaSubscription(int serial) {
        Log.d(mTag, "getCdmaSubscription");

        String mdn = "";
        String hSid = "";
        String hNid = "";
        String min = "";
        String prl = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getCdmaSubscriptionResponse(rsp, mdn, hSid, hNid, min, prl);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCdmaSubscription from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaSubscriptionSource(int serial) {
        Log.d(mTag, "getCdmaSubscriptionSource");

        int source = 0; // CdmaSubscriptionSource.RUIM_SIM

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getCdmaSubscriptionSourceResponse(rsp, source);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCdmaSubscriptionSource from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getFacilityLockForApp(
            int serial, String facility, String password, int serviceClass, String appId) {
        Log.d(mTag, "getFacilityLockForApp");
        int numOfSimApp = mSimAppList.size();
        int responseError = RadioError.NONE;
        int simAppIdx;
        boolean isHandled = false;
        boolean isFacilitySupport = true;
        int responseData = -1;

        synchronized (mCacheUpdateMutex) {
            // TODO: check service class
            for (simAppIdx = 0;
                    simAppIdx < numOfSimApp && isFacilitySupport && !isHandled;
                    simAppIdx++) {
                switch (facility) {
                    case "FD": // FDN status query
                        if (appId.equals(mSimAppList.get(simAppIdx).getAid())) {
                            responseData = mSimAppList.get(simAppIdx).getFdnStatus();
                            isHandled = true;
                        }
                        break;
                    case "SC": // PIN1 status query
                        if (appId.equals(mSimAppList.get(simAppIdx).getAid())) {
                            responseData = mSimAppList.get(simAppIdx).getPin1State();
                            isHandled = true;
                        }
                        break;
                    default:
                        isFacilitySupport = false;
                        break;
                }
            }
        }

        if (!isHandled) {
            Log.e(mTag, "Not support sim application aid = " + appId);
            responseError = RadioError.NO_SUCH_ELEMENT;
        } else if (!isFacilitySupport) {
            Log.e(mTag, "Not support facility = " + facility);
            responseError = RadioError.REQUEST_NOT_SUPPORTED;
        } else if (responseData == -1) {
            responseError = RadioError.INTERNAL_ERR;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioSimResponse.getFacilityLockForAppResponse(rsp, responseData);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getFacilityLockForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getIccCardStatus(int serial) {
        Log.d(mTag, "getIccCardStatus");
        CardStatus cardStatus;

        synchronized (mCacheUpdateMutex) {
            cardStatus = mCardStatus;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioSimResponse.getIccCardStatusResponse(rsp, cardStatus);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getIccCardStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getImsiForApp(int serial, String aid) {
        Log.d(mTag, "getImsiForApp");
        String imsi = "";
        int numOfSimApp = mSimAppList.size();
        int responseError = RadioError.NONE;
        int simAppIdx;
        boolean isHandled;

        synchronized (mCacheUpdateMutex) {
            for (simAppIdx = 0, isHandled = false;
                    simAppIdx < numOfSimApp && !isHandled;
                    simAppIdx++) {
                if (aid.equals(mSimAppList.get(simAppIdx).getAid())) {
                    imsi = mSimAppList.get(simAppIdx).getImsi();
                    isHandled = true;
                }
            }
        }

        if (!isHandled) {
            Log.e(mTag, "Not support sim application aid = " + aid);
            responseError = RadioError.NO_SUCH_ELEMENT;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioSimResponse.getImsiForAppResponse(rsp, imsi);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getImsiForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimPhonebookCapacity(int serial) {
        Log.d(mTag, "getSimPhonebookCapacity");

        android.hardware.radio.sim.PhonebookCapacity capacity =
                new android.hardware.radio.sim.PhonebookCapacity();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getSimPhonebookCapacityResponse(rsp, capacity);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSimPhonebookCapacity from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimPhonebookRecords(int serial) {
        Log.d(mTag, "getSimPhonebookRecords");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getSimPhonebookRecordsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSimPhonebookRecords from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccCloseLogicalChannel(int serial, int channelId) {
        Log.d(mTag, "iccCloseLogicalChannel");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccCloseLogicalChannelResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to iccCloseLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccCloseLogicalChannelWithSessionInfo(int serial,
            android.hardware.radio.sim.SessionInfo sessionInfo) {
        Log.d(mTag, "iccCloseLogicalChannelWithSessionInfo");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccCloseLogicalChannelWithSessionInfoResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag,
                    "Failed to iccCloseLogicalChannelWithSessionInfo from AIDL. Exception" + ex);
        }
    }

    private String encodeBcdString(String str) {
        StringBuffer bcdString = new StringBuffer();

        if (str.length() % 2 != 0) {
            Log.d(mTag, "Invalid string(" + str + ") for Bcd format");
            return "";
        }

        for (int i = 0; i < str.length(); i += 2) {
            bcdString.append(str.substring(i + 1, i + 2));
            bcdString.append(str.substring(i, i + 1));
        }

        return bcdString.toString();
    }

    private int getIccIoResult(
            android.hardware.radio.sim.IccIoResult iccIoResult,
            int command,
            int fileId,
            String path,
            int p1,
            int p2,
            int p3,
            String aid) {
        int numOfSimApp = mSimAppList.size();
        int simAppIdx;
        boolean foundAid;
        int responseError = RadioError.GENERIC_FAILURE;

        if (iccIoResult == null) {
            return responseError;
        }

        synchronized (mCacheUpdateMutex) {
            for (simAppIdx = 0, foundAid = false; simAppIdx < numOfSimApp; simAppIdx++) {
                if (aid.equals(mSimAppList.get(simAppIdx).getAid())) {
                    foundAid = true;
                    break;
                }
            }

            if (!foundAid) {
                Log.e(mTag, "Not support sim application aid = " + aid);
                iccIoResult.sw1 = 0x6A;
                iccIoResult.sw2 = 0x82;
            } else {
                switch (fileId) {
                    case EF_ICCID:
                        if (command == COMMAND_READ_BINARY) {
                            String bcdIccid =
                                    encodeBcdString(mSimAppList.get(simAppIdx).getIccid());
                            iccIoResult.simResponse = bcdIccid;
                            Log.d(mTag, "Get IccIo result: ICCID = " + iccIoResult.simResponse);
                            iccIoResult.sw1 = 0x90;
                            responseError = RadioError.NONE;
                        } else if (command == COMMAND_GET_RESPONSE) {
                            iccIoResult.simResponse = mSimAppList.get(simAppIdx).getIccidInfo();
                            Log.d(mTag, "Get IccIo result: ICCID = " + iccIoResult.simResponse);
                            iccIoResult.sw1 = 0x90;
                            responseError = RadioError.NONE;
                        } else {
                            Log.d(
                                    mTag,
                                    "Command("
                                            + command
                                            + ") not support for file id = 0x"
                                            + Integer.toHexString(fileId));
                            iccIoResult.sw1 = 0x6A;
                            iccIoResult.sw2 = 0x82;
                        }
                        break;
                    case EF_GID1:
                        if (command == COMMAND_READ_BINARY) {
                            String gid1 = mSimAppList.get(simAppIdx).getGid1();
                            if (gid1 != null) {
                                iccIoResult.simResponse = gid1;
                                Log.d(
                                        mTag,
                                        "COMMAND_READ_BINARY result: GID1 = "
                                                + iccIoResult.simResponse);
                                iccIoResult.sw1 = 0x90;
                                iccIoResult.sw2 = 0x00;
                                responseError = RadioError.NONE;
                            } else {
                                Log.d(mTag, "No COMMAND_READ_BINARY result for GID1");
                                iccIoResult.sw1 = 0x6A;
                                iccIoResult.sw2 = 0x82;
                            }
                        } else if (command == COMMAND_GET_RESPONSE) {
                            String gid1Info = mSimAppList.get(simAppIdx).getGid1Info();
                            if (gid1Info != null) {
                                iccIoResult.simResponse = gid1Info;
                                Log.d(
                                        mTag,
                                        "COMMAND_GET_RESPONSE result: GID1 = "
                                                + iccIoResult.simResponse);
                                iccIoResult.sw1 = 0x90;
                                iccIoResult.sw2 = 0x00;
                                responseError = RadioError.NONE;
                            } else {
                                Log.d(mTag, "No COMMAND_GET_RESPONSE result for GID1");
                                iccIoResult.sw1 = 0x6A;
                                iccIoResult.sw2 = 0x82;
                            }
                        }
                        break;
                    default:
                        Log.d(mTag, "Not find EF file id = 0x" + Integer.toHexString(fileId));
                        iccIoResult.sw1 = 0x6A;
                        iccIoResult.sw2 = 0x82;
                        break;
                }
            }
        }

        return responseError;
    }

    @Override
    public void iccIoForApp(int serial, android.hardware.radio.sim.IccIo iccIo) {
        Log.d(mTag, "iccIoForApp");
        int responseError = RadioError.NONE;
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        switch (iccIo.command) {
            case COMMAND_READ_BINARY:
            case COMMAND_GET_RESPONSE:
                responseError =
                        getIccIoResult(
                                iccIoResult,
                                iccIo.command,
                                iccIo.fileId,
                                iccIo.path,
                                iccIo.p1,
                                iccIo.p2,
                                iccIo.p3,
                                iccIo.aid);
                break;
            default:
                responseError = RadioError.REQUEST_NOT_SUPPORTED;
                break;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial, responseError);
        try {
            mRadioSimResponse.iccIoForAppResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to iccIoForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccOpenLogicalChannel(int serial, String aid, int p2) {
        Log.d(mTag, "iccOpenLogicalChannel");
        // TODO: cache value
        int channelId = 0;
        byte[] selectResponse = new byte[0];

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccOpenLogicalChannelResponse(rsp, channelId, selectResponse);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to iccOpenLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccTransmitApduBasicChannel(
            int serial, android.hardware.radio.sim.SimApdu message) {
        Log.d(mTag, "iccTransmitApduBasicChannel");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccTransmitApduBasicChannelResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to iccTransmitApduBasicChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccTransmitApduLogicalChannel(
            int serial, android.hardware.radio.sim.SimApdu message) {
        Log.d(mTag, "iccTransmitApduLogicalChannel");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccTransmitApduBasicChannelResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to iccTransmitApduLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void reportStkServiceIsRunning(int serial) {
        Log.d(mTag, "reportStkServiceIsRunning");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.reportStkServiceIsRunningResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to reportStkServiceIsRunning from AIDL. Exception" + ex);
        }
    }

    @Override
    public void requestIccSimAuthentication(
            int serial, int authContext, String authData, String aid) {
        Log.d(mTag, "requestIccSimAuthentication");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.requestIccSimAuthenticationResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to requestIccSimAuthentication from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendEnvelope(int serial, String contents) {
        Log.d(mTag, "sendEnvelope");
        // TODO: cache value
        String commandResponse = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendEnvelopeResponse(rsp, commandResponse);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendEnvelope from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendEnvelopeWithStatus(int serial, String contents) {
        Log.d(mTag, "sendEnvelopeWithStatus");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendEnvelopeWithStatusResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendEnvelopeWithStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendTerminalResponseToSim(int serial, String contents) {
        Log.d(mTag, "sendTerminalResponseToSim");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendTerminalResponseToSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendTerminalResponseToSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setAllowedCarriers(
            int serial,
            android.hardware.radio.sim.CarrierRestrictions carriers,
            int multiSimPolicy) {
        Log.d(mTag, "sendTerminalResponseToSim");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setAllowedCarriersResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setAllowedCarriers from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCarrierInfoForImsiEncryption(
            int serial, android.hardware.radio.sim.ImsiEncryptionInfo imsiEncryptionInfo) {
        Log.d(mTag, "setCarrierInfoForImsiEncryption");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setCarrierInfoForImsiEncryptionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCarrierInfoForImsiEncryption from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaSubscriptionSource(int serial, int cdmaSub) {
        Log.d(mTag, "setCdmaSubscriptionSource");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setCdmaSubscriptionSourceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCdmaSubscriptionSource from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setFacilityLockForApp(
            int serial,
            String facility,
            boolean lockState,
            String password,
            int serviceClass,
            String appId) {
        Log.d(mTag, "setFacilityLockForApp");
        // TODO: cache value
        int retry = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setFacilityLockForAppResponse(rsp, retry);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setFacilityLockForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSimCardPower(int serial, int powerUp) {
        Log.d(mTag, "setSimCardPower");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setSimCardPowerResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSimCardPower from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setUiccSubscription(int serial, android.hardware.radio.sim.SelectUiccSub uiccSub) {
        Log.d(mTag, "setUiccSubscription");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setUiccSubscriptionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setUiccSubscription from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPin2ForApp(int serial, String pin2, String aid) {
        Log.d(mTag, "supplyIccPin2ForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPin2ForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplyIccPin2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPinForApp(int serial, String pin, String aid) {
        Log.d(mTag, "supplyIccPinForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPinForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplyIccPinForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPuk2ForApp(int serial, String puk2, String pin2, String aid) {
        Log.d(mTag, "supplyIccPuk2ForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPuk2ForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplyIccPuk2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPukForApp(int serial, String puk, String pin, String aid) {
        Log.d(mTag, "supplyIccPukForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPukForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplyIccPukForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplySimDepersonalization(int serial, int persoType, String controlKey) {
        Log.d(mTag, "supplySimDepersonalization");
        // TODO: cache value
        int retPersoType = persoType;
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplySimDepersonalizationResponse(
                    rsp, retPersoType, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplySimDepersonalization from AIDL. Exception" + ex);
        }
    }

    @Override
    public void updateSimPhonebookRecords(
            int serial, android.hardware.radio.sim.PhonebookRecordInfo recordInfo) {
        Log.d(mTag, "updateSimPhonebookRecords");
        // TODO: cache value
        int updatedRecordIndex = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.updateSimPhonebookRecordsResponse(rsp, updatedRecordIndex);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to updateSimPhonebookRecords from AIDL. Exception" + ex);
        }
    }

    public void carrierInfoForImsiEncryption() {
        Log.d(mTag, "carrierInfoForImsiEncryption");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.carrierInfoForImsiEncryption(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to carrierInfoForImsiEncryption from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void cdmaSubscriptionSourceChanged(int cdmaSource) {
        Log.d(mTag, "carrierInfoForImsiEncryption");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.cdmaSubscriptionSourceChanged(
                        RadioIndicationType.UNSOLICITED, cdmaSource);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to cdmaSubscriptionSourceChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void simPhonebookChanged() {
        Log.d(mTag, "simPhonebookChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simPhonebookChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to simPhonebookChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void simPhonebookRecordsReceived(
            byte status, android.hardware.radio.sim.PhonebookRecordInfo[] records) {
        Log.d(mTag, "simPhonebookRecordsReceived");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simPhonebookRecordsReceived(
                        RadioIndicationType.UNSOLICITED, status, records);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to simPhonebookRecordsReceived from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void simRefresh(SimRefreshResult refreshResult) {
        Log.d(mTag, "simRefresh");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simRefresh(RadioIndicationType.UNSOLICITED, refreshResult);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to simRefresh from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void simStatusChanged() {
        Log.d(mTag, "simStatusChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simStatusChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to simStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void stkEventNotify(String cmd) {
        Log.d(mTag, "stkEventNotify");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkEventNotify(RadioIndicationType.UNSOLICITED, cmd);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to stkEventNotify from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void stkProactiveCommand(String cmd) {
        Log.d(mTag, "stkProactiveCommand");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkProactiveCommand(RadioIndicationType.UNSOLICITED, cmd);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to stkProactiveCommand from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void stkSessionEnd() {
        Log.d(mTag, "stkSessionEnd");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkSessionEnd(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to stkSessionEnd from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void subscriptionStatusChanged(boolean activate) {
        Log.d(mTag, "subscriptionStatusChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.subscriptionStatusChanged(
                        RadioIndicationType.UNSOLICITED, activate);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to subscriptionStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    public void uiccApplicationsEnablementChanged(boolean enabled) {
        Log.d(mTag, "uiccApplicationsEnablementChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.uiccApplicationsEnablementChanged(
                        RadioIndicationType.UNSOLICITED, enabled);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to uiccApplicationsEnablementChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioSimIndication");
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioSim.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioSim.VERSION;
    }

    public void updateCarrierRestrictionStatusInfo(
            Carrier[] carrierList, int carrierRestrictionStatus) {
        mCarrierList = carrierList;
        mCarrierRestrictionStatus = carrierRestrictionStatus;
    }

    public void updateCarrierRestrictionRules(CarrierRestrictions carrierRestrictionRules,
            int multiSimPolicy) {
        Log.d(mTag, "updateCarrierRestrictionRules");
        mCarrierRestrictionRules = carrierRestrictionRules;
        mMultiSimPolicy = multiSimPolicy;
    }
}
