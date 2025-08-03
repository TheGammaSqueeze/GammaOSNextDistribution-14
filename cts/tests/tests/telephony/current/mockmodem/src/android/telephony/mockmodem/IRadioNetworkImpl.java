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
import android.hardware.radio.network.BarringInfo;
import android.hardware.radio.network.BarringTypeSpecificInfo;
import android.hardware.radio.network.CellIdentity;
import android.hardware.radio.network.Domain;
import android.hardware.radio.network.EmergencyRegResult;
import android.hardware.radio.network.IRadioNetwork;
import android.hardware.radio.network.IRadioNetworkIndication;
import android.hardware.radio.network.IRadioNetworkResponse;
import android.hardware.radio.network.NetworkScanRequest;
import android.hardware.radio.network.RadioAccessSpecifier;
import android.hardware.radio.network.RegState;
import android.hardware.radio.network.SignalThresholdInfo;
import android.hardware.radio.sim.CardStatus;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.mockmodem.MockModemConfigBase.SimInfoChangedResult;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;

public class IRadioNetworkImpl extends IRadioNetwork.Stub {
    private static final String TAG = "MRNW";

    private final MockModemService mService;
    private IRadioNetworkResponse mRadioNetworkResponse;
    private IRadioNetworkIndication mRadioNetworkIndication;
    private MockModemConfigInterface mMockModemConfigInterface;
    private final Object mCacheUpdateMutex;
    private final Handler mHandler;
    private int mSubId;
    private String mTag;

    // ***** Events
    static final int EVENT_RADIO_STATE_CHANGED = 1;
    static final int EVENT_SIM_STATUS_CHANGED = 2;
    static final int EVENT_PREFERRED_MODE_CHANGED = 3;

    // ***** Cache of modem attributes/status
    private int mNetworkTypeBitmap;
    private boolean mNetworkSelectionMode;
    private boolean mNullCipherAndIntegrityEnabled;

    private int mRadioState;
    private boolean mSimReady;

    private MockNetworkService mServiceState;

    public IRadioNetworkImpl(
            MockModemService service,
            Context context,
            MockModemConfigInterface configInterface,
            int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mCacheUpdateMutex = new Object();
        mHandler = new IRadioNetworkHandler();
        mSubId = instanceId;
        mServiceState = new MockNetworkService(context);

        // Default network type GPRS|EDGE|UMTS|HSDPA|HSUPA|HSPA|LTE|HSPA+|GSM|LTE_CA|NR
        mNetworkTypeBitmap =
                MockNetworkService.GSM
                        | MockNetworkService.WCDMA
                        | MockNetworkService.LTE
                        | MockNetworkService.NR;
        mServiceState.updateHighestRegisteredRat(mNetworkTypeBitmap);

        // Null security algorithms are allowed by default
        mNullCipherAndIntegrityEnabled = true;

        mMockModemConfigInterface.registerForRadioStateChanged(
                mSubId, mHandler, EVENT_RADIO_STATE_CHANGED, null);
        mMockModemConfigInterface.registerForCardStatusChanged(
                mSubId, mHandler, EVENT_SIM_STATUS_CHANGED, null);
    }

    /** Handler class to handle callbacks */
    private final class IRadioNetworkHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_SIM_STATUS_CHANGED:
                        Log.d(mTag, "Received EVENT_SIM_STATUS_CHANGED");
                        boolean oldSimReady = mSimReady;
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mSimReady = updateSimReady(ar);
                            if (oldSimReady != mSimReady) {
                                updateNetworkStatus();
                            }
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;

