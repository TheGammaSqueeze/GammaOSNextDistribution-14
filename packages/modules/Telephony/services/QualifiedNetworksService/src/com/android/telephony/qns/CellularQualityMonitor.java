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
package com.android.telephony.qns;

import static android.telephony.CellInfo.UNAVAILABLE;

import android.annotation.NonNull;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * This class manages cellular threshold information registered from AccessNetworkEvaluator. It
 * extends QualityMonitor class to implement and notify the signal changes in Cellular RAT.
 */
class CellularQualityMonitor extends QualityMonitor {

    private static final int MAX_THRESHOLD_COUNT =
            SignalThresholdInfo.MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED;
    private final String mTag;
    private TelephonyManager mTelephonyManager;
    private QnsCarrierConfigManager mConfigManager;
    private int mSubId;
    private final int mSlotIndex;
    private boolean mIsQnsListenerRegistered;
    private final List<SignalThresholdInfo> mSignalThresholdInfoList;
    private final HandlerThread mHandlerThread;

    /**
     * thresholdMatrix stores the thresholds according to measurement type and netCapability. For
     * ex: LTE_RSRP: {TYPE_IMS: [-112, -110, -90], TYPE_XCAP: [-100, -99]} LTE_RSSNR:{TYPE_IMS:
     * [-10, -15], TYPE_EMERGENCY: [-15]}
     */
    private final ConcurrentHashMap<String, SparseArray<List<Integer>>> mThresholdMatrix =
            new ConcurrentHashMap<>();

    private final HashMap<String, int[]> mThresholdsRegistered = new HashMap<>();
    private HashMap<String, Integer> mThresholdWaitTimer = new HashMap<>();
    private SignalStrengthUpdateRequest mSSUpdateRequest;
    private final CellularSignalStrengthListener mSignalStrengthListener;
    private final QnsTelephonyListener mQnsTelephonyListener;
    @VisibleForTesting final Handler mHandler;
    /**
     * Constructor to instantiate CellularQualityMonitor
     *
     * @param context application context
     * @param listener QnsTelephonyListener instance
     * @param slotIndex slot index
     */
    CellularQualityMonitor(Context context,
            QnsCarrierConfigManager configMgr,
            QnsTelephonyListener listener,
            int slotIndex) {
        super(QualityMonitor.class.getSimpleName() + "-C-" + slotIndex);
        mContext = context;
        mSlotIndex = slotIndex;
        mQnsTelephonyListener = listener;

        mTag = CellularQualityMonitor.class.getSimpleName() + "-" + mSlotIndex;
        mSubId = QnsUtils.getSubId(mContext, mSlotIndex);
        mIsQnsListenerRegistered = false;
        mSignalThresholdInfoList = new ArrayList<>();
        mHandlerThread = new HandlerThread(mTag);
        mHandlerThread.start();
        mHandler = new CellularEventsHandler(mHandlerThread.getLooper());
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mQnsTelephonyListener.registerSubscriptionIdListener(
                mHandler, EVENT_SUBSCRIPTION_ID_CHANGED, null);
        if (mTelephonyManager != null) {
            mTelephonyManager = mTelephonyManager.createForSubscriptionId(mSubId);
        } else {
            Log.e(mTag, "Failed to get Telephony Service");
        }
        mConfigManager = configMgr;
        mSignalStrengthListener = new CellularSignalStrengthListener(mContext.getMainExecutor());
        mSignalStrengthListener.setSignalStrengthListener(this::onSignalStrengthsChanged);
    }

    /** Listener for change of signal strength. */
    private interface OnSignalStrengthListener {
        /** Notify the cellular signal strength changed. */
        void onSignalStrengthsChanged(SignalStrength signalStrength);
    }

    /** {@link TelephonyCallback} to listen to Cellular Service State Changed. */
    private class CellularSignalStrengthListener extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener {
        private OnSignalStrengthListener mSignalStrengthListener;
        private Executor mExecutor;

        CellularSignalStrengthListener(Executor executor) {
            super();
            mExecutor = executor;
        }

        void setSignalStrengthListener(OnSignalStrengthListener listener) {
            mSignalStrengthListener = listener;
        }

