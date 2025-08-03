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

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.modem.IRadioModem;
import android.hardware.radio.modem.IRadioModemIndication;
import android.hardware.radio.modem.IRadioModemResponse;
import android.hardware.radio.modem.ImeiInfo;
import android.hardware.radio.modem.RadioState;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IRadioModemImpl extends IRadioModem.Stub {
    private static final String TAG = "MRMDM";

    private final MockModemService mService;
    private IRadioModemResponse mRadioModemResponse;
    private IRadioModemIndication mRadioModemIndication;

    private int mForceRadioPowerError = -1;

    private MockModemConfigInterface mMockModemConfigInterface;
    private final Object mCacheUpdateMutex;
    private final Handler mHandler;
    private int mSubId;
    private String mTag;

    // ***** Events
    static final int EVENT_BASEBAND_VERSION_CHANGED = 1;
    static final int EVENT_DEVICE_IDENTITY_CHANGED = 2;
    static final int EVENT_RADIO_STATE_CHANGED = 3;
    static final int EVENT_DEVICE_IMEI_INFO_CHANGED = 4;
    private static final int LATCH_MAX = 2;
    private final CountDownLatch[] mLatches = new CountDownLatch[LATCH_MAX];
    // ***** Cache of modem attributes/status
    private String mBasebandVer;
    private String mImei;
    private String mImeiSv;
    private String mEsn;
    private String mMeid;
    private int mImeiType;
    private int mRadioState;

    public IRadioModemImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mCacheUpdateMutex = new Object();
        mHandler = new IRadioModemHandler();
        mSubId = instanceId;

        // Register events
        mMockModemConfigInterface.registerForBasebandVersionChanged(
                mSubId, mHandler, EVENT_BASEBAND_VERSION_CHANGED, null);
        mMockModemConfigInterface.registerForDeviceIdentityChanged(
                mSubId, mHandler, EVENT_DEVICE_IDENTITY_CHANGED, null);
        mMockModemConfigInterface.registerForRadioStateChanged(
                mSubId, mHandler, EVENT_RADIO_STATE_CHANGED, null);
        mMockModemConfigInterface.registerForDeviceImeiInfoChanged(
                mSubId, mHandler, EVENT_DEVICE_IMEI_INFO_CHANGED, null);
    }

    /** Handler class to handle callbacks */
    private final class IRadioModemHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_BASEBAND_VERSION_CHANGED:
                        Log.d(mTag, "Received EVENT_BASEBAND_VERSION_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mBasebandVer = (String) ar.result;
                            Log.i(mTag, "Basedband version = " + mBasebandVer);
                        } else {
                            Log.e(
                                    mTag,
                                    msg.what
                                            + " failure. Not update baseband version."
                                            + ar.exception);
                        }
                        break;
                    case EVENT_DEVICE_IDENTITY_CHANGED:
                        Log.d(mTag, "Received EVENT_DEVICE_IDENTITY_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            String[] deviceIdentity = (String[]) ar.result;
                            mImei = deviceIdentity[0];
                            mImeiSv = deviceIdentity[1];
                            mEsn = deviceIdentity[2];
                            mMeid = deviceIdentity[3];
                            Log.i(
                                    mTag,
                                    "Device identity: IMEI = "
                                            + mImei
                                            + " IMEISV = "
                                            + mImeiSv
                                            + " ESN = "
                                            + mEsn
                                            + " MEID ="
                                            + mMeid);
                        } else {
                            Log.e(
                                    mTag,
                                    msg.what
                                            + " failure. Not update device identity."
                                            + ar.exception);
                        }
                        break;
                    case EVENT_RADIO_STATE_CHANGED:
                        Log.d(mTag, "Received EVENT_RADIO_STATE_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mRadioState = (int) ar.result;
                            Log.i(mTag, "Radio state: " + mRadioState);
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_DEVICE_IMEI_INFO_CHANGED:
                        Log.d(mTag, "Received EVENT_DEVICE_IMEIINFO_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            ImeiInfo imeiInfo = (ImeiInfo) ar.result;
                            mImei = imeiInfo.imei;
                            mImeiSv = imeiInfo.svn;
                            mImeiType = imeiInfo.type;
                            Log.i(mTag, "IMEIInfo : ImeiType = " + mImeiType);
                        } else {
                            Log.e(mTag, msg.what + " failure. Not update device ImeiInfo."
                                    + ar.exception);
                        }
                        break;
                }
            }
        }
    }

    // Implementation of IRadioModem functions
    @Override
    public void setResponseFunctions(
            IRadioModemResponse radioModemResponse, IRadioModemIndication radioModemIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioModemResponse = radioModemResponse;
        mRadioModemIndication = radioModemIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void enableModem(int serial, boolean on) {
        Log.d(mTag, "getNumOfLiveModems " + on);

        // TODO: cache value
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.enableModemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to enableModem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getBasebandVersion(int serial) {
        Log.d(mTag, "getBasebandVersion");

        String baseband;

        synchronized (mCacheUpdateMutex) {
            baseband = mBasebandVer;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getBasebandVersionResponse(rsp, baseband);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getBasebandVersion from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDeviceIdentity(int serial) {
        Log.d(mTag, "getDeviceIdentity");

        String imei, imeisv, esn, meid;

        synchronized (mCacheUpdateMutex) {
            imei = mImei;
            imeisv = mImeiSv;
            esn = mEsn;
            meid = mMeid;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getDeviceIdentityResponse(rsp, imei, imeisv, esn, meid);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getDeviceIdentity from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getImei(int serial) {
        Log.d(mTag, "getImei");
        android.hardware.radio.modem.ImeiInfo imeiInfo =
                new android.hardware.radio.modem.ImeiInfo();
        synchronized (mCacheUpdateMutex) {
            imeiInfo.type = mImeiType;
            imeiInfo.imei = mImei;
            imeiInfo.svn = mImeiSv;
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getImeiResponse(rsp, imeiInfo);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getImeiResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getHardwareConfig(int serial) {
        Log.d(mTag, "getHardwareConfig");

        android.hardware.radio.modem.HardwareConfig[] config =
                new android.hardware.radio.modem.HardwareConfig[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getHardwareConfigResponse(rsp, config);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getHardwareConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemActivityInfo(int serial) {
        Log.d(mTag, "getModemActivityInfo");

        android.hardware.radio.modem.ActivityStatsInfo activityInfo =
                new android.hardware.radio.modem.ActivityStatsInfo();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemActivityInfoResponse(rsp, activityInfo);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getModemActivityInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemStackStatus(int serial) {
        Log.d(mTag, "getModemStackStatus");

        boolean isEnabled = false;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemStackStatusResponse(rsp, isEnabled);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getModemStackStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getRadioCapability(int serial) {
        Log.d(mTag, "getRadioCapability");

        android.hardware.radio.modem.RadioCapability rc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getRadioCapabilityResponse(rsp, rc);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvReadItem(int serial, int itemId) {
        Log.d(mTag, "nvReadItem");

        // TODO: cache value
        String result = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvReadItemResponse(rsp, result);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to nvReadItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvResetConfig(int serial, int resetType) {
        Log.d(mTag, "nvResetConfig");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvResetConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to nvResetConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteCdmaPrl(int serial, byte[] prl) {
        Log.d(mTag, "nvWriteCdmaPrl");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteCdmaPrlResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to nvWriteCdmaPrl from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteItem(int serial, android.hardware.radio.modem.NvWriteItem item) {
        Log.d(mTag, "nvWriteItem");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteItemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to nvWriteItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void requestShutdown(int serial) {
        Log.d(mTag, "requestShutdown");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.requestShutdownResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to requestShutdown from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendDeviceState(int serial, int deviceStateType, boolean state) {
        Log.d(mTag, "sendDeviceState");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.sendDeviceStateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendDeviceState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
    }

    @Override
    public void setRadioCapability(int serial, android.hardware.radio.modem.RadioCapability rc) {
        Log.d(mTag, "setRadioCapability");

        // TODO: cache value
        android.hardware.radio.modem.RadioCapability respRc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.setRadioCapabilityResponse(rsp, respRc);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setRadioPower(
            int serial,
            boolean powerOn,
            boolean forEmergencyCall,
            boolean preferredForEmergencyCall) {
        Log.d(mTag, "setRadioPower");
        RadioResponseInfo rsp = null;

        // Check if the error response needs to be modified
        if (mForceRadioPowerError != -1) {
            rsp = mService.makeSolRsp(serial, mForceRadioPowerError);
        } else {
            synchronized (mCacheUpdateMutex) {
                if (powerOn) {
                    mRadioState = MockModemConfigInterface.RADIO_STATE_ON;
                } else {
                    mRadioState = MockModemConfigInterface.RADIO_STATE_OFF;
                }
                mMockModemConfigInterface.setRadioState(mSubId, mRadioState, mTag);
            }
            rsp = mService.makeSolRsp(serial);
        }

        try {
            mRadioModemResponse.setRadioPowerResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setRadioPower from AIDL. Exception" + ex);
        }

        if (rsp.error == RadioError.NONE) {
            if (powerOn) {
                radioStateChanged(RadioState.ON);
            } else {
                radioStateChanged(RadioState.OFF);
            }
        }
    }

    /**
     * Sent when setRadioCapability() completes. Returns the same RadioCapability as
     * getRadioCapability() and is the same as the one sent by setRadioCapability().
     *
     * @param radioCapability Current radio capability
     */
    public void radioCapabilityIndication(
            android.hardware.radio.modem.RadioCapability radioCapability) {
        Log.d(mTag, "radioCapabilityIndication");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioCapabilityIndication(
                        RadioIndicationType.UNSOLICITED, radioCapability);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to radioCapabilityIndication from AIDL. Exception" + ex);
            }
        } else {

            Log.e(mTag, "null mRadioModemIndication");
        }
    }

    /**
     * Sent when there is a change in the IMEI mapping with respect to sim slot.
     *
     * @param imeiInfo : change in the imeiInfo value
     */
    public void onImeiMappingChanged(ImeiInfo imeiInfo) {
        Log.d(TAG, "onImeiMappingChanged");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.onImeiMappingChanged(RadioIndicationType.UNSOLICITED,
                        imeiInfo);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to onImeiMappingChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    /**
     * Indicates when radio state changes.
     *
     * @param radioState Current radio state
     */
    public void radioStateChanged(int radioState) {
        Log.d(mTag, "radioStateChanged");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioStateChanged(
                        RadioIndicationType.UNSOLICITED, radioState);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to radioStateChanged from AIDL. Exception" + ex);
            }
        } else {

            Log.e(mTag, "null mRadioModemIndication");
        }
    }

    /** Indicates the ril connects and returns the version. */
    public void rilConnected() {
        Log.d(mTag, "rilConnected");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.rilConnected(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to rilConnected from AIDL. Exception" + ex);
            }
        } else {

            Log.e(mTag, "null mRadioModemIndication");
        }
    }

    public void forceErrorResponse(int requestId, int error) {
        switch (requestId) {
            case RIL_REQUEST_RADIO_POWER:
                mForceRadioPowerError = error;
                break;
            default:
                break;
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioModem.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioModem.VERSION;
    }

    /**
     * Helper method that will change the Imei mapping to another slot.
     */
    public void changeImeiMapping() {
        if (mImeiType == ImeiInfo.ImeiType.PRIMARY
                && MockModemConfigInterface.DEFAULT_PHONE1_IMEI.equals(mImei)) {
            mImeiType = ImeiInfo.ImeiType.SECONDARY;
            mImei = MockModemConfigInterface.DEFAULT_PHONE2_IMEI;
        } else {
            mImeiType = ImeiInfo.ImeiType.PRIMARY;
            mImei = MockModemConfigInterface.DEFAULT_PHONE1_IMEI;
        }

        Log.d(TAG, "changeImeiMapping");
        android.hardware.radio.modem.ImeiInfo imeiInfo =
                new android.hardware.radio.modem.ImeiInfo();
        synchronized (mCacheUpdateMutex) {
            imeiInfo.type = mImeiType;
            imeiInfo.imei = mImei;
            imeiInfo.svn = mImeiSv;
        }
        onImeiMappingChanged(imeiInfo);
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
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        synchronized (mLatches) {
            for (int i = 0; i < LATCH_MAX; i++) {
                mLatches[i] = new CountDownLatch(1);
            }
        }
    }
}
