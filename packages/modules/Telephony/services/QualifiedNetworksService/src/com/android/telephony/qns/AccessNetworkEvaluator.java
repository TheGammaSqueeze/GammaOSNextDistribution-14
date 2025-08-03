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

import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.qns.AccessNetworkSelectionPolicy.GuardingPreCondition;
import com.android.telephony.qns.AccessNetworkSelectionPolicy.PreCondition;
import com.android.telephony.qns.IwlanNetworkStatusTracker.IwlanAvailabilityInfo;
import com.android.telephony.qns.QualifiedNetworksServiceImpl.QualifiedNetworksInfo;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

/**
 * AccessNetworkEvaluator evaluates prioritized AccessNetwork list base on Cellular/Wi-Fi network
 * status and configurations from carrier/user.
 */
class AccessNetworkEvaluator {
    private static final boolean DBG = true;
    private static final int EVENT_BASE = 10000;
    private static final int EVENT_IWLAN_NETWORK_STATUS_CHANGED = EVENT_BASE;
    private static final int EVENT_QNS_TELEPHONY_INFO_CHANGED = EVENT_BASE + 1;
    private static final int EVENT_RESTRICT_INFO_CHANGED = EVENT_BASE + 4;
    private static final int EVENT_SET_CALL_TYPE = EVENT_BASE + 5;
    private static final int EVENT_DATA_CONNECTION_STATE_CHANGED = EVENT_BASE + 6;
    private static final int EVENT_PROVISIONING_INFO_CHANGED = EVENT_BASE + 8;
    private static final int EVENT_IMS_REGISTRATION_STATE_CHANGED = EVENT_BASE + 10;
    private static final int EVENT_WIFI_RTT_STATUS_CHANGED = EVENT_BASE + 11;
    private static final int EVENT_SIP_DIALOG_SESSION_STATE_CHANGED = EVENT_BASE + 12;
    private static final int EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED = EVENT_BASE + 13;
    private static final int EVALUATE_SPECIFIC_REASON_NONE = 0;
    private static final int EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE = 1;
    private static final int EVALUATE_SPECIFIC_REASON_DATA_DISCONNECTED = 2;
    private static final int EVALUATE_SPECIFIC_REASON_DATA_FAILED = 3;
    private static final int EVALUATE_SPECIFIC_REASON_DATA_CONNECTED = 4;

    protected final int mSlotIndex;
    protected final Context mContext;
    private final String mLogTag;
    private final int mNetCapability;
    @VisibleForTesting
    protected final Handler mHandler;
    private final HandlerThread mHandlerThread;
    private final RestrictManager mRestrictManager;
    protected QnsCarrierConfigManager mConfigManager;
    protected QnsComponents mQnsComponents;
    protected QualityMonitor mWifiQualityMonitor;
    protected QualityMonitor mCellularQualityMonitor;
    protected CellularNetworkStatusTracker mCellularNetworkStatusTracker;
    protected IwlanNetworkStatusTracker mIwlanNetworkStatusTracker;
    protected DataConnectionStatusTracker mDataConnectionStatusTracker;
    protected QnsEventDispatcher mQnsEventDispatcher;
    protected QnsCallStatusTracker mCallStatusTracker;
    protected QnsProvisioningListener mQnsProvisioningListener;
    protected QnsImsManager mQnsImsManager;
    protected WifiBackhaulMonitor mWifiBackhaulMonitor;
    protected QnsTelephonyListener mQnsTelephonyListener;
    // for metric
    protected QnsMetrics mQnsMetrics;

    protected int mCellularAccessNetworkType = AccessNetworkType.UNKNOWN;
    protected boolean mCellularAvailable = false;
    protected boolean mIwlanAvailable = false;
    private boolean mIsCrossWfc = false;

    protected QnsRegistrantList mQualifiedNetworksChangedRegistrants = new QnsRegistrantList();
    // pre-conditions
    private int mCallType;
    private int mCoverage;
    private int mLatestAvailableCellularAccessNetwork = AccessNetworkType.UNKNOWN;
    private List<AccessNetworkSelectionPolicy> mAccessNetworkSelectionPolicies = new ArrayList<>();
    private List<Integer> mLastQualifiedAccessNetworkTypes;
    private boolean mIsNotifiedLastQualifiedAccessNetworkTypes = false;
    private boolean mWfcPlatformEnabled = false;
    private boolean mSettingWfcEnabled = false;
    private int mSettingWfcMode = QnsConstants.CELL_PREF;
    private boolean mSettingWfcRoamingEnabled = false;
    private int mSettingWfcRoamingMode = QnsConstants.WIFI_PREF;
    private boolean mAllowIwlanForWfcActivation = false;
    private Map<PreCondition, List<AccessNetworkSelectionPolicy>> mAnspPolicyMap = null;
    private ThresholdListener mThresholdListener;
    private boolean mInitialized = false;
    private boolean mIsRttCheckSuccess = false;
    private QnsProvisioningListener.QnsProvisioningInfo mLastProvisioningInfo =
            new QnsProvisioningListener.QnsProvisioningInfo();
    private boolean mSipDialogSessionState = false;
    private int mCachedTransportTypeForEmergencyInitialConnect =
            AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    private int mLastEvaluateSpecificReason = EVALUATE_SPECIFIC_REASON_NONE;

    AccessNetworkEvaluator(QnsComponents qnsComponents, int netCapability, int slotIndex) {
        mNetCapability = netCapability;
        mQnsComponents = qnsComponents;
        mSlotIndex = slotIndex;
        mContext = mQnsComponents.getContext();
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + AccessNetworkEvaluator.class.getSimpleName()
                        + "_"
                        + mSlotIndex
                        + "_"
                        + QnsUtils.getNameOfNetCapability(netCapability);
        // load configurations & sort by purpose.

        log("created AccessNetworkEvaluator");

        // make handler to handle events for evaluate available AccessNetworks.
        mHandlerThread =
                new HandlerThread(AccessNetworkEvaluator.class.getSimpleName() + mNetCapability);
        mHandlerThread.start();
        mHandler = new EvaluatorEventHandler(mHandlerThread.getLooper());
        Executor executor = new QnsUtils.QnsExecutor(mHandler);

        mConfigManager = mQnsComponents.getQnsCarrierConfigManager(mSlotIndex);
        mCallStatusTracker = mQnsComponents.getQnsCallStatusTracker(mSlotIndex);
        mQnsProvisioningListener = mQnsComponents.getQnsProvisioningListener(mSlotIndex);
        mIwlanNetworkStatusTracker = mQnsComponents.getIwlanNetworkStatusTracker();
        mDataConnectionStatusTracker =
                new DataConnectionStatusTracker(
                        mQnsComponents.getQnsTelephonyListener(mSlotIndex),
                        mHandlerThread.getLooper(),
                        mSlotIndex,
                        mNetCapability);
        mQnsImsManager = mQnsComponents.getQnsImsManager(mSlotIndex);
        mWifiBackhaulMonitor = mQnsComponents.getWifiBackhaulMonitor(mSlotIndex);
        mQnsTelephonyListener = mQnsComponents.getQnsTelephonyListener(mSlotIndex);
        mQnsMetrics = mQnsComponents.getQnsMetrics();

        // Pre-Conditions
        mCellularNetworkStatusTracker = mQnsComponents.getCellularNetworkStatusTracker(mSlotIndex);
        mQnsEventDispatcher = mQnsComponents.getQnsEventDispatcher(mSlotIndex);
        mThresholdListener = new ThresholdListener(executor);

        // Post-Conditions
        mWifiQualityMonitor = mQnsComponents.getWifiQualityMonitor();
        mCellularQualityMonitor = mQnsComponents.getCellularQualityMonitor(mSlotIndex);

        // Evaluates
        mRestrictManager =
                new RestrictManager(
                        mQnsComponents,
                        mHandler.getLooper(),
                        mNetCapability,
                        mDataConnectionStatusTracker,
                        mSlotIndex);

        mHandler.post(() -> buildAccessNetworkSelectionPolicy(false));
        initLastNotifiedQualifiedNetwork();
        initSettings();
        registerListeners();
    }

    @VisibleForTesting
    AccessNetworkEvaluator(
            QnsComponents qnsComponents,
            int netCapability,
            RestrictManager restrictManager,
            DataConnectionStatusTracker dataConnectionStatusTracker,
            int slotIndex) {
        mQnsComponents = qnsComponents;
        mSlotIndex = slotIndex;
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + AccessNetworkEvaluator.class.getSimpleName()
                        + "_"
                        + mSlotIndex
                        + "_"
                        + QnsUtils.getNameOfNetCapability(netCapability);
        // load configurations & sort by purpose.
        mNetCapability = netCapability;
        mContext = mQnsComponents.getContext();
        mRestrictManager = restrictManager;
        mConfigManager = mQnsComponents.getQnsCarrierConfigManager(mSlotIndex);
        mWifiQualityMonitor = mQnsComponents.getWifiQualityMonitor();
        mCellularQualityMonitor = mQnsComponents.getCellularQualityMonitor(mSlotIndex);
        mCellularNetworkStatusTracker = mQnsComponents.getCellularNetworkStatusTracker(mSlotIndex);
        mIwlanNetworkStatusTracker = mQnsComponents.getIwlanNetworkStatusTracker();
        mDataConnectionStatusTracker = dataConnectionStatusTracker;
        mQnsEventDispatcher = mQnsComponents.getQnsEventDispatcher(mSlotIndex);
        mCallStatusTracker = mQnsComponents.getQnsCallStatusTracker(mSlotIndex);
        mQnsProvisioningListener = mQnsComponents.getQnsProvisioningListener(mSlotIndex);
        mQnsImsManager = mQnsComponents.getQnsImsManager(mSlotIndex);
        mWifiBackhaulMonitor = mQnsComponents.getWifiBackhaulMonitor(mSlotIndex);
        mQnsTelephonyListener = mQnsComponents.getQnsTelephonyListener(mSlotIndex);
        mQnsMetrics = mQnsComponents.getQnsMetrics();
        mHandlerThread =
                new HandlerThread(AccessNetworkEvaluator.class.getSimpleName() + mNetCapability);
        mHandlerThread.start();
        mHandler = new EvaluatorEventHandler(mHandlerThread.getLooper());
        mHandler.post(() -> buildAccessNetworkSelectionPolicy(false));
        initLastNotifiedQualifiedNetwork();
        initSettings();
        registerListeners();
    }