        /** Register a TelephonyCallback for this listener. */
        void register() {
            long identity = Binder.clearCallingIdentity();
            try {
                mTelephonyManager.registerTelephonyCallback(mExecutor, this);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /** Unregister a TelephonyCallback for this listener. */
        void unregister() {
            mTelephonyManager.unregisterTelephonyCallback(this);
        }

        @Override
        public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
            if (mSignalStrengthListener != null) {
                Log.d(mTag, "Signal Strength Changed : " + signalStrength);
                mSignalStrengthListener.onSignalStrengthsChanged(signalStrength);
            }
        }
    }

    private void onSignalStrengthsChanged(SignalStrength signalStrength) {
        List<CellSignalStrength> ss = signalStrength.getCellSignalStrengths();
        if (!ss.isEmpty()) {
            for (CellSignalStrength cs : ss) {
                checkAndNotifySignalStrength(cs);
            }
        }
    }

    private void checkAndNotifySignalStrength(CellSignalStrength cellSignalStrength) {
        Log.d(mTag, "CellSignalStrength Changed: " + cellSignalStrength);

        int signalStrength;
        for (Map.Entry<String, List<Threshold>> entry : mThresholdsList.entrySet()) {
            // check if key is in waiting list of backhaul
            if (mWaitingThresholds.getOrDefault(entry.getKey(), false)) {
                Log.d(mTag, "Backhaul timer already running for the threshold");
                continue;
            }
            List<Threshold> matchedThresholds = new ArrayList<>();
            Threshold threshold;
            for (Threshold th : entry.getValue()) {
                signalStrength =
                        getSignalStrength(
                                th.getAccessNetwork(), th.getMeasurementType(), cellSignalStrength);
                if (signalStrength != UNAVAILABLE && th.isMatching(signalStrength)) {
                    threshold = th.copy();
                    threshold.setThreshold(signalStrength);
                    matchedThresholds.add(threshold);
                }
            }
            if (matchedThresholds.size() > 0) {
                notifyThresholdChange(entry.getKey(), matchedThresholds.toArray(new Threshold[0]));
            }
        }
    }

    @Override
    synchronized int getCurrentQuality(int accessNetwork, int measurementType) {
        SignalStrength ss = mTelephonyManager.getSignalStrength();
        int quality = SignalStrength.INVALID; // Int Max Value
        if (ss != null) {
            List<CellSignalStrength> cellSignalStrengthList = ss.getCellSignalStrengths();
            for (CellSignalStrength cs : cellSignalStrengthList) {
                quality = getSignalStrength(accessNetwork, measurementType, cs);
                if (quality != UNAVAILABLE) {
                    return quality;
                }
            }
        }
        return quality;
    }

    @Override
    synchronized void registerThresholdChange(
            ThresholdCallback thresholdCallback,
            int netCapability,
            Threshold[] ths,
            int slotIndex) {
        Log.d(mTag, "registerThresholdChange for netCapability= " + netCapability);
        super.registerThresholdChange(thresholdCallback, netCapability, ths, slotIndex);
        updateThresholdsForNetCapability(netCapability, slotIndex, ths);
    }

    @Override
    synchronized void unregisterThresholdChange(int netCapability, int slotIndex) {
        Log.d(mTag, "unregisterThresholdChange for netCapability= " + netCapability);
        super.unregisterThresholdChange(netCapability, slotIndex);
        updateThresholdsMatrix(netCapability, null);
        if (updateRegisteredThresholdsArray()) {
            createSignalThresholdsInfoList();
            listenRequests();
        }
    }

    @Override
    synchronized void updateThresholdsForNetCapability(
            int netCapability, int slotIndex, Threshold[] ths) {
        Log.d(mTag, "updateThresholdsForNetCapability for netCapability= " + netCapability);
        super.updateThresholdsForNetCapability(netCapability, slotIndex, ths);
        if (ths != null && ths.length > 0 && !validateThresholdList(ths)) {
            throw new IllegalStateException("Thresholds are not in valid range.");
        }
        updateThresholdsMatrix(netCapability, ths);
        if (updateRegisteredThresholdsArray()) {
            createSignalThresholdsInfoList();
            listenRequests();
        }
    }

