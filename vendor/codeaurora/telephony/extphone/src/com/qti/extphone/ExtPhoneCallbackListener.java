/*
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.CellInfo;
import android.util.Log;

import com.qti.extphone.BearerAllocationStatus;
import com.qti.extphone.DcParam;
import com.qti.extphone.DualDataRecommendation;
import com.qti.extphone.IExtPhoneCallback;
import com.qti.extphone.NetworkSelectionMode;
import com.qti.extphone.NrConfig;
import com.qti.extphone.NrConfigType;
import com.qti.extphone.NrIconType;
import com.qti.extphone.QRadioResponseInfo;
import com.qti.extphone.QtiPersoUnlockStatus;
import com.qti.extphone.SignalStrength;
import com.qti.extphone.Status;
import com.qti.extphone.Token;
import com.qti.extphone.UpperLayerIndInfo;

import java.lang.ref.WeakReference;
import java.util.List;

public class ExtPhoneCallbackListener {
    private static final String TAG = "ExtPhoneCallbackListener";
    private static final boolean DBG = true;

    public static final int EVENT_ALL = 0;
    public static final int EVENT_GET_FACILITY_LOCK_FOR_APP_RESPONSE = 1;
    public static final int EVENT_GET_NETWORK_SELECTION_MODE_RESPONSE = 2;
    public static final int EVENT_GET_QOS_PARAMETERS_RESPONSE =3 ;
    public static final int EVENT_GET_QTIRADIO_CAPABILITY_RESPONSE = 4;
    public static final int EVENT_GET_SECURE_MODE_STATUS_RESPONSE = 5;
    public static final int EVENT_NETWORK_SCAN_RESULT = 6;
    public static final int EVENT_ON_5G_CONFIG_INFO = 7;
    public static final int EVENT_ON_5G_STATUS = 8;
    public static final int EVENT_ON_ANY_NR_BEARER_ALLOCATION = 9;
    public static final int EVENT_ON_DATA_DEACTIVATE_DELAY_TIME = 10;
    public static final int EVENT_ON_DDS_SWITCH_CAPABILITY_CHANGE = 11;
    public static final int EVENT_ON_DDS_SWITCH_CRITERIA_CHANGE = 12;
    public static final int EVENT_ON_DDS_SWITCH_RECOMMENDATION = 13;
    public static final int EVENT_ON_ENABLE_ENDC = 14;
    public static final int EVENT_ON_ENDC_STATUS = 15;
    public static final int EVENT_ON_EPDG_OVER_CELLULAR_DATA_SUPPORTED = 16;
    public static final int EVENT_ON_IMEI_TYPE_CHANGED = 17;
    public static final int EVENT_ON_NR_CONFIG_STATUS = 18;
    public static final int EVENT_ON_NR_DC_PARAM = 19;
    public static final int EVENT_ON_NR_ICON_TYPE = 20;
    public static final int EVENT_ON_QOS_PARAMETERS_CHANGED = 21;
    public static final int EVENT_ON_SECURE_MODE_STATUS_CHANGE = 22;
    public static final int EVENT_ON_SEND_USER_PREFERENCE_FOR_DATA_DURING_VOICE_CALL = 23;
    public static final int EVENT_ON_SET_NR_CONFIG = 24;
    public static final int EVENT_ON_SIGNAL_STRENGTH = 25;
    public static final int EVENT_ON_SIM_TYPE_CHANGED = 26;
    public static final int EVENT_ON_UPPER_LAYER_IND_INFO = 27;
    public static final int EVENT_QUERY_CALL_FORWARD_STATUS_RESPONSE = 28;
    public static final int EVENT_SEND_CDMA_SMS_RESPONSE = 29;
    public static final int EVENT_SET_CARRIER_INFO_FOR_IMSI_ENCRYPTION_RESPONSE = 30;
    public static final int EVENT_SET_MSIM_PREFERENCE_RESPONSE = 31;
    public static final int EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_RESPONSE = 32;
    public static final int EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_RESPONSE = 33;
    public static final int EVENT_SET_SIM_TYPE_RESPONSE = 34;
    public static final int EVENT_SET_SMART_DDS_SWITCH_TOGGLE_RESPONSE = 35;
    public static final int EVENT_START_NETWORK_SCAN_RESPONSE = 36;
    public static final int EVENT_STOP_NETWORK_SCAN_RESPONSE = 37;
    public static final int EVENT_ON_CIWLAN_CAPABILITY_CHANGE = 38;
    public static final int EVENT_ON_DUAL_DATA_CAPABILITY_CHANGED = 39;
    public static final int EVENT_SET_DUAL_DATA_USER_PREFERENCE_RESPONSE = 40;
    public static final int EVENT_ON_DUAL_DATA_RECOMMENDATION = 41;
    public static final int EVENT_ON_SIM_PERSO_UNLOCK_STATUS_CHANGE = 42;

    private Handler mHandler;
    IExtPhoneCallback mCallback = new IExtPhoneCallbackStub(this);

    /**
     * Create the sHandlerThread per process.
     * sHandlerThread is destroyed when process dies.
     */
    private static HandlerThread sHandlerThread;
    private Looper mLooper;

    public ExtPhoneCallbackListener() {
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(TAG);
            sHandlerThread.start();
        }
        mLooper = sHandlerThread.getLooper();
        init();
    }

    public ExtPhoneCallbackListener(Looper looper) {
        mLooper = looper;
        init();
    }

    public void cleanUp() {
        mLooper = null;
    }

    private void init() {
        if (mLooper == null) {
            return;
        }
        mHandler = new Handler(mLooper) {
            public void handleMessage(Message msg) {
                Log.d(TAG, "handleMessage");
                if (DBG) {
                    Log.d(TAG, " what=0x" + Integer.toHexString(msg.what) + " msg=" + msg);
                }
                if (mLooper == null) {
                    Log.d(TAG, "Client is unregistered for events. msg=" + msg);
                    return;
                }
                switch (msg.what) {
                    case EVENT_GET_FACILITY_LOCK_FOR_APP_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.getFacilityLockForAppResponse(
                                    result.mStatus, (int[]) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_GET_FACILITY_LOCK_FOR_APP_RESPONSE : Exception = " +
                                    e);
                        }
                        break;
                    case EVENT_GET_NETWORK_SELECTION_MODE_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.getNetworkSelectionModeResponse(
                                    result.mSlotId, result.mToken, result.mStatus,
                                    (NetworkSelectionMode) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_GET_NETWORK_SELECTION_MODE_RESPONSE : Exception = " +
                                    e);
                        }
                        break;
                    case EVENT_GET_QOS_PARAMETERS_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.getQosParametersResponse(result.mSlotId,
                                    result.mToken, result.mStatus,
                                    (QosParametersResult) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_GET_QOS_PARAMETERS_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_GET_QTIRADIO_CAPABILITY_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.getQtiRadioCapabilityResponse(
                                    result.mSlotId, result.mToken, result.mStatus,
                                    (int) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_GET_QTIRADIO_CAPABILITY_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_GET_SECURE_MODE_STATUS_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.getSecureModeStatusResponse(result.mToken,
                                    result.mStatus, (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_GET_SECURE_MODE_STATUS_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_NETWORK_SCAN_RESULT:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.networkScanResult(result.mSlotId,
                                    result.mToken, result.mStatus.get(), result.mError,
                                    (List<CellInfo>) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_NETWORK_SCAN_RESULT : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_5G_CONFIG_INFO:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.on5gConfigInfo(result.mSlotId,
                                    result.mToken, result.mStatus, (NrConfigType) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_5G_CONFIG_INFO : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_5G_STATUS:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.on5gStatus(result.mSlotId, result.mToken,
                                    result.mStatus, (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_5G_STATUS : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_ANY_NR_BEARER_ALLOCATION:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onAnyNrBearerAllocation(result.mSlotId,
                                    result.mToken, result.mStatus,
                                    (BearerAllocationStatus) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_ANY_NR_BEARER_ALLOCATION : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DATA_DEACTIVATE_DELAY_TIME:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDataDeactivateDelayTime(result.mSlotId,
                                    (long) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DATA_DEACTIVATE_DELAY_TIME : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DDS_SWITCH_CAPABILITY_CHANGE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDdsSwitchCapabilityChange(
                                    result.mSlotId, result.mToken, result.mStatus,
                                    (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DDS_SWITCH_CAPABILITY_CHANGE : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DDS_SWITCH_CRITERIA_CHANGE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDdsSwitchCriteriaChange(result.mSlotId,
                                    (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DDS_SWITCH_CRITERIA_CHANGE : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DDS_SWITCH_RECOMMENDATION:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDdsSwitchRecommendation(result.mSlotId,
                                    (int) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DDS_SWITCH_RECOMMENDATION : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_ENABLE_ENDC:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onEnableEndc(result.mSlotId,
                                    result.mToken, result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_ENABLE_ENDC : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_ENDC_STATUS:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onEndcStatus(result.mSlotId,
                                    result.mToken, result.mStatus, (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_ENDC_STATUS : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_EPDG_OVER_CELLULAR_DATA_SUPPORTED:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onEpdgOverCellularDataSupported(
                                    result.mSlotId, (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_EPDG_OVER_CELLULAR_DATA_SUPPORTED : Exception = "
                                    + e);
                        }
                        break;
                    case EVENT_ON_IMEI_TYPE_CHANGED:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onImeiTypeChanged(
                                    (QtiImeiInfo[]) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_IMEI_TYPE_CHANGED : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_NR_CONFIG_STATUS:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onNrConfigStatus(result.mSlotId,
                                    result.mToken, result.mStatus, (NrConfig) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_NR_CONFIG_STATUS : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_NR_DC_PARAM:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onNrDcParam(result.mSlotId,
                                    result.mToken, result.mStatus, (DcParam) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_NR_DC_PARAM : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_NR_ICON_TYPE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onNrIconType(result.mSlotId,
                                    result.mToken, result.mStatus, (NrIconType) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_NR_ICON_TYPE : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_QOS_PARAMETERS_CHANGED:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onQosParametersChanged(result.mSlotId,
                                    result.mError, (QosParametersResult) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_QOS_PARAMETERS_CHANGED : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SECURE_MODE_STATUS_CHANGE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onSecureModeStatusChange(
                                    (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_SECURE_MODE_STATUS_CHANGE : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SEND_USER_PREFERENCE_FOR_DATA_DURING_VOICE_CALL:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.
                                    onSendUserPreferenceForDataDuringVoiceCall(result.mSlotId,
                                    result.mToken, result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_SEND_USER_PREFERENCE_FOR_DATA_DURING_VOICE_CALL : "
                                    + "Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SET_NR_CONFIG:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onSetNrConfig(result.mSlotId,
                                    result.mToken, result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_SET_NR_CONFIG : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SIGNAL_STRENGTH:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onSignalStrength(result.mSlotId,
                                    result.mToken, result.mStatus, (SignalStrength) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_SIGNAL_STRENGTH : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SIM_TYPE_CHANGED:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onSimTypeChanged(
                                    (QtiSimType[]) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_SIM_TYPE_CHANGED : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_UPPER_LAYER_IND_INFO:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onUpperLayerIndInfo(result.mSlotId,
                                    result.mToken, result.mStatus,
                                    (UpperLayerIndInfo) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_UPPER_LAYER_IND_INFO : Exception = " + e);
                        }
                        break;
                    case EVENT_QUERY_CALL_FORWARD_STATUS_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.queryCallForwardStatusResponse(
                                    result.mStatus, (QtiCallForwardInfo[]) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_QUERY_CALL_FORWARD_STATUS_RESPONSE : Exception = " +
                                    e);
                        }
                        break;
                    case EVENT_SEND_CDMA_SMS_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.sendCdmaSmsResponse(result.mSlotId,
                                    result.mToken, result.mStatus, (SmsResult) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SEND_CDMA_SMS_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_SET_CARRIER_INFO_FOR_IMSI_ENCRYPTION_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setCarrierInfoForImsiEncryptionResponse(
                                    result.mSlotId, result.mToken,
                                    (QRadioResponseInfo) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_CARRIER_INFO_FOR_IMSI_ENCRYPTION_RESPONSE : " +
                                    "Exception = " + e);
                        }
                        break;
                    case EVENT_SET_MSIM_PREFERENCE_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setMsimPreferenceResponse(result.mToken,
                                    result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_MSIM_PREFERENCE_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setNetworkSelectionModeAutomaticResponse(
                                    result.mSlotId, result.mToken, result.mError);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_RESPONSE : " +
                                    "Exception = " + e);
                        }
                        break;
                    case EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setNetworkSelectionModeManualResponse(
                                    result.mSlotId, result.mToken, result.mError);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_RESPONSE : " +
                                    "Exception = " + e);
                        }
                        break;
                    case EVENT_SET_SIM_TYPE_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setSimTypeResponse(result.mToken,
                                    result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_SIM_TYPE_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_SET_SMART_DDS_SWITCH_TOGGLE_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setSmartDdsSwitchToggleResponse(
                                    result.mToken, (boolean) result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_SMART_DDS_SWITCH_TOGGLE_RESPONSE : Exception = " +
                                    e);
                        }
                        break;
                    case EVENT_START_NETWORK_SCAN_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                        ExtPhoneCallbackListener.this.startNetworkScanResponse(result.mSlotId,
                                result.mToken, result.mError);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_START_NETWORK_SCAN_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_STOP_NETWORK_SCAN_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.stopNetworkScanResponse(result.mSlotId,
                                    result.mToken, result.mError);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_STOP_NETWORK_SCAN_RESPONSE : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DUAL_DATA_CAPABILITY_CHANGED:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDualDataCapabilityChanged(
                                    result.mToken, result.mStatus, (boolean)result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DUAL_DATA_CAPABILITY_CHANGED : Exception = " + e);
                        }
                        break;
                    case EVENT_SET_DUAL_DATA_USER_PREFERENCE_RESPONSE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.setDualDataUserPreferenceResponse(
                                    result.mToken, result.mStatus);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_SET_DUAL_DATA_USER_PREFERENCE_RESPONSE :" +
                                    "Exception = " + e);
                        }
                        break;
                    case EVENT_ON_DUAL_DATA_RECOMMENDATION:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onDualDataRecommendation(
                                    (DualDataRecommendation)result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG, "EVENT_ON_DUAL_DATA_RECOMMENDATION : Exception = " + e);
                        }
                        break;
                    case EVENT_ON_SIM_PERSO_UNLOCK_STATUS_CHANGE:
                        try {
                            IExtPhoneCallbackStub.Result result =
                                    (IExtPhoneCallbackStub.Result) msg.obj;
                            ExtPhoneCallbackListener.this.onSimPersoUnlockStatusChange(
                                    result.mSlotId, (QtiPersoUnlockStatus)result.mData);
                        } catch (RemoteException e) {
                            Log.e(TAG,
                                    "EVENT_ON_SIM_PERSO_UNLOCK_STATUS_CHANGE : Exception = " + e);
                        }
                        break;
                    default :
                        Log.d(TAG, "default : " + msg.what);
                }
            }
        };
    }

    public void onNrIconType(int slotId, Token token, Status status, NrIconType nrIconType) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrIconType: slotId = " + slotId + " token = " + token +
                " status = " + status + " NrIconType = " + nrIconType);
    }

    public void onEnableEndc(int slotId, Token token, Status status) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onEnableEndc: slotId = " + slotId + " token = " + token +
                " status = " + status);
    }

    public void onEndcStatus(int slotId, Token token, Status status, boolean enableStatus) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onEndcStatus: slotId = " + slotId + " token = " + token +
                " status = " + status + " enableStatus = " + enableStatus);
    }

    public void onSetNrConfig(int slotId, Token token, Status status) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSetNrConfig: slotId = " + slotId + " token = " + token +
                " status = " +status);
    }

    public void onNrConfigStatus(int slotId, Token token, Status status, NrConfig nrConfig) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrConfigStatus: slotId = " + slotId + " token = " + token +
                " status = " + status + " NrConfig = " + nrConfig);
    }

    public void sendCdmaSmsResponse(int slotId, Token token, Status status, SmsResult sms) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: sendCdmaSmsResponse: slotId = " + slotId +
                " token = " + token + " status = " + status + " SmsResult = " + sms);
    }

    public void on5gStatus(int slotId, Token token, Status status, boolean enableStatus) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: on5gStatus: slotId = " + slotId + " token = " + token +
                " status" + status + " enableStatus = " + enableStatus);
    }

    public void onAnyNrBearerAllocation(int slotId, Token token, Status status,
            BearerAllocationStatus bearerStatus) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onAnyNrBearerAllocation: slotId = " + slotId +
                " token = " + token + " status = " + status + " bearerStatus = " + bearerStatus);
    }

    public void getQtiRadioCapabilityResponse(int slotId, Token token, Status status, int raf)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getQtiRadioCapabilityResponse: slotId = " + slotId +
                " token = " + token + " status" + status + " raf = " + raf);
    }

    public void getQosParametersResponse(int slotId, Token token, Status status,
                QosParametersResult result) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getQosParametersResponse: slotId = " + slotId +
                " token = " + token + " status" + status + " result = " + result);
    }

    public void onNrDcParam(int slotId, Token token, Status status, DcParam dcParam) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrDcParam: slotId = " + slotId +
                " token = " + token + " status" + status + " dcParam = " + dcParam);
    }

    public void onUpperLayerIndInfo(int slotId, Token token, Status status,
            UpperLayerIndInfo uilInfo) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onUpperLayerIndInfo: slotId = " + slotId +
                " token = " + token + " " + "status" + status +
                " UpperLayerIndInfo = " + uilInfo);
    }

    public void on5gConfigInfo(int slotId, Token token, Status status, NrConfigType nrConfigType)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: on5gConfigInfo: slotId = " + slotId + " token = " +
                token + " " + "status" + status + " NrConfigType = " + nrConfigType);
    }

    public void onSignalStrength(int slotId, Token token, Status status,
            SignalStrength signalStrength) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSignalStrength: slotId = " + slotId +
                " token = " + token + " " + "status" + status +
                " signalStrength = " + signalStrength);
    }

    public void setCarrierInfoForImsiEncryptionResponse(int slotId, Token token,
            QRadioResponseInfo info) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setCarrierInfoForImsiEncryptionResponse: slotId = " + slotId +
                " token = " + token + " info = " + info);
    }

    public void queryCallForwardStatusResponse(Status status, QtiCallForwardInfo[] infos) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: queryCallForwardStatusResponse: status = " + status +
                " CallForwardInfo = " + infos);
    }

    public void getFacilityLockForAppResponse(Status status, int[] response) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getFacilityLockForAppResponse: status = " + status +
                " response = " + response);
    }

    public void setSmartDdsSwitchToggleResponse(Token token, boolean result) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setSmartDdsSwitchToggleResponse: token = " + token +
                " result = " + result);
    }

    public void onImeiTypeChanged(QtiImeiInfo[] imeiInfo) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onImeiTypeChanged: imeiInfo = " + imeiInfo);
    }

    public void onSendUserPreferenceForDataDuringVoiceCall(int slotId, Token token, Status status)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSendUserPreferenceForDataDuringVoiceCall: slotId = " + slotId +
                " token = " + token + " status = " + status);
    }

    public void onDdsSwitchCapabilityChange(int slotId, Token token, Status status, boolean support)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDdsSwitchCapabilityChange: slotId = " + slotId + " token = " +
                token + " status = " + status + " support = " + support);
    }

    public void onDdsSwitchCriteriaChange(int slotId, boolean telephonyDdsSwitch)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDdsSwitchCriteriaChange: slotId = " + slotId +
                " telephonyDdsSwitch = " + telephonyDdsSwitch);
    }

    public void onDdsSwitchRecommendation(int slotId, int recommendedSlotId)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDdsSwitchRecommendation: slotId = " + slotId +
                " recommendedSlotId = " + recommendedSlotId);
    }

    public void onDataDeactivateDelayTime(int slotId, long delayTimeMilliSecs)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDataDeactivateDelayTime: slotId = " + slotId +
                " delayTimeMilliSecs = " + delayTimeMilliSecs);
    }

    public void onEpdgOverCellularDataSupported(int slotId, boolean support)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onEpdgOverCellularDataSupported: slotId = " + slotId +
                " support = " + support);
    }

    public void getSecureModeStatusResponse(Token token, Status status, boolean enableStatus)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getSecureModeStatusResponse: token = " + token + " status = " +
                status + " enableStatus = " + enableStatus);
    }

    public void onSecureModeStatusChange(boolean enabled) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSecureModeStatusChange: enabled = " + enabled);
    }

    public void startNetworkScanResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: startNetworkScanResponse: slotId = " + slotId +
                " token = " + token + " errorCode = " + errorCode);
    }

    public void stopNetworkScanResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: stopNetworkScanResponse: slotId = " + slotId +
                " token = " + token + " errorCode = " + errorCode);
    }

    public void setNetworkSelectionModeManualResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setNetworkSelectionModeManualResponse: slotId = " + slotId +
                " token = " + token + " errorCode = " + errorCode);
    }

    public void setNetworkSelectionModeAutomaticResponse(int slotId, Token token, int errorCode)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setNetworkSelectionModeAutomaticResponse: slotId = " + slotId +
                " token = " + token + " errorCode = " + errorCode);
    }

    public void getNetworkSelectionModeResponse(int slotId, Token token, Status status,
            NetworkSelectionMode modes) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getNetworkSelectionModeResponse: slotId = " + slotId +
                " token = " + token + " status = " + status + " modes = " + modes);
    }

    public void networkScanResult(int slotId, Token token, int status, int error,
            List<CellInfo> cellInfos) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: networkScanResult: slotId = " + slotId +
                " token = " + token + " status = " + status + " error = " + error +
                " cellInfos = " + cellInfos);
    }

    public void setMsimPreferenceResponse(Token token, Status status) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setMsimPreferenceResponse: token = " + token +
                " status = " + status);
    }

    public void onQosParametersChanged(int slotId, int cid, QosParametersResult result)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onQosParametersChanged: slotId = " + slotId +
                " cid = " + cid + " result = " + result);
    }

    public void setSimTypeResponse(Token token, Status status) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setSimTypeResponse: token = " + token + " status = " + status);
    }

    public void onSimTypeChanged(QtiSimType[] simtype) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSimTypeChanged: simtype = " + simtype);
    }

    public void onDualDataCapabilityChanged(Token token, Status status, boolean support)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDualDataCapabilityChanged: support = " + support);
    }

    public void setDualDataUserPreferenceResponse(Token token, Status status)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setDualDataUserPreferenceResponse: token = "
                + token + " status = " + status);
    }

    public void onDualDataRecommendation(DualDataRecommendation rec)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onDualDataRecommendation: rec = " + rec);
    }

    public void onSimPersoUnlockStatusChange(int slotId, QtiPersoUnlockStatus persoUnlockStatus)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSimPersoUnlockStatusChange: slotId = "
                + slotId + " persoUnlockStatus = " + persoUnlockStatus);
    }

    private static class IExtPhoneCallbackStub extends IExtPhoneCallback.Stub {
        private WeakReference<ExtPhoneCallbackListener> mExtPhoneCallbackListenerWeakRef;

        public IExtPhoneCallbackStub(ExtPhoneCallbackListener extPhoneCallbackListener) {
            mExtPhoneCallbackListenerWeakRef =
                    new WeakReference<ExtPhoneCallbackListener>(extPhoneCallbackListener);
        }

        private void send(int what, int arg1, int arg2, Object obj) {
            ExtPhoneCallbackListener listener = mExtPhoneCallbackListenerWeakRef.get();
            if (listener != null) {
                Message.obtain(listener.mHandler, what, arg1, arg2, obj).sendToTarget();
            } else {
                if (DBG) {
                    Log.d(TAG, " listener is null");
                }
            }
        }

        @Override
        public void onNrIconType(int slotId, Token token, Status status, NrIconType nrIconType)
                throws RemoteException {
            send(EVENT_ON_NR_ICON_TYPE, 0, 0, new Result(slotId, token, status, 0, nrIconType));
        }

        @Override
        public void onEnableEndc(int slotId, Token token, Status status) throws RemoteException {
            send(EVENT_ON_ENABLE_ENDC, 0, 0, new Result(slotId, token, status, 0, null));
        }

        @Override
        public void onEndcStatus(int slotId, Token token, Status status, boolean enableStatus)
                throws RemoteException {
            send(EVENT_ON_ENDC_STATUS, 0, 0, new Result(slotId, token, status, 0, enableStatus));
        }

        @Override
        public void onSetNrConfig(int slotId, Token token, Status status) throws RemoteException {
            send(EVENT_ON_SET_NR_CONFIG, 0, 0, new Result(slotId, token, status, 0, null));
        }

        @Override
        public void onNrConfigStatus(int slotId, Token token, Status status, NrConfig nrConfig)
                throws RemoteException {
            send(EVENT_ON_NR_CONFIG_STATUS, 0, 0, new Result(slotId, token, status, 0, nrConfig));
        }

        @Override
        public void sendCdmaSmsResponse(int slotId, Token token, Status status, SmsResult sms)
                throws RemoteException {
            send(EVENT_SEND_CDMA_SMS_RESPONSE, 0, 0, new Result(slotId, token, status, 0, sms));
        }

        @Override
        public void on5gStatus(int slotId, Token token, Status status, boolean enableStatus) throws
                RemoteException {
            send(EVENT_ON_5G_STATUS, 0, 0, new Result(slotId, token, status, 0, enableStatus));
        }

        @Override
        public void onAnyNrBearerAllocation(int slotId, Token token, Status status,
                BearerAllocationStatus bearerStatus) throws RemoteException {
            send(EVENT_ON_ANY_NR_BEARER_ALLOCATION, 0, 0, new Result(slotId, token, status, 0,
                    bearerStatus));
        }

        @Override
        public void getQtiRadioCapabilityResponse(int slotId, Token token, Status status, int raf)
                throws RemoteException {
            send(EVENT_GET_QTIRADIO_CAPABILITY_RESPONSE, 0, 0, new Result(slotId, token, status, 0,
                    raf));
        }

        @Override
        public void getQosParametersResponse(int slotId, Token token, Status status,
                    QosParametersResult result) throws RemoteException {
            send(EVENT_GET_QOS_PARAMETERS_RESPONSE, 0, 0, new Result(slotId, token, status, 0,
                    result));
        }

        public void onNrDcParam(int slotId, Token token, Status status, DcParam dcParam) throws
                RemoteException {
            send(EVENT_ON_NR_DC_PARAM, 0, 0, new Result(slotId, token, status, 0, dcParam));
        }

        @Override
        public void onUpperLayerIndInfo(int slotId, Token token, Status status,
                UpperLayerIndInfo uilInfo) throws RemoteException {
            send(EVENT_ON_UPPER_LAYER_IND_INFO, 0, 0, new Result(slotId, token, status, 0,
                    uilInfo));
        }

        @Override
        public void on5gConfigInfo(int slotId, Token token, Status status,
                NrConfigType nrConfigType) throws RemoteException {
            send(EVENT_ON_5G_CONFIG_INFO, 0, 0, new Result(slotId, token, status, 0,
                    nrConfigType));
        }

        @Override
        public void onSignalStrength(int slotId, Token token, Status status,
                SignalStrength signalStrength) throws RemoteException {
            send(EVENT_ON_SIGNAL_STRENGTH, 0, 0, new Result(slotId, token, status, 0,
                    signalStrength));
        }

        @Override
        public void setCarrierInfoForImsiEncryptionResponse(int slotId, Token token,
                QRadioResponseInfo info) throws RemoteException {
            send(EVENT_SET_CARRIER_INFO_FOR_IMSI_ENCRYPTION_RESPONSE, 0, 0, new Result(slotId,
                    token, null, 0, info));
        }

        @Override
        public void queryCallForwardStatusResponse(Status status, QtiCallForwardInfo[] infos) throws
                RemoteException {
            send(EVENT_QUERY_CALL_FORWARD_STATUS_RESPONSE, 0, 0, new Result(0, null, status, 0,
                    infos));
        }

        @Override
        public void getFacilityLockForAppResponse(Status status, int[] response) throws
                RemoteException {
            send(EVENT_GET_FACILITY_LOCK_FOR_APP_RESPONSE, 0, 0, new Result(0, null, status, 0,
                    response));
        }

        @Override
        public void setSmartDdsSwitchToggleResponse(Token token, boolean result) throws
                RemoteException {
            send(EVENT_SET_SMART_DDS_SWITCH_TOGGLE_RESPONSE, 0, 0, new Result(0, token, null, 0,
                    result));
        }

        @Override
        public void onImeiTypeChanged(QtiImeiInfo[] imeiInfo) throws RemoteException {
            send(EVENT_ON_IMEI_TYPE_CHANGED, 0, 0, new Result(-1 , null, null, -1, imeiInfo));
        }

        public void onSendUserPreferenceForDataDuringVoiceCall(int slotId, Token token,
                Status status) throws RemoteException {
            send(EVENT_ON_SEND_USER_PREFERENCE_FOR_DATA_DURING_VOICE_CALL, 0, 0, new Result(slotId,
                    token, status, 0, null));
        }

        @Override
        public void onDdsSwitchCapabilityChange(int slotId, Token token, Status status,
                boolean support) throws RemoteException {
            send(EVENT_ON_DDS_SWITCH_CAPABILITY_CHANGE, 0, 0, new Result(slotId, token, status, 0,
                    support));
        }

        @Override
        public void onDdsSwitchCriteriaChange(int slotId, boolean telephonyDdsSwitch)
                throws RemoteException {
            send(EVENT_ON_DDS_SWITCH_CRITERIA_CHANGE, 0, 0,
                    new Result(slotId , null, null, -1, telephonyDdsSwitch));
        }

        @Override
        public void onDdsSwitchRecommendation(int slotId, int recommendedSlotId)
                throws RemoteException {
            send(EVENT_ON_DDS_SWITCH_RECOMMENDATION, 0, 0,
                    new Result(slotId , null, null, -1, recommendedSlotId));
        }

        @Override
        public void onDataDeactivateDelayTime(int slotId, long delayTimeMilliSecs)
                throws RemoteException {
            send(EVENT_ON_DATA_DEACTIVATE_DELAY_TIME, 0, 0,
                    new Result(slotId , null, null, -1, delayTimeMilliSecs));
        }

        @Override
        public void onEpdgOverCellularDataSupported(int slotId, boolean support)
                throws RemoteException {
            send(EVENT_ON_EPDG_OVER_CELLULAR_DATA_SUPPORTED, 0, 0,
                    new Result(slotId , null, null, -1, support));
        }

        @Override
        public void getSecureModeStatusResponse(Token token, Status status, boolean enableStatus)
                throws RemoteException {
            send(EVENT_GET_SECURE_MODE_STATUS_RESPONSE, 0, 0, new Result(0, token, status, 0,
                    enableStatus));
        }

        @Override
        public void onSecureModeStatusChange(boolean enabled) throws RemoteException {
            send(EVENT_ON_SECURE_MODE_STATUS_CHANGE, 0, 0, new Result(-1, null, null, -1, enabled));
        }

        @Override
        public void startNetworkScanResponse(int slotId, Token token, int errorCode) throws
                RemoteException {
            send(EVENT_START_NETWORK_SCAN_RESPONSE, 0, 0, new Result(slotId, token, null, errorCode,
                    null));
        }

        @Override
        public void stopNetworkScanResponse(int slotId, Token token, int errorCode) throws
                RemoteException {
            send(EVENT_STOP_NETWORK_SCAN_RESPONSE, 0, 0, new Result(slotId, token, null, errorCode,
                    null));
        }

        @Override
        public void setNetworkSelectionModeManualResponse(int slotId, Token token, int errorCode)
                throws RemoteException {
            send(EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_RESPONSE, 0, 0, new Result(slotId, token,
                    null, errorCode, null));
        }

        @Override
        public void setNetworkSelectionModeAutomaticResponse(int slotId, Token token, int errorCode)
                throws RemoteException {
            send(EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_RESPONSE, 0, 0, new Result(slotId,
                    token, null, errorCode, null));
        }

        @Override
        public void getNetworkSelectionModeResponse(int slotId, Token token, Status status,
                NetworkSelectionMode modes) throws RemoteException {
            send(EVENT_GET_NETWORK_SELECTION_MODE_RESPONSE, 0, 0, new Result(slotId, token,
                    status, 0, modes));
        }

        @Override
        public void networkScanResult(int slotId, Token token, int status, int error,
                List<CellInfo> cellInfos) throws RemoteException {
            send(EVENT_NETWORK_SCAN_RESULT, 0, 0, new Result(slotId, token, new Status(status),
                    error, cellInfos));
        }

        @Override
        public void setMsimPreferenceResponse(Token token, Status status) throws RemoteException {
            send(EVENT_SET_MSIM_PREFERENCE_RESPONSE, 0, 0, new Result(0, token, status, 0, null));
        }

        @Override
        public void onQosParametersChanged(int slotId, int cid, QosParametersResult result)
                throws RemoteException {
            send(EVENT_ON_QOS_PARAMETERS_CHANGED, 0, 0,
                    new Result(slotId, null, null, cid, result));
        }

        @Override
        public void setSimTypeResponse(Token token, Status status) throws RemoteException {
            send(EVENT_SET_SIM_TYPE_RESPONSE, 0, 0, new Result(0, token, status, 0, null));
        }

        @Override
        public void onSimTypeChanged(QtiSimType[] simtype) throws RemoteException {
            send(EVENT_ON_SIM_TYPE_CHANGED, 0, 0, new Result(-1, null, null, -1, simtype));
        }

        @Override
        public void onDualDataCapabilityChanged(Token token, Status status, boolean support)
                throws RemoteException {
            send(EVENT_ON_DUAL_DATA_CAPABILITY_CHANGED, 0, 0,
                    new Result(-1, token, status, -1, support));
        }

        @Override
        public void setDualDataUserPreferenceResponse(Token token, Status status)
                throws RemoteException {
            send(EVENT_SET_DUAL_DATA_USER_PREFERENCE_RESPONSE, 0, 0,
                    new Result(-1, token, status, -1, null));
        }

        @Override
        public void onDualDataRecommendation(DualDataRecommendation rec)
                throws RemoteException {
            send(EVENT_ON_DUAL_DATA_RECOMMENDATION, 0, 0,
                    new Result(-1, null, null, -1, rec));
        }

        @Override
        public void onSimPersoUnlockStatusChange(int slotId, QtiPersoUnlockStatus persoUnlockStatus)
                throws RemoteException {
            send(EVENT_ON_SIM_PERSO_UNLOCK_STATUS_CHANGE, 0, 0,
                    new Result(slotId, null, null, -1, persoUnlockStatus));
        }

        class Result {
            int mSlotId;
            Token mToken;
            Status mStatus;
            int mError;
            Object mData;

            public Result(int mSlotId, Token mToken, Status mStatus, int mError, Object mData) {
                this.mSlotId = mSlotId;
                this.mToken = mToken;
                this.mStatus = mStatus;
                this.mError = mError;
                this.mData = mData;
            }

            @Override
            public String toString() {
                return "Result{" + "mSlotId=" + mSlotId + ", mToken=" + mToken + ", mStatus=" +
                        mStatus + ", mError=" + mError + ", mData=" + mData + "}";
            }
        }
    }
}