                    case EVENT_RADIO_STATE_CHANGED:
                        Log.d(mTag, "Received EVENT_RADIO_STATE_CHANGED");
                        int oldRadioState = mRadioState;
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mRadioState = (int) ar.result;
                            Log.i(mTag, "Radio state: " + mRadioState);
                            if (oldRadioState != mRadioState) {
                                updateNetworkStatus();
                            }
                        } else {
                            Log.e(mTag, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;

                    case EVENT_PREFERRED_MODE_CHANGED:
                        Log.d(mTag, "Received EVENT_PREFERRED_MODE_CHANGED");
                        mServiceState.updateNetworkStatus(
                                MockNetworkService.NETWORK_UPDATE_PREFERRED_MODE_CHANGE);
                        updateNetworkStatus();
                        break;
                }
            }
        }
    }

    // Implementation of IRadioNetwork utility functions

    private void notifyServiceStateChange() {
        Log.d(mTag, "notifyServiceStateChange");

        Handler handler = mMockModemConfigInterface.getMockModemConfigHandler(mSubId);
        Message msg =
                handler.obtainMessage(
                        MockModemConfigBase.EVENT_SERVICE_STATE_CHANGE, mServiceState);
        handler.sendMessage(msg);
    }

    private void updateNetworkStatus() {
        updateNetworkStatus(Domain.CS | Domain.PS);
    }

    private void updateNetworkStatus(int domainBitmask) {
        if (mRadioState != MockModemConfigInterface.RADIO_STATE_ON) {
            // Update to OOS state
            mServiceState.updateServiceState(RegState.NOT_REG_MT_NOT_SEARCHING_OP, domainBitmask);
        } else if (!mSimReady) {
            // Update to Searching state
            mServiceState.updateServiceState(RegState.NOT_REG_MT_SEARCHING_OP, domainBitmask);
        } else if (mServiceState.isHomeCellExisted() && mServiceState.getIsHomeCamping()) {
            // Update to Home state
            mServiceState.updateServiceState(RegState.REG_HOME, domainBitmask);
        } else if (mServiceState.isRoamingCellExisted() && mServiceState.getIsRoamingCamping()) {
            // Update to Roaming state
            mServiceState.updateServiceState(RegState.REG_ROAMING, domainBitmask);
        } else if (mServiceState.getRegFailCause() != 0) {
            mServiceState.updateServiceState(RegState.REG_DENIED, domainBitmask);
        } else {
            // Update to Searching state
            mServiceState.updateServiceState(RegState.NOT_REG_MT_SEARCHING_OP, domainBitmask);
        }

        unsolNetworkStateChanged();
        unsolCurrentSignalStrength();
        unsolCellInfoList();
    }

    private boolean updateSimReady(AsyncResult ar) {
        String simPlmn = "";
        CardStatus cardStatus = new CardStatus();
        cardStatus = (CardStatus) ar.result;

        if (cardStatus.cardState != CardStatus.STATE_PRESENT) {
            return false;
        }

        int numApplications = cardStatus.applications.length;
        if (numApplications < 1) {
            return false;
        }

        for (int i = 0; i < numApplications; i++) {
            android.hardware.radio.sim.AppStatus rilAppStatus = cardStatus.applications[i];
            if (rilAppStatus.appState == android.hardware.radio.sim.AppStatus.APP_STATE_READY) {
                Log.i(mTag, "SIM is ready");
                simPlmn = mMockModemConfigInterface.getSimInfo(mSubId,
                        SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC, mTag);
                mServiceState.updateSimPlmn(simPlmn);
                return true;
            }
        }

        mServiceState.updateSimPlmn(simPlmn);
        return false;
    }

    public boolean changeNetworkService(int carrierId, boolean registration) {
        Log.d(mTag, "changeNetworkService: carrier id(" + carrierId + "): " + registration);
        return changeNetworkService(carrierId, registration, Domain.CS | Domain.PS);
    }

    /** Change Network Service */
    public boolean changeNetworkService(
            int carrierId,
            boolean registration,
            int domainBitmask) {
        return changeNetworkService(carrierId, registration, domainBitmask, 0 /* regFailCause */);
    }

    /** Change Network Service */
    public boolean changeNetworkService(
            int carrierId,
            boolean registration,
            int domainBitmask,
            int regFailCause) {
        Log.d(
                mTag,
                "changeNetworkService: carrier id("
                        + carrierId
                        + "): "
                        + registration
                        + " with domainBitmask = "
                        + domainBitmask);

        synchronized (mCacheUpdateMutex) {
            // TODO: compare carrierId and sim to decide home or roming
            mServiceState.setRegFailCause(regFailCause);
            mServiceState.setServiceStatus(false, registration);
            updateNetworkStatus(domainBitmask);
        }

        return true;
    }

    /**
     * Updates the emergency registration state.
     * @param regResult the emergency registration state.
     */
    public void setEmergencyRegResult(MockEmergencyRegResult regResult) {
        Log.d(mTag, "setEmergencyRegResult");

        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyRegResult(convertEmergencyRegResult(regResult));
        }
    }

    /**
     * Resets the current emergency mode.
     */
    public void resetEmergencyMode() {
        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyMode(0);
        }
    }

    /**
     * Returns the current emergency mode.
     */
    public int getEmergencyMode() {
        Log.d(mTag, "getEmergencyMode");

        synchronized (mCacheUpdateMutex) {
            return mServiceState.getEmergencyMode();
        }
    }

    /**
     * @return whether emergency network scan is triggered.
     */
    public boolean isEmergencyNetworkScanTriggered() {
        synchronized (mCacheUpdateMutex) {
            return mServiceState.isEmergencyNetworkScanTriggered();
        }
    }

    /**
     * @return whether emergency network scan is canceled.
     */
    public boolean isEmergencyNetworkScanCanceled() {
        synchronized (mCacheUpdateMutex) {
            return mServiceState.isEmergencyNetworkScanCanceled();
        }
    }

    /**
     * @return the list of preferred network type.
     */
    public int[] getEmergencyNetworkScanAccessNetwork() {
        synchronized (mCacheUpdateMutex) {
            return mServiceState.getEmergencyNetworkScanAccessNetwork();
        }
    }

    /**
     * @return the preferred scan type.
     */
    public int getEmergencyNetworkScanType() {
        synchronized (mCacheUpdateMutex) {
            return mServiceState.getEmergencyNetworkScanType();
        }
    }

    /**
     * Resets the emergency network scan attributes.
     */
    public void resetEmergencyNetworkScan() {
        synchronized (mCacheUpdateMutex) {
            mServiceState.resetEmergencyNetworkScan();
        }
    }

    // Implementation of IRadioNetwork functions
    @Override
    public void getAllowedNetworkTypesBitmap(int serial) {
        Log.d(mTag, "getAllowedNetworkTypesBitmap");
        int networkTypeBitmap = mNetworkTypeBitmap;

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getAllowedNetworkTypesBitmapResponse(rsp, networkTypeBitmap);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getAllowedNetworkTypesBitmap from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAvailableBandModes(int serial) {
        Log.d(mTag, "getAvailableBandModes");

        int[] bandModes = new int[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getAvailableBandModesResponse(rsp, bandModes);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getAvailableBandModes from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAvailableNetworks(int serial) {
        Log.d(mTag, "getAvailableNetworks");

        android.hardware.radio.network.OperatorInfo[] networkInfos =
                new android.hardware.radio.network.OperatorInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getAvailableNetworksResponse(rsp, networkInfos);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getAvailableNetworks from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getBarringInfo(int serial) {
        Log.d(mTag, "getBarringInfo");

        CellIdentity cellIdentity;
        BarringInfo[] barringInfos;
        synchronized (mCacheUpdateMutex) {
            cellIdentity = mServiceState.getPrimaryCellIdentity();
            barringInfos = mServiceState.getBarringInfo();
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getBarringInfoResponse(rsp, cellIdentity, barringInfos);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getBarringInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaRoamingPreference(int serial) {
        Log.d(mTag, "getCdmaRoamingPreference");
        int type = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getCdmaRoamingPreferenceResponse(rsp, type);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCdmaRoamingPreference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCellInfoList(int serial) {
        Log.d(mTag, "getCellInfoList");
        android.hardware.radio.network.CellInfo[] cells;

        synchronized (mCacheUpdateMutex) {
            cells = mServiceState.getCells();
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getCellInfoListResponse(rsp, cells);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCellInfoList from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDataRegistrationState(int serial) {
        Log.d(mTag, "getDataRegistrationState");

        android.hardware.radio.network.RegStateResult dataRegResponse =
                new android.hardware.radio.network.RegStateResult();

        dataRegResponse.cellIdentity = new android.hardware.radio.network.CellIdentity();
        dataRegResponse.reasonForDenial = mServiceState.getRegFailCause();

        synchronized (mCacheUpdateMutex) {
            dataRegResponse.regState =
                    mServiceState.getRegistration(android.hardware.radio.network.Domain.PS);
            dataRegResponse.rat = mServiceState.getRegistrationRat();
            if (mServiceState.isInService()) {
                dataRegResponse.registeredPlmn =
                        mServiceState.getPrimaryCellOperatorInfo().operatorNumeric;
            }

            dataRegResponse.cellIdentity = mServiceState.getPrimaryCellIdentity();
        }

        // TODO: support accessTechnologySpecificInfo
        dataRegResponse.accessTechnologySpecificInfo =
                android.hardware.radio.network.AccessTechnologySpecificInfo.noinit(true);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getDataRegistrationStateResponse(rsp, dataRegResponse);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getImsRegistrationState(int serial) {
        Log.d(mTag, "getImsRegistrationState");
        boolean isRegistered = false;
        int ratFamily = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getImsRegistrationStateResponse(rsp, isRegistered, ratFamily);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getImsRegistrationState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getNetworkSelectionMode(int serial) {
        Log.d(mTag, "getNetworkSelectionMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getNetworkSelectionModeResponse(rsp, mNetworkSelectionMode);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getNetworkSelectionMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getOperator(int serial) {
        Log.d(mTag, "getOperator");

        String longName = "";
        String shortName = "";
        String numeric = "";

        synchronized (mCacheUpdateMutex) {
            if (mServiceState.isInService()) {
                android.hardware.radio.network.OperatorInfo operatorInfo =
                        mServiceState.getPrimaryCellOperatorInfo();
                longName = operatorInfo.alphaLong;
                shortName = operatorInfo.alphaShort;
                numeric = operatorInfo.operatorNumeric;
            }
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getOperatorResponse(rsp, longName, shortName, numeric);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getOperator from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSignalStrength(int serial) {
        Log.d(mTag, "getSignalStrength");

        android.hardware.radio.network.SignalStrength signalStrength =
                new android.hardware.radio.network.SignalStrength();

        synchronized (mCacheUpdateMutex) {
            if (mServiceState.getIsHomeCamping()
                    && mRadioState == MockModemConfigInterface.RADIO_STATE_ON) {
                signalStrength = mServiceState.getSignalStrength();
            }
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getSignalStrengthResponse(rsp, signalStrength);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSignalStrength from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSystemSelectionChannels(int serial) {
        Log.d(mTag, "getSystemSelectionChannels");

        android.hardware.radio.network.RadioAccessSpecifier[] specifiers =
                new android.hardware.radio.network.RadioAccessSpecifier[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getSystemSelectionChannelsResponse(rsp, specifiers);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSystemSelectionChannels from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getVoiceRadioTechnology(int serial) {
        Log.d(mTag, "getVoiceRadioTechnology");
        int rat;

        synchronized (mCacheUpdateMutex) {
            rat = mServiceState.getRegistrationRat();
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getVoiceRadioTechnologyResponse(rsp, rat);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getVoiceRadioTechnology from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getVoiceRegistrationState(int serial) {
        Log.d(mTag, "getVoiceRegistrationState");

        android.hardware.radio.network.RegStateResult voiceRegResponse =
                new android.hardware.radio.network.RegStateResult();

        voiceRegResponse.cellIdentity = new android.hardware.radio.network.CellIdentity();
        voiceRegResponse.reasonForDenial = mServiceState.getRegFailCause();

        synchronized (mCacheUpdateMutex) {
            voiceRegResponse.regState =
                    mServiceState.getRegistration(android.hardware.radio.network.Domain.CS);
            voiceRegResponse.rat = mServiceState.getRegistrationRat();
            if (mServiceState.isInService()) {
                voiceRegResponse.registeredPlmn =
                        mServiceState.getPrimaryCellOperatorInfo().operatorNumeric;
            }

            voiceRegResponse.cellIdentity = mServiceState.getPrimaryCellIdentity();
        }

        // TODO: support accessTechnologySpecificInfo
        voiceRegResponse.accessTechnologySpecificInfo =
                android.hardware.radio.network.AccessTechnologySpecificInfo.noinit(true);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getVoiceRegistrationStateResponse(rsp, voiceRegResponse);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getVoiceRegistrationState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void isNrDualConnectivityEnabled(int serial) {
        Log.d(mTag, "isNrDualConnectivityEnabled");
        boolean isEnabled = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.isNrDualConnectivityEnabledResponse(rsp, isEnabled);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to isNrDualConnectivityEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
    }

    @Override
    public void setAllowedNetworkTypesBitmap(int serial, int networkTypeBitmap) {
        Log.d(mTag, "setAllowedNetworkTypesBitmap");
        boolean isModeChange = false;

        if (mNetworkTypeBitmap != networkTypeBitmap) {
            mNetworkTypeBitmap = networkTypeBitmap;
            synchronized (mCacheUpdateMutex) {
                isModeChange = mServiceState.updateHighestRegisteredRat(mNetworkTypeBitmap);
            }
            if (isModeChange) {
                mHandler.obtainMessage(EVENT_PREFERRED_MODE_CHANGED).sendToTarget();
            }
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.setAllowedNetworkTypesBitmapResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setAllowedNetworkTypesBitmap from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setBandMode(int serial, int mode) {
        Log.d(mTag, "setBandMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setBandModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setBandMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setBarringPassword(
            int serial, String facility, String oldPassword, String newPassword) {
        Log.d(mTag, "setBarringPassword");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setBarringPasswordResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setBarringPassword from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaRoamingPreference(int serial, int type) {
        Log.d(mTag, "setCdmaRoamingPreference");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setCdmaRoamingPreferenceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCdmaRoamingPreference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCellInfoListRate(int serial, int rate) {
        Log.d(mTag, "setCellInfoListRate");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setCellInfoListRateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setCellInfoListRate from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setIndicationFilter(int serial, int indicationFilter) {
        Log.d(mTag, "setIndicationFilter");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setIndicationFilterResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setIndicationFilter from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setLinkCapacityReportingCriteria(
            int serial,
            int hysteresisMs,
            int hysteresisDlKbps,
            int hysteresisUlKbps,
            int[] thresholdsDownlinkKbps,
            int[] thresholdsUplinkKbps,
            int accessNetwork) {
        Log.d(mTag, "setLinkCapacityReportingCriteria");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setLinkCapacityReportingCriteriaResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setLinkCapacityReportingCriteria from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setLocationUpdates(int serial, boolean enable) {
        Log.d(mTag, "setLocationUpdates");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setLocationUpdatesResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setLocationUpdates from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNetworkSelectionModeAutomatic(int serial) {
        Log.d(mTag, "setNetworkSelectionModeAutomatic");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNetworkSelectionModeAutomaticResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setNetworkSelectionModeAutomatic from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNetworkSelectionModeManual(int serial, String operatorNumeric, int ran) {
        Log.d(mTag, "setNetworkSelectionModeManual");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNetworkSelectionModeManualResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setNetworkSelectionModeManual from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNrDualConnectivityState(int serial, byte nrDualConnectivityState) {
        Log.d(mTag, "setNrDualConnectivityState");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNrDualConnectivityStateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setNrDualConnectivityState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setResponseFunctions(
            IRadioNetworkResponse radioNetworkResponse,
            IRadioNetworkIndication radioNetworkIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioNetworkResponse = radioNetworkResponse;
        mRadioNetworkIndication = radioNetworkIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void setSignalStrengthReportingCriteria(
            int serial, SignalThresholdInfo[] signalThresholdInfos) {
        Log.d(mTag, "setSignalStrengthReportingCriteria");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSignalStrengthReportingCriteriaResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSignalStrengthReportingCriteria from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSuppServiceNotifications(int serial, boolean enable) {
        Log.d(mTag, "setSuppServiceNotifications");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSuppServiceNotificationsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSuppServiceNotifications from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSystemSelectionChannels(
            int serial, boolean specifyChannels, RadioAccessSpecifier[] specifiers) {
        Log.d(mTag, "setSystemSelectionChannels");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSystemSelectionChannelsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setSystemSelectionChannels from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startNetworkScan(int serial, NetworkScanRequest request) {
        Log.d(mTag, "startNetworkScan");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.startNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopNetworkScan(int serial) {
        Log.d(mTag, "stopNetworkScan");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.stopNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to stopNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyNetworkDepersonalization(int serial, String netPin) {
        Log.d(mTag, "supplyNetworkDepersonalization");
        int remainingRetries = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.supplyNetworkDepersonalizationResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to supplyNetworkDepersonalization from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setUsageSetting(int serial, int usageSetting) {
        Log.d(mTag, "setUsageSetting");
        int remainingRetries = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setUsageSettingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setUsageSetting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getUsageSetting(int serial) {
        Log.d(mTag, "getUsageSetting");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getUsageSettingResponse(rsp, -1 /* Invalid value */);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getUsageSetting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setEmergencyMode(int serial, int emcModeType) {
        Log.d(TAG, "setEmergencyMode");

        EmergencyRegResult result;
        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyMode(emcModeType);
            result = mServiceState.getEmergencyRegResult();
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.setEmergencyModeResponse(rsp, result);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setEmergencyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void triggerEmergencyNetworkScan(int serial,
            android.hardware.radio.network.EmergencyNetworkScanTrigger request) {
        Log.d(TAG, "triggerEmergencyNetworkScan");

        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyNetworkScanTriggered(true,
                    request.accessNetwork, request.scanType);
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.triggerEmergencyNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to triggerEmergencyNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void cancelEmergencyNetworkScan(int serial, boolean resetScan) {
        Log.d(TAG, "cancelEmergencyNetworkScan");

        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyNetworkScanCanceled(true);
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.cancelEmergencyNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to cancelEmergencyNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void exitEmergencyMode(int serial) {
        Log.d(TAG, "exitEmergencyMode");

        synchronized (mCacheUpdateMutex) {
            mServiceState.setEmergencyMode(0);
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.exitEmergencyModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to exitEmergencyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNullCipherAndIntegrityEnabled(int serial, boolean isEnabled) {
        Log.d(TAG, "setNullCipherAndIntegrityEnabled");

        mNullCipherAndIntegrityEnabled = isEnabled;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.NONE);
        try {
            mRadioNetworkResponse.setNullCipherAndIntegrityEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setNullCipherAndIntegrityEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void isNullCipherAndIntegrityEnabled(int serial) {
        Log.d(TAG, "isNullCipherAndIntegrityEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.NONE);
        try {
            mRadioNetworkResponse.isNullCipherAndIntegrityEnabledResponse(rsp,
                    mNullCipherAndIntegrityEnabled);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to call isNullCipherAndIntegrityEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void isN1ModeEnabled(int serial) {
        Log.d(TAG, "isN1ModeEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.isN1ModeEnabledResponse(rsp, false);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to isN1ModeEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void setN1ModeEnabled(int serial, boolean enable) {
        Log.d(TAG, "setN1ModeEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setN1ModeEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setN1ModeEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void isCellularIdentifierTransparencyEnabled(int serial) {
        Log.d(TAG, "isCellularIdentifierTransparencyEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.isCellularIdentifierTransparencyEnabledResponse(rsp, false);
        } catch (RemoteException ex) {
            Log.e(
                TAG,
                "Failed to isCellularIdentifierTransparencyEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void setCellularIdentifierTransparencyEnabled(int serial, boolean enable) {
        Log.d(TAG, "setCellularIdentifierTransparencyEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setCellularIdentifierTransparencyEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(
                TAG,
                "Failed to setCellularIdentifierTransparencyEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void isSecurityAlgorithmsUpdatedEnabled(int serial) {
        Log.d(TAG, "isSecurityAlgorithmsUpdatedEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.isSecurityAlgorithmsUpdatedEnabledResponse(rsp, false);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to isSecurityAlgorithmsUpdatedEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public void setSecurityAlgorithmsUpdatedEnabled(int serial, boolean enable) {
        Log.d(TAG, "setSecurityAlgorithmsUpdatedEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSecurityAlgorithmsUpdatedEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSecurityAlgorithmsUpdatedEnabled from AIDL. Exception " + ex);
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioNetwork.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioNetwork.VERSION;
    }

    public void unsolNetworkStateChanged() {
        Log.d(mTag, "unsolNetworkStateChanged");

        // Notify other module
        notifyServiceStateChange();

        if (mRadioNetworkIndication != null) {
            try {
                mRadioNetworkIndication.networkStateChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to invoke networkStateChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioNetworkIndication");
        }
    }

    public void unsolCurrentSignalStrength() {
        Log.d(mTag, "unsolCurrentSignalStrength");
        if (mRadioState != MockModemConfigInterface.RADIO_STATE_ON) {
            return;
        }

        if (mRadioNetworkIndication != null) {
            android.hardware.radio.network.SignalStrength signalStrength =
                    new android.hardware.radio.network.SignalStrength();

            synchronized (mCacheUpdateMutex) {
                signalStrength = mServiceState.getSignalStrength();
            }

            try {
                mRadioNetworkIndication.currentSignalStrength(
                        RadioIndicationType.UNSOLICITED, signalStrength);
            } catch (RemoteException ex) {
                Log.e(
                        mTag,
                        "Failed to invoke currentSignalStrength change from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioNetworkIndication");
        }
    }

    public void unsolCellInfoList() {
        Log.d(mTag, "unsolCellInfoList");

        if (mRadioState != MockModemConfigInterface.RADIO_STATE_ON) {
            return;
        }

        if (mRadioNetworkIndication != null) {
            android.hardware.radio.network.CellInfo[] cells;

            synchronized (mCacheUpdateMutex) {
                cells = mServiceState.getCells();
            }
            try {
                mRadioNetworkIndication.cellInfoList(RadioIndicationType.UNSOLICITED, cells);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to invoke cellInfoList change from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioNetworkIndication");
        }
    }

    public boolean unsolBarringInfoChanged(
            SparseArray<android.telephony.BarringInfo.BarringServiceInfo> barringServiceInfos) {
        Log.d(mTag, "unsolBarringInfoChanged");

        if (mRadioState != MockModemConfigInterface.RADIO_STATE_ON) {
            Log.d(mTag, "unsolBarringInfoChanged radio is off");
            return false;
        }

        if (mRadioNetworkIndication != null) {
            CellIdentity cellIdentity = new CellIdentity();
            BarringInfo[] halBarringInfos = convertBarringInfo(barringServiceInfos);
            synchronized (mCacheUpdateMutex) {
                cellIdentity = mServiceState.getPrimaryCellIdentity();
                mServiceState.updateBarringInfos(halBarringInfos);
            }

            try {
                mRadioNetworkIndication.barringInfoChanged(RadioIndicationType.UNSOLICITED,
                        cellIdentity, halBarringInfos);
                return true;
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to invoke barringInfoChanged change from AIDL. Exception" + ex);
            }
        } else {
            Log.e(mTag, "null mRadioNetworkIndication");
        }
        return false;
    }

    public boolean unsolEmergencyNetworkScanResult(MockEmergencyRegResult regResult) {
        Log.d(TAG, "unsolEmergencyNetworkScanResult");

        if (mRadioState != MockModemConfigInterface.RADIO_STATE_ON) {
            return false;
        }

        if (mRadioNetworkIndication != null) {
            EmergencyRegResult result = convertEmergencyRegResult(regResult);

            synchronized (mCacheUpdateMutex) {
                mServiceState.setEmergencyRegResult(result);
            }

            try {
                mRadioNetworkIndication.emergencyNetworkScanResult(
                        RadioIndicationType.UNSOLICITED, result);
                return true;
            } catch (RemoteException ex) {
                Log.e(TAG,
                        "Failed to invoke emergencyNetworkScanResult change from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(TAG, "null mRadioNetworkIndication");
        }
        return false;
    }

    private static EmergencyRegResult convertEmergencyRegResult(MockEmergencyRegResult regResult) {

        EmergencyRegResult result = new EmergencyRegResult();

        result.accessNetwork = regResult.getAccessNetwork();
        result.regState = convertRegState(regResult.getRegState());
        result.emcDomain = regResult.getDomain();
        result.isVopsSupported = regResult.isVopsSupported();
        result.isEmcBearerSupported = regResult.isEmcBearerSupported();
        result.nwProvidedEmc = (byte) regResult.getNwProvidedEmc();
        result.nwProvidedEmf = (byte) regResult.getNwProvidedEmf();
        result.mcc = regResult.getMcc();
        result.mnc = regResult.getMnc();

        return result;
    }

    /**
     * Convert RegistrationState to RegState
     * @param regState Registration state
     * @return Converted registration state.
     */
    private static int convertRegState(@NetworkRegistrationInfo.RegistrationState int regState) {
        switch (regState) {
            case NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING:
                return RegState.NOT_REG_MT_NOT_SEARCHING_OP_EM;
            case NetworkRegistrationInfo.REGISTRATION_STATE_HOME:
                return RegState.REG_HOME;
            case NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING:
                return RegState.NOT_REG_MT_SEARCHING_OP_EM;
            case NetworkRegistrationInfo.REGISTRATION_STATE_DENIED:
                return RegState.REG_DENIED_EM;
            case NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN:
                return RegState.UNKNOWN_EM;
            case NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING:
                return RegState.REG_ROAMING;
            default:
                return RegState.NOT_REG_MT_NOT_SEARCHING_OP_EM;
        }
    }

    private BarringInfo[] convertBarringInfo(
            SparseArray<android.telephony.BarringInfo.BarringServiceInfo> barringServiceInfos) {
        ArrayList<BarringInfo> halBarringInfo = new ArrayList<>();

        for (int i = BarringInfo.SERVICE_TYPE_CS_SERVICE; i <= BarringInfo.SERVICE_TYPE_SMS; i++) {
            android.telephony.BarringInfo.BarringServiceInfo serviceInfo =
                    barringServiceInfos.get(i);
            if (serviceInfo != null) {
                BarringInfo barringInfo = new BarringInfo();
                barringInfo.serviceType = i;
                barringInfo.barringType = serviceInfo.getBarringType();
                barringInfo.barringTypeSpecificInfo = new BarringTypeSpecificInfo();
                barringInfo.barringTypeSpecificInfo.isBarred = serviceInfo.isConditionallyBarred();
                barringInfo.barringTypeSpecificInfo.factor =
                        serviceInfo.getConditionalBarringFactor();
                barringInfo.barringTypeSpecificInfo.timeSeconds =
                        serviceInfo.getConditionalBarringTimeSeconds();
                halBarringInfo.add(barringInfo);
            }
        }
        return halBarringInfo.toArray(new BarringInfo[0]);
    }

    /**
     * Waits for the event of network service.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     */
    public boolean waitForLatchCountdown(int latchIndex, int waitMs) {
        return mServiceState.waitForLatchCountdown(latchIndex, waitMs);
    }

    /**
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        mServiceState.resetAllLatchCountdown();
    }
}
