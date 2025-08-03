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

import static android.telephony.data.ThrottleStatus.THROTTLE_TYPE_ELAPSED_TIME;

import android.annotation.NonNull;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.QualifiedNetworksService;
import android.telephony.data.ThrottleStatus;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the qualified networks service, which is a service providing up-to-date
 * qualified network information to the frameworks for data handover control. A qualified network is
 * defined as an access network that is ready for bringing up data connection for given APN types.
 */
public class QualifiedNetworksServiceImpl extends QualifiedNetworksService {

    static final String LOG_TAG = QualifiedNetworksServiceImpl.class.getSimpleName();
    private static final boolean DBG = true;
    private static final int QNS_CONFIGURATION_LOADED = 1;
    private static final int QUALIFIED_NETWORKS_CHANGED = 2;
    private static final int QNS_CONFIGURATION_CHANGED = 3;
    HashMap<Integer, NetworkAvailabilityProviderImpl> mProviderMap = new HashMap<>();
    HashMap<Integer, HandlerThread> mHandlerThreadMap = new HashMap<>();
    Context mContext;
    HandlerThread mHandlerThread;
    @VisibleForTesting QnsComponents mQnsComponents;

    /** Default constructor. */
    public QualifiedNetworksServiceImpl() {
        super();
        log("created QualifiedNetworksServiceImpl.");
    }

    /**
     * Create the instance of {@link NetworkAvailabilityProvider}. Vendor qualified network service
     * must override this method to facilitate the creation of {@link NetworkAvailabilityProvider}
     * instances. The system will call this method after binding the qualified networks service for
     * each active SIM slot index.
     *
     * @param slotIndex SIM slot index the qualified networks service associated with.
     * @return Qualified networks service instance
     */
    @Override
    public NetworkAvailabilityProvider onCreateNetworkAvailabilityProvider(int slotIndex) {
        log("Qualified Networks Service created for slot " + slotIndex);
        if (!QnsUtils.isValidSlotIndex(mContext, slotIndex)) {
            log("Invalid slotIndex " + slotIndex + ". fail to create NetworkAvailabilityProvider");
            return null;
        }
        if (!mHandlerThreadMap.containsKey(slotIndex)) {
            HandlerThread ht = new HandlerThread("NapConfigHandler-" + slotIndex);
            ht.start();
            mHandlerThreadMap.put(slotIndex, ht);
        }
        NetworkAvailabilityProviderImpl provider = new NetworkAvailabilityProviderImpl(slotIndex);
        mProviderMap.put(slotIndex, provider);
        return provider;
    }

    @Override
    public void onCreate() {
        log("onCreate");
        mContext = getApplicationContext();
        mQnsComponents = new QnsComponents(mContext);
        mHandlerThread = new HandlerThread(LOG_TAG);
        mHandlerThread.start();
    }

    @Override
    public void onDestroy() {
        log("onDestroy");
        for (NetworkAvailabilityProviderImpl provider : mProviderMap.values()) {
            provider.close();
        }
        for (HandlerThread ht : mHandlerThreadMap.values()) {
            ht.quitSafely();
        }
        mHandlerThreadMap.clear();
        mProviderMap.clear();
        mHandlerThread.quitSafely();
        super.onDestroy();
    }

    protected void log(String s) {
        if (DBG) Log.d(LOG_TAG, s);
    }

    static class QualifiedNetworksInfo {
        int mNetCapability;
        List<Integer> mAccessNetworkTypes;

        QualifiedNetworksInfo(int netCapability, List<Integer> accessNetworkTypes) {
            mNetCapability = netCapability;
            mAccessNetworkTypes = accessNetworkTypes;
        }

        int getNetCapability() {
            return mNetCapability;
        }

        void setNetCapability(int netCapability) {
            mNetCapability = netCapability;
        }

        List<Integer> getAccessNetworkTypes() {
            return mAccessNetworkTypes;
        }

        void setAccessNetworkTypes(List<Integer> accessNetworkTypes) {
            mAccessNetworkTypes = accessNetworkTypes;
        }
    }