    void rebuild() {
        log("rebuild");
        initSettings();
        mLastProvisioningInfo.clear();
        mConfigManager.setQnsProvisioningInfo(mLastProvisioningInfo);
        mHandler.post(() -> buildAccessNetworkSelectionPolicy(true));
        mRestrictManager.clearRestrictions();
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            if (mWifiBackhaulMonitor.isRttCheckEnabled()) {
                mWifiBackhaulMonitor.registerForRttStatusChange(
                        mHandler, EVENT_WIFI_RTT_STATUS_CHANGED);
            } else {
                mWifiBackhaulMonitor.clearAll();
            }
        }
        reportQualifiedNetwork(getInitialAccessNetworkTypes());
        initLastNotifiedQualifiedNetwork();
        unregisterThresholdToQualityMonitor();

        if (isAllowed(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                && evaluateAvailability(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        isAllowed(AccessNetworkConstants.TRANSPORT_TYPE_WWAN))) {
            mHandler.post(this::evaluate);
        }
        mLastEvaluateSpecificReason = EVALUATE_SPECIFIC_REASON_NONE;
    }

    void close() {
        log("close");
        mHandler.post(this::onClose);
        mHandlerThread.quitSafely();
    }

    private void onClose() {
        notifyForQualifiedNetworksChanged(getInitialAccessNetworkTypes());
        initLastNotifiedQualifiedNetwork();
        unregisterListeners();
        mQualifiedNetworksChangedRegistrants.removeAll();
        mDataConnectionStatusTracker.close();
        mRestrictManager.close();
    }

    void registerForQualifiedNetworksChanged(Handler h, int what) {
        mInitialized = true;
        mQualifiedNetworksChangedRegistrants.addUnique(h, what, null);
        if (isNotifiedQualifiedAccessNetworkTypes()) {
            if (DBG) {
                log(
                        "registerForQualifiedNetworksChanged, report:"
                                + QnsUtils.getStringAccessNetworkTypes(
                                        mLastQualifiedAccessNetworkTypes));
            }
            notifyForQualifiedNetworksChanged(mLastQualifiedAccessNetworkTypes);
        }
        mHandler.post(this::evaluate);
    }

    void unregisterForQualifiedNetworksChanged(Handler h) {
        mQualifiedNetworksChangedRegistrants.remove(h);
    }

    private List<Integer> getInitialAccessNetworkTypes() {
        // The framework treats empty lists as WWAN.
        return List.of();
    }

    protected int getLastQualifiedTransportType() {
        if (mLastQualifiedAccessNetworkTypes.size() > 0
                && mLastQualifiedAccessNetworkTypes.get(0) == AccessNetworkType.IWLAN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        }
        // otherwise, returns WWAN. (includes an empty list)
        return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    }

    protected boolean isNotifiedQualifiedAccessNetworkTypes() {
        return mIsNotifiedLastQualifiedAccessNetworkTypes;
    }

    protected void initLastNotifiedQualifiedNetwork() {
        mIsNotifiedLastQualifiedAccessNetworkTypes = false;
        mLastQualifiedAccessNetworkTypes = getInitialAccessNetworkTypes();
        log(
                "initLastNotifiedQualifiedNetwork mLastQualifiedAccessNetworkTypes:"
                        + QnsUtils.getStringAccessNetworkTypes(mLastQualifiedAccessNetworkTypes));
    }

    protected boolean equalsLastNotifiedQualifiedNetwork(List<Integer> accessNetworkTypes) {
        return mLastQualifiedAccessNetworkTypes.equals(accessNetworkTypes);
    }

    protected void updateLastNotifiedQualifiedNetwork(List<Integer> accessNetworkTypes) {
        mLastQualifiedAccessNetworkTypes = accessNetworkTypes;
        mRestrictManager.updateLastNotifiedTransportType(getLastQualifiedTransportType());
        log(
                "updateLastNotifiedQualifiedNetwork mLastQualifiedAccessNetworkTypes:"
                        + QnsUtils.getStringAccessNetworkTypes(mLastQualifiedAccessNetworkTypes));
    }

    protected void notifyForQualifiedNetworksChanged(List<Integer> accessNetworkTypes) {
        mIsNotifiedLastQualifiedAccessNetworkTypes = true;
        QualifiedNetworksInfo info = new QualifiedNetworksInfo(mNetCapability, accessNetworkTypes);
        QnsAsyncResult ar = new QnsAsyncResult(null, info, null);
        mQualifiedNetworksChangedRegistrants.notifyRegistrants(ar);

        // metrics
        sendMetricsForQualifiedNetworks(info);
    }