    @Override
    protected void notifyThresholdChange(String key, Threshold[] ths) {
        IThresholdListener listener = mThresholdCallbackMap.get(key);
        Log.d(mTag, "Notify Threshold Change to listener = " + listener);
        if (listener != null) {
            listener.onCellularThresholdChanged(ths);
        }
    }

    private void createSignalThresholdsInfoList() {
        mSignalThresholdInfoList.clear();
        for (Map.Entry<String, int[]> entry : mThresholdsRegistered.entrySet()) {
            if (entry.getValue().length == 0) continue;
            int networkType = Integer.parseInt(entry.getKey().split("_")[0]);
            int measurementType = Integer.parseInt(entry.getKey().split("_")[1]);
            SignalThresholdInfo.Builder builder =
                    new SignalThresholdInfo.Builder()
                            .setRadioAccessNetworkType(networkType)
                            .setSignalMeasurementType(measurementType)
                            .setThresholds(entry.getValue());
            int backhaulTime = mThresholdWaitTimer.getOrDefault(entry.getKey(), -1);
            if (backhaulTime > 0) {
                builder.setHysteresisMs(backhaulTime);
            }
            int hysteresisDb = mConfigManager.getWwanHysteresisDbLevel(networkType,
                    measurementType);
            builder.setHysteresisDb(hysteresisDb);
            mSignalThresholdInfoList.add(builder.build());
            Log.d(mTag, "Updated SignalThresholdInfo List: " + mSignalThresholdInfoList);
        }
    }

    private boolean updateRegisteredThresholdsArray() {
        boolean isUpdated = false;
        for (Map.Entry<String, SparseArray<List<Integer>>> entry : mThresholdMatrix.entrySet()) {
            SparseArray<List<Integer>> netCapabilityThresholds =
                    mThresholdMatrix.getOrDefault(entry.getKey(), new SparseArray<>());
            Set<Integer> thresholdsSet = new HashSet<>(); // to store unique thresholds
            int count = 0;
            for (int i = 0;
                    (i < netCapabilityThresholds.size() && count <= MAX_THRESHOLD_COUNT);
                    i++) {
                List<Integer> thresholdsList =
                        netCapabilityThresholds.get(
                                netCapabilityThresholds.keyAt(i), new ArrayList<>());
                for (int t : thresholdsList) {
                    if (thresholdsSet.add(t)) {
                        count++;
                    }
                    if (count >= MAX_THRESHOLD_COUNT) {
                        break;
                    }
                }
            }
            int[] newThresholds = new int[thresholdsSet.size()];
            count = 0;
            for (int i : thresholdsSet) {
                newThresholds[count++] = i;
            }
            Arrays.sort(newThresholds);
            int[] oldThresholds = mThresholdsRegistered.get(entry.getKey());
            Log.d(
                    mTag,
                    "For measurement type= "
                            + entry.getKey()
                            + " old Threshold= "
                            + Arrays.toString(oldThresholds)
                            + " new Threshold= "
                            + Arrays.toString(newThresholds));
            if (!Arrays.equals(newThresholds, oldThresholds)) {
                mThresholdsRegistered.put(entry.getKey(), newThresholds);
                isUpdated = true;
            }
        }
        return isUpdated;
    }

    private void updateThresholdsMatrix(int netCapability, Threshold[] ths) {

        Log.d(mTag, "Current threshold matrix: " + mThresholdMatrix);
        // clear old threshold for the netCapability in given netCapability from threshold matrix.
        for (Map.Entry<String, SparseArray<List<Integer>>> entry : mThresholdMatrix.entrySet()) {
            SparseArray<List<Integer>> netCapabilityThresholds =
                    mThresholdMatrix.get(entry.getKey());
            if (netCapabilityThresholds != null) {
                netCapabilityThresholds.remove(netCapability);
            }
        }
        if (ths == null || ths.length == 0) {
            return;
        }

        // store new thresholds in threshold matrix
        for (Threshold th : ths) {
            String key = th.getAccessNetwork() + "_" + th.getMeasurementType();
            SparseArray<List<Integer>> netCapabilityThresholds =
                    mThresholdMatrix.getOrDefault(key, new SparseArray<>());
            List<Integer> thresholdsList =
                    netCapabilityThresholds.get(netCapability, new ArrayList<>());
            thresholdsList.add(th.getThreshold());
            netCapabilityThresholds.put(netCapability, thresholdsList);
            mThresholdMatrix.put(key, netCapabilityThresholds);
            mThresholdWaitTimer.put(key, th.getWaitTime());
        }
        Log.d(mTag, "updated thresholds matrix: " + mThresholdMatrix);
    }

