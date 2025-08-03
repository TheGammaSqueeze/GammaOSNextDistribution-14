/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.ipsec.ike.ike3gpp.Ike3gppParams.PDU_SESSION_ID_UNSET;
import static android.telephony.PreciseDataConnectionState.NetworkValidationStatus;

import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_DEACTIVATE_DATA_CALL;
import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_IN_DEACTIVATING_STATE;
import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_NETWORK_UPDATE_WHEN_TUNNEL_IN_BRINGUP;
import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_SERVICE_OUT_OF_SYNC;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.TunnelMetricsInterface.OnClosedMetrics;
import com.google.android.iwlan.TunnelMetricsInterface.OnOpenedMetrics;
import com.google.android.iwlan.epdg.EpdgSelector;
import com.google.android.iwlan.epdg.EpdgTunnelManager;
import com.google.android.iwlan.epdg.TunnelLinkProperties;
import com.google.android.iwlan.epdg.TunnelSetupRequest;
import com.google.android.iwlan.flags.FeatureFlags;
import com.google.android.iwlan.flags.FeatureFlagsImpl;
import com.google.android.iwlan.proto.MetricsAtom;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class IwlanDataService extends DataService {

    private final FeatureFlags mFeatureFlags;
    private static final String TAG = IwlanDataService.class.getSimpleName();

    private static final String CONTEXT_ATTRIBUTION_TAG = "IWLAN";
    private static Context mContext;
    private IwlanNetworkMonitorCallback mNetworkMonitorCallback;
    private static boolean sNetworkConnected = false;
    private static Network sNetwork = null;
    private static LinkProperties sLinkProperties = null;
    @VisibleForTesting Handler mIwlanDataServiceHandler;
    private HandlerThread mIwlanDataServiceHandlerThread;
    private static final Map<Integer, IwlanDataServiceProvider> sIwlanDataServiceProviders =
            new ConcurrentHashMap<>();
    private static final int INVALID_SUB_ID = -1;

    // The current subscription with the active internet PDN. Need not be the default data sub.
    // If internet is over WiFi, this value will be INVALID_SUB_ID.
    private static int mConnectedDataSub = INVALID_SUB_ID;

    private static final int EVENT_BASE = IwlanEventListener.DATA_SERVICE_INTERNAL_EVENT_BASE;
    private static final int EVENT_TUNNEL_OPENED = EVENT_BASE;
    private static final int EVENT_TUNNEL_CLOSED = EVENT_BASE + 1;
    private static final int EVENT_SETUP_DATA_CALL = EVENT_BASE + 2;
    private static final int EVENT_DEACTIVATE_DATA_CALL = EVENT_BASE + 3;
    private static final int EVENT_DATA_CALL_LIST_REQUEST = EVENT_BASE + 4;
    private static final int EVENT_FORCE_CLOSE_TUNNEL = EVENT_BASE + 5;
    private static final int EVENT_ADD_DATA_SERVICE_PROVIDER = EVENT_BASE + 6;
    private static final int EVENT_REMOVE_DATA_SERVICE_PROVIDER = EVENT_BASE + 7;
    private static final int EVENT_TUNNEL_OPENED_METRICS = EVENT_BASE + 8;
    private static final int EVENT_TUNNEL_CLOSED_METRICS = EVENT_BASE + 9;
    private static final int EVENT_DEACTIVATE_DATA_CALL_WITH_DELAY = EVENT_BASE + 10;
    private static final int EVENT_ON_LIVENESS_STATUS_CHANGED = EVENT_BASE + 11;
    private static final int EVENT_REQUEST_NETWORK_VALIDATION = EVENT_BASE + 12;

    @VisibleForTesting
    enum Transport {
        UNSPECIFIED_NETWORK,
        MOBILE,
        WIFI
    }

    private static Transport sDefaultDataTransport = Transport.UNSPECIFIED_NETWORK;

    private boolean mIs5GEnabledOnUi;

    public IwlanDataService() {
        mFeatureFlags = new FeatureFlagsImpl();
    }

    @VisibleForTesting
    IwlanDataService(FeatureFlags featureFlags) {
        mFeatureFlags = featureFlags;
    }

    // TODO: see if network monitor callback impl can be shared between dataservice and
    // networkservice
    // This callback runs in the same thread as IwlanDataServiceHandler
    static class IwlanNetworkMonitorCallback extends ConnectivityManager.NetworkCallback {

        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.d(TAG, "onAvailable: " + network);
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link NetworkCallback#onAvailable} call
         * with the new replacement network for graceful handover. This method is not guaranteed to
         * be called before {@link NetworkCallback#onLost} is called, for example in case a network
         * is suddenly disconnected.
         */
        @Override
        public void onLosing(@NonNull Network network, int maxMsToLive) {
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(@NonNull Network network) {
            Log.d(TAG, "onLost: " + network);
            IwlanDataService.setConnectedDataSub(INVALID_SUB_ID);
            IwlanDataService.setNetworkConnected(false, network, Transport.UNSPECIFIED_NETWORK);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + linkProperties);

            if (!network.equals(sNetwork)) {
                Log.d(TAG, "Ignore LinkProperties changes for unused Network.");
                return;
            }

            if (!linkProperties.equals(sLinkProperties)) {
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.dnsPrefetchCheck();
                    sLinkProperties = linkProperties;
                    dp.updateNetwork(network, linkProperties);
                }
            }
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + network + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(TAG, "onCapabilitiesChanged: " + network + " " + networkCapabilities);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    Log.d(TAG, "Network " + network + " connected using transport MOBILE");
                    IwlanDataService.setConnectedDataSub(getConnectedDataSub(networkCapabilities));
                    IwlanDataService.setNetworkConnected(true, network, Transport.MOBILE);
                } else if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    Log.d(TAG, "Network " + network + " connected using transport WIFI");
                    IwlanDataService.setConnectedDataSub(INVALID_SUB_ID);
                    IwlanDataService.setNetworkConnected(true, network, Transport.WIFI);
                } else {
                    Log.w(TAG, "Network does not have cellular or wifi capability");
                }
            }
        }
    }

    @VisibleForTesting
    class IwlanDataServiceProvider extends DataService.DataServiceProvider {
        private static final int CALLBACK_TYPE_SETUP_DATACALL_COMPLETE = 1;
        private static final int CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE = 2;
        private static final int CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE = 3;

        private final String SUB_TAG;
        private final IwlanDataService mIwlanDataService;
        private final IwlanTunnelCallback mIwlanTunnelCallback;
        private final IwlanTunnelMetricsImpl mIwlanTunnelMetrics;
        private boolean mWfcEnabled = false;
        private boolean mCarrierConfigReady = false;
        private final EpdgSelector mEpdgSelector;
        private final IwlanDataTunnelStats mTunnelStats;
        private CellInfo mCellInfo = null;
        private int mCallState = TelephonyManager.CALL_STATE_IDLE;
        private long mProcessingStartTime = 0;

        // apn to TunnelState
        // Access should be serialized inside IwlanDataServiceHandler
        private final Map<String, TunnelState> mTunnelStateForApn = new ConcurrentHashMap<>();
        private final Map<String, MetricsAtom> mMetricsAtomForApn = new ConcurrentHashMap<>();
        private Calendar mCalendar;

        // Holds the state of a tunnel (for an APN)
        @VisibleForTesting
        class TunnelState {

            // this should be ideally be based on path MTU discovery. 1280 is the minimum packet
            // size ipv6 routers have to handle so setting it to 1280 is the safest approach.
            // ideally it should be 1280 - tunnelling overhead ?
            private static final int LINK_MTU = 1280; // TODO: need to subtract tunnelling overhead?
            private static final int LINK_MTU_CST = 1200; // Reserve 80 bytes for VCN.
            static final int TUNNEL_DOWN = 1;
            static final int TUNNEL_IN_BRINGUP = 2;
            static final int TUNNEL_UP = 3;
            static final int TUNNEL_IN_BRINGDOWN = 4;
            static final int TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP = 5;
            private DataServiceCallback dataServiceCallback;
            private int mState;
            private int mPduSessionId;
            private TunnelLinkProperties mTunnelLinkProperties;
            private boolean mIsHandover;
            private Date mBringUpStateTime = null;
            private Date mUpStateTime = null;
            private boolean mIsImsOrEmergency;
            private DeactivateDataCallData mPendingDeactivateDataCallData;
            private boolean mIsDataCallWithN1;
            private int mNetworkValidationStatus =
                    PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS;

            public boolean getIsDataCallWithN1() {
                return mIsDataCallWithN1;
            }

            public void setIsDataCallWithN1(boolean mIsDataCallWithN1) {
                this.mIsDataCallWithN1 = mIsDataCallWithN1;
            }

            public int getPduSessionId() {
                return mPduSessionId;
            }

            public void setPduSessionId(int mPduSessionId) {
                this.mPduSessionId = mPduSessionId;
            }

            public int getLinkMtu() {
                if ((sDefaultDataTransport == Transport.MOBILE) && sNetworkConnected) {
                    return LINK_MTU_CST;
                } else {
                    return LINK_MTU; // TODO: need to subtract tunnelling overhead
                }
            }

            public @ApnSetting.ProtocolType int getRequestedProtocolType() {
                return mProtocolType;
            }

            public void setProtocolType(int protocolType) {
                mProtocolType = protocolType;
            }

            private int mProtocolType; // from DataProfile

            public TunnelLinkProperties getTunnelLinkProperties() {
                return mTunnelLinkProperties;
            }

            public void setTunnelLinkProperties(TunnelLinkProperties tunnelLinkProperties) {
                mTunnelLinkProperties = tunnelLinkProperties;
            }

            public DataServiceCallback getDataServiceCallback() {
                return dataServiceCallback;
            }

            public void setDataServiceCallback(DataServiceCallback dataServiceCallback) {
                this.dataServiceCallback = dataServiceCallback;
            }

            public TunnelState(DataServiceCallback callback) {
                dataServiceCallback = callback;
                mState = TUNNEL_DOWN;
            }

            public int getState() {
                return mState;
            }

            public DeactivateDataCallData getPendingDeactivateDataCallData() {
                return mPendingDeactivateDataCallData;
            }

            public boolean hasPendingDeactivateDataCallData() {
                return mPendingDeactivateDataCallData != null;
            }

            /**
             * @param state (TunnelState.TUNNEL_DOWN|TUNNEL_UP|TUNNEL_DOWN)
             */
            public void setState(int state) {
                mState = state;
                if (mState == TunnelState.TUNNEL_IN_BRINGUP) {
                    mBringUpStateTime = mCalendar.getTime();
                }
                if (mState == TunnelState.TUNNEL_UP) {
                    mUpStateTime = mCalendar.getTime();
                }
            }

            public void setIsHandover(boolean isHandover) {
                mIsHandover = isHandover;
            }

            public boolean getIsHandover() {
                return mIsHandover;
            }

            public Date getBringUpStateTime() {
                return mBringUpStateTime;
            }

            public Date getUpStateTime() {
                return mUpStateTime;
            }

            public Date getCurrentTime() {
                return mCalendar.getTime();
            }

            public boolean getIsImsOrEmergency() {
                return mIsImsOrEmergency;
            }

            public void setIsImsOrEmergency(boolean isImsOrEmergency) {
                mIsImsOrEmergency = isImsOrEmergency;
            }

            public void setPendingDeactivateDataCallData(
                    DeactivateDataCallData deactivateDataCallData) {
                mPendingDeactivateDataCallData = deactivateDataCallData;
            }

            public void setNetworkValidationStatus(int networkValidationStatus) {
                mNetworkValidationStatus = networkValidationStatus;
            }

            public int getNetworkValidationStatus() {
                return mNetworkValidationStatus;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                String tunnelState = "UNKNOWN";
                switch (mState) {
                    case TUNNEL_DOWN:
                        tunnelState = "DOWN";
                        break;
                    case TUNNEL_IN_BRINGUP:
                        tunnelState = "IN BRINGUP";
                        break;
                    case TUNNEL_UP:
                        tunnelState = "UP";
                        break;
                    case TUNNEL_IN_BRINGDOWN:
                        tunnelState = "IN BRINGDOWN";
                        break;
                    case TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP:
                        tunnelState = "IN FORCE CLEAN WAS IN BRINGUP";
                        break;
                }
                sb.append("\tCurrent State of this tunnel: ")
                        .append(mState)
                        .append(" ")
                        .append(tunnelState);
                sb.append("\n\tTunnel state is in Handover: ").append(mIsHandover);
                if (mBringUpStateTime != null) {
                    sb.append("\n\tTunnel bring up initiated at: ").append(mBringUpStateTime);
                } else {
                    sb.append("\n\tPotential leak. Null mBringUpStateTime");
                }
                if (mUpStateTime != null) {
                    sb.append("\n\tTunnel is up at: ").append(mUpStateTime);
                }
                if (mUpStateTime != null && mBringUpStateTime != null) {
                    long tunnelUpTime = mUpStateTime.getTime() - mBringUpStateTime.getTime();
                    sb.append("\n\tTime taken for the tunnel to come up in ms: ")
                            .append(tunnelUpTime);
                }
                sb.append("\n\tCurrent network validation status: ")
                        .append(mNetworkValidationStatus);
                return sb.toString();
            }
        }

        @VisibleForTesting
        class IwlanTunnelCallback implements EpdgTunnelManager.TunnelCallback {

            IwlanDataServiceProvider mIwlanDataServiceProvider;

            public IwlanTunnelCallback(IwlanDataServiceProvider dsp) {
                mIwlanDataServiceProvider = dsp;
            }

            // TODO: full implementation

            public void onOpened(String apnName, TunnelLinkProperties linkProperties) {
                Log.d(
                        SUB_TAG,
                        "Tunnel opened! APN: " + apnName + ", linkProperties: " + linkProperties);
                getIwlanDataServiceHandler()
                        .sendMessage(
                                getIwlanDataServiceHandler()
                                        .obtainMessage(
                                                EVENT_TUNNEL_OPENED,
                                                new TunnelOpenedData(
                                                        apnName,
                                                        linkProperties,
                                                        mIwlanDataServiceProvider)));
            }

            public void onClosed(String apnName, IwlanError error) {
                Log.d(SUB_TAG, "Tunnel closed! APN: " + apnName + ", Error: " + error);
                // this is called, when a tunnel that is up, is closed.
                // the expectation is error==NO_ERROR for user initiated/normal close.
                getIwlanDataServiceHandler()
                        .sendMessage(
                                getIwlanDataServiceHandler()
                                        .obtainMessage(
                                                EVENT_TUNNEL_CLOSED,
                                                new TunnelClosedData(
                                                        apnName,
                                                        error,
                                                        mIwlanDataServiceProvider)));
            }

            public void onNetworkValidationStatusChanged(
                    String apnName, @NetworkValidationStatus int status) {
                Log.d(
                        SUB_TAG,
                        "Liveness status changed. APN: "
                                + apnName
                                + ", status: "
                                + PreciseDataConnectionState.networkValidationStatusToString(
                                        status));
                getIwlanDataServiceHandler()
                        .obtainMessage(
                                EVENT_ON_LIVENESS_STATUS_CHANGED,
                                new TunnelValidationStatusData(
                                        apnName, status, mIwlanDataServiceProvider))
                        .sendToTarget();
            }
        }

        /** Holds all tunnel related time and count statistics for this IwlanDataServiceProvider */
        @VisibleForTesting
        class IwlanDataTunnelStats {

            // represents the start time from when the following events are recorded
            private Date mStartTime;

            // Stats for TunnelSetup Success time (BRING_UP -> UP state)
            @VisibleForTesting
            Map<String, LongSummaryStatistics> mTunnelSetupSuccessStats =
                    new HashMap<String, LongSummaryStatistics>();

            // Count for Tunnel Setup failures onClosed when in BRING_UP
            @VisibleForTesting
            Map<String, Long> mTunnelSetupFailureCounts = new HashMap<String, Long>();

            // Count for unsol tunnel down onClosed when in UP without deactivate
            @VisibleForTesting
            Map<String, Long> mUnsolTunnelDownCounts = new HashMap<String, Long>();

            // Stats for how long the tunnel is in up state onClosed when in UP
            @VisibleForTesting
            Map<String, LongSummaryStatistics> mTunnelUpStats =
                    new HashMap<String, LongSummaryStatistics>();

            private long statCount;
            private final long COUNT_MAX = 1000;

            public IwlanDataTunnelStats() {
                mStartTime = mCalendar.getTime();
                statCount = 0L;
            }

            public void reportTunnelSetupSuccess(String apn, TunnelState tunnelState) {
                if (statCount > COUNT_MAX || maxApnReached()) {
                    reset();
                }
                statCount++;

                Date bringUpTime = tunnelState.getBringUpStateTime();
                Date upTime = tunnelState.getUpStateTime();

                if (bringUpTime != null && upTime != null) {
                    long tunnelUpTime = upTime.getTime() - bringUpTime.getTime();
                    if (!mTunnelSetupSuccessStats.containsKey(apn)) {
                        mTunnelSetupSuccessStats.put(apn, new LongSummaryStatistics());
                    }
                    LongSummaryStatistics stats = mTunnelSetupSuccessStats.get(apn);
                    stats.accept(tunnelUpTime);
                    mTunnelSetupSuccessStats.put(apn, stats);
                }
            }

            public void reportTunnelDown(String apn, TunnelState tunnelState) {
                if (statCount > COUNT_MAX || maxApnReached()) {
                    reset();
                }
                statCount++;

                // Setup fail
                if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGUP) {
                    if (!mTunnelSetupFailureCounts.containsKey(apn)) {
                        mTunnelSetupFailureCounts.put(apn, 0L);
                    }
                    long count = mTunnelSetupFailureCounts.get(apn);
                    mTunnelSetupFailureCounts.put(apn, ++count);
                    return;
                }

                // Unsolicited tunnel down as tunnel has to be in BRINGDOWN if
                // there is a deactivateDataCall() associated with this.
                if (tunnelState.getState() == TunnelState.TUNNEL_UP) {
                    if (!mUnsolTunnelDownCounts.containsKey(apn)) {
                        mUnsolTunnelDownCounts.put(apn, 0L);
                    }
                    long count = mUnsolTunnelDownCounts.get(apn);
                    mUnsolTunnelDownCounts.put(apn, ++count);
                }
                Date currentTime = tunnelState.getCurrentTime();
                Date upTime = tunnelState.getUpStateTime();
                if (upTime != null) {
                    if (!mTunnelUpStats.containsKey(apn)) {
                        mTunnelUpStats.put(apn, new LongSummaryStatistics());
                    }
                    LongSummaryStatistics stats = mTunnelUpStats.get(apn);
                    stats.accept(currentTime.getTime() - upTime.getTime());
                    mTunnelUpStats.put(apn, stats);
                }
            }

            boolean maxApnReached() {
                int APN_COUNT_MAX = 10;
                return mTunnelSetupSuccessStats.size() >= APN_COUNT_MAX
                        || mTunnelSetupFailureCounts.size() >= APN_COUNT_MAX
                        || mUnsolTunnelDownCounts.size() >= APN_COUNT_MAX
                        || mTunnelUpStats.size() >= APN_COUNT_MAX;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("IwlanDataTunnelStats:");
                sb.append("\n\tmStartTime: ").append(mStartTime);
                sb.append("\n\ttunnelSetupSuccessStats:");
                for (Map.Entry<String, LongSummaryStatistics> entry :
                        mTunnelSetupSuccessStats.entrySet()) {
                    sb.append("\n\t  Apn: ").append(entry.getKey());
                    sb.append("\n\t  ").append(entry.getValue());
                }
                sb.append("\n\ttunnelUpStats:");
                for (Map.Entry<String, LongSummaryStatistics> entry : mTunnelUpStats.entrySet()) {
                    sb.append("\n\t  Apn: ").append(entry.getKey());
                    sb.append("\n\t  ").append(entry.getValue());
                }

                sb.append("\n\ttunnelSetupFailureCounts: ");
                for (Map.Entry<String, Long> entry : mTunnelSetupFailureCounts.entrySet()) {
                    sb.append("\n\t  Apn: ").append(entry.getKey());
                    sb.append("\n\t  counts: ").append(entry.getValue());
                }
                sb.append("\n\tunsolTunnelDownCounts: ");
                for (Map.Entry<String, Long> entry : mTunnelSetupFailureCounts.entrySet()) {
                    sb.append("\n\t  Apn: ").append(entry.getKey());
                    sb.append("\n\t  counts: ").append(entry.getValue());
                }
                sb.append("\n\tendTime: ").append(mCalendar.getTime());
                return sb.toString();
            }

            private void reset() {
                mStartTime = mCalendar.getTime();
                mTunnelSetupSuccessStats = new HashMap<String, LongSummaryStatistics>();
                mTunnelUpStats = new HashMap<String, LongSummaryStatistics>();
                mTunnelSetupFailureCounts = new HashMap<String, Long>();
                mUnsolTunnelDownCounts = new HashMap<String, Long>();
                statCount = 0L;
            }
        }

        /**
         * Constructor
         *
         * @param slotIndex SIM slot index the data service provider associated with.
         */
        public IwlanDataServiceProvider(int slotIndex, IwlanDataService iwlanDataService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";

            // TODO:
            // get reference carrier config for this sub
            // get reference to resolver
            mIwlanDataService = iwlanDataService;
            mIwlanTunnelCallback = new IwlanTunnelCallback(this);
            mIwlanTunnelMetrics = new IwlanTunnelMetricsImpl(this, getIwlanDataServiceHandler());
            mEpdgSelector = EpdgSelector.getSelectorInstance(mContext, slotIndex);
            mCalendar = Calendar.getInstance();
            mTunnelStats = new IwlanDataTunnelStats();

            // Register IwlanEventListener
            List<Integer> events = new ArrayList<Integer>();
            events.add(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
            events.add(IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT);
            events.add(IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.CELLINFO_CHANGED_EVENT);
            events.add(IwlanEventListener.CALL_STATE_CHANGED_EVENT);
            events.add(IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT);
            IwlanEventListener.getInstance(mContext, slotIndex)
                    .addEventListener(events, getIwlanDataServiceHandler());
        }

        private EpdgTunnelManager getTunnelManager() {
            return EpdgTunnelManager.getInstance(mContext, getSlotIndex());
        }

        // creates a DataCallResponse for an apn irrespective of state
        private DataCallResponse apnTunnelStateToDataCallResponse(String apn) {
            TunnelState tunnelState = mTunnelStateForApn.get(apn);
            if (tunnelState == null) {
                return null;
            }

            DataCallResponse.Builder responseBuilder = new DataCallResponse.Builder();
            int state = tunnelState.getState();
            TunnelLinkProperties tunnelLinkProperties = tunnelState.getTunnelLinkProperties();
            if (tunnelLinkProperties == null) {
                Log.d(TAG, "PDN with empty linkProperties. TunnelState : " + state);
                return responseBuilder.build();
            }
            responseBuilder
                    .setId(apn.hashCode())
                    .setProtocolType(tunnelLinkProperties.getProtocolType())
                    .setCause(DataFailCause.NONE)
                    .setLinkStatus(
                            state == TunnelState.TUNNEL_UP
                                    ? DataCallResponse.LINK_STATUS_ACTIVE
                                    : DataCallResponse.LINK_STATUS_INACTIVE);

            // fill wildcard address for gatewayList (used by DataConnection to add routes)
            List<InetAddress> gatewayList = new ArrayList<>();
            List<LinkAddress> linkAddrList = tunnelLinkProperties.internalAddresses();
            if (linkAddrList.stream().anyMatch(LinkAddress::isIpv4)) {
                try {
                    gatewayList.add(Inet4Address.getByName("0.0.0.0"));
                } catch (UnknownHostException e) {
                    // should never happen for static string 0.0.0.0
                }
            }
            if (linkAddrList.stream().anyMatch(LinkAddress::isIpv6)) {
                try {
                    gatewayList.add(Inet6Address.getByName("::"));
                } catch (UnknownHostException e) {
                    // should never happen for static string ::
                }
            }

            if (tunnelLinkProperties.sliceInfo().isPresent()) {
                responseBuilder.setSliceInfo(tunnelLinkProperties.sliceInfo().get());
            }

            return responseBuilder
                    .setAddresses(linkAddrList)
                    .setDnsAddresses(tunnelLinkProperties.dnsAddresses())
                    .setPcscfAddresses(tunnelLinkProperties.pcscfAddresses())
                    .setInterfaceName(tunnelLinkProperties.ifaceName())
                    .setGatewayAddresses(gatewayList)
                    .setMtu(tunnelState.getLinkMtu())
                    .setMtuV4(tunnelState.getLinkMtu())
                    .setMtuV6(tunnelState.getLinkMtu())
                    .setPduSessionId(tunnelState.getPduSessionId())
                    .setNetworkValidationStatus(tunnelState.getNetworkValidationStatus())
                    .build(); // underlying n/w is same
        }

        private List<DataCallResponse> getCallList() {
            List<DataCallResponse> dcList = new ArrayList<>();
            for (String key : mTunnelStateForApn.keySet()) {
                DataCallResponse dcRsp = apnTunnelStateToDataCallResponse(key);
                if (dcRsp != null) {
                    Log.d(SUB_TAG, "Apn: " + key + "Link state: " + dcRsp.getLinkStatus());
                    dcList.add(dcRsp);
                }
            }
            return dcList;
        }

        private void deliverCallback(
                int callbackType, int result, DataServiceCallback callback, DataCallResponse rsp) {
            if (callback == null) {
                Log.d(SUB_TAG, "deliverCallback: callback is null.  callbackType:" + callbackType);
                return;
            }
            Log.d(
                    SUB_TAG,
                    "Delivering callbackType:"
                            + callbackType
                            + " result:"
                            + result
                            + " rsp:"
                            + rsp);
            switch (callbackType) {
                case CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE:
                    callback.onDeactivateDataCallComplete(result);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_SETUP_DATACALL_COMPLETE:
                    if (result == DataServiceCallback.RESULT_SUCCESS && rsp == null) {
                        Log.d(SUB_TAG, "Warning: null rsp for success case");
                    }
                    callback.onSetupDataCallComplete(result, rsp);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE:
                    callback.onRequestDataCallListComplete(result, getCallList());
                    // TODO: add code for the rest of the cases
            }
        }

        /**
         * Setup a data connection.
         *
         * @param accessNetworkType Access network type that the data call will be established on.
         *     Must be one of {@link android.telephony.AccessNetworkConstants.AccessNetworkType}.
         * @param dataProfile Data profile used for data call setup. See {@link DataProfile}
         * @param isRoaming True if the device is data roaming.
         * @param allowRoaming True if data roaming is allowed by the user.
         * @param reason The reason for data setup. Must be {@link #REQUEST_REASON_NORMAL} or {@link
         *     #REQUEST_REASON_HANDOVER}.
         * @param linkProperties If {@code reason} is {@link #REQUEST_REASON_HANDOVER}, this is the
         *     link properties of the existing data connection, otherwise null.
         * @param pduSessionId The pdu session id to be used for this data call. The standard range
         *     of values are 1-15 while 0 means no pdu session id was attached to this call.
         *     Reference: 3GPP TS 24.007 section 11.2.3.1b.
         * @param sliceInfo The slice info related to this data call.
         * @param trafficDescriptor TrafficDescriptor for which data connection needs to be
         *     established. It is used for URSP traffic matching as described in 3GPP TS 24.526
         *     Section 4.2.2. It includes an optional DNN which, if present, must be used for
         *     traffic matching; it does not specify the end point to be used for the data call.
         * @param matchAllRuleAllowed Indicates if using default match-all URSP rule for this
         *     request is allowed. If false, this request must not use the match-all URSP rule and
         *     if a non-match-all rule is not found (or if URSP rules are not available) then {@link
         *     DataCallResponse#getCause()} is {@link
         *     android.telephony.DataFailCause#MATCH_ALL_RULE_NOT_ALLOWED}. This is needed as some
         *     requests need to have a hard failure if the intention cannot be met, for example, a
         *     zero-rating slice.
         * @param callback The result callback for this request.
         */
        @Override
        public void setupDataCall(
                int accessNetworkType,
                @NonNull DataProfile dataProfile,
                boolean isRoaming,
                boolean allowRoaming,
                @SetupDataReason int reason,
                @Nullable LinkProperties linkProperties,
                @IntRange(from = 0, to = 15) int pduSessionId,
                @Nullable NetworkSliceInfo sliceInfo,
                @Nullable TrafficDescriptor trafficDescriptor,
                boolean matchAllRuleAllowed,
                @NonNull DataServiceCallback callback) {

            mProcessingStartTime = System.currentTimeMillis();
            Log.d(
                    SUB_TAG,
                    "Setup data call with network: "
                            + accessNetworkType
                            + ", reason: "
                            + requestReasonToString(reason)
                            + ", pduSessionId: "
                            + pduSessionId
                            + ", DataProfile: "
                            + dataProfile
                            + ", isRoaming:"
                            + isRoaming
                            + ", allowRoaming: "
                            + allowRoaming
                            + ", linkProperties: "
                            + linkProperties);

            SetupDataCallData setupDataCallData =
                    new SetupDataCallData(
                            accessNetworkType,
                            dataProfile,
                            isRoaming,
                            allowRoaming,
                            reason,
                            linkProperties,
                            pduSessionId,
                            sliceInfo,
                            trafficDescriptor,
                            matchAllRuleAllowed,
                            callback,
                            this);

            int networkTransport = -1;
            if (sDefaultDataTransport == Transport.MOBILE) {
                networkTransport = TRANSPORT_CELLULAR;
            } else if (sDefaultDataTransport == Transport.WIFI) {
                networkTransport = TRANSPORT_WIFI;
            }

            if (dataProfile != null) {
                ApnSetting apnSetting = dataProfile.getApnSetting();
                this.setMetricsAtom(
                        // ApnName
                        apnSetting != null ? apnSetting.getApnName() : "",
                        // ApnType
                        apnSetting != null ? apnSetting.getApnTypeBitmask() : ApnSetting.TYPE_NONE,
                        // IsHandover
                        (reason == DataService.REQUEST_REASON_HANDOVER),
                        // Source Rat
                        getCurrentCellularRat(),
                        // IsRoaming
                        isRoaming,
                        // Is Network Connected
                        sNetworkConnected,
                        // Transport Type
                        networkTransport);
            }

            getIwlanDataServiceHandler()
                    .sendMessage(
                            getIwlanDataServiceHandler()
                                    .obtainMessage(EVENT_SETUP_DATA_CALL, setupDataCallData));
        }

        /**
         * Deactivate a data connection. The data service provider must implement this method to
         * support data connection tear down. When completed or error, the service must invoke the
         * provided callback to notify the platform.
         *
         * <p>Note: For handovers, in compliance with 3GPP specs (TS 23.402 clause 8.6.1, TS 23.502
         * clause 4.11.4.1), a {@link KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT} delay is
         * implemented to allow the network to release the IKE tunnel. If the network fails to
         * release it within this timeframe, the UE will take over the release process.
         *
         * @param cid Call id returned in the callback of {@link
         *     DataServiceProvider#setupDataCall(int, DataProfile, boolean, boolean, int,
         *     LinkProperties, DataServiceCallback)}.
         * @param reason The reason for data deactivation. Must be {@link #REQUEST_REASON_NORMAL},
         *     {@link #REQUEST_REASON_SHUTDOWN} or {@link #REQUEST_REASON_HANDOVER}.
         * @param callback The result callback for this request. Null if the client does not care
         */
        @Override
        public void deactivateDataCall(
                int cid, @DeactivateDataReason int reason, DataServiceCallback callback) {
            Log.d(
                    SUB_TAG,
                    "Deactivate data call with reason: "
                            + requestReasonToString(reason)
                            + ", cid: "
                            + cid
                            + ", callback: "
                            + callback);

            boolean isRequestForHandoverToWWAN = (reason == REQUEST_REASON_HANDOVER);

            int delayTimeSeconds = 0;
            if (isRequestForHandoverToWWAN) {
                delayTimeSeconds =
                        IwlanCarrierConfig.getConfigInt(
                                mContext,
                                getSlotIndex(),
                                IwlanCarrierConfig.KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT);
            }

            int event =
                    (delayTimeSeconds > 0)
                            ? EVENT_DEACTIVATE_DATA_CALL_WITH_DELAY
                            : EVENT_DEACTIVATE_DATA_CALL;

            DeactivateDataCallData deactivateDataCallData =
                    new DeactivateDataCallData(cid, reason, callback, this, delayTimeSeconds);

            getIwlanDataServiceHandler()
                    .obtainMessage(event, deactivateDataCallData)
                    .sendToTarget();
        }

        /**
         * Requests validation check to see if the network is working properly for a given data
         * call.
         *
         * <p>This request is completed immediately after submitting the request to the data service
         * provider and receiving {@link DataServiceCallback.ResultCode}, and progress status or
         * validation results are notified through {@link
         * DataCallResponse#getNetworkValidationStatus}.
         *
         * <p>If the network validation request is submitted successfully, {@link
         * DataServiceCallback#RESULT_SUCCESS} is passed to {@code resultCodeCallback}. If the
         * network validation feature is not supported by the data service provider itself, {@link
         * DataServiceCallback#RESULT_ERROR_UNSUPPORTED} is passed to {@code resultCodeCallback}.
         * See {@link DataServiceCallback.ResultCode} for the type of response that indicates
         * whether the request was successfully submitted or had an error.
         *
         * <p>In response to this network validation request, providers can validate the data call
         * in their own way. For example, in IWLAN, the DPD (Dead Peer Detection) can be used as a
         * tool to check whether a data call is alive.
         *
         * @param cid The identifier of the data call which is provided in {@link DataCallResponse}
         * @param executor The callback executor for the response.
         * @param resultCodeCallback Listener for the {@link DataServiceCallback.ResultCode} that
         *     request validation to the DataService and checks if the request has been submitted.
         */
        @Override
        public void requestNetworkValidation(
                int cid, Executor executor, Consumer<Integer> resultCodeCallback) {
            Objects.requireNonNull(executor, "executor cannot be null");
            Objects.requireNonNull(resultCodeCallback, "resultCodeCallback cannot be null");
            Log.d(TAG, "request Network Validation: " + cid);

            getIwlanDataServiceHandler()
                    .obtainMessage(
                            EVENT_REQUEST_NETWORK_VALIDATION,
                            new NetworkValidationInfo(cid, executor, resultCodeCallback, this))
                    .sendToTarget();
        }

        public void forceCloseTunnelsInDeactivatingState() {
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                TunnelState tunnelState = entry.getValue();
                if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGDOWN) {
                    getTunnelManager()
                            .closeTunnel(
                                    entry.getKey(),
                                    true /* forceClose */,
                                    getIwlanTunnelCallback(),
                                    getIwlanTunnelMetrics(),
                                    BRINGDOWN_REASON_IN_DEACTIVATING_STATE);
                }
            }
        }

        /**
         * Closes all tunnels forcefully for a specified reason.
         *
         * @param reason The reason for closing the tunnel. Must be {@link
         *     EpdgTunnelManager.TunnelBringDownReason}.
         */
        void forceCloseTunnels(@EpdgTunnelManager.TunnelBringDownReason int reason) {
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                getTunnelManager()
                        .closeTunnel(
                                entry.getKey(),
                                true /* forceClose */,
                                getIwlanTunnelCallback(),
                                getIwlanTunnelMetrics(),
                                reason);
            }
        }

        /**
         * Get the active data call list.
         *
         * @param callback The result callback for this request.
         */
        @Override
        public void requestDataCallList(DataServiceCallback callback) {
            getIwlanDataServiceHandler()
                    .sendMessage(
                            getIwlanDataServiceHandler()
                                    .obtainMessage(
                                            EVENT_DATA_CALL_LIST_REQUEST,
                                            new DataCallRequestData(
                                                    callback, IwlanDataServiceProvider.this)));
        }

        @VisibleForTesting
        protected void setTunnelState(
                DataProfile dataProfile,
                DataServiceCallback callback,
                int tunnelStatus,
                TunnelLinkProperties linkProperties,
                boolean isHandover,
                int pduSessionId,
                boolean isImsOrEmergency,
                boolean isDataCallSetupWithN1) {
            TunnelState tunnelState = new TunnelState(callback);
            tunnelState.setState(tunnelStatus);
            tunnelState.setProtocolType(dataProfile.getApnSetting().getProtocol());
            tunnelState.setTunnelLinkProperties(linkProperties);
            tunnelState.setIsHandover(isHandover);
            tunnelState.setPduSessionId(pduSessionId);
            tunnelState.setIsImsOrEmergency(isImsOrEmergency);
            tunnelState.setIsDataCallWithN1(isDataCallSetupWithN1);
            mTunnelStateForApn.put(dataProfile.getApnSetting().getApnName(), tunnelState);
        }

        @VisibleForTesting
        void setMetricsAtom(
                String apnName,
                int apntype,
                boolean isHandover,
                int sourceRat,
                boolean isRoaming,
                boolean isNetworkConnected,
                int transportType) {
            MetricsAtom metricsAtom = new MetricsAtom();
            metricsAtom.setApnType(apntype);
            metricsAtom.setIsHandover(isHandover);
            metricsAtom.setSourceRat(sourceRat);
            metricsAtom.setIsCellularRoaming(isRoaming);
            metricsAtom.setIsNetworkConnected(isNetworkConnected);
            metricsAtom.setTransportType(transportType);
            mMetricsAtomForApn.put(apnName, metricsAtom);
        }

        @VisibleForTesting
        @Nullable
        public MetricsAtom getMetricsAtomByApn(String apnName) {
            return mMetricsAtomForApn.get(apnName);
        }

        @VisibleForTesting
        public IwlanTunnelCallback getIwlanTunnelCallback() {
            return mIwlanTunnelCallback;
        }

        @VisibleForTesting
        public IwlanTunnelMetricsImpl getIwlanTunnelMetrics() {
            return mIwlanTunnelMetrics;
        }

        @VisibleForTesting
        IwlanDataTunnelStats getTunnelStats() {
            return mTunnelStats;
        }

        private void updateNetwork(
                @Nullable Network network, @Nullable LinkProperties linkProperties) {
            if (mIwlanDataService.isNetworkConnected(
                    isActiveDataOnOtherSub(getSlotIndex()),
                    IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex()))) {
                getTunnelManager().updateNetwork(network, linkProperties);
            }

            if (Objects.equals(network, sNetwork)) {
                return;
            }
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                TunnelState tunnelState = entry.getValue();
                if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGUP) {
                    // force close tunnels in bringup since IKE lib only supports
                    // updating network for tunnels that are already up.
                    // This may not result in actual closing of Ike Session since
                    // epdg selection may not be complete yet.
                    tunnelState.setState(TunnelState.TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP);
                    getTunnelManager()
                            .closeTunnel(
                                    entry.getKey(),
                                    true /* forceClose */,
                                    getIwlanTunnelCallback(),
                                    getIwlanTunnelMetrics(),
                                    BRINGDOWN_REASON_NETWORK_UPDATE_WHEN_TUNNEL_IN_BRINGUP);
                }
            }
        }

        private boolean isRegisteredCellInfoChanged(List<CellInfo> cellInfoList) {
            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }

                if (mCellInfo == null || mCellInfo != cellInfo) {
                    mCellInfo = cellInfo;
                    Log.d(TAG, " Update cached cellinfo");
                    return true;
                }
            }
            return false;
        }

        private void dnsPrefetchCheck() {
            boolean networkConnected =
                    mIwlanDataService.isNetworkConnected(
                            isActiveDataOnOtherSub(getSlotIndex()),
                            IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex()));
            /* Check if we need to do prefecting */
            if (networkConnected
                    && mCarrierConfigReady
                    && mWfcEnabled
                    && mTunnelStateForApn.isEmpty()) {

                // Get roaming status
                TelephonyManager telephonyManager =
                        mContext.getSystemService(TelephonyManager.class);
                telephonyManager =
                        telephonyManager.createForSubscriptionId(
                                IwlanHelper.getSubId(mContext, getSlotIndex()));
                boolean isRoaming = telephonyManager.isNetworkRoaming();
                Log.d(TAG, "Trigger EPDG prefetch. Roaming=" + isRoaming);

                prefetchEpdgServerList(mIwlanDataService.sNetwork, isRoaming);
            }
        }

        private void prefetchEpdgServerList(Network network, boolean isRoaming) {
            mEpdgSelector.getValidatedServerList(
                    0,
                    EpdgSelector.PROTO_FILTER_IPV4V6,
                    EpdgSelector.SYSTEM_PREFERRED,
                    isRoaming,
                    false,
                    network,
                    null);
            mEpdgSelector.getValidatedServerList(
                    0,
                    EpdgSelector.PROTO_FILTER_IPV4V6,
                    EpdgSelector.SYSTEM_PREFERRED,
                    isRoaming,
                    true,
                    network,
                    null);
        }

        private int getCurrentCellularRat() {
            TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
            telephonyManager =
                    telephonyManager.createForSubscriptionId(
                            IwlanHelper.getSubId(mContext, getSlotIndex()));
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList == null) {
                Log.e(TAG, "cellInfoList is NULL");
                return 0;
            }

            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }
                if (cellInfo instanceof CellInfoGsm) {
                    return TelephonyManager.NETWORK_TYPE_GSM;
                } else if (cellInfo instanceof CellInfoWcdma) {
                    return TelephonyManager.NETWORK_TYPE_UMTS;
                } else if (cellInfo instanceof CellInfoLte) {
                    return TelephonyManager.NETWORK_TYPE_LTE;
                } else if (cellInfo instanceof CellInfoNr) {
                    return TelephonyManager.NETWORK_TYPE_NR;
                }
            }
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        /* Determines if this subscription is in an active call */
        private boolean isOnCall() {
            return mCallState != TelephonyManager.CALL_STATE_IDLE;
        }

        /**
         * IMS and Emergency are not allowed to retry with initial attach during call to keep call
         * continuity. Other APNs like XCAP and MMS are allowed to retry with initial attach
         * regardless of the call state.
         */
        private boolean shouldRetryWithInitialAttachForHandoverRequest(
                String apn, TunnelState tunnelState) {
            boolean isOnImsOrEmergencyCall = tunnelState.getIsImsOrEmergency() && isOnCall();
            return tunnelState.getIsHandover()
                    && !isOnImsOrEmergencyCall
                    && ErrorPolicyManager.getInstance(mContext, getSlotIndex())
                            .shouldRetryWithInitialAttach(apn);
        }

        /**
         * Called when the instance of data service is destroyed (e.g. got unbind or binder died) or
         * when the data service provider is removed.
         */
        @Override
        public void close() {
            // TODO: call epdgtunnelmanager.releaseInstance or equivalent
            mIwlanDataService.removeDataServiceProvider(this);
            IwlanEventListener iwlanEventListener =
                    IwlanEventListener.getInstance(mContext, getSlotIndex());
            iwlanEventListener.removeEventListener(getIwlanDataServiceHandler());
            iwlanEventListener.unregisterContentObserver();
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("---- IwlanDataServiceProvider[" + getSlotIndex() + "] ----");
            boolean isDDS = IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex());
            boolean isCSTEnabled = IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex());
            pw.println(
                    "isDefaultDataSlot: "
                            + isDDS
                            + "subID: "
                            + IwlanHelper.getSubId(mContext, getSlotIndex())
                            + " mConnectedDataSub: "
                            + mConnectedDataSub
                            + " isCrossSimEnabled: "
                            + isCSTEnabled);
            pw.println(
                    "isNetworkConnected: "
                            + isNetworkConnected(
                                    isActiveDataOnOtherSub(getSlotIndex()), isCSTEnabled)
                            + " Wfc enabled: "
                            + mWfcEnabled);
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                pw.println("Tunnel state for APN: " + entry.getKey());
                pw.println(entry.getValue());
            }
            pw.println(mTunnelStats);
            EpdgTunnelManager.getInstance(mContext, getSlotIndex()).dump(pw);
            ErrorPolicyManager.getInstance(mContext, getSlotIndex()).dump(pw);
            pw.println("-------------------------------------");
        }

        @VisibleForTesting
        public void setCalendar(Calendar c) {
            mCalendar = c;
        }

        private boolean isPdnReestablishNeededOnIdleN1Update() {
            return isN1ModeSupported() && (needIncludeN1ModeCapability() != mIs5GEnabledOnUi);
        }

        private void disconnectPdnForN1ModeUpdate() {
            if (hasActiveOrInitiatingDataCall()) {
                Log.d(TAG, "Disconnect PDNs for N1 mode update");
                forceCloseTunnels(
                        mIs5GEnabledOnUi
                                ? EpdgTunnelManager.BRINGDOWN_REASON_ENABLE_N1_MODE
                                : EpdgTunnelManager.BRINGDOWN_REASON_DISABLE_N1_MODE);
            }
        }

        private boolean hasActiveOrInitiatingDataCall() {
            return mTunnelStateForApn.values().stream()
                    .anyMatch(
                            tunnelState ->
                                    tunnelState.getState() == TunnelState.TUNNEL_UP
                                            || tunnelState.getState()
                                                    == TunnelState.TUNNEL_IN_BRINGUP);
        }

        // TODO(b/309867756): Include N1_MODE_CAPABILITY inclusion status in metrics.
        private boolean needIncludeN1ModeCapability() {
            if (!IwlanCarrierConfig.getConfigBoolean(
                    mContext,
                    getSlotIndex(),
                    IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL)) {
                return isN1ModeSupported();
            }
            if (!isN1ModeSupported()) {
                return false;
            }
            // Maintain uniform N1_MODE_CAPABILITY Notify inclusion for all PDNs.
            // Initiate PDN with current N1 inclusion in tunnel_up or tunnel_in_bringup states;
            // otherwise, use UI settings.
            return hasActiveOrInitiatingDataCall() ? isDataCallSetupWithN1() : mIs5GEnabledOnUi;
        }

        private boolean isDataCallSetupWithN1() {
            return mTunnelStateForApn.values().stream().anyMatch(TunnelState::getIsDataCallWithN1);
        }

        protected boolean isN1ModeSupported() {
            int[] nrAvailabilities =
                    IwlanCarrierConfig.getConfigIntArray(
                            mContext,
                            getSlotIndex(),
                            CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY);
            Log.d(
                    TAG,
                    "KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY : "
                            + Arrays.toString(nrAvailabilities));
            return Arrays.stream(nrAvailabilities)
                    .anyMatch(k -> k == CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA);
        }
    }

    private final class IwlanDataServiceHandler extends Handler {
        private final String TAG = IwlanDataServiceHandler.class.getSimpleName();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "msg.what = " + eventToString(msg.what));

            String apnName;
            IwlanDataServiceProvider iwlanDataServiceProvider;
            IwlanDataServiceProvider.TunnelState tunnelState;
            DataServiceCallback callback;
            int reason;
            int slotId;
            int retryTimeMillis;
            int errorCause;
            MetricsAtom metricsAtom;

            switch (msg.what) {
                case EVENT_TUNNEL_OPENED:
                    TunnelOpenedData tunnelOpenedData = (TunnelOpenedData) msg.obj;
                    iwlanDataServiceProvider = tunnelOpenedData.mIwlanDataServiceProvider;
                    apnName = tunnelOpenedData.mApnName;
                    TunnelLinkProperties tunnelLinkProperties =
                            tunnelOpenedData.mTunnelLinkProperties;

                    tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
                    // tunnelstate should not be null, design violation.
                    // if its null, we should crash and debug.
                    tunnelState.setTunnelLinkProperties(tunnelLinkProperties);
                    tunnelState.setState(IwlanDataServiceProvider.TunnelState.TUNNEL_UP);
                    iwlanDataServiceProvider.mTunnelStats.reportTunnelSetupSuccess(
                            apnName, tunnelState);

                    iwlanDataServiceProvider.deliverCallback(
                            IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_SUCCESS,
                            tunnelState.getDataServiceCallback(),
                            iwlanDataServiceProvider.apnTunnelStateToDataCallResponse(apnName));
                    break;

                case EVENT_TUNNEL_CLOSED:
                    TunnelClosedData tunnelClosedData = (TunnelClosedData) msg.obj;
                    iwlanDataServiceProvider = tunnelClosedData.mIwlanDataServiceProvider;
                    apnName = tunnelClosedData.mApnName;
                    IwlanError iwlanError = tunnelClosedData.mIwlanError;

                    tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);

                    if (tunnelState == null) {
                        // On a successful handover to EUTRAN, the NW may initiate an IKE DEL before
                        // the UE initiates a deactivateDataCall(). There may be a race condition
                        // where the deactivateDataCall() arrives immediately before
                        // IwlanDataService receives EVENT_TUNNEL_CLOSED (and clears TunnelState).
                        // Even though there is no tunnel, EpdgTunnelManager will still process the
                        // bringdown request and send back an onClosed() to ensure state coherence.
                        if (iwlanError.getErrorType() != IwlanError.TUNNEL_NOT_FOUND) {
                            Log.w(
                                    TAG,
                                    "Tunnel state does not exist! Unexpected IwlanError: "
                                            + iwlanError);
                        }
                        break;
                    }

                    if (tunnelState.hasPendingDeactivateDataCallData()) {
                        // Iwlan delays handling EVENT_DEACTIVATE_DATA_CALL to give the network time
                        // to release the PDN.  This allows for immediate response to Telephony if
                        // the network releases the PDN before timeout. Otherwise, Telephony's PDN
                        // state waits for Iwlan, blocking further actions on this PDN.
                        resumePendingDeactivationIfExists(
                                tunnelState.getPendingDeactivateDataCallData());
                    }

                    iwlanDataServiceProvider.mTunnelStats.reportTunnelDown(apnName, tunnelState);
                    iwlanDataServiceProvider.mTunnelStateForApn.remove(apnName);
                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);

                    if (tunnelState.getState()
                                    == IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGUP
                            || tunnelState.getState()
                                    == IwlanDataServiceProvider.TunnelState
                                            .TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP) {
                        DataCallResponse.Builder respBuilder = new DataCallResponse.Builder();
                        respBuilder
                                .setId(apnName.hashCode())
                                .setProtocolType(tunnelState.getRequestedProtocolType());

                        if (iwlanDataServiceProvider.shouldRetryWithInitialAttachForHandoverRequest(
                                apnName, tunnelState)) {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL);
                            metricsAtom.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL);
                        } else if (tunnelState.getIsHandover()) {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
                            metricsAtom.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
                        }

                        errorCause =
                                ErrorPolicyManager.getInstance(
                                                mContext, iwlanDataServiceProvider.getSlotIndex())
                                        .getDataFailCause(apnName);
                        if (errorCause != DataFailCause.NONE) {
                            respBuilder.setCause(errorCause);
                            metricsAtom.setDataCallFailCause(errorCause);

                            retryTimeMillis =
                                    (int)
                                            ErrorPolicyManager.getInstance(
                                                            mContext,
                                                            iwlanDataServiceProvider.getSlotIndex())
                                                    .getRemainingRetryTimeMs(apnName);
                            respBuilder.setRetryDurationMillis(retryTimeMillis);
                            metricsAtom.setRetryDurationMillis(retryTimeMillis);
                        } else {
                            // TODO(b/265215349): Use a different DataFailCause for scenario where
                            // tunnel in bringup is closed or force-closed without error.
                            respBuilder.setCause(DataFailCause.IWLAN_NETWORK_FAILURE);
                            metricsAtom.setDataCallFailCause(DataFailCause.IWLAN_NETWORK_FAILURE);
                            respBuilder.setRetryDurationMillis(5000);
                            metricsAtom.setRetryDurationMillis(5000);
                        }

                        // Record setup result for the Metrics
                        metricsAtom.setSetupRequestResult(DataServiceCallback.RESULT_SUCCESS);
                        metricsAtom.setIwlanError(iwlanError.getErrorType());

                        metricsAtom.setIwlanErrorWrappedClassnameAndStack(iwlanError);

                        metricsAtom.setTunnelState(tunnelState.getState());
                        metricsAtom.setMessageId(
                                IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED);

                        metricsAtom.setErrorCountOfSameCause(
                                ErrorPolicyManager.getInstance(
                                                mContext, iwlanDataServiceProvider.getSlotIndex())
                                        .getLastErrorCountOfSameCause(apnName));

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                respBuilder.build());
                        return;
                    }

                    // iwlan service triggered teardown
                    if (tunnelState.getState()
                            == IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGDOWN) {

                        // IO exception happens when IKE library fails to retransmit requests.
                        // This can happen for multiple reasons:
                        // 1. Network disconnection due to wifi off.
                        // 2. Epdg server does not respond.
                        // 3. Socket send/receive fails.
                        // Ignore this during tunnel bring down.
                        if (iwlanError.getErrorType() != IwlanError.NO_ERROR
                                && iwlanError.getErrorType()
                                        != IwlanError.IKE_INTERNAL_IO_EXCEPTION) {
                            Log.e(TAG, "Unexpected error during tunnel bring down: " + iwlanError);
                        }

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                null);

                        return;
                    }

                    // just update list of data calls. No way to send error up
                    iwlanDataServiceProvider.notifyDataCallListChanged(
                            iwlanDataServiceProvider.getCallList());

                    // Report IwlanPdnDisconnectedReason due to the disconnection is neither for
                    // SETUP_DATA_CALL nor DEACTIVATE_DATA_CALL request.
                    metricsAtom.setDataCallFailCause(
                            ErrorPolicyManager.getInstance(
                                            mContext, iwlanDataServiceProvider.getSlotIndex())
                                    .getDataFailCause(apnName));

                    WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
                    if (wifiManager == null) {
                        Log.e(TAG, "Could not find wifiManager");
                        return;
                    }

                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo == null) {
                        Log.e(TAG, "wifiInfo is null");
                        return;
                    }

                    metricsAtom.setWifiSignalValue(wifiInfo.getRssi());
                    metricsAtom.setMessageId(IwlanStatsLog.IWLAN_PDN_DISCONNECTED_REASON_REPORTED);
                    break;

                case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mCarrierConfigReady = true;
                    iwlanDataServiceProvider.dnsPrefetchCheck();
                    break;

                case IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mCarrierConfigReady = false;
                    break;

                case IwlanEventListener.WIFI_CALLING_ENABLE_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mWfcEnabled = true;
                    iwlanDataServiceProvider.dnsPrefetchCheck();
                    break;

                case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mWfcEnabled = false;
                    break;

                case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);
                    iwlanDataServiceProvider.updateNetwork(sNetwork, sLinkProperties);
                    break;

                case IwlanEventListener.CELLINFO_CHANGED_EVENT:
                    List<CellInfo> cellInfolist = (List<CellInfo>) msg.obj;
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    if (cellInfolist != null
                            && iwlanDataServiceProvider.isRegisteredCellInfoChanged(cellInfolist)) {
                        int[] addrResolutionMethods =
                                IwlanCarrierConfig.getConfigIntArray(
                                        mContext,
                                        iwlanDataServiceProvider.getSlotIndex(),
                                        CarrierConfigManager.Iwlan
                                                .KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY);
                        for (int addrResolutionMethod : addrResolutionMethods) {
                            if (addrResolutionMethod
                                    == CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC) {
                                iwlanDataServiceProvider.dnsPrefetchCheck();
                            }
                        }
                    }
                    break;

                case IwlanEventListener.CALL_STATE_CHANGED_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    int previousCallState = iwlanDataServiceProvider.mCallState;
                    int currentCallState = iwlanDataServiceProvider.mCallState = msg.arg2;

                    if (!IwlanCarrierConfig.getConfigBoolean(
                            mContext,
                            iwlanDataServiceProvider.getSlotIndex(),
                            IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL)) {
                        break;
                    }

                    // Disconnect PDN if call ends and re-establishment needed.
                    if (previousCallState != currentCallState
                            && currentCallState == TelephonyManager.CALL_STATE_IDLE
                            && iwlanDataServiceProvider.isPdnReestablishNeededOnIdleN1Update()) {
                        iwlanDataServiceProvider.disconnectPdnForN1ModeUpdate();
                    }
                    break;

                case IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);
                    if (!IwlanCarrierConfig.getConfigBoolean(
                            mContext,
                            iwlanDataServiceProvider.getSlotIndex(),
                            IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL)) {
                        break;
                    }
                    long allowedNetworkType = (long) msg.obj;
                    onPreferredNetworkTypeChanged(iwlanDataServiceProvider, allowedNetworkType);
                    break;

                case EVENT_SETUP_DATA_CALL:
                    SetupDataCallData setupDataCallData = (SetupDataCallData) msg.obj;
                    int accessNetworkType = setupDataCallData.mAccessNetworkType;
                    @NonNull DataProfile dataProfile = setupDataCallData.mDataProfile;
                    boolean isRoaming = setupDataCallData.mIsRoaming;
                    reason = setupDataCallData.mReason;
                    LinkProperties linkProperties = setupDataCallData.mLinkProperties;
                    @IntRange(from = 0, to = 15)
                    int pduSessionId = setupDataCallData.mPduSessionId;
                    callback = setupDataCallData.mCallback;
                    iwlanDataServiceProvider = setupDataCallData.mIwlanDataServiceProvider;

                    if ((accessNetworkType != AccessNetworkType.IWLAN)
                            || (dataProfile == null)
                            || (dataProfile.getApnSetting() == null)
                            || (linkProperties == null
                                    && reason == DataService.REQUEST_REASON_HANDOVER)) {

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                                callback,
                                null);
                        return;
                    }

                    slotId = iwlanDataServiceProvider.getSlotIndex();
                    boolean isCSTEnabled = IwlanHelper.isCrossSimCallingEnabled(mContext, slotId);
                    boolean networkConnected =
                            isNetworkConnected(isActiveDataOnOtherSub(slotId), isCSTEnabled);
                    Log.d(
                            TAG + "[" + slotId + "]",
                            "isDds: "
                                    + IwlanHelper.isDefaultDataSlot(mContext, slotId)
                                    + ", isActiveDataOnOtherSub: "
                                    + isActiveDataOnOtherSub(slotId)
                                    + ", isCstEnabled: "
                                    + isCSTEnabled
                                    + ", transport: "
                                    + sDefaultDataTransport);

                    if (!networkConnected) {
                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                5 /* DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE
                                   */,
                                callback,
                                null);
                        return;
                    }

                    // Update Network & LinkProperties to EpdgTunnelManager
                    iwlanDataServiceProvider
                            .getTunnelManager()
                            .updateNetwork(sNetwork, sLinkProperties);
                    Log.d(TAG, "Update Network for SetupDataCall request");

                    tunnelState =
                            iwlanDataServiceProvider.mTunnelStateForApn.get(
                                    dataProfile.getApnSetting().getApnName());

                    // Return the existing PDN if the pduSessionId is the same and the tunnel
                    // state is TUNNEL_UP.
                    if (tunnelState != null) {
                        if (tunnelState.getPduSessionId() == pduSessionId
                                && tunnelState.getState()
                                        == IwlanDataServiceProvider.TunnelState.TUNNEL_UP) {
                            Log.w(
                                    TAG + "[" + slotId + "]",
                                    "The tunnel for "
                                            + dataProfile.getApnSetting().getApnName()
                                            + " already exists.");
                            iwlanDataServiceProvider.deliverCallback(
                                    IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                    DataServiceCallback.RESULT_SUCCESS,
                                    callback,
                                    iwlanDataServiceProvider.apnTunnelStateToDataCallResponse(
                                            dataProfile.getApnSetting().getApnName()));
                        } else {
                            Log.e(
                                    TAG + "[" + slotId + "]",
                                    "Force close the existing PDN. pduSessionId = "
                                            + tunnelState.getPduSessionId()
                                            + " Tunnel State = "
                                            + tunnelState.getState());
                            iwlanDataServiceProvider
                                    .getTunnelManager()
                                    .closeTunnel(
                                            dataProfile.getApnSetting().getApnName(),
                                            true /* forceClose */,
                                            iwlanDataServiceProvider.getIwlanTunnelCallback(),
                                            iwlanDataServiceProvider.getIwlanTunnelMetrics(),
                                            BRINGDOWN_REASON_SERVICE_OUT_OF_SYNC);
                            iwlanDataServiceProvider.deliverCallback(
                                    IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                    5 /* DataServiceCallback
                                      .RESULT_ERROR_TEMPORARILY_UNAVAILABLE */,
                                    callback,
                                    null);
                        }
                        return;
                    }

                    int apnTypeBitmask = dataProfile.getApnSetting().getApnTypeBitmask();
                    boolean isIms = hasApnTypes(apnTypeBitmask, ApnSetting.TYPE_IMS);
                    boolean isEmergency = hasApnTypes(apnTypeBitmask, ApnSetting.TYPE_EMERGENCY);

                    boolean isDataCallSetupWithN1 =
                            iwlanDataServiceProvider.needIncludeN1ModeCapability();

                    // Override N1_MODE_CAPABILITY exclusion only for Emergency PDN due to carrier
                    // network limitations
                    if (IwlanCarrierConfig.getConfigBoolean(
                                    mContext,
                                    slotId,
                                    IwlanCarrierConfig
                                            .KEY_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL)
                            && isEmergency) {
                        isDataCallSetupWithN1 = false;
                    }

                    TunnelSetupRequest.Builder tunnelReqBuilder =
                            TunnelSetupRequest.builder()
                                    .setApnName(dataProfile.getApnSetting().getApnName())
                                    .setIsRoaming(isRoaming)
                                    .setPduSessionId(
                                            isDataCallSetupWithN1
                                                    ? pduSessionId
                                                    : PDU_SESSION_ID_UNSET)
                                    .setApnIpProtocol(
                                            isRoaming
                                                    ? dataProfile
                                                            .getApnSetting()
                                                            .getRoamingProtocol()
                                                    : dataProfile.getApnSetting().getProtocol())
                                    .setRequestPcscf(isIms || isEmergency)
                                    .setIsEmergency(isEmergency);

                    if (reason == DataService.REQUEST_REASON_HANDOVER) {
                        // for now assume that, at max,  only one address of eachtype (v4/v6).
                        // TODO: Check if multiple ips can be sent in ike tunnel setup
                        for (LinkAddress lAddr : linkProperties.getLinkAddresses()) {
                            if (lAddr.isIpv4()) {
                                tunnelReqBuilder.setSrcIpv4Address(lAddr.getAddress());
                            } else if (lAddr.isIpv6()) {
                                tunnelReqBuilder.setSrcIpv6Address(lAddr.getAddress());
                                tunnelReqBuilder.setSrcIpv6AddressPrefixLength(
                                        lAddr.getPrefixLength());
                            }
                        }
                    }

                    iwlanDataServiceProvider.setTunnelState(
                            dataProfile,
                            callback,
                            IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGUP,
                            null,
                            (reason == DataService.REQUEST_REASON_HANDOVER),
                            pduSessionId,
                            isIms || isEmergency,
                            isDataCallSetupWithN1);

                    boolean result =
                            iwlanDataServiceProvider
                                    .getTunnelManager()
                                    .bringUpTunnel(
                                            tunnelReqBuilder.build(),
                                            iwlanDataServiceProvider.getIwlanTunnelCallback(),
                                            iwlanDataServiceProvider.getIwlanTunnelMetrics());
                    Log.d(TAG + "[" + slotId + "]", "bringup Tunnel with result:" + result);
                    if (!result) {
                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                                callback,
                                null);
                        return;
                    }
                    break;

                case EVENT_DEACTIVATE_DATA_CALL:
                    handleDeactivateDataCall((DeactivateDataCallData) msg.obj);
                    break;

                case EVENT_DEACTIVATE_DATA_CALL_WITH_DELAY:
                    handleDeactivateDataCallWithDelay((DeactivateDataCallData) msg.obj);
                    break;

                case EVENT_DATA_CALL_LIST_REQUEST:
                    DataCallRequestData dataCallRequestData = (DataCallRequestData) msg.obj;
                    callback = dataCallRequestData.mCallback;
                    iwlanDataServiceProvider = dataCallRequestData.mIwlanDataServiceProvider;

                    iwlanDataServiceProvider.deliverCallback(
                            IwlanDataServiceProvider.CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE,
                            DataServiceCallback.RESULT_SUCCESS,
                            callback,
                            null);
                    break;

                case EVENT_FORCE_CLOSE_TUNNEL:
                    for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                        dp.forceCloseTunnels(EpdgTunnelManager.BRINGDOWN_REASON_UNKNOWN);
                    }
                    break;

                case EVENT_ADD_DATA_SERVICE_PROVIDER:
                    iwlanDataServiceProvider = (IwlanDataServiceProvider) msg.obj;
                    addIwlanDataServiceProvider(iwlanDataServiceProvider);
                    break;

                case EVENT_REMOVE_DATA_SERVICE_PROVIDER:
                    iwlanDataServiceProvider = (IwlanDataServiceProvider) msg.obj;

                    slotId = iwlanDataServiceProvider.getSlotIndex();
                    IwlanDataServiceProvider dsp = sIwlanDataServiceProviders.remove(slotId);
                    if (dsp == null) {
                        Log.w(TAG + "[" + slotId + "]", "No DataServiceProvider exists for slot!");
                    }

                    if (sIwlanDataServiceProviders.isEmpty()) {
                        deinitNetworkCallback();
                    }
                    break;

                case EVENT_TUNNEL_OPENED_METRICS:
                    OnOpenedMetrics openedMetricsData = (OnOpenedMetrics) msg.obj;
                    iwlanDataServiceProvider = openedMetricsData.getIwlanDataServiceProvider();
                    apnName = openedMetricsData.getApnName();

                    // Record setup result for the Metrics
                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);
                    tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
                    metricsAtom.setSetupRequestResult(DataServiceCallback.RESULT_SUCCESS);
                    metricsAtom.setIwlanError(IwlanError.NO_ERROR);
                    metricsAtom.setDataCallFailCause(DataFailCause.NONE);
                    metricsAtom.setTunnelState(tunnelState.getState());
                    metricsAtom.setHandoverFailureMode(-1);
                    metricsAtom.setRetryDurationMillis(0);
                    metricsAtom.setMessageId(IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED);
                    metricsAtom.setEpdgServerAddress(openedMetricsData.getEpdgServerAddress());
                    metricsAtom.setProcessingDurationMillis(
                            (int)
                                    (System.currentTimeMillis()
                                            - iwlanDataServiceProvider.mProcessingStartTime));
                    metricsAtom.setEpdgServerSelectionDurationMillis(
                            openedMetricsData.getEpdgServerSelectionDuration());
                    metricsAtom.setIkeTunnelEstablishmentDurationMillis(
                            openedMetricsData.getIkeTunnelEstablishmentDuration());

                    metricsAtom.sendMetricsData();
                    metricsAtom.setMessageId(metricsAtom.INVALID_MESSAGE_ID);
                    break;

                case EVENT_TUNNEL_CLOSED_METRICS:
                    OnClosedMetrics closedMetricsData = (OnClosedMetrics) msg.obj;
                    iwlanDataServiceProvider = closedMetricsData.getIwlanDataServiceProvider();
                    apnName = closedMetricsData.getApnName();

                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);
                    if (metricsAtom == null) {
                        Log.w(TAG, "EVENT_TUNNEL_CLOSED_METRICS: MetricsAtom is null!");
                        break;
                    }
                    metricsAtom.setEpdgServerAddress(closedMetricsData.getEpdgServerAddress());
                    metricsAtom.setProcessingDurationMillis(
                            iwlanDataServiceProvider.mProcessingStartTime > 0
                                    ? (int)
                                            (System.currentTimeMillis()
                                                    - iwlanDataServiceProvider.mProcessingStartTime)
                                    : 0);
                    metricsAtom.setEpdgServerSelectionDurationMillis(
                            closedMetricsData.getEpdgServerSelectionDuration());
                    metricsAtom.setIkeTunnelEstablishmentDurationMillis(
                            closedMetricsData.getIkeTunnelEstablishmentDuration());

                    metricsAtom.sendMetricsData();
                    metricsAtom.setMessageId(metricsAtom.INVALID_MESSAGE_ID);
                    iwlanDataServiceProvider.mMetricsAtomForApn.remove(apnName);
                    break;

                case EVENT_ON_LIVENESS_STATUS_CHANGED:
                    handleLivenessStatusChange((TunnelValidationStatusData) msg.obj);
                    break;

                case EVENT_REQUEST_NETWORK_VALIDATION:
                    handleNetworkValidationRequest((NetworkValidationInfo) msg.obj);
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }

        public void handleDeactivateDataCall(DeactivateDataCallData data) {
            handleDeactivateDataCall(data, false);
        }

        public void handleDeactivateDataCallWithDelay(DeactivateDataCallData data) {
            handleDeactivateDataCall(data, true);
        }

        public void handleDeactivateDataCall(DeactivateDataCallData data, boolean isWithDelay) {
            IwlanDataServiceProvider serviceProvider = data.mIwlanDataServiceProvider;
            String matchingApn = findMatchingApn(serviceProvider, data.mCid);

            if (matchingApn == null) {
                deliverDeactivationError(serviceProvider, data.mCallback);
                return;
            }

            if (isWithDelay) {
                Log.d(TAG, "Delaying deactivation for APN: " + matchingApn);
                scheduleDelayedDeactivateDataCall(serviceProvider, data, matchingApn);
                return;
            }
            Log.d(TAG, "Processing deactivation for APN: " + matchingApn);
            processDeactivateDataCall(serviceProvider, data, matchingApn);
        }

        private void handleNetworkValidationRequest(NetworkValidationInfo networkValidationInfo) {
            IwlanDataServiceProvider iwlanDataServiceProvider =
                    networkValidationInfo.mIwlanDataServiceProvider;
            int cid = networkValidationInfo.mCid;
            Executor executor = networkValidationInfo.mExecutor;
            Consumer<Integer> resultCodeCallback = networkValidationInfo.mResultCodeCallback;
            IwlanDataServiceProvider.TunnelState tunnelState;

            String apnName = findMatchingApn(iwlanDataServiceProvider, cid);
            int resultCode;
            if (apnName == null) {
                Log.w(TAG, "no matching APN name found for network validation.");
                resultCode = DataServiceCallback.RESULT_ERROR_UNSUPPORTED;
            } else {
                EpdgTunnelManager epdgTunnelManager = iwlanDataServiceProvider.getTunnelManager();
                epdgTunnelManager.requestNetworkValidationForApn(apnName);
                resultCode = DataServiceCallback.RESULT_SUCCESS;
                tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
                if (tunnelState == null) {
                    Log.w(TAG, "EVENT_REQUEST_NETWORK_VALIDATION: tunnel state is null.");
                } else {
                    tunnelState.setNetworkValidationStatus(
                            PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS);
                }
            }
            executor.execute(() -> resultCodeCallback.accept(resultCode));
        }

        private void handleLivenessStatusChange(TunnelValidationStatusData validationStatusData) {
            IwlanDataServiceProvider iwlanDataServiceProvider =
                    validationStatusData.mIwlanDataServiceProvider;
            String apnName = validationStatusData.mApnName;
            IwlanDataServiceProvider.TunnelState tunnelState =
                    iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
            if (tunnelState == null) {
                Log.w(TAG, "EVENT_ON_LIVENESS_STATUS_CHANGED: tunnel state is null.");
                return;
            }
            tunnelState.setNetworkValidationStatus(validationStatusData.mStatus);
            iwlanDataServiceProvider.notifyDataCallListChanged(
                    iwlanDataServiceProvider.getCallList());
        }

        private String findMatchingApn(IwlanDataServiceProvider serviceProvider, int cid) {
            return serviceProvider.mTunnelStateForApn.keySet().stream()
                    .filter(apn -> apn.hashCode() == cid)
                    .findFirst()
                    .orElse(null);
        }

        private void deliverDeactivationError(
                IwlanDataServiceProvider serviceProvider, DataServiceCallback callback) {
            serviceProvider.deliverCallback(
                    IwlanDataServiceProvider.CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                    DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                    callback,
                    null);
        }

        private void scheduleDelayedDeactivateDataCall(
                IwlanDataServiceProvider serviceProvider,
                DeactivateDataCallData data,
                String matchingApn) {
            IwlanDataServiceProvider.TunnelState tunnelState =
                    serviceProvider.mTunnelStateForApn.get(matchingApn);
            tunnelState.setPendingDeactivateDataCallData(data);
            Handler handler = getIwlanDataServiceHandler();
            handler.sendMessageDelayed(
                    handler.obtainMessage(EVENT_DEACTIVATE_DATA_CALL, data),
                    data.mDelayTimeSeconds * 1000L);
        }

        private void processDeactivateDataCall(
                IwlanDataServiceProvider serviceProvider,
                DeactivateDataCallData data,
                String matchingApn) {
            int slotId = serviceProvider.getSlotIndex();
            boolean isNetworkLost =
                    !isNetworkConnected(
                            isActiveDataOnOtherSub(slotId),
                            IwlanHelper.isCrossSimCallingEnabled(mContext, slotId));
            boolean isHandoverSuccessful = (data.mReason == REQUEST_REASON_HANDOVER);

            IwlanDataServiceProvider.TunnelState tunnelState =
                    serviceProvider.mTunnelStateForApn.get(matchingApn);
            tunnelState.setState(IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGDOWN);
            tunnelState.setDataServiceCallback(data.mCallback);

            serviceProvider
                    .getTunnelManager()
                    .closeTunnel(
                            matchingApn,
                            isNetworkLost || isHandoverSuccessful, /* forceClose */
                            serviceProvider.getIwlanTunnelCallback(),
                            serviceProvider.getIwlanTunnelMetrics(),
                            BRINGDOWN_REASON_DEACTIVATE_DATA_CALL);
        }

        private void resumePendingDeactivationIfExists(
                DeactivateDataCallData deactivateDataCallData) {
            Handler handler = getIwlanDataServiceHandler();
            if (handler.hasMessages(EVENT_DEACTIVATE_DATA_CALL, deactivateDataCallData)) {
                // Remove any existing deactivation messages and request a new one in the front
                handler.removeMessages(EVENT_DEACTIVATE_DATA_CALL, deactivateDataCallData);
                handler.sendMessageAtFrontOfQueue(
                        handler.obtainMessage(EVENT_DEACTIVATE_DATA_CALL, deactivateDataCallData));
            }
        }

        IwlanDataServiceHandler(Looper looper) {
            super(looper);
        }
    }

    private static final class TunnelOpenedData {
        final String mApnName;
        final TunnelLinkProperties mTunnelLinkProperties;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private TunnelOpenedData(
                String apnName,
                TunnelLinkProperties tunnelLinkProperties,
                IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mTunnelLinkProperties = tunnelLinkProperties;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class TunnelClosedData {
        final String mApnName;
        final IwlanError mIwlanError;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private TunnelClosedData(
                String apnName, IwlanError iwlanError, IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mIwlanError = iwlanError;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class TunnelValidationStatusData {
        final String mApnName;
        final int mStatus;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private TunnelValidationStatusData(
                String apnName, int status, IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mStatus = status;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class NetworkValidationInfo {
        final int mCid;
        final Executor mExecutor;
        final Consumer<Integer> mResultCodeCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private NetworkValidationInfo(
                int cid, Executor executor, Consumer<Integer> r, IwlanDataServiceProvider dsp) {
            mCid = cid;
            mExecutor = executor;
            mResultCodeCallback = r;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class SetupDataCallData {
        final int mAccessNetworkType;
        @NonNull final DataProfile mDataProfile;
        final boolean mIsRoaming;
        final boolean mAllowRoaming;
        final int mReason;
        @Nullable final LinkProperties mLinkProperties;

        @IntRange(from = 0, to = 15)
        final int mPduSessionId;

        @Nullable final NetworkSliceInfo mSliceInfo;
        @Nullable final TrafficDescriptor mTrafficDescriptor;
        final boolean mMatchAllRuleAllowed;
        @NonNull final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private SetupDataCallData(
                int accessNetworkType,
                DataProfile dataProfile,
                boolean isRoaming,
                boolean allowRoaming,
                int reason,
                LinkProperties linkProperties,
                int pduSessionId,
                NetworkSliceInfo sliceInfo,
                TrafficDescriptor trafficDescriptor,
                boolean matchAllRuleAllowed,
                DataServiceCallback callback,
                IwlanDataServiceProvider dsp) {
            mAccessNetworkType = accessNetworkType;
            mDataProfile = dataProfile;
            mIsRoaming = isRoaming;
            mAllowRoaming = allowRoaming;
            mReason = reason;
            mLinkProperties = linkProperties;
            mPduSessionId = pduSessionId;
            mSliceInfo = sliceInfo;
            mTrafficDescriptor = trafficDescriptor;
            mMatchAllRuleAllowed = matchAllRuleAllowed;
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class DeactivateDataCallData {
        final int mCid;
        final int mReason;
        final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;
        final int mDelayTimeSeconds;

        private DeactivateDataCallData(
                int cid,
                int reason,
                DataServiceCallback callback,
                IwlanDataServiceProvider dsp,
                int delayTimeSeconds) {
            mCid = cid;
            mReason = reason;
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
            mDelayTimeSeconds = delayTimeSeconds;
        }
    }

    private static final class DataCallRequestData {
        final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private DataCallRequestData(DataServiceCallback callback, IwlanDataServiceProvider dsp) {
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
        }
    }

    static int getConnectedDataSub(NetworkCapabilities networkCapabilities) {
        int connectedDataSub = INVALID_SUB_ID;
        NetworkSpecifier specifier = networkCapabilities.getNetworkSpecifier();
        TransportInfo transportInfo = networkCapabilities.getTransportInfo();

        if (specifier instanceof TelephonyNetworkSpecifier) {
            connectedDataSub = ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
        } else if (transportInfo instanceof VcnTransportInfo) {
            connectedDataSub = ((VcnTransportInfo) transportInfo).getSubId();
        }
        return connectedDataSub;
    }

    static void setConnectedDataSub(int subId) {
        mConnectedDataSub = subId;
    }

    @VisibleForTesting
    static boolean isActiveDataOnOtherSub(int slotId) {
        int subId = IwlanHelper.getSubId(mContext, slotId);
        return mConnectedDataSub != INVALID_SUB_ID && subId != mConnectedDataSub;
    }

    @VisibleForTesting
    static boolean isNetworkConnected(boolean isActiveDataOnOtherSub, boolean isCstEnabled) {
        if (isActiveDataOnOtherSub && isCstEnabled) {
            // For cross-SIM IWLAN (Transport.MOBILE), an active data PDN must be maintained on the
            // other subscription.
            if (sNetworkConnected && (sDefaultDataTransport != Transport.MOBILE)) {
                Log.e(TAG, "Internet is on other slot, but default transport is not MOBILE!");
            }
            return sNetworkConnected;
        } else {
            // For all other cases, only Transport.WIFI can be used.
            return ((sDefaultDataTransport == Transport.WIFI) && sNetworkConnected);
        }
    }

    /* Note: this api should have valid transport if networkConnected==true */
    static void setNetworkConnected(
            boolean networkConnected, @NonNull Network network, Transport transport) {

        boolean hasNetworkChanged = false;
        boolean hasTransportChanged = false;
        boolean hasNetworkConnectedChanged = false;

        if (sNetworkConnected == networkConnected
                && network.equals(sNetwork)
                && sDefaultDataTransport == transport) {
            // Nothing changed
            return;
        }

        // safety check
        if (networkConnected && transport == Transport.UNSPECIFIED_NETWORK) {
            Log.e(TAG, "setNetworkConnected: Network connected but transport unspecified");
            return;
        }

        if (!network.equals(sNetwork)) {
            Log.e(TAG, "System default network changed from: " + sNetwork + " TO: " + network);
            hasNetworkChanged = true;
        }

        if (transport != sDefaultDataTransport) {
            Log.d(
                    TAG,
                    "Transport was changed from "
                            + sDefaultDataTransport.name()
                            + " to "
                            + transport.name());
            hasTransportChanged = true;
        }

        if (sNetworkConnected != networkConnected) {
            Log.d(
                    TAG,
                    "Network connected state change from "
                            + sNetworkConnected
                            + " to "
                            + networkConnected);
            hasNetworkConnectedChanged = true;
        }

        sDefaultDataTransport = transport;
        sNetworkConnected = networkConnected;

        if (networkConnected) {
            if (hasTransportChanged) {
                // Perform forceClose for tunnels in bringdown.
                // let framework handle explicit teardown
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.forceCloseTunnelsInDeactivatingState();
                }
            }

            if (transport == Transport.WIFI && hasNetworkConnectedChanged) {
                IwlanEventListener.onWifiConnected(mContext);
            }
            // only prefetch dns and updateNetwork if Network has changed
            if (hasNetworkChanged) {
                ConnectivityManager connectivityManager =
                        mContext.getSystemService(ConnectivityManager.class);
                LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                sLinkProperties = linkProperties;
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.dnsPrefetchCheck();
                    dp.updateNetwork(network, linkProperties);
                }
                IwlanHelper.updateCountryCodeWhenNetworkConnected();
            }
        } else {
            for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                // once network is disconnected, even NAT KA offload fails
                // But we should still let framework do an explicit teardown
                // so as to not affect an ongoing handover
                // only force close tunnels in bring down state
                dp.forceCloseTunnelsInDeactivatingState();
            }
        }
        sNetwork = network;
    }

    /**
     * Get the DataServiceProvider associated with the slotId
     *
     * @param slotId slot index
     * @return DataService.DataServiceProvider associated with the slot
     */
    public static DataService.DataServiceProvider getDataServiceProvider(int slotId) {
        return sIwlanDataServiceProviders.get(slotId);
    }

    public static Context getContext() {
        return mContext;
    }

    @Override
    public DataServiceProvider onCreateDataServiceProvider(int slotIndex) {
        // TODO: validity check on slot index
        Log.d(TAG, "Creating provider for " + slotIndex);

        if (mNetworkMonitorCallback == null) {
            // start monitoring network and register for default network callback
            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            mNetworkMonitorCallback = new IwlanNetworkMonitorCallback();
            if (connectivityManager != null) {
                connectivityManager.registerSystemDefaultNetworkCallback(
                        mNetworkMonitorCallback, getIwlanDataServiceHandler());
            }
            Log.d(TAG, "Registered with Connectivity Service");
        }

        IwlanDataServiceProvider dp = new IwlanDataServiceProvider(slotIndex, this);

        getIwlanDataServiceHandler()
                .sendMessage(
                        getIwlanDataServiceHandler()
                                .obtainMessage(EVENT_ADD_DATA_SERVICE_PROVIDER, dp));
        return dp;
    }

    public void removeDataServiceProvider(IwlanDataServiceProvider dp) {
        getIwlanDataServiceHandler()
                .sendMessage(
                        getIwlanDataServiceHandler()
                                .obtainMessage(EVENT_REMOVE_DATA_SERVICE_PROVIDER, dp));
    }

    @VisibleForTesting
    void addIwlanDataServiceProvider(IwlanDataServiceProvider dp) {
        int slotIndex = dp.getSlotIndex();
        if (sIwlanDataServiceProviders.containsKey(slotIndex)) {
            throw new IllegalStateException(
                    "DataServiceProvider already exists for slot " + slotIndex);
        }
        sIwlanDataServiceProviders.put(slotIndex, dp);
    }

    void deinitNetworkCallback() {
        // deinit network related stuff
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        if (connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(mNetworkMonitorCallback);
        }
        mNetworkMonitorCallback = null;
    }

    boolean hasApnTypes(int apnTypeBitmask, int expectedApn) {
        return (apnTypeBitmask & expectedApn) != 0;
    }

    @VisibleForTesting
    void setAppContext(Context appContext) {
        mContext = appContext;
    }

    @VisibleForTesting
    IwlanNetworkMonitorCallback getNetworkMonitorCallback() {
        return mNetworkMonitorCallback;
    }

    @VisibleForTesting
    @NonNull
    Handler getIwlanDataServiceHandler() {
        if (mIwlanDataServiceHandler == null) {
            mIwlanDataServiceHandler = new IwlanDataServiceHandler(getLooper());
        }
        return mIwlanDataServiceHandler;
    }

    @VisibleForTesting
    Looper getLooper() {
        mIwlanDataServiceHandlerThread = new HandlerThread("IwlanDataServiceThread");
        mIwlanDataServiceHandlerThread.start();
        return mIwlanDataServiceHandlerThread.getLooper();
    }

    private static String eventToString(int event) {
        switch (event) {
            case EVENT_TUNNEL_OPENED:
                return "EVENT_TUNNEL_OPENED";
            case EVENT_TUNNEL_CLOSED:
                return "EVENT_TUNNEL_CLOSED";
            case EVENT_SETUP_DATA_CALL:
                return "EVENT_SETUP_DATA_CALL";
            case EVENT_DEACTIVATE_DATA_CALL:
                return "EVENT_DEACTIVATE_DATA_CALL";
            case EVENT_DATA_CALL_LIST_REQUEST:
                return "EVENT_DATA_CALL_LIST_REQUEST";
            case EVENT_FORCE_CLOSE_TUNNEL:
                return "EVENT_FORCE_CLOSE_TUNNEL";
            case EVENT_ADD_DATA_SERVICE_PROVIDER:
                return "EVENT_ADD_DATA_SERVICE_PROVIDER";
            case EVENT_REMOVE_DATA_SERVICE_PROVIDER:
                return "EVENT_REMOVE_DATA_SERVICE_PROVIDER";
            case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                return "CARRIER_CONFIG_CHANGED_EVENT";
            case IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT:
                return "CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT";
            case IwlanEventListener.WIFI_CALLING_ENABLE_EVENT:
                return "WIFI_CALLING_ENABLE_EVENT";
            case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                return "WIFI_CALLING_DISABLE_EVENT";
            case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT:
                return "CROSS_SIM_CALLING_ENABLE_EVENT";
            case IwlanEventListener.CELLINFO_CHANGED_EVENT:
                return "CELLINFO_CHANGED_EVENT";
            case EVENT_TUNNEL_OPENED_METRICS:
                return "EVENT_TUNNEL_OPENED_METRICS";
            case EVENT_TUNNEL_CLOSED_METRICS:
                return "EVENT_TUNNEL_CLOSED_METRICS";
            case EVENT_DEACTIVATE_DATA_CALL_WITH_DELAY:
                return "EVENT_DEACTIVATE_DATA_CALL_WITH_DELAY";
            case IwlanEventListener.CALL_STATE_CHANGED_EVENT:
                return "CALL_STATE_CHANGED_EVENT";
            case IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT:
                return "PREFERRED_NETWORK_TYPE_CHANGED_EVENT";
            case EVENT_ON_LIVENESS_STATUS_CHANGED:
                return "EVENT_ON_LIVENESS_STATUS_CHANGED";
            case EVENT_REQUEST_NETWORK_VALIDATION:
                return "EVENT_REQUEST_NETWORK_VALIDATION";
            default:
                return "Unknown(" + event + ")";
        }
    }

    private void initAllowedNetworkType() {
        TelephonyManager mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mIs5GEnabledOnUi =
                ((mTelephonyManager.getAllowedNetworkTypesBitmask()
                                & TelephonyManager.NETWORK_TYPE_BITMASK_NR)
                        != 0);
    }

    private void onPreferredNetworkTypeChanged(
            IwlanDataServiceProvider iwlanDataServiceProvider, long allowedNetworkType) {
        boolean isCurrentUiEnable5G =
                (allowedNetworkType & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0;
        boolean isPreviousUiEnable5G = mIs5GEnabledOnUi;
        mIs5GEnabledOnUi = isCurrentUiEnable5G;
        if (!iwlanDataServiceProvider.isN1ModeSupported()) {
            return;
        }
        if (isPreviousUiEnable5G != isCurrentUiEnable5G) {
            if (!iwlanDataServiceProvider.isOnCall()) {
                iwlanDataServiceProvider.disconnectPdnForN1ModeUpdate();
            }
        }
    }

    @Override
    public void onCreate() {
        Context context = getApplicationContext().createAttributionContext(CONTEXT_ATTRIBUTION_TAG);
        setAppContext(context);
        IwlanBroadcastReceiver.startListening(mContext);
        IwlanCarrierConfigChangeListener.startListening(mContext);
        IwlanHelper.startCountryDetector(mContext);
        initAllowedNetworkType();
    }

    @Override
    public void onDestroy() {
        IwlanCarrierConfigChangeListener.stopListening(mContext);
        IwlanBroadcastReceiver.stopListening(mContext);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "IwlanDataService onBind");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "IwlanDataService onUnbind");
        getIwlanDataServiceHandler()
                .sendMessage(getIwlanDataServiceHandler().obtainMessage(EVENT_FORCE_CLOSE_TUNNEL));
        return super.onUnbind(intent);
    }

    private String requestReasonToString(int reason) {
        switch (reason) {
            case DataService.REQUEST_REASON_UNKNOWN:
                return "UNKNOWN";
            case DataService.REQUEST_REASON_NORMAL:
                return "NORMAL";
            case DataService.REQUEST_REASON_SHUTDOWN:
                return "SHUTDOWN";
            case DataService.REQUEST_REASON_HANDOVER:
                return "HANDOVER";
            default:
                return "UNKNOWN(" + reason + ")";
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        String transport = "UNSPECIFIED";
        if (sDefaultDataTransport == Transport.MOBILE) {
            transport = "CELLULAR";
        } else if (sDefaultDataTransport == Transport.WIFI) {
            transport = "WIFI";
        }
        pw.println("Default transport: " + transport);
        for (IwlanDataServiceProvider provider : sIwlanDataServiceProviders.values()) {
            pw.println();
            provider.dump(fd, pw, args);
            pw.println();
            pw.println();
        }
    }
}