    private void initSettings() {
        mWfcPlatformEnabled = QnsUtils.isWfcEnabledByPlatform(mQnsImsManager);
        mSettingWfcEnabled = QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, false);
        mSettingWfcMode = QnsUtils.getWfcMode(mQnsImsManager, false);
        mSettingWfcRoamingEnabled =
                QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, true);
        mSettingWfcRoamingMode = QnsUtils.getWfcMode(mQnsImsManager, true);
        mAllowIwlanForWfcActivation = false;
        mSipDialogSessionState = false;
        mCachedTransportTypeForEmergencyInitialConnect =
                AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        log(
                "WfcSettings. mWfcPlatformEnabled:"
                        + mWfcPlatformEnabled
                        + " WfcEnabled:"
                        + mSettingWfcEnabled
                        + " WfcMode:"
                        + QnsConstants.preferenceToString(mSettingWfcMode)
                        + " WfcRoamingEnabled:"
                        + mSettingWfcRoamingEnabled
                        + " WfcRoamingMode:"
                        + QnsConstants.preferenceToString(mSettingWfcRoamingMode));
    }

    protected void registerListeners() {
        log("registerListeners");
        mWifiQualityMonitor.registerThresholdChange(
                mThresholdListener, mNetCapability, null, mSlotIndex);
        mCellularQualityMonitor.registerThresholdChange(
                mThresholdListener, mNetCapability, null, mSlotIndex);
        mIwlanNetworkStatusTracker.registerIwlanNetworksChanged(
                mSlotIndex, mHandler, EVENT_IWLAN_NETWORK_STATUS_CHANGED);
        mDataConnectionStatusTracker.registerDataConnectionStatusChanged(
                mHandler, EVENT_DATA_CONNECTION_STATE_CHANGED);
        mQnsImsManager.registerImsRegistrationStatusChanged(
                mHandler, EVENT_IMS_REGISTRATION_STATE_CHANGED);
        mQnsImsManager.registerSipDialogSessionStateChanged(
                mHandler, EVENT_SIP_DIALOG_SESSION_STATE_CHANGED);
        mCellularNetworkStatusTracker.registerQnsTelephonyInfoChanged(
                mNetCapability, mHandler, EVENT_QNS_TELEPHONY_INFO_CHANGED);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                || mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            mCallStatusTracker.registerCallTypeChangedListener(
                    mNetCapability, mHandler, EVENT_SET_CALL_TYPE, null);
        }

        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            if (mWifiBackhaulMonitor.isRttCheckEnabled()) {
                mWifiBackhaulMonitor.registerForRttStatusChange(
                        mHandler, EVENT_WIFI_RTT_STATUS_CHANGED);
            }
        }
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(
                mHandler, EVENT_PROVISIONING_INFO_CHANGED, null, true);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mQnsTelephonyListener.registerImsCallDropDisconnectCauseListener(
                    mHandler, EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED, null);
        }
        List<Integer> events = new ArrayList<>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_SIM_ABSENT);
        events.add(QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION);
        events.add(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION);
        mQnsEventDispatcher.registerEvent(events, mHandler);
        mRestrictManager.registerRestrictInfoChanged(mHandler, EVENT_RESTRICT_INFO_CHANGED);
    }

    protected void unregisterListeners() {
        log("unregisterListeners");
        mWifiQualityMonitor.unregisterThresholdChange(mNetCapability, mSlotIndex);
        mCellularQualityMonitor.unregisterThresholdChange(mNetCapability, mSlotIndex);
        mDataConnectionStatusTracker.unRegisterDataConnectionStatusChanged(mHandler);
        mQnsImsManager.unregisterImsRegistrationStatusChanged(mHandler);
        mQnsImsManager.unregisterSipDialogSessionStateChanged(mHandler);
        mCellularNetworkStatusTracker.unregisterQnsTelephonyInfoChanged(mNetCapability, mHandler);
        mIwlanNetworkStatusTracker.unregisterIwlanNetworksChanged(mSlotIndex, mHandler);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                || mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            mCallStatusTracker.unregisterCallTypeChangedListener(mNetCapability, mHandler);
            if (mWifiBackhaulMonitor.isRttCheckEnabled()) {
                mWifiBackhaulMonitor.unRegisterForRttStatusChange(mHandler);
            }
        }
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mQnsTelephonyListener.unregisterImsCallDropDisconnectCauseListener(mHandler);
        }
        mQnsProvisioningListener.unregisterProvisioningItemInfoChanged(mHandler);
        mQnsEventDispatcher.unregisterEvent(mHandler);
        mRestrictManager.unRegisterRestrictInfoChanged(mHandler);
    }

    private boolean isIwlanAvailableWithoutRestrict() {
        return (mIwlanAvailable
                && !mRestrictManager.isRestricted(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
    }

    private boolean isCellularAvailableWithoutRestrict() {
        return (mCellularAvailable
                && !mRestrictManager.isRestricted(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    protected void onQnsTelephonyInfoChanged(QnsTelephonyListener.QnsTelephonyInfo info) {
        boolean needEvaluate = false;

        if (info instanceof QnsTelephonyListener.QnsTelephonyInfoIms) {
            QnsTelephonyListener.QnsTelephonyInfoIms infoIms =
                    (QnsTelephonyListener.QnsTelephonyInfoIms) info;
            boolean checkVoPs = false;
            boolean volteRoamingSupported = true;
            int cellularAccessNetworkType =
                    QnsUtils.getCellularAccessNetworkType(
                            infoIms.getDataRegState(), infoIms.getDataNetworkType());

            int coverage = getCoverage(infoIms);
            if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                    && infoIms.isCellularAvailable()) {
                checkVoPs = vopsCheckRequired(cellularAccessNetworkType, coverage, mCallType);
                volteRoamingSupported = mConfigManager.isVolteRoamingSupported(coverage);
            }
            boolean checkBarring = mConfigManager.isServiceBarringCheckSupported();
            if (DBG) {
                log(
                        "checkVoPs:"
                                + checkVoPs
                                + ", checkBarring:"
                                + checkBarring
                                + ", volteRoamingSupported:"
                                + volteRoamingSupported);
            }
            boolean cellAvailable =
                    infoIms.isCellularAvailable(
                            mNetCapability, checkVoPs, checkBarring, volteRoamingSupported);
            if (mCellularAvailable != cellAvailable) {
                mCellularAvailable = cellAvailable;
                needEvaluate = true;
                log("onQnsTelephonyInfoChanged cellularAvailableIms:" + mCellularAvailable);
            }
        } else if (mCellularAvailable != info.isCellularAvailable()) {
            mCellularAvailable = info.isCellularAvailable();
            needEvaluate = true;
            log("onQnsTelephonyInfoChanged cellularAvailable:" + mCellularAvailable);
        }
        if (mCellularAccessNetworkType
                != QnsUtils.getCellularAccessNetworkType(
                        info.getDataRegState(), info.getDataNetworkType())) {
            mCellularAccessNetworkType =
                    QnsUtils.getCellularAccessNetworkType(
                            info.getDataRegState(), info.getDataNetworkType());
            mRestrictManager.setCellularAccessNetwork(mCellularAccessNetworkType);
            if (mCellularAccessNetworkType != AccessNetworkType.UNKNOWN) {
                mLatestAvailableCellularAccessNetwork = mCellularAccessNetworkType;
            }

            needEvaluate = true;
            log(
                    "onQnsTelephonyInfoChanged cellularAccessNetworkType:"
                            + mCellularAccessNetworkType);
        }
        int coverage = getCoverage(info);
        if (mCoverage != coverage) {
            mCoverage = coverage;
            needEvaluate = true;
            mRestrictManager.setCellularCoverage(mCoverage);
            log("onQnsTelephonyInfoChanged Coverage:" + mCoverage);
        }

        if (needEvaluate) {
            evaluate();
        }
    }

    @VisibleForTesting
    int getCoverage(QnsTelephonyListener.QnsTelephonyInfo info) {
        if (DBG) {
            log("getCoverage roaming=" + info.isCoverage());
        }
        if (info.isCoverage()) {
            return QnsConstants.COVERAGE_ROAM;
        } else {
            return QnsConstants.COVERAGE_HOME;
        }
    }

    protected void onIwlanNetworkStatusChanged(IwlanAvailabilityInfo info) {
        if (info != null) {
            if (mIwlanAvailable != info.getIwlanAvailable()) {
                mIwlanAvailable = info.getIwlanAvailable();
            }
            mIsCrossWfc = info.isCrossWfc();
            log("onIwlanNetworkStatusChanged IwlanAvailable:" + mIwlanAvailable);
            if (info.getNotifyIwlanDisabled()) {
                evaluate(EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE);
            } else {
                evaluate();
            }
        }
    }

    private void onWfcEnabledChanged(boolean enabled, boolean roaming) {
        StringBuilder sb = new StringBuilder("onWfcEnabledChanged");
        sb.append(" enabled:").append(enabled);
        sb.append(" coverage:")
                .append(
                        QnsConstants.coverageToString(
                                roaming ? QnsConstants.COVERAGE_ROAM : QnsConstants.COVERAGE_HOME));

        boolean needEvaluate = false;
        if (!roaming && mSettingWfcEnabled != enabled) {
            if (mCoverage == QnsConstants.COVERAGE_HOME) {
                needEvaluate = true;
            }
            mSettingWfcEnabled = enabled;
            sb.append(" mSettingWfcEnabled:").append(mSettingWfcEnabled);
        } else if (roaming && mSettingWfcRoamingEnabled != enabled) {
            if (mCoverage == QnsConstants.COVERAGE_ROAM) {
                needEvaluate = true;
            }
            mSettingWfcRoamingEnabled = enabled;
            sb.append(" mSettingWfcRoamingEnabled:").append(mSettingWfcRoamingEnabled);
        }

        if (needEvaluate) {
            sb.append(" evaluate.");
            log(sb.toString());
            if (enabled) {
                evaluate();
            } else {
                evaluate(EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE);
            }
        } else {
            log(sb.toString());
        }
    }

    private void onWfcModeChanged(int mode, boolean roaming) {
        StringBuilder sb = new StringBuilder("onWfcModeChanged");
        sb.append(" mode:").append(QnsConstants.preferenceToString(mode));
        sb.append(" coverage:")
                .append(
                        QnsConstants.coverageToString(
                                roaming ? QnsConstants.COVERAGE_ROAM : QnsConstants.COVERAGE_HOME));

        boolean needEvaluate = false;
        if (!roaming && mSettingWfcMode != mode) {
            if (mCoverage == QnsConstants.COVERAGE_HOME) {
                needEvaluate = true;
            }
            mSettingWfcMode = mode;
            sb.append(" mSettingWfcMode:").append(QnsConstants.preferenceToString(mSettingWfcMode));
        } else if (roaming && mSettingWfcRoamingMode != mode) {
            if (mCoverage == QnsConstants.COVERAGE_ROAM) {
                needEvaluate = true;
            }
            mSettingWfcRoamingMode = mode;
            sb.append(" mSettingWfcRoamingMode:");
            sb.append(QnsConstants.preferenceToString(mSettingWfcRoamingMode));
        }

        if (needEvaluate) {
            sb.append(" evaluate.");
            log(sb.toString());
            evaluate();
        } else {
            log(sb.toString());
        }
    }

    private void onWfcPlatformChanged(boolean bWfcPlatformEnabled) {
        StringBuilder sb = new StringBuilder("onWfcPlatformChanged");
        sb.append(" bWfcPlatformEnabled:").append(bWfcPlatformEnabled);
        if (mWfcPlatformEnabled == bWfcPlatformEnabled) {
            sb.append(" no changes");
            log(sb.toString());
        } else {
            mWfcPlatformEnabled = bWfcPlatformEnabled;
            if (bWfcPlatformEnabled) {
                sb.append(" evaluate.");
                log(sb.toString());
                evaluate();
            } else {
                sb.append(" report cellular as qualified network directly.");
                log(sb.toString());
                reportQualifiedNetwork(new ArrayList<>(List.of(mCellularAccessNetworkType)));
            }
        }
    }

    private void onSimAbsent() {
        log("onSimAbsent");

        // report default qualified access network.
        reportQualifiedNetwork(getInitialAccessNetworkTypes());
    }

    private void onRestrictInfoChanged() {
        // TODO
        log("onRestrictInfoChanged");
        evaluate();
    }

    @VisibleForTesting
    void onTryWfcConnectionStateChanged(boolean isEnabled) {
        log("onTryWfcConnectionStateChanged enabled:" + isEnabled);
        int timeout = mConfigManager.getVowifiRegistrationTimerForVowifiActivation();

        if (mAllowIwlanForWfcActivation == isEnabled) {
            return;
        }
        if (isEnabled) {
            mHandler.sendEmptyMessageDelayed(
                    QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION,
                    timeout + /* milliseconds */ 3000);
        } else {
            mHandler.removeMessages(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION);
        }
        mAllowIwlanForWfcActivation = isEnabled;
        evaluate();
    }

    @VisibleForTesting
    void onSetCallType(@QnsConstants.QnsCallType int callType) {
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                && callType == QnsConstants.CALL_TYPE_EMERGENCY) {
            if (!mDataConnectionStatusTracker.isActiveState()) return;
        }

        // metrics
        sendMetricsForCallTypeChanged(mCallType, callType);

        mCallType = callType;
        mRestrictManager.setQnsCallType(mCallType);
        log("onSetCallType CallType:" + mCallType);

        // call type from service manager API

        // TODO
        evaluate();
    }

    void onEmergencyPreferredTransportTypeChanged(
            @AccessNetworkConstants.TransportType int transport) {
        if (mNetCapability != NetworkCapabilities.NET_CAPABILITY_EIMS) {
            return;
        }
        mHandler.post(() -> {
                    log(
                            "onEmergencyPreferredTransportTypeChanged transport:"
                                    + QnsConstants.transportTypeToString(transport));
                    if (mDataConnectionStatusTracker.isInactiveState()
                            || (mDataConnectionStatusTracker.isActiveState()
                                    && mCallType == QnsConstants.CALL_TYPE_IDLE)) {
                        // If data network state is inactive OR active but call is not active yet,
                        // QNS will follow domain selection's decision.
                        enforceNotifyQualifiedNetworksWithTransportType(transport);
                    } else {
                        log(
                                "cache transportType for emergency: "
                                        + QnsConstants.transportTypeToString(transport));
                        mCachedTransportTypeForEmergencyInitialConnect = transport;
                    }
                }
        );
    }

    private void enforceNotifyQualifiedNetworksWithTransportType(
            @AccessNetworkConstants.TransportType int transportType) {
        List<Integer> accessNetworkTypes = new ArrayList<>();
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            accessNetworkTypes.add(AccessNetworkType.IWLAN);
        } else if (mCellularAccessNetworkType != AccessNetworkType.UNKNOWN) {
            accessNetworkTypes.add(mCellularAccessNetworkType);
        }
        updateLastNotifiedQualifiedNetwork(accessNetworkTypes);
        notifyForQualifiedNetworksChanged(accessNetworkTypes);
    }

    @VisibleForTesting
    void onDataConnectionStateChanged(
            DataConnectionStatusTracker.DataConnectionChangedInfo info) {
        log("onDataConnectionStateChanged info:" + info);
        boolean needEvaluate = false;
        int evaluateSpecificReason = EVALUATE_SPECIFIC_REASON_NONE;
        switch (info.getEvent()) {
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED:
                evaluateSpecificReason = EVALUATE_SPECIFIC_REASON_DATA_DISCONNECTED;
                if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                    // If FWK guided emergency's transport type during data connected state, notify
                    // the transport type when the data connection is disconnected.
                    notifyCachedTransportTypeForEmergency();
                    // When cellular is not available, CellularQualityMonitor will stop listening to
                    // TelephonyCallback. Thresholds for Emergency apn may not be reset as ANE for
                    // emergency only evaluates while data is connected. CellularQualityMonitor will
                    // not listen to TelephonyCallback again until the set of threshold values
                    // changed. Resetting thresholds for emergency after the emergency connection
                    // disconnects.
                    unregisterThresholdToQualityMonitor();
                } else {
                    needEvaluate = true;
                    initLastNotifiedQualifiedNetwork();
                }
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED:
                evaluateSpecificReason = EVALUATE_SPECIFIC_REASON_DATA_CONNECTED;
                mHandler.post(() -> onDataConnectionConnected(info.getTransportType()));
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_FAILED:
                evaluateSpecificReason = EVALUATE_SPECIFIC_REASON_DATA_FAILED;
                if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                    // If FWK guided emergency's transport type during data connecting state, notify
                    // the transport type when the data connection is failed.
                    notifyCachedTransportTypeForEmergency();
                    // When cellular is not available, CellularQualityMonitor will stop listening to
                    // TelephonyCallback. Thresholds for Emergency apn may not be reset as ANE for
                    // emergency only evaluates while data is connected. CellularQualityMonitor will
                    // not listen to TelephonyCallback again until the set of threshold values
                    // changed. Resetting thresholds for emergency after the emergency connection
                    // disconnects.
                    unregisterThresholdToQualityMonitor();
                } else {
                    needEvaluate = true;
                }
                break;
        }

        // metrics
        sendMetricsForDataConnectionChanged(info);

        if (needEvaluate) {
            evaluate(evaluateSpecificReason);
        }
    }

    private void notifyCachedTransportTypeForEmergency() {
        if (mCachedTransportTypeForEmergencyInitialConnect
                != AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
            enforceNotifyQualifiedNetworksWithTransportType(
                    mCachedTransportTypeForEmergencyInitialConnect);
            mCachedTransportTypeForEmergencyInitialConnect =
                    AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        }
    }

    private void onDataConnectionConnected(int transportType) {
        int otherTransportType;
        if (DBG) {
            log("onDataConnectionConnected :" + QnsConstants.transportTypeToString(transportType));
        }
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
            log("Error: onDataConnectionConnected invalid transport type.");
            return;
        } else {
            otherTransportType =
                    transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                            ? AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                            : AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        }
        if (!mRestrictManager.isRestricted(otherTransportType)) {
            evaluate();
        } // else case : evaluate() will process when restrictions released.
    }

    protected void onProvisioningInfoChanged(QnsProvisioningListener.QnsProvisioningInfo info) {
        boolean needBuildAnsp = false;
        boolean needEvaluate = false;

        log("onProvisioningInfoChanged info:" + info);

        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_LTE_THRESHOLD_1)) {
            log(
                    "onProvisioningInfoChanged, KEY_LTE_THRESHOLD_1("
                            + ProvisioningManager.KEY_LTE_THRESHOLD_1
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_1));
            mConfigManager.setQnsProvisioningInfo(info);
            needBuildAnsp = true;
            needEvaluate = true;
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_LTE_THRESHOLD_2)) {
            log(
                    "onProvisioningInfoChanged, KEY_LTE_THRESHOLD_2("
                            + ProvisioningManager.KEY_LTE_THRESHOLD_2
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_2));
            mConfigManager.setQnsProvisioningInfo(info);
            needBuildAnsp = true;
            needEvaluate = true;
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_LTE_THRESHOLD_3)) {
            log(
                    "onProvisioningInfoChanged, KEY_LTE_THRESHOLD_3("
                            + ProvisioningManager.KEY_LTE_THRESHOLD_3
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_LTE_THRESHOLD_3));
            mConfigManager.setQnsProvisioningInfo(info);
            needBuildAnsp = true;
            needEvaluate = true;
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_WIFI_THRESHOLD_A)) {
            log(
                    "onProvisioningInfoChanged, KEY_WIFI_THRESHOLD_A("
                            + ProvisioningManager.KEY_WIFI_THRESHOLD_A
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_WIFI_THRESHOLD_A));
            mConfigManager.setQnsProvisioningInfo(info);
            needBuildAnsp = true;
            needEvaluate = true;
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_WIFI_THRESHOLD_B)) {
            log(
                    "onProvisioningInfoChanged, KEY_WIFI_THRESHOLD_B("
                            + ProvisioningManager.KEY_WIFI_THRESHOLD_B
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_WIFI_THRESHOLD_B));
            mConfigManager.setQnsProvisioningInfo(info);
            needBuildAnsp = true;
            needEvaluate = true;
        }

        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC)) {
            log(
                    "onProvisioningInfoChanged, KEY_LTE_EPDG_TIMER_SEC("
                            + ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC));
            mConfigManager.setQnsProvisioningInfo(info);
            needEvaluate = true;
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC)) {
            log(
                    "onProvisioningInfoChanged, KEY_WIFI_EPDG_TIMER_SEC("
                            + ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC
                            + ") is provisioned to "
                            + info.getIntegerItem(ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC));
            mConfigManager.setQnsProvisioningInfo(info);
            needEvaluate = true;
        }

        mLastProvisioningInfo = info;

        if (needBuildAnsp) {
            buildAccessNetworkSelectionPolicy(true);
        }
        if (needEvaluate) {
            evaluate();
        }

        /* TODO to be checked
        ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS
        ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID
        */
    }

    private void onImsRegStateChanged(QnsImsManager.ImsRegistrationState imsRegEvent) {
        if (mConfigManager.getRatPreference(mNetCapability)
                != QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE) {
            return;
        }
        int transportType = imsRegEvent.getTransportType();
        int event = imsRegEvent.getEvent();
        if (event == QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED
                || event == QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED) {
            log(
                    "onImsRegStateChanged, "
                            + QnsConstants.transportTypeToString(transportType)
                            + ","
                            + QnsConstants.imsRegistrationEventToString(event));
            evaluate();
        }
    }

    protected void onSipDialogSessionStateChanged(boolean isActive) {
        if (mConfigManager.getSipDialogSessionPolicy()
                == QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE) {
            mSipDialogSessionState = false;
            return;
        }
        if (mSipDialogSessionState != isActive) {
            mSipDialogSessionState = isActive;
            log("onSipDialogSessionStateChanged isActive:" + isActive);
            evaluate();
        }
    }

    protected void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReasonInfo) {
        if (imsReasonInfo == null) {
            return;
        }
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                && imsReasonInfo.getCode() == ImsReasonInfo.CODE_MEDIA_NO_DATA) {
            log(
                    "onImsCallDisconnectCauseChanged: iwlanAvailable="
                            + mIwlanAvailable
                            + " cellularAvailable="
                            + mCellularAvailable
                            + " imsReasonInfo="
                            + imsReasonInfo);
            if (mIwlanAvailable && mCellularAvailable) {
                // metrics
                sendMetricsForImsCallDropStats();
            }
        }
    }

    protected void onCellularQualityChanged(Threshold[] ths) {
        if (ths == null || ths.length == 0) {
            log("onCellularQualityChanged: E threshold is null");
            return;
        }
        log("onCellularQualityChanged Threshold:" + Arrays.toString(ths));
        // TODO
        evaluate();
    }

    protected void onWiFiQualityChanged(Threshold[] ths) {
        if (ths == null || ths.length == 0) {
            log("onCellularQualityChanged: E threshold is null");
            return;
        }
        log("onWiFiQualityChanged Threshold:" + Arrays.toString(ths));
        // TODO
        evaluate();
    }

    private void onRttStatusChanged(boolean result) {
        log("onRttStatusChanged status: " + result);
        if (result != mIsRttCheckSuccess) {
            mIsRttCheckSuccess = result;
            if (mIsRttCheckSuccess) {
                evaluate();
            }
        }
    }

    protected boolean isWfcEnabled() {

        validateWfcSettingsAndUpdate();

        if (mAllowIwlanForWfcActivation) {
            return true;
        }
        if (!mWfcPlatformEnabled) {
            log("isWfcPlatformEnabled:false");
            return false;
        }
        if (mCoverage == QnsConstants.COVERAGE_HOME && mSettingWfcEnabled) {
            return true;
        }
        if ((mCoverage == QnsConstants.COVERAGE_ROAM
                        || mIwlanNetworkStatusTracker.isInternationalRoaming(mSlotIndex))
                && mSettingWfcRoamingEnabled) {
            return true;
        }

        if (mConfigManager.allowImsOverIwlanCellularLimitedCase()
                && !isAccessNetworkAllowed(mCellularAccessNetworkType, mNetCapability)
                && mCallType == QnsConstants.CALL_TYPE_IDLE
                && mIwlanAvailable) {
            log("isWfcEnabled:true for idle, allow wfc");
            return true;
        }

        if (mConfigManager.allowVideoOverIWLANWithCellularLimitedCase()
                && mCallType == QnsConstants.CALL_TYPE_VIDEO
                && mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            log("isWfcEnabled:true for video");
            return true;
        }

        return false;
    }

    private void validateWfcSettingsAndUpdate() {
        boolean roaming = (mCoverage == QnsConstants.COVERAGE_ROAM);
        boolean wfcSetting =
                QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, roaming);
        if (roaming && mSettingWfcRoamingEnabled != wfcSetting) {
            log("validateWfcSettingsAndUpdate, found wfc roaming setting mismatch");
            if (wfcSetting) {
                mWfcPlatformEnabled = true;
                mSettingWfcRoamingEnabled = true;
            } else {
                mWfcPlatformEnabled = QnsUtils.isWfcEnabledByPlatform(mQnsImsManager);
                mSettingWfcRoamingEnabled = false;
            }
            mSettingWfcRoamingMode = QnsUtils.getWfcMode(mQnsImsManager, true);
        } else if (!roaming && mSettingWfcEnabled != wfcSetting) {
            log("validateWfcSettingsAndUpdate, found wfc setting mismatch");
            if (wfcSetting) {
                mWfcPlatformEnabled = true;
                mSettingWfcEnabled = true;
            } else {
                mWfcPlatformEnabled = QnsUtils.isWfcEnabledByPlatform(mQnsImsManager);
                mSettingWfcEnabled = false;
            }
            mSettingWfcMode = QnsUtils.getWfcMode(mQnsImsManager, false);
        }
    }

    private boolean isAccessNetworkAllowed(int accessNetwork, int netCapability) {
        switch (netCapability) {
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
            case NetworkCapabilities.NET_CAPABILITY_IMS:
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
                return mConfigManager.isAccessNetworkAllowed(accessNetwork, netCapability);
            default:
                if (accessNetwork == AccessNetworkType.UNKNOWN) {
                    return false;
                }
        }
        return true;
    }

    private boolean isAllowed(int transportType) {
        StringBuilder sb = new StringBuilder();
        sb.append(" evaluate isAllowed for transportType:")
                .append(QnsConstants.transportTypeToString(transportType));

        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            boolean isWfcEnabled = isWfcEnabled();
            sb.append(" isWfcEnabled:").append(isWfcEnabled);
            if (!isWfcEnabled) {
                log(sb.toString());
                return false;
            }

            if (mQnsEventDispatcher.isAirplaneModeToggleOn()) {
                boolean isWfcAllowInAirplaneMode = mConfigManager.allowWFCOnAirplaneModeOn();
                sb.append(" isWfcAllowInAirplaneMode:").append(isWfcAllowInAirplaneMode);
                if (!isWfcAllowInAirplaneMode) {
                    log(sb.toString());
                    return false;
                }
            }

            if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                    && (mQnsEventDispatcher.isAirplaneModeToggleOn()
                            || !mQnsComponents
                                    .getQnsTelephonyListener(mSlotIndex)
                                    .getLastQnsTelephonyInfo()
                                    .isCellularAvailable())
                    && mIwlanNetworkStatusTracker.isInternationalRoaming(mSlotIndex)) {
                boolean isBlockIwlan = mConfigManager.blockIwlanInInternationalRoamWithoutWwan();
                sb.append(" isBlockIwlanInInternationalRoamWithoutWwan:").append(isBlockIwlan);
                if (isBlockIwlan) {
                    log(sb.toString());
                    return false;
                }
            }
        }

        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            if (mQnsEventDispatcher.isAirplaneModeToggleOn()) {
                sb.append(" AirplaneModeOn");
                log(sb.toString());
                return false;
            }

            if (getPreferredMode() == QnsConstants.WIFI_ONLY) {
                sb.append(" isWiFiOnly:false");
                log(sb.toString());
                return false;
            }
        }

        if (mConfigManager.getRatPreference(mNetCapability)
                != QnsConstants.RAT_PREFERENCE_DEFAULT) {
            switch (mConfigManager.getRatPreference(mNetCapability)) {
                case QnsConstants.RAT_PREFERENCE_WIFI_ONLY:
                    if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                        sb.append(" isAllowRatPrefWiFiOnly:false");
                        log(sb.toString());
                        return false;
                    }
                    sb.append(" isAllowRatPrefWiFiOnly:true");
                    break;
                case QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE:
                    if ((transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                                    && mQnsImsManager.isImsRegistered(
                                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN))
                            || (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                                    && !mQnsImsManager.isImsRegistered(
                                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN))) {
                        sb.append(" isAllowRatPrefWfcAvail:false");
                        log(sb.toString());
                        return false;
                    }
                    sb.append(" isAllowRatPrefWfcAvail:true");
                    break;
                case QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR:
                    if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                            && mCellularAvailable) {
                        sb.append(" isAllowRatPrefNoCell:false");
                        log(sb.toString());
                        return false;
                    }
                    sb.append(" isAllowRatPrefNoCell:true");
                    break;
                case QnsConstants.RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE:
                    if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                            && (!mCellularAvailable || mCoverage != QnsConstants.COVERAGE_HOME)) {
                        sb.append(" isAllowRatPrefNotHome:false");
                        log(sb.toString());
                        return false;
                    }
                    sb.append(" isAllowRatPrefNotHome:true");
                    break;
            }
        }

        log(sb.toString());
        return true;
    }

    private boolean evaluateAvailability(int transportType, boolean isAllowedForOtherType) {
        boolean isAvailable =
                (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        ? mIwlanAvailable
                        : mCellularAvailable;
        boolean isOtherTypeAvailable =
                isAllowedForOtherType
                        && ((transportType != AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                                ? mIwlanAvailable
                                : mCellularAvailable);
        boolean isRestricted = mRestrictManager.isRestricted(transportType);
        boolean isAllowedOnSingleTransport =
                mRestrictManager.isAllowedOnSingleTransport(transportType);

        StringBuilder sb = new StringBuilder();
        sb.append(" evaluate Available for transportType:")
                .append(QnsConstants.transportTypeToString(transportType));

        sb.append(" isAvailable:").append(isAvailable);
        if (!isAvailable) {
            log(sb.toString());
            return false;
        } else if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                && mIsCrossWfc
                && !isCrossWfcAllowed(mCellularAvailable)) {
            sb.append(" CrossWfc is not allowed. mCellularAvailable:").append(mCellularAvailable);
            log(sb.toString());
            return false;
        }

        if (isRestricted) {
            PreCondition cond = getMatchingPreCondition();
            if (cond instanceof GuardingPreCondition
                    && ((GuardingPreCondition) cond).getGuarding() != QnsConstants.GUARDING_NONE) {
                isRestricted = mRestrictManager.isRestrictedExceptGuarding(transportType);
                sb.append(" isRestrictedExceptGuarding:").append(isRestricted);
            } else {
                sb.append(" isRestricted:").append(isRestricted);
            }
        } else {
            sb.append(" isRestricted:").append(isRestricted);
        }
        if (!isRestricted) {
            log(sb.toString());
            return true;
        }

        sb.append(" hasIgnorableRestriction:").append(isAllowedOnSingleTransport);
        if (!isAllowedOnSingleTransport) {
            log(sb.toString());
            return false;
        }

        sb.append(" isOtherTypeAvailable:").append(isOtherTypeAvailable);
        log(sb.toString());
        return !isOtherTypeAvailable;
    }

    protected void evaluate() {
        evaluate(EVALUATE_SPECIFIC_REASON_NONE);
    }

    protected synchronized void evaluate(int specificReason) {
        if (!mInitialized) {
            if (DBG) log("ANE is not initialized yet.");
            return;
        }
        mLastEvaluateSpecificReason = specificReason;
        log("evaluate reason:" + evaluateSpecificReasonToString(specificReason));
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            if (!mDataConnectionStatusTracker.isActiveState()) {
                log("QNS only handles HO of EMERGENCY data connection");
                return;
            }
        }

        if (mDataConnectionStatusTracker.isActiveState()) {
            /* Check handover policy */
            if (isHandoverPolicyCheckAvailable()) {
                if (!moveTransportTypeAllowed()) {
                    log("Handover is not allowed. Skip this evaluation.");
                    return;
                }
            } else {
                if (specificReason == EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE
                        && !mCellularAvailable) {
                    log("Allow evaluation without handover policy check");
                } else {
                    log("Handover policy check is not available. Skip this evaluation.");
                    return;
                }
            }
        }

        /* Check network Availability */
        boolean isAllowedForIwlan = isAllowed(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        boolean isAllowedForCellular = isAllowed(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        boolean availabilityIwlan =
                isAllowedForIwlan
                        && evaluateAvailability(
                                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, isAllowedForCellular);
        boolean availabilityCellular =
                isAllowedForCellular
                        && evaluateAvailability(
                                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, isAllowedForIwlan);
        log(" availability Iwlan:" + availabilityIwlan + " Cellular:" + availabilityCellular);

        if (mWifiBackhaulMonitor.isRttCheckEnabled()
                && mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mWifiBackhaulMonitor.setCellularAvailable(
                    availabilityCellular
                            && isAccessNetworkAllowed(mCellularAccessNetworkType, mNetCapability));
        }
        // QualifiedNetworksService checks AccessNetworkSelectionPolicy only in the case both
        // networks(cellular and iwlan) are available. If only one network is available, no
        // evaluation is run. Available network of accessNetworkType will be reported immediately.
        if (availabilityIwlan && availabilityCellular) {
            updateAccessNetworkSelectionPolicy();
            List<Integer> accessNetworkTypes =
                    evaluateAccessNetworkSelectionPolicy(availabilityIwlan, availabilityCellular);
            reportSatisfiedAccessNetworkTypesByState(accessNetworkTypes, true);
            reevaluateLastNotifiedSecondAccessNetwork();
        } else if (availabilityIwlan) {
            updateAccessNetworkSelectionPolicy();
            if (!mIsCrossWfc && hasWifiThresholdWithoutCellularCondition()) {
                List<Integer> accessNetworkTypes =
                        evaluateAccessNetworkSelectionPolicy(
                                availabilityIwlan, availabilityCellular);
                reportSatisfiedAccessNetworkTypesByState(accessNetworkTypes, true);
            } else {
                reportSatisfiedAccessNetworkTypesByState(List.of(AccessNetworkType.IWLAN), false);
            }
        } else if (availabilityCellular) {
            reportSatisfiedAccessNetworkTypesByState(
                    new ArrayList<>(List.of(mCellularAccessNetworkType)), false);
        } else {
            // TODO Is it better to report to cellular when there is nothing?
            log("evaluate nothing without an available network.");
            if (specificReason == EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE) {
                reportQualifiedNetwork(getInitialAccessNetworkTypes());
            }
        }
    }

    @VisibleForTesting
    boolean vopsCheckRequired(int cellularAccessNetworkType, int coverage, int callType) {
        boolean checkVoPs = false;
        if (mConfigManager.isMmtelCapabilityRequired(coverage)
                && (cellularAccessNetworkType == AccessNetworkType.EUTRAN
                        || cellularAccessNetworkType == AccessNetworkType.NGRAN)) {
            if (mDataConnectionStatusTracker.isInactiveState()) {
                checkVoPs = true;
            } else if (mDataConnectionStatusTracker.getLastTransportType()
                    == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                if (callType == QnsConstants.CALL_TYPE_IDLE
                        || !mConfigManager.isInCallHoDecisionWlanToWwanWithoutVopsCondition()) {
                    checkVoPs = true;
                }
            } else if (mDataConnectionStatusTracker.getLastTransportType()
                    == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                // If there is an on-going call and UE is moving into an area which does not support
                // VoPS, don't consider VoPS condition and let AccessNetworkEvaluator decide a
                // preferred RAT.
                if (callType == QnsConstants.CALL_TYPE_IDLE) {
                    checkVoPs = true;
                }
            }
        }
        return checkVoPs;
    }

    private boolean hasWifiThresholdWithoutCellularCondition() {
        if (mAccessNetworkSelectionPolicies == null || mAccessNetworkSelectionPolicies.isEmpty()) {
            return false;
        }
        for (AccessNetworkSelectionPolicy policy : mAccessNetworkSelectionPolicies) {
            if (policy.hasWifiThresholdWithoutCellularCondition()) {
                return true;
            }
        }
        return false;
    }

    protected synchronized void reportSatisfiedAccessNetworkTypesByState(
            List<Integer> satisfiedAccessNetworkTypes, boolean needMonitor) {
        if (needMonitor) {
            if (mDataConnectionStatusTracker.isInactiveState()) {
                if (satisfiedAccessNetworkTypes.size() > 0) {
                    reportQualifiedNetwork(satisfiedAccessNetworkTypes);
                    updateAccessNetworkSelectionPolicy();
                }
                updateQualityMonitor();
            } else if (!mDataConnectionStatusTracker.isConnectionInProgress()) {
                if (isHandoverNeeded(satisfiedAccessNetworkTypes)) {
                    reportQualifiedNetwork(satisfiedAccessNetworkTypes);
                    unregisterThresholdToQualityMonitor();
                } else if (isFallbackCase(satisfiedAccessNetworkTypes)) {
                    reportQualifiedNetwork(satisfiedAccessNetworkTypes);
                    updateAccessNetworkSelectionPolicy();
                    updateQualityMonitor();
                } else {
                    updateQualityMonitor();
                }
            }
        } else {
            if (mDataConnectionStatusTracker.isInactiveState()) {
                reportQualifiedNetwork(satisfiedAccessNetworkTypes);
            } else if (!mDataConnectionStatusTracker.isConnectionInProgress()) {
                if (isHandoverNeeded(satisfiedAccessNetworkTypes)) {
                    reportQualifiedNetwork(satisfiedAccessNetworkTypes);
                } else if (isFallbackCase(satisfiedAccessNetworkTypes)) {
                    reportQualifiedNetwork(satisfiedAccessNetworkTypes);
                }
            }
            unregisterThresholdToQualityMonitor();
        }
    }

    protected int getTargetTransportType(List<Integer> accessNetworkTypes) {
        if (accessNetworkTypes == null || accessNetworkTypes.size() == 0) {
            return AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        }
        if (accessNetworkTypes.get(0) == AccessNetworkType.IWLAN) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        }
        return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    }

    @VisibleForTesting
    boolean isHandoverPolicyCheckAvailable() {
        if (mDataConnectionStatusTracker.isActiveState()) {
            int srcTransportType = mDataConnectionStatusTracker.getLastTransportType();
            boolean targetNetworkAvailable =
                    srcTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                            ? mCellularAvailable
                            : mIwlanAvailable;
            if (srcTransportType == getLastQualifiedTransportType() && targetNetworkAvailable) {
                log(" handover policy check is available for this evaluation.");
                return true;
            }
        }
        log("handover policy check is not available for this evaluation.");
        return false;
    }

    @VisibleForTesting
    boolean moveTransportTypeAllowed() {
        int srcAccessNetwork =
                mDataConnectionStatusTracker.getLastTransportType()
                                == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                        ? AccessNetworkType.IWLAN
                        : mLatestAvailableCellularAccessNetwork;
        int dstAccessNetwork =
                srcAccessNetwork == AccessNetworkType.IWLAN
                        ? mCellularAccessNetworkType
                        : AccessNetworkType.IWLAN;
        if (mConfigManager.isHandoverAllowedByPolicy(
                mNetCapability, srcAccessNetwork, dstAccessNetwork, mCoverage)) {
            return true;
        } else {
            if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                    && mCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                // Telephony will make new connection with preferred AccessNetwork
                log("handover is not allowed. but need to move to target Transport.");
                return true;
            }
            if (dstAccessNetwork == AccessNetworkType.IWLAN && useDifferentApnOverIwlan()) {
                // Telephony will make new connection when change transport type
                log(
                        "handover is not allowed. but need to move to target Transport using"
                                + " different apn over IWLAN.");
                return true;
            }
            if (dstAccessNetwork != AccessNetworkType.IWLAN
                    && mCellularAvailable
                    && mConfigManager.getRatPreference(mNetCapability)
                            == QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR) {
                // Telephony will make new connection when change transport type
                log(
                        "handover is not allowed. but need to move to target Transport for the"
                                + " RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR.");
                return true;
            }
        }
        return false;
    }

    private boolean useDifferentApnOverIwlan() {
        // supports MMS, XCAP and CBS
        if (mNetCapability != NetworkCapabilities.NET_CAPABILITY_MMS
                && mNetCapability != NetworkCapabilities.NET_CAPABILITY_XCAP
                && mNetCapability != NetworkCapabilities.NET_CAPABILITY_CBS) {
            return false;
        }

        ApnSetting apnSetting =
                mDataConnectionStatusTracker.getLastApnSetting(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (apnSetting != null
                && (apnSetting.getApnTypeBitmask() & ApnSetting.TYPE_DEFAULT) != 0
                && (apnSetting.getNetworkTypeBitmask()
                                & (1 << (TelephonyManager.NETWORK_TYPE_IWLAN - 1)))
                        == 0) {
            log("useDifferentApnOverIwlan true");
            return true;
        }
        log("useDifferentApnOverIwlan false");
        return false;
    }

    protected boolean isHandoverNeeded(List<Integer> accessNetworkTypes) {
        if (mDataConnectionStatusTracker.isInactiveState()
                || mDataConnectionStatusTracker.isHandoverState()) {
            return false;
        }

        int targetTransportType = getTargetTransportType(accessNetworkTypes);
        if (targetTransportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
            return false;
        }

        // Handover case
        int sourceTransportType = mDataConnectionStatusTracker.getLastTransportType();
        return sourceTransportType != targetTransportType;
    }

    protected boolean isFallbackCase(List<Integer> accessNetworkTypes) {
        if (mDataConnectionStatusTracker.isInactiveState()) {
            return false;
        }

        int targetTransportType = getTargetTransportType(accessNetworkTypes);
        if (targetTransportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
            return false;
        }
        if (targetTransportType != mDataConnectionStatusTracker.getLastTransportType()) {
            return false;
        }

        if (getLastQualifiedTransportType() == targetTransportType) {
            return false;
        }

        log("isFallbackcase:true");
        return true;
    }

    protected void reportQualifiedNetwork(List<Integer> accessNetworkTypes) {
        if (accessNetworkTypes.contains(AccessNetworkType.UNKNOWN)) {
            log(
                    "reportQualifiedNetwork, remove UNKNOWN access network."
                            + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
            accessNetworkTypes.remove(AccessNetworkType.UNKNOWN);
        }
        List<Integer> supportAccessNetworkTypes = new ArrayList<>();
        if (mConfigManager.allowImsOverIwlanCellularLimitedCase()) {
            for (Integer accessNetwork : accessNetworkTypes) {
                if (accessNetwork == AccessNetworkType.IWLAN
                        || isAccessNetworkAllowed(accessNetwork, mNetCapability)) {
                    supportAccessNetworkTypes.add(accessNetwork);
                }
            }
            if (!accessNetworkTypes.isEmpty() && supportAccessNetworkTypes.isEmpty()) {
                log("supportAccessNetworkTypes is empty");
                return;
            }
        } else {
            supportAccessNetworkTypes.addAll(accessNetworkTypes);
        }

        log(
                "reportQualifiedNetwork supportAccessNetworkTypes:"
                        + QnsUtils.getStringAccessNetworkTypes(supportAccessNetworkTypes));

        if (equalsLastNotifiedQualifiedNetwork(supportAccessNetworkTypes)) {
            return;
        } else {
            if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                    && mWifiBackhaulMonitor.isRttCheckEnabled()) {
                if (supportAccessNetworkTypes.contains(AccessNetworkType.IWLAN)) {
                    if (!isRttSuccess()) {
                        mWifiBackhaulMonitor.requestRttCheck();
                        return;
                    }
                } else {
                    mIsRttCheckSuccess = false;
                }
            }
        }

        updateLastNotifiedQualifiedNetwork(supportAccessNetworkTypes);
        notifyForQualifiedNetworksChanged(supportAccessNetworkTypes);
    }

    private List<Threshold> findUnmatchedThresholds() {
        List<Threshold> unmatchedThresholds = new ArrayList<>();
        if (mAccessNetworkSelectionPolicies == null
                || mAccessNetworkSelectionPolicies.size() == 0) {
            return unmatchedThresholds;
        }

        List<Integer> excludeThresholdGroup = new ArrayList<>();
        boolean bIwlanRegistrable = isIwlanAvailableWithoutRestrict();
        boolean bCellularRegistrable = isCellularAvailableWithoutRestrict();
        for (AccessNetworkSelectionPolicy policy : mAccessNetworkSelectionPolicies) {
            List<Threshold> policyUnmatchedThresholds =
                    policy.findUnmatchedThresholds(mWifiQualityMonitor, mCellularQualityMonitor);
            if (policyUnmatchedThresholds == null || policyUnmatchedThresholds.size() == 0) {
                continue;
            }
            for (Threshold threshold : policyUnmatchedThresholds) {
                if (bIwlanRegistrable && threshold.getAccessNetwork() == AccessNetworkType.IWLAN) {
                    unmatchedThresholds.add(threshold);
                }
                if (bCellularRegistrable
                        && threshold.getAccessNetwork() != AccessNetworkType.IWLAN) {
                    if (threshold.getAccessNetwork() == mCellularAccessNetworkType) {
                        unmatchedThresholds.add(threshold);
                    } else if (threshold.getGroupId() >= 0) {
                        // If the current cellular access network and the access network of
                        // cellular threshold are different, it is not necessary to monitor the
                        // entire threshold of this group.
                        excludeThresholdGroup.add(threshold.getGroupId());
                    }
                }
            }
        }
        for (int excludeGid : excludeThresholdGroup) {
            unmatchedThresholds.removeIf(threshold -> threshold.getGroupId() == excludeGid);
        }
        return unmatchedThresholds;
    }

    private void updateQualityMonitor() {
        List<Threshold> unmatchedThresholds = findUnmatchedThresholds();
        if (unmatchedThresholds.size() == 0) {
            log("updateQualityMonitor empty unmatchedThresholds.");
            unregisterThresholdToQualityMonitor();
            return;
        }

        log("updateQualityMonitor");

        LinkedHashSet<Integer> unmatchedGroupIdSet = new LinkedHashSet<>();
        HashMap<Integer, Integer> unmatchedMonitorTypeMap = new HashMap<>();
        LinkedHashSet<Integer> unmatchedMonitorTypeSet = new LinkedHashSet<>();
        for (Threshold th : unmatchedThresholds) {
            if (th.getGroupId() < 0) {
                continue;
            }
            unmatchedGroupIdSet.add(th.getGroupId());
            int key = th.getAccessNetwork() << 16 | th.getMeasurementType();
            unmatchedMonitorTypeMap.put(key, unmatchedMonitorTypeMap.getOrDefault(key, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> list_entries =
                new ArrayList<>(unmatchedMonitorTypeMap.entrySet());
        list_entries.sort(Entry.comparingByValue());
        for (Entry<Integer, Integer> entry : list_entries) {
            unmatchedMonitorTypeSet.add(entry.getKey());
        }

        LinkedHashSet<Integer> reducedGroupIdSet = new LinkedHashSet<>();
        for (int type : unmatchedMonitorTypeSet) {
            reducedGroupIdSet.clear();
            boolean skipReduce = false;
            for (Threshold th : unmatchedThresholds) {
                if (type == (th.getAccessNetwork() << 16 | th.getMeasurementType())) {
                    if (th.getGroupId() < 0) {
                        skipReduce = true;
                        break;
                    }
                } else {
                    reducedGroupIdSet.add(th.getGroupId());
                }
            }
            if (skipReduce) {
                continue;
            }

            if (reducedGroupIdSet.containsAll(unmatchedGroupIdSet)) {
                unmatchedThresholds.removeIf(
                        threshold ->
                                type
                                        == (threshold.getAccessNetwork() << 16
                                                | threshold.getMeasurementType()));
            }
        }

        registerThresholdsToQualityMonitor(unmatchedThresholds);
    }

    private void unregisterThresholdToQualityMonitor() {
        mWifiQualityMonitor.updateThresholdsForNetCapability(mNetCapability, mSlotIndex, null);
        mCellularQualityMonitor.updateThresholdsForNetCapability(mNetCapability, mSlotIndex, null);
    }

    private void registerThresholdsToQualityMonitor(List<Threshold> thresholds) {
        if (thresholds == null) {
            thresholds = new ArrayList<>();
        }

        List<Threshold> monitorWiFiThresholds = new ArrayList<>();
        List<Threshold> monitorCellThresholds = new ArrayList<>();
        for (Threshold th : thresholds) {
            if (th.getAccessNetwork() == AccessNetworkType.IWLAN) {
                monitorWiFiThresholds.add(th);
            } else {
                monitorCellThresholds.add(th);
            }
        }

        for (Threshold th : monitorWiFiThresholds) {
            log("  monitorWiFiThresholds th:" + th.toShortString());
        }
        for (Threshold th : monitorCellThresholds) {
            log("  monitorCellThresholds th:" + th.toShortString());
        }

        // refresh threshold to be monitored.
        mWifiQualityMonitor.updateThresholdsForNetCapability(
                mNetCapability, mSlotIndex, monitorWiFiThresholds.toArray(new Threshold[0]));
        mCellularQualityMonitor.updateThresholdsForNetCapability(
                mNetCapability, mSlotIndex, monitorCellThresholds.toArray(new Threshold[0]));
    }

    protected void updateThrottleStatus(
            boolean isThrottle, long throttleTimeMillis, int transportType) {
        mRestrictManager.notifyThrottling(isThrottle, throttleTimeMillis, transportType);
    }

    protected int getPreferredMode() {
        if (mAllowIwlanForWfcActivation) {
            return QnsConstants.WIFI_PREF;
        }
        if (mCoverage == QnsConstants.COVERAGE_ROAM) {
            return mSettingWfcRoamingMode;
        }
        return mSettingWfcMode;
    }

    private int getPreferredAccessNetwork() {
        switch (getPreferredMode()) {
            case QnsConstants.CELL_PREF:
                return mCellularAccessNetworkType;
            case QnsConstants.WIFI_PREF:
            case QnsConstants.WIFI_ONLY:
                return AccessNetworkType.IWLAN;
        }
        return mCellularAccessNetworkType;
    }

    private boolean isRttSuccess() {
        return !isAccessNetworkAllowed(
                        mCellularAccessNetworkType, NetworkCapabilities.NET_CAPABILITY_IMS)
                || mIsRttCheckSuccess;
    }

    private List<Integer> evaluateAccessNetworkSelectionPolicy(
            boolean availabilityIwlan, boolean availabilityCellular) {
        log("evaluateAccessNetworkSelectionPolicy");

        if (mAccessNetworkSelectionPolicies == null || mAccessNetworkSelectionPolicies.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> accessNetworkTypes = new ArrayList<>();
        for (AccessNetworkSelectionPolicy policy : mAccessNetworkSelectionPolicies) {
            if (policy.satisfiedByThreshold(
                    mWifiQualityMonitor,
                    mCellularQualityMonitor,
                    availabilityIwlan,
                    availabilityCellular,
                    mCellularAccessNetworkType)) {
                log(
                        "  satisfiedByThreshold TargetTransportType:"
                                + QnsConstants.transportTypeToString(
                                        policy.getTargetTransportType()));
                if (policy.getTargetTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                    if (!accessNetworkTypes.contains(AccessNetworkType.IWLAN)) {
                        accessNetworkTypes.add(AccessNetworkType.IWLAN);
                    }

                } else {
                    if (!accessNetworkTypes.contains(mCellularAccessNetworkType)) {
                        if (availabilityCellular) {
                            accessNetworkTypes.add(mCellularAccessNetworkType);
                        } else {
                            accessNetworkTypes.add(AccessNetworkType.UNKNOWN);
                        }
                    }
                    if ((mCallType == QnsConstants.CALL_TYPE_VOICE
                                    || mCallType == QnsConstants.CALL_TYPE_EMERGENCY)
                            && policy.satisfiedWithWifiLowSignalStrength()) {
                        int reason = mConfigManager.getQnsIwlanHoRestrictReason();
                        if (reason == QnsConstants.FALLBACK_REASON_RTP_OR_WIFI
                                || reason == QnsConstants.FALLBACK_REASON_WIFI_ONLY) {
                            log("WWAN decision with Wi-Fi low signalStrength in Voice call");
                            mRestrictManager.increaseCounterToRestrictIwlanInCall();
                        }
                    }
                }
            }
        }

        // If the evaluator has not yet reported a Qualified Networks, report preferred.
        if (!isNotifiedQualifiedAccessNetworkTypes()) {
            if (accessNetworkTypes.size() == 0) {
                accessNetworkTypes.add(getPreferredAccessNetwork());
                log(
                        "  no candidate access network, use preferred:"
                                + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
            } else if (accessNetworkTypes.size() > 1) {
                accessNetworkTypes.remove(Integer.valueOf(getPreferredAccessNetwork()));
                accessNetworkTypes.add(0, getPreferredAccessNetwork());
                log(
                        "  two more candidate access network, use preferred:"
                                + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
            }
        }

        // TODO. need to be improved handling the second access network
        // Add a second access network, if the preferred access network does not exist in list.
        if (availabilityIwlan
                && availabilityCellular
                && mConfigManager.isOverrideImsPreferenceSupported()) {
            if (!accessNetworkTypes.isEmpty()
                    && accessNetworkTypes.get(0) != AccessNetworkType.IWLAN
                    && getPreferredMode() == QnsConstants.CELL_PREF
                    && isAccessNetworkAllowed(accessNetworkTypes.get(0), mNetCapability)) {
                if (!accessNetworkTypes.contains(AccessNetworkType.IWLAN)) {
                    accessNetworkTypes.add(AccessNetworkType.IWLAN);
                    log(
                            "  add a second accessNetworkTypes : "
                                    + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
                }
            } else if (accessNetworkTypes.isEmpty()
                    && !mLastQualifiedAccessNetworkTypes.isEmpty()
                    && mLastQualifiedAccessNetworkTypes.get(0) != AccessNetworkType.IWLAN
                    && getPreferredMode() == QnsConstants.CELL_PREF
                    && isAccessNetworkAllowed(
                            mLastQualifiedAccessNetworkTypes.get(0), mNetCapability)) {
                if (!mLastQualifiedAccessNetworkTypes.contains(AccessNetworkType.IWLAN)) {
                    accessNetworkTypes.addAll(mLastQualifiedAccessNetworkTypes);
                    accessNetworkTypes.add(AccessNetworkType.IWLAN);
                    log(
                            "  add a second accessNetworkTypes in existing "
                                    + "mLastQualifiedAccessNetworkTypes : "
                                    + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
                }
            }
        }

        log("  accessNetworkTypes:" + QnsUtils.getStringAccessNetworkTypes(accessNetworkTypes));
        return accessNetworkTypes;
    }

    // TODO. need to be improved handling the second access network
    // A qualified conditions are required for the second access network.
    // What threshold conditions are qualified for the second access network? (like rove in)
    // What are the threshold conditions for exiting the second access network? (like a rove out)
    private void reevaluateLastNotifiedSecondAccessNetwork() {
        if (mConfigManager.isOverrideImsPreferenceSupported()
                && mLastQualifiedAccessNetworkTypes.size() > 1
                && mLastQualifiedAccessNetworkTypes.get(1) == AccessNetworkType.IWLAN) {
            if (!isAccessNetworkAllowed(mCellularAccessNetworkType, mNetCapability)
                    || getPreferredMode() != QnsConstants.CELL_PREF) {
                log("reevaluateLastNotifiedSecondAccessNetwork, Removed a second access network");
                reportQualifiedNetwork(
                        new ArrayList<>(List.of(mLastQualifiedAccessNetworkTypes.get(0))));
            }
        }
    }

    private Map<PreCondition, List<AccessNetworkSelectionPolicy>> buildAccessNetworkSelectionPolicy(
            boolean bForceUpdate) {
        if (mAnspPolicyMap == null || bForceUpdate) {
            log("Building list of AccessNetworkSelectionPolicy.");
            mAnspPolicyMap =
                    AccessNetworkSelectionPolicyBuilder.build(mConfigManager, mNetCapability);

            if (DBG) {
                mAnspPolicyMap
                        .values()
                        .forEach(
                                list ->
                                        list.stream()
                                                .map(policy -> " retriMap ANSP=" + policy)
                                                .forEach(this::log));
            }
        }
        return mAnspPolicyMap;
    }

    private void updateAccessNetworkSelectionPolicy() {
        Map<PreCondition, List<AccessNetworkSelectionPolicy>> map =
                buildAccessNetworkSelectionPolicy(false);
        if (map == null) {
            map = new HashMap<>();
        }

        log("Create a list of AccessNetworkSelectionPolicy that match the preconditions.");

        PreCondition preCondition = getMatchingPreCondition();
        List<AccessNetworkSelectionPolicy> matchedPolicies = new ArrayList<>();
        List<AccessNetworkSelectionPolicy> list = map.get(preCondition);
        if (list != null) {
            for (AccessNetworkSelectionPolicy policy : list) {
                if (policy.satisfyPrecondition(preCondition)
                        && !mDataConnectionStatusTracker.isConnectionInProgress()
                        && (!isNotifiedQualifiedAccessNetworkTypes()
                                || getLastQualifiedTransportType()
                                        != policy.getTargetTransportType())) {
                    matchedPolicies.add(policy);
                }
            }
        }

        if (mAccessNetworkSelectionPolicies.equals(matchedPolicies)) {
            for (AccessNetworkSelectionPolicy policy : matchedPolicies) {
                log("  Matched ANSP=" + policy);
            }
        }

        for (AccessNetworkSelectionPolicy policy : matchedPolicies) {
            log("  Found new ANSP=" + policy);
        }

        mAccessNetworkSelectionPolicies = matchedPolicies;
    }

    private PreCondition getMatchingPreCondition() {
        int callType = mCallType;
        if ((mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS
                        || mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS)
                && mCallType == QnsConstants.CALL_TYPE_EMERGENCY) {
            callType = QnsConstants.CALL_TYPE_VOICE;
        }
        int sipDialogPolicy = mConfigManager.getSipDialogSessionPolicy();
        if (callType == QnsConstants.CALL_TYPE_IDLE && mSipDialogSessionState) {
            if (sipDialogPolicy > QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE) {
                log("apply sipDialogPolicy:"
                        + QnsConstants.qnsSipDialogSessionPolicyToString(sipDialogPolicy));
            }
            switch (sipDialogPolicy) {
                case QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL:
                    callType = QnsConstants.CALL_TYPE_VOICE;
                    break;
                case QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL:
                    callType = QnsConstants.CALL_TYPE_VIDEO;
                    break;
                case QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE:
                    // fall-through
                default:
                    // do-nothing
                    break;
            }
        }

        if (mConfigManager.hasThresholdGapWithGuardTimer()) {
            @QnsConstants.QnsGuarding int guarding = QnsConstants.GUARDING_NONE;
            int source = getLastQualifiedTransportType();
            if (source != AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                    && mRestrictManager.hasRestrictionType(
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                            RestrictManager.RESTRICT_TYPE_GUARDING)) {
                guarding = QnsConstants.GUARDING_WIFI;
            } else if (source != AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                    && mRestrictManager.hasRestrictionType(
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                            RestrictManager.RESTRICT_TYPE_GUARDING)) {
                guarding = QnsConstants.GUARDING_CELLULAR;
            }
            return new GuardingPreCondition(callType, getPreferredMode(), mCoverage, guarding);
        }
        return new PreCondition(callType, getPreferredMode(), mCoverage);
    }

    private String evaluateSpecificReasonToString(int specificReason) {
        if (specificReason == EVALUATE_SPECIFIC_REASON_NONE) {
            return "EVALUATE_SPECIFIC_REASON_NONE";
        } else if (specificReason == EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE) {
            return "EVALUATE_SPECIFIC_REASON_IWLAN_DISABLE";
        } else if (specificReason == EVALUATE_SPECIFIC_REASON_DATA_DISCONNECTED) {
            return "EVALUATE_SPECIFIC_REASON_DATA_DISCONNECTED";
        } else if (specificReason == EVALUATE_SPECIFIC_REASON_DATA_FAILED) {
            return "EVALUATE_SPECIFIC_REASON_DATA_FAILED";
        } else if (specificReason == EVALUATE_SPECIFIC_REASON_DATA_CONNECTED) {
            return "EVALUATE_SPECIFIC_REASON_DATA_CONNECTED";
        }
        return "UNKNOWN";
    }

    private boolean isCrossWfcAllowed(boolean cellularAvailable) {
        if (mConfigManager.getRatPreference(mNetCapability)
                == QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE) {
            // Should follow RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE and do not block CST when IMS
            // connected to CST
            return true;
        }
        return !cellularAvailable;
    }

    private class EvaluatorEventHandler extends Handler {
        EvaluatorEventHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message message) {
            log("handleMessage msg=" + message.what);
            QnsAsyncResult ar = (QnsAsyncResult) message.obj;
            switch (message.what) {
                case EVENT_IWLAN_NETWORK_STATUS_CHANGED:
                    onIwlanNetworkStatusChanged((IwlanAvailabilityInfo) ar.mResult);
                    break;
                case EVENT_QNS_TELEPHONY_INFO_CHANGED:
                    onQnsTelephonyInfoChanged((QnsTelephonyListener.QnsTelephonyInfo) ar.mResult);
                    break;
                case EVENT_RESTRICT_INFO_CHANGED:
                    onRestrictInfoChanged();
                    break;
                case EVENT_SET_CALL_TYPE:
                    onSetCallType((int) ar.mResult);
                    break;
                case EVENT_DATA_CONNECTION_STATE_CHANGED:
                    onDataConnectionStateChanged(
                            (DataConnectionStatusTracker.DataConnectionChangedInfo) ar.mResult);
                    break;
                case EVENT_PROVISIONING_INFO_CHANGED:
                    onProvisioningInfoChanged(
                            (QnsProvisioningListener.QnsProvisioningInfo) ar.mResult);
                    break;
                case EVENT_IMS_REGISTRATION_STATE_CHANGED:
                    onImsRegStateChanged((QnsImsManager.ImsRegistrationState) ar.mResult);
                    break;
                case EVENT_SIP_DIALOG_SESSION_STATE_CHANGED:
                    onSipDialogSessionStateChanged((boolean) ar.mResult);
                    break;
                case EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED:
                    onImsCallDisconnectCauseChanged((ImsReasonInfo) ar.mResult);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ENABLED:
                    onWfcEnabledChanged(true, false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_DISABLED:
                    onWfcEnabledChanged(false, false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY:
                    onWfcModeChanged(QnsConstants.WIFI_ONLY, false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED:
                    onWfcModeChanged(QnsConstants.CELL_PREF, false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED:
                    onWfcModeChanged(QnsConstants.WIFI_PREF, false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED:
                    onWfcEnabledChanged(true, true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED:
                    onWfcEnabledChanged(false, true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY:
                    onWfcModeChanged(QnsConstants.WIFI_ONLY, true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED:
                    onWfcModeChanged(QnsConstants.CELL_PREF, true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED:
                    onWfcModeChanged(QnsConstants.WIFI_PREF, true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED:
                    onWfcPlatformChanged(true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_DISABLED:
                    onWfcPlatformChanged(false);
                    break;
                case QnsEventDispatcher.QNS_EVENT_SIM_ABSENT:
                    onSimAbsent();
                    break;
                case EVENT_WIFI_RTT_STATUS_CHANGED:
                    onRttStatusChanged((boolean) ar.mResult);
                    break;
                case QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION:
                    onTryWfcConnectionStateChanged(true);
                    break;
                case QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION:
                    onTryWfcConnectionStateChanged(false);
                    break;
                default:
                    log("never reach here msg=" + message.what);
            }
        }
    }

    private class ThresholdListener extends ThresholdCallback
            implements ThresholdCallback.WifiThresholdListener,
                    ThresholdCallback.CellularThresholdListener {

        ThresholdListener(Executor executor) {
            this.init(executor);
        }

        @Override
        public void onWifiThresholdChanged(Threshold[] thresholds) {
            onWiFiQualityChanged(thresholds);
        }

        @Override
        public void onCellularThresholdChanged(Threshold[] thresholds) {
            onCellularQualityChanged(thresholds);
        }
    }

    /**
     * Dumps the state of {@link QualityMonitor}
     *
     * @param pw {@link PrintWriter} to write the state of the object.
     * @param prefix String to append at start of dumped log.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(
                prefix
                        + "ANE["
                        + QnsUtils.getNameOfNetCapability(mNetCapability)
                        + "_"
                        + mSlotIndex
                        + "]:");
        pw.println(prefix + "mInitialized=" + mInitialized);
        pw.println(
                prefix
                        + "mQualifiedNetworksChangedRegistrant size="
                        + mQualifiedNetworksChangedRegistrants.size());
        pw.println(
                prefix
                        + "mCellularAccessNetworkType="
                        + QnsConstants.accessNetworkTypeToString(mCellularAccessNetworkType)
                        + ", mLatestAvailableCellularAccessNetwork="
                        + QnsConstants.accessNetworkTypeToString(
                                mLatestAvailableCellularAccessNetwork)
                        + ", mIsNotifiedLastQualifiedAccessNetworkTypes="
                        + mIsNotifiedLastQualifiedAccessNetworkTypes);
        pw.print(prefix + "mLastQualifiedAccessNetworkTypes=");
        mLastQualifiedAccessNetworkTypes.forEach(
                accessNetwork ->
                        pw.print(QnsConstants.accessNetworkTypeToString(accessNetwork) + "|"));
        pw.println(
                ", mIsNotifiedLastQualifiedAccessNetworkTypes="
                        + mIsNotifiedLastQualifiedAccessNetworkTypes);
        pw.println(
                prefix
                        + "mCellularAvailable="
                        + mCellularAvailable
                        + ", mIwlanAvailable="
                        + mIwlanAvailable
                        + ", mIsCrossWfc="
                        + QnsConstants.callTypeToString(mCallType)
                        + ", mCoverage"
                        + QnsConstants.coverageToString(mCoverage));
        pw.println(
                prefix
                        + "mWfcPlatformEnabled="
                        + mWfcPlatformEnabled
                        + ", mSettingWfcEnabled="
                        + mSettingWfcEnabled
                        + ", mSettingWfcMode="
                        + QnsConstants.preferenceToString(mSettingWfcMode)
                        + ", mSettingWfcRoamingEnabled="
                        + mSettingWfcRoamingEnabled
                        + ", mSettingWfcRoamingMode="
                        + QnsConstants.preferenceToString(mSettingWfcRoamingMode)
                        + ", mAllowIwlanForWfcActivation="
                        + mAllowIwlanForWfcActivation);
        pw.println(prefix + "mLastProvisioningInfo=" + mLastProvisioningInfo);
        pw.println(prefix + "mAccessNetworkSelectionPolicies=" + mAccessNetworkSelectionPolicies);
        pw.println(prefix + "mAnspPolicyMap=" + mAnspPolicyMap);
        pw.println(prefix + "mCachedTransportTypeForEmergencyInitialConnect"
                + mCachedTransportTypeForEmergencyInitialConnect);
        mRestrictManager.dump(pw, prefix + "  ");
    }

    @VisibleForTesting
    boolean getSipDialogSessionState() {
        return mSipDialogSessionState;
    }

    private void sendMetricsForQualifiedNetworks(QualifiedNetworksInfo info) {
        if (!mCellularAvailable
                || !mIwlanAvailable
                || mCellularAccessNetworkType == AccessNetworkType.UNKNOWN
                || mLastEvaluateSpecificReason == EVALUATE_SPECIFIC_REASON_DATA_DISCONNECTED) {
            // b/268557926, decided to cut off if WWAN and WLAN are not in contention.
            return;
        }
        mQnsMetrics.reportAtomForQualifiedNetworks(
                info,
                mSlotIndex,
                mDataConnectionStatusTracker.getLastTransportType(),
                mCoverage,
                mSettingWfcEnabled,
                mSettingWfcRoamingEnabled,
                mSettingWfcMode,
                mSettingWfcRoamingMode,
                mCellularAccessNetworkType,
                mIwlanAvailable,
                mIsCrossWfc,
                mRestrictManager,
                mCellularQualityMonitor,
                mWifiQualityMonitor,
                mCallType);
    }

    private void sendMetricsForCallTypeChanged(int oldCallType, int newCallType) {
        int transportTypeOfCall = mDataConnectionStatusTracker.getLastTransportType();
        mQnsMetrics.reportAtomForCallTypeChanged(mNetCapability, mSlotIndex,
                oldCallType, newCallType, mRestrictManager, transportTypeOfCall);
    }

    private void sendMetricsForDataConnectionChanged(
            DataConnectionStatusTracker.DataConnectionChangedInfo info) {
        mQnsMetrics.reportAtomForDataConnectionChanged(
                mNetCapability, mSlotIndex, info, mConfigManager.getCarrierId());
    }

    private void sendMetricsForImsCallDropStats() {
        int transportTypeOfCall = mDataConnectionStatusTracker.getLastTransportType();
        mQnsMetrics.reportAtomForImsCallDropStats(mNetCapability, mSlotIndex, mRestrictManager,
                mCellularQualityMonitor, mWifiQualityMonitor, transportTypeOfCall,
                mCellularAccessNetworkType);
    }

}