    /** This methods stops listening for the thresholds. */
    private synchronized void clearOldRequests() {
        if (mSSUpdateRequest != null) {
            Log.d(mTag, "Clearing request: " + mSSUpdateRequest);
            mTelephonyManager.clearSignalStrengthUpdateRequest(mSSUpdateRequest);
            mSSUpdateRequest = null;
        }
        mSignalStrengthListener.unregister();
    }

    /** This methods starts listening for the thresholds. */
    private void listenRequests() {
        clearOldRequests();
        if (mSignalThresholdInfoList.size() > 0) {
            mSSUpdateRequest =
                    new SignalStrengthUpdateRequest.Builder()
                            .setSignalThresholdInfos(mSignalThresholdInfoList)
                            .setReportingRequestedWhileIdle(true)
                            .build();
            mTelephonyManager.setSignalStrengthUpdateRequest(mSSUpdateRequest);
            Log.d(mTag, "Listening to request: " + mSSUpdateRequest);
            mSignalStrengthListener.register();
            if (!mIsQnsListenerRegistered) {
                mQnsTelephonyListener.registerQnsTelephonyInfoChanged(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        mHandler,
                        EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED,
                        null,
                        false);
                mIsQnsListenerRegistered = true;
            }
        } else {
            Log.d(mTag, "No requests are pending to listen");
            mQnsTelephonyListener.unregisterQnsTelephonyInfoChanged(
                    NetworkCapabilities.NET_CAPABILITY_IMS, mHandler);
            mIsQnsListenerRegistered = false;
        }
    }

