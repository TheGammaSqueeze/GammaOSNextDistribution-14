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

import android.content.Context;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.data.DataProfileInfo;
import android.hardware.radio.data.IRadioData;
import android.hardware.radio.data.IRadioDataIndication;
import android.hardware.radio.data.IRadioDataResponse;
import android.hardware.radio.data.KeepaliveRequest;
import android.hardware.radio.data.LinkAddress;
import android.hardware.radio.data.SetupDataCallResult;
import android.hardware.radio.data.SliceInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;

public class IRadioDataImpl extends IRadioData.Stub {
    private static final String TAG = "MRDATA";

    private final MockModemService mService;
    private final MockDataService mMockDataService;
    private IRadioDataResponse mRadioDataResponse;
    private IRadioDataIndication mRadioDataIndication;
    private MockModemConfigInterface mMockModemConfigInterface;
    private static Object sCacheUpdateMutex = new Object();
    private final Handler mHandler;
    private int mSubId;
    private String mTag;

    private static MockNetworkService sServiceState;

    // Event
    static final int EVENT_NETWORK_STATUS_CHANGED = 1;

    public IRadioDataImpl(
            MockModemService service,
            Context context,
            MockModemConfigInterface configInterface,
            int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        mMockDataService = new MockDataService(context, instanceId);
        this.mService = service;

        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;

        mHandler = new IRadioDataHandler();

        // Register event
        mMockModemConfigInterface.registerForServiceStateChanged(
                mSubId, mHandler, EVENT_NETWORK_STATUS_CHANGED, null);
    }

    public MockDataService getMockDataServiceInstance() {
        return mMockDataService;
    }

    // Implementation of IRadioData functions
    @Override
    public void setResponseFunctions(
            IRadioDataResponse radioDataResponse, IRadioDataIndication radioDataIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioDataResponse = radioDataResponse;
        mRadioDataIndication = radioDataIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);

        unsolDataCallListChanged();
    }

    @Override
    public void allocatePduSessionId(int serial) {
        Log.d(mTag, "allocatePduSessionId");
        int id = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.allocatePduSessionIdResponse(rsp, id);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to allocatePduSessionId from AIDL. Exception" + ex);
        }
    }

    @Override
    public void cancelHandover(int serial, int callId) {
        Log.d(mTag, "cancelHandover");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.cancelHandoverResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to cancelHandover from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deactivateDataCall(int serial, int cid, int reason) {
        Log.d(mTag, "deactivateDataCall");

        mMockDataService.deactivateDataCall(cid, reason);
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioDataResponse.deactivateDataCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to deactivateDataCall from AIDL. Exception" + ex);
        }
        // send the data call list changed
        unsolDataCallListChanged();
    }

    @Override
    public void getDataCallList(int serial) {
        Log.d(mTag, "getDataCallList");

        List<SetupDataCallResult> dataCallLists = mMockDataService.getDataCallList();
        SetupDataCallResult[] dcList = new SetupDataCallResult[dataCallLists.size()];
        dcList = dataCallLists.toArray(dcList);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioDataResponse.getDataCallListResponse(rsp, dcList);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getDataCallList from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSlicingConfig(int serial) {
        Log.d(mTag, "getSlicingConfig");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.getSlicingConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSlicingConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void releasePduSessionId(int serial, int id) {
        Log.d(mTag, "releasePduSessionId");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.releasePduSessionIdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to releasePduSessionId from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
    }

    @Override
    public void setDataAllowed(int serial, boolean allow) {
        Log.d(mTag, "setDataAllowed");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setDataAllowedResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setDataAllowed from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setDataProfile(int serial, DataProfileInfo[] profiles) {
        Log.d(mTag, "setDataProfile");

        // set data profiles to mockdataservice
        mMockDataService.setDataProfileInfo(profiles);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioDataResponse.setDataProfileResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setDataProfile from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setDataThrottling(
            int serial, byte dataThrottlingAction, long completionDurationMillis) {
        Log.d(mTag, "setDataThrottling");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setDataThrottlingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setDataThrottling from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setInitialAttachApn(int serial, DataProfileInfo dataProfileInfo) {
        Log.d(mTag, "setInitialAttachApn");
        // set initial attach apn to mockdataservice
        mMockDataService.setInitialAttachProfile(dataProfileInfo);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioDataResponse.setInitialAttachApnResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setInitialAttachApn from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setupDataCall(
            int serial,
            int accessNetwork,
            DataProfileInfo dataProfileInfo,
            boolean roamingAllowed,
            int reason,
            LinkAddress[] addresses,
            String[] dnses,
            int pduSessionId,
            SliceInfo sliceInfo,
            boolean matchAllRuleAllowed) {
        Log.d(mTag, "setupDataCall");

        RadioResponseInfo rsp;
        SetupDataCallResult dc = new SetupDataCallResult();
        rsp = mService.makeSolRsp(serial);
        synchronized (sCacheUpdateMutex) {
            if (sServiceState == null || !sServiceState.isPsInService()) {
                rsp = mService.makeSolRsp(serial, RadioError.OP_NOT_ALLOWED_BEFORE_REG_TO_NW);
            } else {
                if (mMockDataService.isSupportedCapability(dataProfileInfo.apn)) {
                    if (dataProfileInfo.apn.equals("ims")) {
                        dc = mMockDataService.setupDataCall(mMockDataService.APN_TYPE_IMS);
                    } else if (dataProfileInfo.apn.equals("internet")) {
                        dc = mMockDataService.setupDataCall(mMockDataService.APN_TYPE_DEFAULT);
                    }
                } else {
                    rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
                }
            }
        }

        try {
            mRadioDataResponse.setupDataCallResponse(rsp, dc);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setupDataCall from AIDL. Exception" + ex);
        }
        // send the data call list changed
        unsolDataCallListChanged();
    }

    @Override
    public void startHandover(int serial, int callId) {
        Log.d(mTag, "startHandover");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.startHandoverResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startHandover from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startKeepalive(int serial, KeepaliveRequest keepalive) {
        Log.d(mTag, "startKeepalive");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.startKeepaliveResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startKeepalive from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopKeepalive(int serial, int sessionHandle) {
        Log.d(mTag, "stopKeepalive");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.stopKeepaliveResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to stopKeepalive from AIDL. Exception" + ex);
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioData.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioData.VERSION;
    }

    public void unsolDataCallListChanged() {
        Log.d(mTag, "unsolDataCallListChanged");

        if (mRadioDataIndication != null) {
            List<SetupDataCallResult> dataCallLists = mMockDataService.getDataCallList();
            SetupDataCallResult[] dcList = new SetupDataCallResult[dataCallLists.size()];
            dcList = dataCallLists.toArray(dcList);

            try {
                mRadioDataIndication.dataCallListChanged(RadioIndicationType.UNSOLICITED, dcList);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to invoke dataCallListChanged change from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioDataIndication");
        }
    }

    /** Handler class to handle callbacks */
    private static final class IRadioDataHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (sCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_NETWORK_STATUS_CHANGED:
                        Log.d(TAG, "Received EVENT_NETWORK_STATUS_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            sServiceState = (MockNetworkService) ar.result;
                            Log.i(TAG, "Service State: " + sServiceState.toString());
                        } else {
                            Log.e(TAG, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                }
            }
        }
    }
}