    /**
     * The network availability provider implementation class. The qualified network service must
     * extend this class to report the available networks for data connection setup. Note that each
     * instance of network availability provider is associated with slot.
     */
    public class NetworkAvailabilityProviderImpl extends NetworkAvailabilityProvider {
        private final String mLogTag;
        private final int mSlotIndex;
        @VisibleForTesting Handler mHandler;
        @VisibleForTesting Handler mConfigHandler;
        private boolean mIsQnsConfigChangeRegistered = false;

        protected QnsCarrierConfigManager mConfigManager;
        protected HashMap<Integer, AccessNetworkEvaluator> mEvaluators = new HashMap<>();
        private boolean mIsClosed;

        /**
         * Constructor
         *
         * @param slotIndex SIM slot index the network availability provider associated with.
         */
        public NetworkAvailabilityProviderImpl(int slotIndex) {
            super(slotIndex);
            mLogTag = NetworkAvailabilityProviderImpl.class.getSimpleName() + "_" + slotIndex;

            mIsClosed = false;
            mSlotIndex = slotIndex;
            mConfigHandler = new NapHandler(mHandlerThreadMap.get(mSlotIndex).getLooper());
            mConfigHandler.post(this::initNetworkAvailabilityProvider);
            mHandler = new NapHandler(mHandlerThread.getLooper());
        }