    private int getSignalStrength(int accessNetwork, int measurementType, CellSignalStrength css) {
        int signalStrength = UNAVAILABLE;
        switch (measurementType) {
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI:
                if (accessNetwork == AccessNetworkType.GERAN
                        && css instanceof CellSignalStrengthGsm) {
                    signalStrength = ((CellSignalStrengthGsm) css).getRssi();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP:
                if (accessNetwork == AccessNetworkType.UTRAN
                        && css instanceof CellSignalStrengthWcdma) {
                    signalStrength = ((CellSignalStrengthWcdma) css).getDbm();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP:
                if (accessNetwork == AccessNetworkType.EUTRAN
                        && css instanceof CellSignalStrengthLte) {
                    signalStrength = ((CellSignalStrengthLte) css).getRsrp();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ:
                if (accessNetwork == AccessNetworkType.EUTRAN
                        && css instanceof CellSignalStrengthLte) {
                    signalStrength = ((CellSignalStrengthLte) css).getRsrq();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR:
                if (accessNetwork == AccessNetworkType.EUTRAN
                        && css instanceof CellSignalStrengthLte) {
                    signalStrength = ((CellSignalStrengthLte) css).getRssnr();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP:
                if (accessNetwork == AccessNetworkType.NGRAN
                        && css instanceof CellSignalStrengthNr) {
                    signalStrength = ((CellSignalStrengthNr) css).getSsRsrp();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ:
                if (accessNetwork == AccessNetworkType.NGRAN
                        && css instanceof CellSignalStrengthNr) {
                    signalStrength = ((CellSignalStrengthNr) css).getSsRsrq();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR:
                if (accessNetwork == AccessNetworkType.NGRAN
                        && css instanceof CellSignalStrengthNr) {
                    signalStrength = ((CellSignalStrengthNr) css).getSsSinr();
                }
                break;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO:
                if (accessNetwork == AccessNetworkType.UTRAN
                        && css instanceof CellSignalStrengthWcdma) {
                    signalStrength = ((CellSignalStrengthWcdma) css).getEcNo();
                }
                break;
            default:
                Log.d(mTag, "measurement type = " + measurementType + " not handled.");
                break;
        }
        return signalStrength;
    }

    private boolean validateThresholdList(Threshold[] ths) {
        for (Threshold threshold : ths) {
            if (!isValidThreshold(threshold.getMeasurementType(), threshold.getThreshold())) {
                return false;
            }
        }
        return true;
    }

    /** Return true if signal measurement type is valid and the threshold value is in range. */
    private static boolean isValidThreshold(int type, int threshold) {
        switch (type) {
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI:
                return threshold >= SignalThresholdInfo.SIGNAL_RSSI_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_RSSI_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP:
                return threshold >= SignalThresholdInfo.SIGNAL_RSCP_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_RSCP_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP:
                return threshold >= SignalThresholdInfo.SIGNAL_RSRP_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_RSRP_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ:
                return threshold >= SignalThresholdInfo.SIGNAL_RSRQ_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_RSRQ_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR:
                return threshold >= SignalThresholdInfo.SIGNAL_RSSNR_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_RSSNR_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP:
                return threshold >= SignalThresholdInfo.SIGNAL_SSRSRP_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_SSRSRP_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ:
                return threshold >= SignalThresholdInfo.SIGNAL_SSRSRQ_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_SSRSRQ_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR:
                return threshold >= SignalThresholdInfo.SIGNAL_SSSINR_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_SSSINR_MAX_VALUE;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO:
                return threshold >= SignalThresholdInfo.SIGNAL_ECNO_MIN_VALUE
                        && threshold <= SignalThresholdInfo.SIGNAL_ECNO_MAX_VALUE;

            default:
                return false;
        }
    }

    @VisibleForTesting
    List<SignalThresholdInfo> getSignalThresholdInfo() {
        return mSignalThresholdInfoList;
    }

    private class CellularEventsHandler extends Handler {
        CellularEventsHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Log.d(mTag, "handleMessage what = " + msg.what);
            QnsAsyncResult ar;
            switch (msg.what) {
                case EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED:
                    ar = (QnsAsyncResult) msg.obj;
                    if (ar.mException == null
                            && ar.mResult instanceof QnsTelephonyListener.QnsTelephonyInfo) {
                        QnsTelephonyListener.QnsTelephonyInfo info =
                                (QnsTelephonyListener.QnsTelephonyInfo) ar.mResult;
                        onQnsTelephonyInfoChanged(info);
                    }
                    break;
                case EVENT_SUBSCRIPTION_ID_CHANGED:
                    ar = (QnsAsyncResult) msg.obj;
                    int newSubId = (int) ar.mResult;
                    clearOldRequests();
                    mSubId = newSubId;
                    mTelephonyManager =
                            mContext.getSystemService(TelephonyManager.class)
                                    .createForSubscriptionId(mSubId);
                    break;
                default:
                    Log.d(mTag, "Not Handled !");
            }
        }

        QnsTelephonyListener.QnsTelephonyInfo mLastQnsTelephonyInfo = null;

        private void onQnsTelephonyInfoChanged(QnsTelephonyListener.QnsTelephonyInfo info) {
            if (mLastQnsTelephonyInfo == null
                    || mLastQnsTelephonyInfo.getDataNetworkType() != info.getDataNetworkType()
                    || mLastQnsTelephonyInfo.getDataRegState() != info.getDataRegState()
                    || mLastQnsTelephonyInfo.isCellularAvailable() != info.isCellularAvailable()) {
                if (!info.isCellularAvailable()) {
                    clearOldRequests();
                }
                mLastQnsTelephonyInfo = info;
            }
        }
    }

    @VisibleForTesting
    @Override
    public void close() {
        mQnsTelephonyListener.unregisterSubscriptionIdChanged(mHandler);
        clearOldRequests();
        mSignalThresholdInfoList.clear();
        mIsQnsListenerRegistered = false;
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    @Override
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(prefix + "CellularQualityMonitor[" + mSlotIndex + "]:");
        pw.println(prefix + "mSubId=" + mSubId);
        super.dump(pw, prefix);
        pw.println(prefix + "mIsQnsListenerRegistered=" + mIsQnsListenerRegistered);
        pw.println(prefix + "mSignalThresholdInfoList=" + mSignalThresholdInfoList);
        pw.println(prefix + "mSSUpdateRequest=" + mSSUpdateRequest);
        pw.println(prefix + "mThresholdMatrix=" + mThresholdMatrix);
        pw.println(prefix + "mThresholdsRegistered=" + mThresholdsRegistered);
        pw.println(prefix + "mThresholdWaitTimer=" + mThresholdWaitTimer);
    }
}
