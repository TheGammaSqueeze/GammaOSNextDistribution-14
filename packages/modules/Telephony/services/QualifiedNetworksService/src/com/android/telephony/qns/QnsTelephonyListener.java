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

import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY;
import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE;

import android.annotation.NonNull;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.BarringInfo;
import android.telephony.CallState;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NrVopsSupportInfo;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.VopsSupportInfo;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.MediaQualityStatus;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/*
 * QnsTelephonyListener
 * Tracks Cellular ServiceState per each slot.
 */
class QnsTelephonyListener {

    private static final Archiving<PreciseDataConnectionState>
            sArchivingPreciseDataConnectionState = new Archiving<>();
    private final String mLogTag;
    private final int mSlotIndex;
    private final Context mContext;
    private final SubscriptionManager mSubscriptionManager;
    private final Handler mHandler;
    private final HandlerThread mHandlerThread;
    QnsRegistrantList mCallStateListener = new QnsRegistrantList();
    QnsRegistrantList mSrvccStateListener = new QnsRegistrantList();
    QnsRegistrantList mSubscriptionIdListener = new QnsRegistrantList();
    QnsRegistrantList mIwlanServiceStateListener = new QnsRegistrantList();
    QnsRegistrantList mImsCallDropDisconnectCauseListener = new QnsRegistrantList();
    List<Consumer<List<CallState>>> mCallStatesConsumerList = new ArrayList<>();
    List<Consumer<Integer>> mSrvccStateConsumerList = new ArrayList<>();
    List<Consumer<MediaQualityStatus>> mMediaQualityConsumerList = new ArrayList<>();
    protected ConcurrentHashMap<Integer, QnsRegistrantList> mQnsTelephonyInfoRegistrantMap =
            new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Integer, QnsRegistrantList> mNetCapabilityRegistrantMap =
            new ConcurrentHashMap<>();
    protected QnsTelephonyInfo mLastQnsTelephonyInfo = new QnsTelephonyInfo();
    protected QnsTelephonyInfoIms mLastQnsTelephonyInfoIms = new QnsTelephonyInfoIms();
    protected ServiceState mLastServiceState = new ServiceState();
    protected HashMap<Integer, PreciseDataConnectionState> mLastPreciseDataConnectionState =
            new HashMap<>();
    private int mSubId;
    @VisibleForTesting TelephonyListener mTelephonyListener;
    private int mCoverage;
    @Annotation.CallState private int mCallState;