        private class NapHandler extends Handler {
            NapHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case QNS_CONFIGURATION_LOADED:
                        onConfigurationLoaded();
                        break;
                    case QNS_CONFIGURATION_CHANGED:
                        log("Qns Configuration changed received");
                        onConfigurationChanged();
                        break;
                    case QUALIFIED_NETWORKS_CHANGED:
                        QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
                        onQualifiedNetworksChanged((QualifiedNetworksInfo) ar.mResult);
                        break;
                    default:
                        log("got event " + msg.what + " never reached here.");
                        break;
                }
            }
        }

        private void initNetworkAvailabilityProvider() {
            mQnsComponents.createQnsComponents(mSlotIndex);
            mConfigManager = mQnsComponents.getQnsCarrierConfigManager(mSlotIndex);
            mConfigManager.registerForConfigurationLoaded(mConfigHandler, QNS_CONFIGURATION_LOADED);
        }

        protected void onConfigurationLoaded() {
            log("onConfigurationLoaded");
            // Register for Upgradable Config items load case
            if (!mIsQnsConfigChangeRegistered) {
                mConfigManager.registerForConfigurationChanged(
                        mConfigHandler, QNS_CONFIGURATION_CHANGED);
                mIsQnsConfigChangeRegistered = true;
            }

            HashMap<Integer, AccessNetworkEvaluator> evaluators = new HashMap<>();
            List<Integer> netCapabilities = mConfigManager.getQnsSupportedNetCapabilities();

            for (int netCapability : netCapabilities) {
                int transportType = mConfigManager.getQnsSupportedTransportType(netCapability);
                if (transportType < 0
                        || transportType == QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN) {
                    continue;
                }
                if (mEvaluators.get(netCapability) != null) {
                    AccessNetworkEvaluator evaluator = mEvaluators.remove(netCapability);
                    evaluators.put(netCapability, evaluator);
                    // reuse evaluator
                    evaluator.rebuild();
                } else {
                    AccessNetworkEvaluator evaluator =
                            new AccessNetworkEvaluator(mQnsComponents, netCapability, mSlotIndex);
                    evaluator.registerForQualifiedNetworksChanged(
                            mHandler, QUALIFIED_NETWORKS_CHANGED);
                    evaluators.put(netCapability, evaluator);
                }
            }
            for (Integer capability : mEvaluators.keySet()) {
                AccessNetworkEvaluator evaluator = mEvaluators.get(capability);
                evaluator.unregisterForQualifiedNetworksChanged(mHandler);
                evaluator.close();
            }
            mEvaluators.clear();
            mEvaluators = evaluators;
        }

        protected void onConfigurationChanged() {}

        private void onQualifiedNetworksChanged(QualifiedNetworksInfo info) {
            log(
                    "Calling updateQualifiedNetworkTypes for mNetCapability["
                            + QnsUtils.getNameOfNetCapability(info.getNetCapability())
                            + "], preferred networks "
                            + QnsUtils.getStringAccessNetworkTypes(info.getAccessNetworkTypes()));

            int apnType = QnsUtils.getApnTypeFromNetCapability(info.getNetCapability());
            updateQualifiedNetworkTypes(apnType, info.getAccessNetworkTypes());
        }

        @Override
        public void reportThrottleStatusChanged(@NonNull List<ThrottleStatus> statuses) {
            log("reportThrottleStatusChanged: statuses size=" + statuses.size());
            for (ThrottleStatus ts : statuses) {
                int netCapability;
                try {
                    netCapability = QnsUtils.getNetCapabilityFromApnType(ts.getApnType());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                log("ThrottleStatus:" + ts + ", netCapability" + netCapability);
                if (ts.getSlotIndex() != getSlotIndex()) {
                    continue;
                }
                AccessNetworkEvaluator evaluator = mEvaluators.get(netCapability);
                if (evaluator == null) {
                    continue;
                }
                boolean isThrottle = ts.getThrottleType() == THROTTLE_TYPE_ELAPSED_TIME;
                evaluator.updateThrottleStatus(
                        isThrottle, ts.getThrottleExpiryTimeMillis(), ts.getTransportType());
            }
        }

        @Override
        public void reportEmergencyDataNetworkPreferredTransportChanged(
                @AccessNetworkConstants.TransportType int transportType) {
            log("reportEmergencyDataNetworkPreferredTransportChanged: "
                    + QnsConstants.transportTypeToString(transportType));
            AccessNetworkEvaluator evaluator =
                    mEvaluators.get(NetworkCapabilities.NET_CAPABILITY_EIMS);
            if (evaluator != null) {
                evaluator.onEmergencyPreferredTransportTypeChanged(transportType);
            } else {
                log("There is no Emergency ANE");
            }
        }

        @Override
        public synchronized void close() {
            mConfigHandler.post(this::onClose);
        }

        private synchronized void onClose() {
            if (!mIsClosed) {
                mIsClosed = true;
                log("closing NetworkAvailabilityProviderImpl");
                mConfigManager.unregisterForConfigurationLoaded(mConfigHandler);
                mConfigManager.unregisterForConfigurationChanged(mConfigHandler);
                mIsQnsConfigChangeRegistered = false;
                for (Integer netCapability : mEvaluators.keySet()) {
                    AccessNetworkEvaluator evaluator = mEvaluators.get(netCapability);
                    evaluator.unregisterForQualifiedNetworksChanged(mHandler);
                    evaluator.close();
                }
                mQnsComponents.closeComponents(mSlotIndex);
                mEvaluators.clear();
            }
        }

        protected void log(String s) {
            if (DBG) Log.d(mLogTag, s);
        }

        /**
         * Dumps the state of {@link QualityMonitor}
         *
         * @param pw {@link PrintWriter} to write the state of the object.
         * @param prefix String to append at start of dumped log.
         */
        void dump(PrintWriter pw, String prefix) {
            pw.println(prefix + "------------------------------");
            pw.println(prefix + "NetworkAvailabilityProviderImpl[" + mSlotIndex + "]:");
            for (Map.Entry<Integer, AccessNetworkEvaluator> aneMap : mEvaluators.entrySet()) {
                AccessNetworkEvaluator ane = aneMap.getValue();
                ane.dump(pw, prefix + "  ");
            }
            QnsTelephonyListener tl = mQnsComponents.getQnsTelephonyListener(mSlotIndex);
            if (tl != null) {
                tl.dump(pw, prefix + "  ");
            }
            CellularQualityMonitor cQM = mQnsComponents.getCellularQualityMonitor(mSlotIndex);
            if (cQM != null) {
                cQM.dump(pw, prefix + "  ");
            }
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println("QualifiedNetworksServiceImpl:");
        pw.println("==============================");
        for (Map.Entry<Integer, NetworkAvailabilityProviderImpl> providerMap :
                mProviderMap.entrySet()) {
            NetworkAvailabilityProviderImpl provider = providerMap.getValue();
            provider.dump(pw, "  ");
        }
        mQnsComponents.dump(pw);
        pw.println("==============================");
    }
}
