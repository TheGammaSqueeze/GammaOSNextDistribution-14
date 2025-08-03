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

package android.telephony.mockmodem;

import android.annotation.NonNull;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.ims.ConnectionFailureInfo;
import android.hardware.radio.ims.IRadioIms;
import android.hardware.radio.ims.IRadioImsIndication;
import android.hardware.radio.ims.IRadioImsResponse;
import android.hardware.radio.ims.ImsDeregistrationReason;
import android.os.RemoteException;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import java.util.List;

public class IRadioImsImpl extends IRadioIms.Stub {
    private static final String TAG = "MRIMS";

    private final MockModemService mService;
    private IRadioImsResponse mRadioImsResponse;
    private IRadioImsIndication mRadioImsIndication;
    private final MockModemConfigInterface mMockModemConfigInterface;
    private final int mSubId;
    private final String mTag;

    private final MockImsService mImsState = new MockImsService();

    private boolean mBlockStartImsTrafficResponse = false;

    public IRadioImsImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;
    }

    // Implementation of IRadioIms functions
    @Override
    public void setResponseFunctions(
            IRadioImsResponse radioImsResponse, IRadioImsIndication radioImsIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioImsResponse = radioImsResponse;
        mRadioImsIndication = radioImsIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void setSrvccCallInfo(int serial, android.hardware.radio.ims.SrvccCall[] srvccCalls) {
        Log.d(mTag, "setSrvccCallInfo");

        mImsState.setSrvccCallInfo(srvccCalls);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioImsResponse.setSrvccCallInfoResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSrvccCallInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void updateImsRegistrationInfo(int serial,
            android.hardware.radio.ims.ImsRegistration imsRegistration) {
        Log.d(mTag, "updateImsRegistrationInfo");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioImsResponse.updateImsRegistrationInfoResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to updateImsRegistrationInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startImsTraffic(int serial,
            int token, int imsTrafficType, int accessNetworkType, int trafficDirection) {
        Log.d(mTag, "startImsTraffic");

        mImsState.startImsTraffic(serial, token, imsTrafficType);

        if (mBlockStartImsTrafficResponse) return;

        ConnectionFailureInfo failureInfo = null;
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioImsResponse.startImsTrafficResponse(rsp, failureInfo);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startImsTraffic from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopImsTraffic(int serial, int token) {
        Log.d(mTag, "stopImsTraffic");

        mImsState.stopImsTraffic(token);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioImsResponse.stopImsTrafficResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to stopImsTraffic from AIDL. Exception" + ex);
        }
    }

    @Override
    public void triggerEpsFallback(int serial, int reason) {
        Log.d(mTag, "triggerEpsFallback");

        mImsState.setEpsFallbackReason(reason);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioImsResponse.triggerEpsFallbackResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to triggerEpsFallback from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendAnbrQuery(int serial, int qosSessionId, int direction, int bitsPerSecond) {
        Log.d(mTag, "sendAnbrQuery");

        mImsState.sendAnbrQuery(qosSessionId, direction, bitsPerSecond);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioImsResponse.sendAnbrQueryResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendAnbrQuery from AIDL. Exception" + ex);
        }
    }

    public void onConnectionSetupFailure(int token, @NonNull ConnectionFailureInfo failureInfo) {
        Log.d(mTag, "onConnectionSetupFailure");

        if (mRadioImsIndication != null) {
            try {
                mRadioImsIndication.onConnectionSetupFailure(
                        RadioIndicationType.UNSOLICITED, token, failureInfo);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to onConnectionSetupFailure indication from AIDL. Exception"
                        + ex);
            }
        } else {
            Log.e(mTag, "null mRadioImsIndication");
        }
    }

    public void notifyAnbr(int qosSessionId, int direction, int bitsPerSecond) {
        Log.d(mTag, "notifyAnbr");

        if (mRadioImsIndication != null) {
            try {
                mRadioImsIndication.notifyAnbr(RadioIndicationType.UNSOLICITED,
                        qosSessionId, direction, bitsPerSecond);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to notifyAnbr indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioImsIndication");
        }
    }

    public void triggerImsDeregistration(
            @ImsRegistrationImplBase.ImsDeregistrationReason int reason) {
        Log.d(mTag, "triggerImsDeregistration");

        int halReason;
        switch (reason) {
            case ImsRegistrationImplBase.REASON_SIM_REFRESH:
                halReason = ImsDeregistrationReason.REASON_SIM_REFRESH;
                break;
            case ImsRegistrationImplBase.REASON_ALLOWED_NETWORK_TYPES_CHANGED:
                halReason = ImsDeregistrationReason.REASON_ALLOWED_NETWORK_TYPES_CHANGED;
                break;
            default:
                halReason = ImsDeregistrationReason.REASON_SIM_REMOVED;
                break;
        }

        if (mRadioImsIndication != null) {
            try {
                mRadioImsIndication.triggerImsDeregistration(
                        RadioIndicationType.UNSOLICITED, halReason);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to triggerImsDeregistration indication from AIDL. Exception"
                        + ex);
            }
        } else {
            Log.e(mTag, "null mRadioImsIndication");
        }
    }

    /** @return The list of {@link MockSrvccCall} instances. */
    public List<MockSrvccCall> getSrvccCalls() {
        return mImsState.getSrvccCalls();
    }

    /**
     * Stop sending default response to startImsTraffic.
     *
     * @param blocked indicates whether sending response is allowed or not.
     */
    public void blockStartImsTrafficResponse(boolean blocked) {
        mBlockStartImsTrafficResponse = blocked;
    }

    /**
     * Returns whether the given IMS traffic type is started or not.
     *
     * @param trafficType the IMS traffic type
     * @return boolean true if the given IMS traffic type is started
     */
    public boolean isImsTrafficStarted(
            @android.telephony.ims.feature.MmTelFeature.ImsTrafficType int trafficType) {
        return mImsState.isImsTrafficStarted(trafficType);
    }

    /**
     * Clears the IMS traffic state.
     */
    public void clearImsTrafficState() {
        mBlockStartImsTrafficResponse = false;
        mImsState.clearImsTrafficState();
    }

    /**
     * Sends the response with the given information.
     *
     * @param trafficType the IMS traffic type
     * @param reason The reason of failure.
     * @param causeCode Failure cause code from network or modem specific to the failure.
     * @param waitTimeMillis Retry wait time provided by network in milliseconds.
     */
    public void sendStartImsTrafficResponse(
            @android.telephony.ims.feature.MmTelFeature.ImsTrafficType int trafficType,
            @android.telephony.ims.feature.ConnectionFailureInfo.FailureReason int reason,
            int causeCode, int waitTimeMillis) {

        ConnectionFailureInfo failureInfo = null;
        if (reason != 0) {
            failureInfo = new ConnectionFailureInfo();
            failureInfo.failureReason = reason;
            failureInfo.causeCode = causeCode;
            failureInfo.waitTimeMillis = waitTimeMillis;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(mImsState.getImsTrafficSerial(trafficType));
        try {
            mRadioImsResponse.startImsTrafficResponse(rsp, failureInfo);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to startImsTrafficResponse from AIDL. Exception" + ex);
        }
    }

    /**
     * Notifies the connection failure info
     *
     * @param trafficType the IMS traffic type
     * @param reason The reason of failure.
     * @param causeCode Failure cause code from network or modem specific to the failure.
     * @param waitTimeMillis Retry wait time provided by network in milliseconds.
     */
    public void sendConnectionFailureInfo(
            @android.telephony.ims.feature.MmTelFeature.ImsTrafficType int trafficType,
            @android.telephony.ims.feature.ConnectionFailureInfo.FailureReason int reason,
            int causeCode, int waitTimeMillis) {

        ConnectionFailureInfo failureInfo = new ConnectionFailureInfo();
        failureInfo.failureReason = reason;
        failureInfo.causeCode = causeCode;
        failureInfo.waitTimeMillis = waitTimeMillis;

        onConnectionSetupFailure(mImsState.getImsTrafficToken(trafficType), failureInfo);
    }

    @Override
    public void updateImsCallStatus(int serial, android.hardware.radio.ims.ImsCall[] imsCalls) {
        Log.d(mTag, "updateImsCallStatus");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioImsResponse.updateImsCallStatusResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to updateImsCallStatus from AIDL. Exception" + ex);
        }
    }

    /**
     * Returns the reason that caused EPS fallback.
     *
     * @return the reason that caused EPS fallback.
     */
    public @MmTelFeature.EpsFallbackReason int getEpsFallbackReason() {
        return mImsState.getEpsFallbackReason();
    }

    /**
     * Clears the EPS fallback reason.
     */
    public void resetEpsFallbackReason() {
        mImsState.resetEpsFallbackReason();
    }

    /**
     * Returns the Anbr values triggered by Anbr Query.
     *
     * @return the Anbr values triggered by Anbr Query.
     */
    public int[] getAnbrValues() {
        return mImsState.getAnbrValues();
    }

    /**
     * Clears the Anbr values.
     */
    public void resetAnbrValues() {
        mImsState.resetAnbrValues();
    }

    /**
     * Waits for the event of IMS state.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     */
    public boolean waitForLatchCountdown(int latchIndex, int waitMs) {
        return mImsState.waitForLatchCountdown(latchIndex, waitMs);
    }

    /**
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        mImsState.resetAllLatchCountdown();
    }

    @Override
    public String getInterfaceHash() {
        return IRadioIms.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioIms.VERSION;
    }
}