    @VisibleForTesting
    final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionsChangeListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    int newSubId = QnsUtils.getSubId(mContext, mSlotIndex);
                    if ((mSubId != newSubId)
                            && (newSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
                        stopTelephonyListener(mSubId); // old
                        mSubId = newSubId;
                        onSubscriptionIdChanged(newSubId);
                        startTelephonyListener(newSubId); // new
                    }
                }
            };

    /** Default constructor. */
    QnsTelephonyListener(@NonNull Context context, int slotIndex) {
        mLogTag = QnsTelephonyListener.class.getSimpleName() + "_" + slotIndex;
        mSlotIndex = slotIndex;
        mContext = context;

        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mHandlerThread = new HandlerThread(QnsTelephonyListener.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mSubId = QnsUtils.getSubId(mContext, mSlotIndex);
        startTelephonyListener(mSubId);

        if (mSubscriptionManager != null) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(
                    new QnsUtils.QnsExecutor(mHandler), mSubscriptionsChangeListener);
        }
    }

    private static int registrationStateToServiceState(int registrationState) {
        switch (registrationState) {
            case NetworkRegistrationInfo.REGISTRATION_STATE_HOME:
            case NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING:
                return ServiceState.STATE_IN_SERVICE;
            default:
                return ServiceState.STATE_OUT_OF_SERVICE;
        }
    }

    protected void notifyQnsTelephonyInfo(QnsTelephonyInfo info) {
        QnsAsyncResult ar;
        for (Integer netCapability : mQnsTelephonyInfoRegistrantMap.keySet()) {
            if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                    || netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                ar = new QnsAsyncResult(null, mLastQnsTelephonyInfoIms, null);
            } else {
                ar = new QnsAsyncResult(null, info, null);
            }
            mQnsTelephonyInfoRegistrantMap.get(netCapability).notifyRegistrants(ar);
        }
    }

    protected void notifyQnsTelephonyInfoIms(QnsTelephonyInfoIms info) {
        QnsAsyncResult ar = new QnsAsyncResult(null, info, null);
        QnsRegistrantList imsRegList =
                mQnsTelephonyInfoRegistrantMap.get(NetworkCapabilities.NET_CAPABILITY_IMS);
        QnsRegistrantList sosRegList =
                mQnsTelephonyInfoRegistrantMap.get(NetworkCapabilities.NET_CAPABILITY_EIMS);
        if (imsRegList != null) {
            imsRegList.notifyRegistrants(ar);
        }
        if (sosRegList != null) {
            sosRegList.notifyRegistrants(ar);
        }
    }

    protected void notifyIwlanServiceStateInfo(boolean isInService) {
        mIwlanServiceStateListener.notifyResult(isInService);
    }

    protected void notifyPreciseDataConnectionStateChanged(
            PreciseDataConnectionState connectionState) {
        List<Integer> netCapabilities =
                QnsUtils.getNetCapabilitiesFromApnTypeBitmask(
                        connectionState.getApnSetting().getApnTypeBitmask());
        QnsAsyncResult ar = new QnsAsyncResult(null, connectionState, null);
        for (int netCapability : netCapabilities) {
            PreciseDataConnectionState lastState = getLastPreciseDataConnectionState(netCapability);
            if (lastState == null || !lastState.equals(connectionState)) {
                mLastPreciseDataConnectionState.put(netCapability, connectionState);
                sArchivingPreciseDataConnectionState.put(
                        mSubId, connectionState.getTransportType(), netCapability, connectionState);
                QnsRegistrantList netCapabilityRegistrantList =
                        mNetCapabilityRegistrantMap.get(netCapability);
                if (netCapabilityRegistrantList != null) {
                    netCapabilityRegistrantList.notifyRegistrants(ar);
                }
            } else {
                log(
                        "onPreciseDataConnectionStateChanged state received for netCapability is"
                                + " same:"
                                + netCapability);
            }
        }
    }

    /** Get a last QnsTelephonyInfo notified previously. */
    QnsTelephonyInfo getLastQnsTelephonyInfo() {
        return mLastQnsTelephonyInfo;
    }

    /** Get a last of the precise data connection state per netCapability. */
    PreciseDataConnectionState getLastPreciseDataConnectionState(int netCapability) {
        return mLastPreciseDataConnectionState.get(netCapability);
    }

    /**
     * Register an event for QnsTelephonyInfo changed.
     *
     * @param netCapability Network Capability to be notified.
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     * @param notifyImmediately set true if want to notify immediately.
     */
    void registerQnsTelephonyInfoChanged(
            int netCapability, Handler h, int what, Object userObj, boolean notifyImmediately) {
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            QnsRegistrantList netCapabilityRegistrantList =
                    mQnsTelephonyInfoRegistrantMap.get(netCapability);
            if (netCapabilityRegistrantList == null) {
                netCapabilityRegistrantList = new QnsRegistrantList();
                mQnsTelephonyInfoRegistrantMap.put(netCapability, netCapabilityRegistrantList);
            }
            netCapabilityRegistrantList.add(r);

            if (notifyImmediately) {
                r.notifyRegistrant(new QnsAsyncResult(null, getLastQnsTelephonyInfo(), null));
            }
        }
    }

    /**
     * Register an event for Precise Data Connection State Changed.
     *
     * @param netCapability Network Capability to be notified.
     * @param h the handler to get event.
     * @param what the event.
     */
    void registerPreciseDataConnectionStateChanged(
            int netCapability, Handler h, int what, Object userObj, boolean notifyImmediately) {
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            QnsRegistrantList netCapabilityRegistrantList =
                    mNetCapabilityRegistrantMap.get(netCapability);
            if (netCapabilityRegistrantList == null) {
                netCapabilityRegistrantList = new QnsRegistrantList();
                mNetCapabilityRegistrantMap.put(netCapability, netCapabilityRegistrantList);
            }
            netCapabilityRegistrantList.add(r);

            PreciseDataConnectionState pdcs = getLastPreciseDataConnectionState(netCapability);
            if (notifyImmediately && pdcs != null) {
                r.notifyRegistrant(new QnsAsyncResult(null, pdcs, null));
            }
        }
    }

    /**
     * Register an event for CallState changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     * @param notifyImmediately set true if want to notify immediately.
     */
    void registerCallStateListener(Handler h, int what, Object userObj, boolean notifyImmediately) {
        log("registerCallStateListener");
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mCallStateListener.add(r);

            if (notifyImmediately) {
                r.notifyRegistrant(new QnsAsyncResult(null, mCallState, null));
            }
        }
    }

    /**
     * Register an event for SRVCC state changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     */
    void registerSrvccStateListener(Handler h, int what, Object userObj) {
        log("registerSrvccStateListener");
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mSrvccStateListener.add(r);
        }
    }

    /**
     * Register an event for subscription Id changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     */
    void registerSubscriptionIdListener(Handler h, int what, Object userObj) {
        log("registerSubscriptionIdListener");
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mSubscriptionIdListener.add(r);
        }
    }

    /**
     * Register an event for iwlan service state changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     */
    void registerIwlanServiceStateListener(Handler h, int what, Object userObj) {
        log("registerIwlanServiceStateListener");
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mIwlanServiceStateListener.add(r);

            NetworkRegistrationInfo lastIwlanNrs =
                    mLastServiceState.getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
            if (lastIwlanNrs != null) {
                r.notifyRegistrant(
                        new QnsAsyncResult(null, lastIwlanNrs.isNetworkRegistered(), null));
            }
        }
    }

    /**
     * Register an event for ImsCallDropDisconnectCause changed.
     *
     * @param h the Handler to get event.
     * @param what the event.
     * @param userObj user object.
     */
    void registerImsCallDropDisconnectCauseListener(Handler h, int what, Object userObj) {
        log("registerImsCallDropDisconnectCauseListener");
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            mImsCallDropDisconnectCauseListener.add(r);
        }
    }

    /**
     * Unregister an event for QnsTelephonyInfo changed.
     *
     * @param netCapability Network Capability to be notified.
     * @param h the handler to get event.
     */
    void unregisterQnsTelephonyInfoChanged(int netCapability, Handler h) {
        if (h != null) {
            QnsRegistrantList netCapabilityRegistrantList =
                    mQnsTelephonyInfoRegistrantMap.get(netCapability);
            if (netCapabilityRegistrantList != null) {
                netCapabilityRegistrantList.remove(h);
            }
        }
    }

    /**
     * Unregister an event for Precise Data Connectio State Changed.
     *
     * @param netCapability Network Capability to be notified.
     * @param h the handler to get event.
     */
    void unregisterPreciseDataConnectionStateChanged(int netCapability, Handler h) {
        if (h != null) {
            QnsRegistrantList netCapabilityRegistrantList =
                    mNetCapabilityRegistrantMap.get(netCapability);
            if (netCapabilityRegistrantList != null) {
                netCapabilityRegistrantList.remove(h);
            }
        }
    }

    /**
     * Unregister an event for CallState changed.
     *
     * @param h the handler to get event.
     */
    void unregisterCallStateChanged(Handler h) {
        log("unregisterCallStateChanged");
        if (h != null) {
            mCallStateListener.remove(h);
        }
    }

    /**
     * Unregister an event for SRVCC state changed.
     *
     * @param h the handler to get event.
     */
    void unregisterSrvccStateChanged(Handler h) {
        log("unregisterSrvccStateChanged");
        if (h != null) {
            mSrvccStateListener.remove(h);
        }
    }

    /**
     * Unregister an event for subscription Id changed.
     *
     * @param h the handler to get event.
     */
    void unregisterSubscriptionIdChanged(Handler h) {
        log("unregisterSubscriptionIdChanged");
        if (h != null) {
            mSubscriptionIdListener.remove(h);
        }
    }

    /**
     * Unregister an event for iwlan service state changed.
     *
     * @param h the handler to get event.
     */
    void unregisterIwlanServiceStateChanged(Handler h) {
        log("unregisterIwlanServiceStateChanged");
        if (h != null) {
            mIwlanServiceStateListener.remove(h);
        }
    }

    /**
     * Unregister an event for ImsCallDropDisconnectCause state changed.
     *
     * @param h the handler to get event.
     */
    void unregisterImsCallDropDisconnectCauseListener(Handler h) {
        log("unregisterImsCallDropDisconnectCauseListener");
        if (h != null) {
            mImsCallDropDisconnectCauseListener.remove(h);
        }
    }

    private void createTelephonyListener() {
        if (mTelephonyListener == null) {
            mTelephonyListener = new TelephonyListener(mContext.getMainExecutor());
            mTelephonyListener.setServiceStateListener(
                    (ServiceState serviceState) -> {
                        onServiceStateChanged(serviceState);
                    });
            mTelephonyListener.setPreciseDataConnectionStateListener(
                    (PreciseDataConnectionState connectionState) -> {
                        onPreciseDataConnectionStateChanged(connectionState);
                    });
            mTelephonyListener.setBarringInfoListener(
                    (BarringInfo barringInfo) -> {
                        onBarringInfoChanged(barringInfo);
                    });
            mTelephonyListener.setCallStateListener(
                    (int callState) -> {
                        onCallStateChanged(callState);
                    });
            mTelephonyListener.setSrvccStateListener(
                    (int srvccState) -> {
                        onSrvccStateChanged(srvccState);
                    });
            mTelephonyListener.setImsCallDisconnectCauseListener(
                    (ImsReasonInfo imsReasonInfo) -> {
                        onImsCallDisconnectCauseChanged(imsReasonInfo);
                    });
        }
    }

    protected void stopTelephonyListener(int subId) {
        if (mTelephonyListener == null) {
            return;
        }
        mTelephonyListener.unregister(subId);
        cleanupQnsTelephonyListenerSettings();
    }

    protected void startTelephonyListener(int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }
        if (mTelephonyListener == null) {
            createTelephonyListener();
        }
        mTelephonyListener.register(mContext, subId);
    }

    private void cleanupQnsTelephonyListenerSettings() {
        log("cleanupQnsTelephonyListenerSettings");
        mLastQnsTelephonyInfo = new QnsTelephonyInfo();
        mLastQnsTelephonyInfoIms = new QnsTelephonyInfoIms();
        mLastServiceState = new ServiceState();
        mLastPreciseDataConnectionState = new HashMap<>();
        // mCoverage, keep previous state.
        mCallState = QnsConstants.CALL_TYPE_IDLE;
    }

    protected void onServiceStateChanged(ServiceState serviceState) {
        QnsTelephonyInfo newInfo = new QnsTelephonyInfo(mLastQnsTelephonyInfo);

        NetworkRegistrationInfo newIwlanNrs =
                serviceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        NetworkRegistrationInfo oldIwlanNrs =
                mLastServiceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        if (newIwlanNrs != null
                && (oldIwlanNrs == null
                        || newIwlanNrs.isNetworkRegistered()
                                != oldIwlanNrs.isNetworkRegistered())) {
            log("Iwlan is in service: " + newIwlanNrs.isNetworkRegistered());
            notifyIwlanServiceStateInfo(newIwlanNrs.isNetworkRegistered());
        }

        NetworkRegistrationInfo newWwanNrs =
                serviceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        NetworkRegistrationInfo oldWwanNrs =
                mLastServiceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        NetworkRegistrationInfo newWwanCsNrs =
                serviceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Event for voice registration state changed
        if (newWwanCsNrs != null) {
            newInfo.setVoiceNetworkType(newWwanCsNrs.getAccessNetworkTechnology());
        }

        // Event for cellular data tech changed
        if (newWwanNrs != null) {
            newInfo.setDataNetworkType(newWwanNrs.getAccessNetworkTechnology());
            newInfo.setDataRegState(
                    registrationStateToServiceState(newWwanNrs.getNetworkRegistrationState()));

            // Event for cellular data roaming registration state changed.
            // Refer roaming state which is not overridden by configs.
            if (!newWwanNrs.isNetworkRoaming()) {
                mCoverage = QnsConstants.COVERAGE_HOME;
            } else {
                mCoverage = QnsConstants.COVERAGE_ROAM;
            }
            newInfo.setRegisteredPlmn(newWwanNrs.getRegisteredPlmn());
            newInfo.setCoverage(mCoverage == QnsConstants.COVERAGE_ROAM);
        } else {
            newInfo.setRegisteredPlmn(null);
        }

        // Event for cellular ps attach state changed.
        boolean hasAirplaneModeOnChanged =
                mLastServiceState.getState() != ServiceState.STATE_POWER_OFF
                        && serviceState.getState() == ServiceState.STATE_POWER_OFF;
        if ((oldWwanNrs == null || !oldWwanNrs.isNetworkRegistered() || hasAirplaneModeOnChanged)
                && (newWwanNrs != null && newWwanNrs.isNetworkRegistered())) {
            newInfo.setCellularAvailable(true);
        }
        if ((oldWwanNrs != null && oldWwanNrs.isNetworkRegistered())
                && (newWwanNrs == null || !newWwanNrs.isNetworkRegistered())) {
            newInfo.setCellularAvailable(false);
        }

        // Event for VOPS changed
        boolean vopsSupport = isSupportVoPS(newWwanNrs);
        boolean vopsEmergencySupport = isSupportEmergencyService(newWwanNrs);
        boolean vopsSupportChanged = vopsSupport != mLastQnsTelephonyInfoIms.getVopsSupport();
        boolean vopsEmergencySupportChanged =
                vopsEmergencySupport != mLastQnsTelephonyInfoIms.getVopsEmergencySupport();

        if (!newInfo.equals(mLastQnsTelephonyInfo)) {
            StringBuilder sb = new StringBuilder();
            if (newInfo.getVoiceNetworkType() != mLastQnsTelephonyInfo.getVoiceNetworkType()) {
                sb.append(" voiceTech:").append(newInfo.getVoiceNetworkType());
            }
            if (newInfo.getDataNetworkType() != mLastQnsTelephonyInfo.getDataNetworkType()) {
                sb.append(" dataTech:").append(newInfo.getDataNetworkType());
            }
            if (newInfo.getDataRegState() != mLastQnsTelephonyInfo.getDataRegState()) {
                sb.append(" dataRegState:").append(newInfo.getDataRegState());
            }
            if (newInfo.isCoverage() != mLastQnsTelephonyInfo.isCoverage()) {
                sb.append(" coverage:").append(newInfo.isCoverage() ? "ROAM" : "HOME");
            }
            if (newInfo.isCellularAvailable() != mLastQnsTelephonyInfo.isCellularAvailable()) {
                sb.append(" cellAvailable:").append(newInfo.isCellularAvailable());
            }
            if (vopsSupportChanged) {
                sb.append(" VOPS support:").append(vopsSupport);
            }
            if (vopsSupportChanged) {
                sb.append(" VOPS emergency support:").append(vopsEmergencySupport);
            }

            log("onCellularServiceStateChanged QnsTelephonyInfo:" + sb);

            mLastQnsTelephonyInfo = newInfo;
            mLastQnsTelephonyInfoIms =
                    new QnsTelephonyInfoIms(
                            newInfo,
                            vopsSupport,
                            vopsEmergencySupport,
                            mLastQnsTelephonyInfoIms.getVoiceBarring(),
                            mLastQnsTelephonyInfoIms.getEmergencyBarring());
            mLastServiceState = serviceState;
            notifyQnsTelephonyInfo(newInfo);
        } else {
            if (vopsSupportChanged || vopsEmergencySupportChanged) {
                log("onCellularServiceStateChanged VoPS enabled" + vopsSupport);
                log("onCellularServiceStateChanged VoPS EMC support" + vopsEmergencySupport);
                mLastQnsTelephonyInfoIms.setVopsSupport(vopsSupport);
                mLastQnsTelephonyInfoIms.setVopsEmergencySupport(vopsEmergencySupport);
                notifyQnsTelephonyInfoIms(mLastQnsTelephonyInfoIms);
            }
        }
        mLastServiceState = serviceState;
    }

    boolean isAirplaneModeEnabled() {
        return mLastServiceState.getState() == ServiceState.STATE_POWER_OFF;
    }

    boolean isSupportVoPS() {
        try {
            NetworkRegistrationInfo networkRegInfo =
                    mLastServiceState.getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            return isSupportVoPS(networkRegInfo);
        } catch (NullPointerException e) {
            return false;
        }
    }

    private boolean isSupportVoPS(NetworkRegistrationInfo nri) {
        try {
            VopsSupportInfo vopsInfo = nri.getDataSpecificInfo().getVopsSupportInfo();
            if (vopsInfo instanceof NrVopsSupportInfo) {
                NrVopsSupportInfo nrVopsInfo = (NrVopsSupportInfo) vopsInfo;
                return nrVopsInfo.getVopsSupport()
                        == NrVopsSupportInfo.NR_STATUS_VOPS_3GPP_SUPPORTED;
            } else {
                return vopsInfo.isVopsSupported();
            }
        } catch (NullPointerException e) {
            return false;
        }
    }

    boolean isSupportEmergencyService() {
        return mLastQnsTelephonyInfoIms.getVopsEmergencySupport();
    }

    private boolean isSupportEmergencyService(NetworkRegistrationInfo networkRegInfo) {
        try {
            VopsSupportInfo vopsInfo = networkRegInfo.getDataSpecificInfo().getVopsSupportInfo();
            if (vopsInfo instanceof NrVopsSupportInfo) {
                NrVopsSupportInfo nrVopsInfo = (NrVopsSupportInfo) vopsInfo;
                return nrVopsInfo.isEmergencyServiceSupported();
            } else {
                return vopsInfo.isEmergencyServiceSupported();
            }
        } catch (NullPointerException e) {
            return false;
        }
    }

    protected void onPreciseDataConnectionStateChanged(PreciseDataConnectionState connectionState) {
        if (!validatePreciseDataConnectionStateChanged(connectionState)) {
            log("invalid onPreciseDataConnectionStateChanged:" + connectionState);
            return;
        }
        log("onPreciseDataConnectionStateChanged state:" + connectionState);
        notifyPreciseDataConnectionStateChanged(connectionState);
    }

    private boolean validatePreciseDataConnectionStateChanged(PreciseDataConnectionState newState) {
        try {
            if (newState.getState() == TelephonyManager.DATA_CONNECTED
                    || newState.getState() == TelephonyManager.DATA_HANDOVER_IN_PROGRESS) {
                List<Integer> netCapabilities =
                        QnsUtils.getNetCapabilitiesFromApnTypeBitmask(
                                newState.getApnSetting().getApnTypeBitmask());
                for (int netCapability : netCapabilities) {
                    PreciseDataConnectionState lastState =
                            getLastPreciseDataConnectionState(netCapability);
                    PreciseDataConnectionState archiveState =
                            sArchivingPreciseDataConnectionState.get(
                                    mSubId, newState.getTransportType(), netCapability);
                    if (archiveState.equals(newState) && lastState == null) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            log("got Exception in validatePreciseDataConnectionStateChanged:" + e);
        }
        return true;
    }

    boolean isVoiceBarring() {
        return mLastQnsTelephonyInfoIms.getVoiceBarring();
    }

    boolean isEmergencyBarring() {
        return mLastQnsTelephonyInfoIms.getEmergencyBarring();
    }

    protected void onBarringInfoChanged(BarringInfo barringInfo) {
        boolean voiceBarringByFactor =
                barringInfo
                                .getBarringServiceInfo(BARRING_SERVICE_TYPE_MMTEL_VOICE)
                                .getConditionalBarringFactor()
                        == 100;
        boolean emergencyBarringByFactor =
                barringInfo
                                .getBarringServiceInfo(BARRING_SERVICE_TYPE_EMERGENCY)
                                .getConditionalBarringFactor()
                        == 100;
        log(
                "onBarringInfoChanged voiceBarringByFactor:"
                        + voiceBarringByFactor
                        + " emergencyBarringFactor"
                        + emergencyBarringByFactor);
        boolean changed = false;
        if (mLastQnsTelephonyInfoIms.getVoiceBarring() != voiceBarringByFactor) {
            log(" onBarringInfoChanged voiceBarring changed:" + voiceBarringByFactor);
            mLastQnsTelephonyInfoIms.setVoiceBarring(voiceBarringByFactor);
            changed = true;
        }
        if (mLastQnsTelephonyInfoIms.getEmergencyBarring() != emergencyBarringByFactor) {
            log(" onBarringInfoChanged emergencyBarring changed:" + emergencyBarringByFactor);
            mLastQnsTelephonyInfoIms.setEmergencyBarring(emergencyBarringByFactor);
            changed = true;
        }
        if (changed) {
            notifyQnsTelephonyInfoIms(mLastQnsTelephonyInfoIms);
        }
    }

    protected void onCallStateChanged(int callState) {
        mCallState = callState;
        mCallStateListener.notifyResult(callState);
    }

    protected void onSrvccStateChanged(int srvccState) {
        mSrvccStateListener.notifyResult(srvccState);
    }

    protected void onSubscriptionIdChanged(int subId) {
        mSubscriptionIdListener.notifyResult(subId);
    }

    protected void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReasonInfo) {
        mImsCallDropDisconnectCauseListener.notifyResult(imsReasonInfo);
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    void close() {
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionsChangeListener);
        mHandlerThread.quitSafely();
        if (mTelephonyListener != null) {
            mTelephonyListener.unregister(mSubId);
        }
        mNetCapabilityRegistrantMap.clear();
        mQnsTelephonyInfoRegistrantMap.clear();
        mLastPreciseDataConnectionState.clear();
        mIwlanServiceStateListener.removeAll();
    }

    /** Listener for change of service state. */
    protected interface OnServiceStateListener {
        /** Notify the cellular service state changed. */
        void onServiceStateChanged(ServiceState serviceState);
    }

    /** Listener for change of precise data connection state. */
    protected interface OnPreciseDataConnectionStateListener {
        /** Notify the PreciseDataConnectionState changed. */
        void onPreciseDataConnectionStateChanged(PreciseDataConnectionState connectionState);
    }

    protected interface OnBarringInfoListener {
        /** Notify the Barring info changed. */
        void onBarringInfoChanged(BarringInfo barringInfo);
    }

    protected interface OnCallStateListener {
        /** Notify the Call state changed. */
        void onCallStateChanged(@Annotation.CallState int state);
    }

    protected interface OnSrvccStateChangedCallback {
        /** Notify the Call state changed. */
        void onSrvccStateChanged(@Annotation.SrvccState int state);
    }

    protected interface OnCallStatesChangedCallback {
        /** Notify the Call state changed. */
        void onCallStatesChanged(List<CallState> callStateList);
    }

    protected interface OnImsCallDisconnectCauseListener {
        /** Notify the call disconnected cause changed. */
        void onImsCallDisconnectCauseChanged(@NonNull ImsReasonInfo imsReasonInfo);
    }

    protected static class Archiving<V> {
        protected HashMap<String, V> mArchiving = new HashMap<>();

        Archiving() {}

        private String getKey(int subId, int transportType, int netCapability) {
            return subId + "_" + transportType + "_" + netCapability;
        }

        void put(int subId, int transportType, int netCapability, V v) {
            mArchiving.put(getKey(subId, transportType, netCapability), v);
        }

        V get(int subId, int transportType, int netCapability) {
            return mArchiving.get(getKey(subId, transportType, netCapability));
        }
    }

    class QnsTelephonyInfoIms extends QnsTelephonyInfo {
        private boolean mVopsSupport;
        private boolean mVopsEmergencySupport;
        private boolean mVoiceBarring;
        private boolean mEmergencyBarring;

        QnsTelephonyInfoIms() {
            super();
            mVoiceBarring = false;
            mVopsSupport = false;
            mEmergencyBarring = false;
            mVopsEmergencySupport = false;
        }

        QnsTelephonyInfoIms(
                QnsTelephonyInfo info,
                boolean vopsSupport,
                boolean vopsEmergencySupport,
                boolean voiceBarring,
                boolean emergencyBarring) {
            super(info);
            mVopsSupport = vopsSupport;
            mVopsEmergencySupport = vopsEmergencySupport;
            mVoiceBarring = voiceBarring;
            mEmergencyBarring = emergencyBarring;
        }

        boolean getVopsSupport() {
            return mVopsSupport;
        }

        void setVopsSupport(boolean support) {
            mVopsSupport = support;
        }

        boolean getVopsEmergencySupport() {
            return mVopsEmergencySupport;
        }

        void setVopsEmergencySupport(boolean support) {
            mVopsEmergencySupport = support;
        }

        boolean getVoiceBarring() {
            return mVoiceBarring;
        }

        void setVoiceBarring(boolean barring) {
            mVoiceBarring = barring;
        }

        boolean getEmergencyBarring() {
            return mEmergencyBarring;
        }

        void setEmergencyBarring(boolean barring) {
            mEmergencyBarring = barring;
        }

        @Override
        public boolean equals(Object o) {
            boolean equal = super.equals(o);
            if (equal) {
                if (!(o instanceof QnsTelephonyInfoIms)) return false;
                equal =
                        (this.mVoiceBarring == ((QnsTelephonyInfoIms) o).getVoiceBarring())
                                && (this.mVopsSupport == ((QnsTelephonyInfoIms) o).getVopsSupport())
                                && (this.mVopsEmergencySupport
                                        == ((QnsTelephonyInfoIms) o).getVopsEmergencySupport())
                                && (this.mEmergencyBarring
                                        == ((QnsTelephonyInfoIms) o).getEmergencyBarring());
            }
            return equal;
        }

        @Override
        public String toString() {
            return "QnsTelephonyInfoIms{"
                    + "mVopsSupport="
                    + mVopsSupport
                    + ", mVopsEmergencySupport="
                    + mVopsEmergencySupport
                    + ", mVoiceBarring="
                    + mVoiceBarring
                    + ", mEmergencyBarring="
                    + mEmergencyBarring
                    + ", mVoiceNetworkType="
                    + getVoiceNetworkType()
                    + ", mDataRegState="
                    + getDataRegState()
                    + ", mDataNetworkType="
                    + getDataNetworkType()
                    + ", mCoverage="
                    + mCoverage
                    + ", mRegisteredPlmn='"
                    + getRegisteredPlmn()
                    + "'"
                    + ", mCellularAvailable="
                    + isCellularAvailable()
                    + '}';
        }

        boolean isCellularAvailable(
                int netCapability,
                boolean checkVops,
                boolean checkBarring,
                boolean volteRoamingSupported) {
            if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
                return super.isCellularAvailable()
                        && (!checkVops || mVopsSupport)
                        && (!checkBarring || !mVoiceBarring)
                        && volteRoamingSupported;
            } else if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                return super.isCellularAvailable()
                        && (!checkVops || mVopsEmergencySupport)
                        && (!checkBarring && !mEmergencyBarring);
            }
            return false;
        }
    }

    class QnsTelephonyInfo {
        private int mVoiceNetworkType;
        private int mDataRegState;
        private int mDataNetworkType;
        private boolean mCoverage;
        private String mRegisteredPlmn;
        private boolean mCellularAvailable;

        QnsTelephonyInfo() {
            mVoiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            mDataRegState = ServiceState.STATE_OUT_OF_SERVICE;
            mDataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            mCoverage = false; // home
            mCellularAvailable = false; // not available
            mRegisteredPlmn = "";
        }

        QnsTelephonyInfo(QnsTelephonyInfo info) {
            mVoiceNetworkType = info.mVoiceNetworkType;
            mDataRegState = info.mDataRegState;
            mDataNetworkType = info.mDataNetworkType;
            mCoverage = info.mCoverage;
            mCellularAvailable = info.mCellularAvailable;
            mRegisteredPlmn = info.mRegisteredPlmn;
        }

        int getVoiceNetworkType() {
            return mVoiceNetworkType;
        }

        void setVoiceNetworkType(int voiceNetworkType) {
            mVoiceNetworkType = voiceNetworkType;
        }

        int getDataRegState() {
            return mDataRegState;
        }

        void setDataRegState(int dataRegState) {
            mDataRegState = dataRegState;
        }

        int getDataNetworkType() {
            return mDataNetworkType;
        }

        void setDataNetworkType(int dataNetworkType) {
            mDataNetworkType = dataNetworkType;
        }

        String getRegisteredPlmn() {
            return mRegisteredPlmn;
        }

        void setRegisteredPlmn(String plmn) {
            if (plmn == null) {
                mRegisteredPlmn = "";
            } else {
                mRegisteredPlmn = plmn;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QnsTelephonyInfo)) return false;
            QnsTelephonyInfo that = (QnsTelephonyInfo) o;
            return mVoiceNetworkType == that.mVoiceNetworkType
                    && mDataRegState == that.mDataRegState
                    && mDataNetworkType == that.mDataNetworkType
                    && mCoverage == that.mCoverage
                    && mRegisteredPlmn.equals(that.mRegisteredPlmn)
                    && mCellularAvailable == that.mCellularAvailable;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mVoiceNetworkType,
                    mDataRegState,
                    mDataNetworkType,
                    mCoverage,
                    mCellularAvailable);
        }

        @Override
        public String toString() {
            return "QnsTelephonyInfo{"
                    + "mVoiceNetworkType="
                    + mVoiceNetworkType
                    + ", mDataRegState="
                    + mDataRegState
                    + ", mDataNetworkType="
                    + mDataNetworkType
                    + ", mCoverage="
                    + mCoverage
                    + ", mRegisteredPlmn='"
                    + mRegisteredPlmn
                    + "'"
                    + ", mCellularAvailable="
                    + mCellularAvailable
                    + '}';
        }

        boolean isCoverage() {
            return mCoverage;
        }

        void setCoverage(boolean coverage) {
            mCoverage = coverage;
        }

        boolean isCellularAvailable() {
            return mCellularAvailable;
        }

        void setCellularAvailable(boolean cellularAvailable) {
            mCellularAvailable = cellularAvailable;
        }
    }

    /** {@link TelephonyCallback} to listen to Cellular Service State Changed. */
    protected class TelephonyListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener,
                    TelephonyCallback.PreciseDataConnectionStateListener,
                    TelephonyCallback.BarringInfoListener,
                    TelephonyCallback.CallStateListener,
                    TelephonyCallback.SrvccStateListener,
                    TelephonyCallback.CallAttributesListener,
                    TelephonyCallback.MediaQualityStatusChangedListener,
                    TelephonyCallback.ImsCallDisconnectCauseListener {
        private final Executor mExecutor;
        private OnServiceStateListener mServiceStateListener;
        private OnPreciseDataConnectionStateListener mPreciseDataConnectionStateListener;
        private OnBarringInfoListener mBarringInfoListener;
        private OnCallStateListener mCallStateListener;
        private OnSrvccStateChangedCallback mSrvccStateCallback;
        private OnSrvccStateChangedCallback mSrvccStateListener;
        private OnCallStatesChangedCallback mCallStatesCallback;
        private OnImsCallDisconnectCauseListener mImsCallDisconnectCauseListener;
        private TelephonyManager mTelephonyManager;

        TelephonyListener(Executor executor) {
            super();
            mExecutor = executor;
        }

        void setServiceStateListener(OnServiceStateListener listener) {
            mServiceStateListener = listener;
        }

        void setPreciseDataConnectionStateListener(OnPreciseDataConnectionStateListener listener) {
            mPreciseDataConnectionStateListener = listener;
        }

        void setBarringInfoListener(OnBarringInfoListener listener) {
            mBarringInfoListener = listener;
        }

        void setCallStateListener(OnCallStateListener listener) {
            mCallStateListener = listener;
        }

        void setSrvccStateCallback(OnSrvccStateChangedCallback callback) {
            mSrvccStateCallback = callback;
        }

        void setSrvccStateListener(OnSrvccStateChangedCallback listener) {
            mSrvccStateListener = listener;
        }

        void setCallStatesCallback(OnCallStatesChangedCallback listener) {
            mCallStatesCallback = listener;
        }

        void setImsCallDisconnectCauseListener(OnImsCallDisconnectCauseListener listener) {
            mImsCallDisconnectCauseListener = listener;
        }

        /**
         * Register a TelephonyCallback for this listener.
         *
         * @param context the Context
         * @param subId the subscription id.
         */
        void register(Context context, int subId) {
            log("register TelephonyCallback for sub:" + subId);
            long identity = Binder.clearCallingIdentity();
            try {
                mTelephonyManager =
                        context.getSystemService(TelephonyManager.class)
                                .createForSubscriptionId(subId);
                mTelephonyManager.registerTelephonyCallback(
                        TelephonyManager.INCLUDE_LOCATION_DATA_NONE, mExecutor, this);
            } catch (NullPointerException e) {
                log("got NullPointerException e:" + e);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Unregister a TelephonyCallback for this listener.
         *
         * @param subId the subscription id.
         */
        void unregister(int subId) {
            log("unregister TelephonyCallback for sub:" + subId);
            mTelephonyManager.unregisterTelephonyCallback(this);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (mServiceStateListener != null) {
                mServiceStateListener.onServiceStateChanged(serviceState);
            }
        }

        @Override
        public void onPreciseDataConnectionStateChanged(
                PreciseDataConnectionState connectionState) {
            if (mPreciseDataConnectionStateListener != null) {
                mPreciseDataConnectionStateListener.onPreciseDataConnectionStateChanged(
                        connectionState);
            }
        }

        @Override
        public void onBarringInfoChanged(BarringInfo barringInfo) {
            if (mBarringInfoListener != null) {
                mBarringInfoListener.onBarringInfoChanged(barringInfo);
            }
        }

        @Override
        public void onCallStateChanged(@Annotation.CallState int state) {
            if (mCallStateListener != null) {
                mCallStateListener.onCallStateChanged(state);
            }
        }

        @Override
        public void onSrvccStateChanged(@Annotation.SrvccState int state) {
            if (mSrvccStateListener != null) {
                mSrvccStateListener.onSrvccStateChanged(state);
            }
            for (Consumer<Integer> consumer : mSrvccStateConsumerList) {
                consumer.accept(state);
            }
        }

        @Override
        public void onCallStatesChanged(List<CallState> callStateList) {
            for (Consumer<List<CallState>> consumer : mCallStatesConsumerList) {
                consumer.accept(callStateList);
            }
            if (mCallStatesCallback != null) {
                mCallStatesCallback.onCallStatesChanged(callStateList);
            }
        }

        @Override
        public void onMediaQualityStatusChanged(MediaQualityStatus status) {
            for (Consumer<MediaQualityStatus> consumer : mMediaQualityConsumerList) {
                consumer.accept(status);
            }
        }

        @Override
        public void onImsCallDisconnectCauseChanged(@NonNull ImsReasonInfo imsReasonInfo) {
            if (mImsCallDisconnectCauseListener != null) {
                mImsCallDisconnectCauseListener.onImsCallDisconnectCauseChanged(imsReasonInfo);
            }
        }
    }

    void addCallStatesChangedCallback(Consumer<List<CallState>> consumer) {
        mCallStatesConsumerList.add(consumer);
    }

    void removeCallStatesChangedCallback(Consumer<List<CallState>> consumer) {
        mCallStatesConsumerList.remove(consumer);
    }

    void addSrvccStateChangedCallback(Consumer<Integer> consumer) {
        mSrvccStateConsumerList.add(consumer);
    }

    void removeSrvccStateChangedCallback(Consumer<Integer> consumer) {
        mSrvccStateConsumerList.remove(consumer);
    }

    void addMediaQualityStatusCallback(Consumer<MediaQualityStatus> consumer) {
        mMediaQualityConsumerList.add(consumer);
    }

    void removeMediaQualityStatusCallback(Consumer<MediaQualityStatus> consumer) {
        mMediaQualityConsumerList.remove(consumer);
    }

    /**
     * Dumps the state of {@link QualityMonitor}
     *
     * @param pw {@link PrintWriter} to write the state of the object.
     * @param prefix String to append at start of dumped log.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(prefix + "QnsTelephonyListener[" + mSlotIndex + "]:");
        pw.println(prefix + "mLastQnsTelephonyInfo=" + mLastQnsTelephonyInfo);
        pw.println(prefix + "mLastQnsTelephonyInfoIms=" + mLastQnsTelephonyInfoIms);
        pw.println(prefix + "mLastServiceState=" + mLastServiceState);
        pw.println(prefix + "mLastPreciseDataConnectionState=" + mLastPreciseDataConnectionState);
    }
}
