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

import android.annotation.NonNull;
import android.content.Context;
import android.location.Country;
import android.location.CountryDetector;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IwlanNetworkStatusTracker monitors if there is a network available for IWLAN and informs it to
 * registrants.
 */
class IwlanNetworkStatusTracker {
    private static final Boolean DBG = true;
    private static final String sLogTag = IwlanNetworkStatusTracker.class.getSimpleName();
    private static final int EVENT_BASE = 1000;
    private static final int EVENT_IWLAN_SERVICE_STATE_CHANGED = EVENT_BASE;
    private final Map<Integer, QnsRegistrantList> mIwlanNetworkListenersArray =
            new ConcurrentHashMap<>();
    private static final String LAST_KNOWN_COUNTRY_CODE_KEY = "last_known_country_code";
    private static final int INVALID_SUB_ID = -1;
    private final SparseArray<QnsCarrierConfigManager> mQnsConfigManagers = new SparseArray<>();
    private final SparseArray<QnsEventDispatcher> mQnsEventDispatchers = new SparseArray<>();
    private final SparseArray<QnsImsManager> mQnsImsManagers = new SparseArray<>();
    private final SparseArray<QnsTelephonyListener> mQnsTelephonyListeners = new SparseArray<>();
    private final Context mContext;
    private DefaultNetworkCallback mDefaultNetworkCallback;
    private final HandlerThread mHandlerThread;
    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;
    private Handler mNetCbHandler;
    private String mLastKnownCountryCode;
    private boolean mWifiAvailable = false;
    private boolean mWifiToggleOn = false;
    private Map<Integer, Boolean> mIwlanRegistered = new ConcurrentHashMap<>();

    // The current active data subscription. May not be the default data subscription.
    private int mConnectedDataSub = INVALID_SUB_ID;
    @VisibleForTesting SparseArray<IwlanEventHandler> mHandlerSparseArray = new SparseArray<>();
    @VisibleForTesting SparseArray<IwlanAvailabilityInfo> mLastIwlanAvailabilityInfo =
            new SparseArray<>();
    private CountryDetector mCountryDetector;

    enum LinkProtocolType {
        UNKNOWN,
        IPV4,
        IPV6,
        IPV4V6;
    }

    private static LinkProtocolType sLinkProtocolType = LinkProtocolType.UNKNOWN;

    class IwlanEventHandler extends Handler {
        private final int mSlotIndex;

        IwlanEventHandler(int slotId, Looper l) {
            super(l);
            mSlotIndex = slotId;
            List<Integer> events = new ArrayList<>();
            events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED);
            events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED);
            events.add(QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING);
            events.add(QnsEventDispatcher.QNS_EVENT_WIFI_ENABLED);
            mQnsEventDispatchers.get(mSlotIndex).registerEvent(events, this);
            mQnsTelephonyListeners
                    .get(mSlotIndex)
                    .registerIwlanServiceStateListener(
                            this, EVENT_IWLAN_SERVICE_STATE_CHANGED, null);
        }

        @Override
        public void handleMessage(Message message) {
            Log.d(sLogTag, "handleMessage msg=" + message.what);
            switch (message.what) {
                case QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED:
                    onCrossSimEnabledEvent(true, mSlotIndex);
                    break;
                case QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED:
                    onCrossSimEnabledEvent(false, mSlotIndex);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WIFI_ENABLED:
                    onWifiEnabled();
                    break;
                case QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING:
                    onWifiDisabling();
                    break;
                case EVENT_IWLAN_SERVICE_STATE_CHANGED:
                    QnsAsyncResult ar = (QnsAsyncResult) message.obj;
                    boolean isRegistered = (boolean) ar.mResult;
                    onIwlanServiceStateChanged(mSlotIndex, isRegistered);
                    break;
                default:
                    Log.d(sLogTag, "Unknown message received!");
                    break;
            }
        }
    }

    IwlanNetworkStatusTracker(@NonNull Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread(IwlanNetworkStatusTracker.class.getSimpleName());
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mNetCbHandler = new Handler(looper);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mLastIwlanAvailabilityInfo.clear();
        registerDefaultNetworkCb();
        Log.d(sLogTag, "Registered with Connectivity Service");
        startCountryDetector();
    }

    void initBySlotIndex(
            @NonNull QnsCarrierConfigManager configManager,
            @NonNull QnsEventDispatcher dispatcher,
            @NonNull QnsImsManager imsManager,
            @NonNull QnsTelephonyListener telephonyListener,
            int slotId) {
        mQnsConfigManagers.put(slotId, configManager);
        mQnsEventDispatchers.put(slotId, dispatcher);
        mQnsImsManagers.put(slotId, imsManager);
        mQnsTelephonyListeners.put(slotId, telephonyListener);
        mHandlerSparseArray.put(slotId, new IwlanEventHandler(slotId, mHandlerThread.getLooper()));
    }

    void closeBySlotIndex(int slotId) {
        IwlanEventHandler handler = mHandlerSparseArray.get(slotId);
        mQnsEventDispatchers.get(slotId).unregisterEvent(handler);
        mQnsTelephonyListeners.get(slotId).unregisterIwlanServiceStateChanged(handler);
        mIwlanNetworkListenersArray.remove(slotId);
        mQnsConfigManagers.remove(slotId);
        mQnsEventDispatchers.remove(slotId);
        mQnsImsManagers.remove(slotId);
        mQnsTelephonyListeners.remove(slotId);
        mHandlerSparseArray.remove(slotId);
    }

    @VisibleForTesting
    void onCrossSimEnabledEvent(boolean enabled, int slotId) {
        Log.d(sLogTag, "onCrossSimEnabledEvent enabled:" + enabled + " slotIndex:" + slotId);
        if (enabled) {
            int activeDataSub = INVALID_SUB_ID;
            NetworkSpecifier specifier;
            final Network activeNetwork = mConnectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                final NetworkCapabilities nc =
                        mConnectivityManager.getNetworkCapabilities(activeNetwork);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    specifier = nc.getNetworkSpecifier();
                    TransportInfo transportInfo = nc.getTransportInfo();
                    if (transportInfo instanceof VcnTransportInfo) {
                        activeDataSub = ((VcnTransportInfo) transportInfo).getSubId();
                    } else if (specifier instanceof TelephonyNetworkSpecifier) {
                        activeDataSub = ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
                    }
                    if (activeDataSub != INVALID_SUB_ID && activeDataSub != mConnectedDataSub) {
                        mConnectedDataSub = activeDataSub;
                    }
                }
            }
            notifyIwlanNetworkStatus();
        } else {
            notifyIwlanNetworkStatus(true);
        }
    }

    @VisibleForTesting
    void onWifiEnabled() {
        mWifiToggleOn = true;
        if (!mWifiAvailable) {
            for (Integer slotId : mIwlanNetworkListenersArray.keySet()) {
                if (!isCrossSimCallingCondition(slotId)
                        && mIwlanRegistered.containsKey(slotId)
                        && mIwlanRegistered.get(slotId)) {
                    mWifiAvailable = true;
                    notifyIwlanNetworkStatus(slotId, false);
                }
            }
        }
    }

    @VisibleForTesting
    void onWifiDisabling() {
        mWifiToggleOn = false;
        if (mWifiAvailable) {
            mWifiAvailable = false;
            notifyIwlanNetworkStatus(true);
        }
    }

    @VisibleForTesting
    void onIwlanServiceStateChanged(int slotId, boolean isRegistered) {
        mIwlanRegistered.put(slotId, isRegistered);
        notifyIwlanNetworkStatus(slotId, false);
    }

    private void notifyIwlanNetworkStatusToRegister(int slotId, QnsRegistrant r) {
        if (DBG) {
            Log.d(sLogTag, "notifyIwlanNetworkStatusToRegister");
        }
        IwlanAvailabilityInfo info = mLastIwlanAvailabilityInfo.get(slotId);
        if (info == null) {
            info = makeIwlanAvailabilityInfo(slotId);
            mLastIwlanAvailabilityInfo.put(slotId, info);
        }
        r.notifyResult(info);
    }

    private void registerDefaultNetworkCb() {
        if (mDefaultNetworkCallback == null) {
            mDefaultNetworkCallback = new DefaultNetworkCallback();
            mConnectivityManager.registerDefaultNetworkCallback(
                    mDefaultNetworkCallback, mNetCbHandler);
        }
    }

    private void unregisterDefaultNetworkCb() {
        if (mDefaultNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
            mDefaultNetworkCallback = null;
        }
    }

    protected void close() {
        mNetCbHandler.post(this::onClose);
        mHandlerThread.quitSafely();
    }

    private void onClose() {
        unregisterDefaultNetworkCb();
        mLastIwlanAvailabilityInfo.clear();
        mIwlanNetworkListenersArray.clear();
        mIwlanRegistered.clear();
        mCountryDetector.unregisterCountryDetectorCallback(this::updateCountryCode);
        Log.d(sLogTag, "closed IwlanNetworkStatusTracker");
    }

    public void registerIwlanNetworksChanged(int slotId, Handler h, int what) {
        if (h != null && mHandlerThread.isAlive()) {
            QnsRegistrant r = new QnsRegistrant(h, what, null);
            if (mIwlanNetworkListenersArray.get(slotId) == null) {
                mIwlanNetworkListenersArray.put(slotId, new QnsRegistrantList());
            }
            mIwlanNetworkListenersArray.get(slotId).add(r);
            IwlanEventHandler handler = mHandlerSparseArray.get(slotId);
            if (handler != null) {
                IwlanAvailabilityInfo lastInfo = mLastIwlanAvailabilityInfo.get(slotId);
                IwlanAvailabilityInfo newInfo = makeIwlanAvailabilityInfo(slotId);
                if (lastInfo == null || !lastInfo.equals(newInfo)) {
                    // if the LastIwlanAvailabilityInfo is no more valid, notify to all registrants.
                    handler.post(() -> notifyIwlanNetworkStatus());
                } else {
                    // if the LastIwlanAvailabilityInfo is valid, notify to only this registrant.
                    handler.post(() -> notifyIwlanNetworkStatusToRegister(slotId, r));
                }
            }
        }
    }

    void unregisterIwlanNetworksChanged(int slotId, Handler h) {
        if (mIwlanNetworkListenersArray.get(slotId) != null) {
            mIwlanNetworkListenersArray.get(slotId).remove(h);
        }
    }

    private IwlanAvailabilityInfo makeIwlanAvailabilityInfo(int slotId) {
        boolean iwlanEnable = false;
        boolean isCrossWfc = false;
        boolean isRegistered = false;
        boolean isBlockIpv6OnlyWifi = false;
        if (mQnsConfigManagers.contains(slotId)) {
            isBlockIpv6OnlyWifi = mQnsConfigManagers.get(slotId).blockIpv6OnlyWifi();
        }
        LinkProtocolType linkProtocolType = sLinkProtocolType;

        if (mIwlanRegistered.containsKey(slotId)) {
            isRegistered = mIwlanRegistered.get(slotId);
        }

        if (mWifiAvailable) {
            boolean blockWifi =
                    isBlockIpv6OnlyWifi
                            && ((linkProtocolType == LinkProtocolType.UNKNOWN)
                                    || (linkProtocolType == LinkProtocolType.IPV6));
            iwlanEnable = !blockWifi && isRegistered;
        } else if (isCrossSimCallingCondition(slotId) && isRegistered) {
            iwlanEnable = true;
            isCrossWfc = true;
        }
        if (DBG) {
            if (QnsUtils.isCrossSimCallingEnabled(mQnsImsManagers.get(slotId))) {
                Log.d(
                        sLogTag,
                        "makeIwlanAvailabilityInfo(slot:"
                                + slotId
                                + ") "
                                + "mWifiAvailable:"
                                + mWifiAvailable
                                + " mConnectedDataSub:"
                                + mConnectedDataSub
                                + " isRegistered:"
                                + isRegistered
                                + " subId:"
                                + QnsUtils.getSubId(mContext, slotId)
                                + " isDDS:"
                                + QnsUtils.isDefaultDataSubs(slotId)
                                + " iwlanEnable:"
                                + iwlanEnable
                                + " isCrossWfc:"
                                + isCrossWfc);
            } else {
                Log.d(
                        sLogTag,
                        "makeIwlanAvailabilityInfo(slot:"
                                + slotId
                                + ")"
                                + " mWifiAvailable:"
                                + mWifiAvailable
                                + " isRegistered:"
                                + isRegistered
                                + " iwlanEnable:"
                                + iwlanEnable
                                + "  isCrossWfc:"
                                + isCrossWfc
                                + " isBlockIpv6OnlyWifi:"
                                + isBlockIpv6OnlyWifi
                                + " linkProtocolType:"
                                + linkProtocolType);
            }
        }
        return new IwlanAvailabilityInfo(iwlanEnable, isCrossWfc);
    }

    private boolean isCrossSimCallingCondition(int slotId) {
        return QnsUtils.isCrossSimCallingEnabled(mQnsImsManagers.get(slotId))
                && QnsUtils.getSubId(mContext, slotId) != mConnectedDataSub
                && mConnectedDataSub != INVALID_SUB_ID;
    }

    private void notifyIwlanNetworkStatus() {
        notifyIwlanNetworkStatus(false);
    }

    private void notifyIwlanNetworkStatus(boolean notifyIwlanDisabled) {
        for (Integer slotId : mIwlanNetworkListenersArray.keySet()) {
            notifyIwlanNetworkStatus(slotId, notifyIwlanDisabled);
        }
    }

    private void notifyIwlanNetworkStatus(int slotId, boolean notifyIwlanDisabled) {
        Log.d(sLogTag, "notifyIwlanNetworkStatus for slot: " + slotId);
        IwlanAvailabilityInfo info = makeIwlanAvailabilityInfo(slotId);
        if (!info.getIwlanAvailable() && notifyIwlanDisabled) {
            Log.d(sLogTag, "setNotifyIwlanDisabled for slot: " + slotId);
            info.setNotifyIwlanDisabled();
        }
        if (!info.equals(mLastIwlanAvailabilityInfo.get(slotId))) {
            Log.d(sLogTag, "notify updated info for slot: " + slotId);
            if (mIwlanNetworkListenersArray.get(slotId) != null) {
                mIwlanNetworkListenersArray.get(slotId).notifyResult(info);
            }
            mLastIwlanAvailabilityInfo.put(slotId, info);
        }
    }

    class IwlanAvailabilityInfo {
        private boolean mIwlanAvailable = false;
        private boolean mIsCrossWfc = false;
        private boolean mNotifyIwlanDisabled = false;

        IwlanAvailabilityInfo(boolean iwlanAvailable, boolean crossWfc) {
            mIwlanAvailable = iwlanAvailable;
            mIsCrossWfc = crossWfc;
        }

        @VisibleForTesting
        void setNotifyIwlanDisabled() {
            mNotifyIwlanDisabled = true;
        }

        boolean getIwlanAvailable() {
            return mIwlanAvailable;
        }

        boolean isCrossWfc() {
            return mIsCrossWfc;
        }

        @VisibleForTesting
        boolean getNotifyIwlanDisabled() {
            return mNotifyIwlanDisabled;
        }

        boolean equals(IwlanAvailabilityInfo info) {
            if (info == null) {
                Log.d(sLogTag, " equals info is null");
                return false;
            }
            Log.d(
                    sLogTag,
                    "equals() IwlanAvailable: "
                            + mIwlanAvailable
                            + "/"
                            + info.mIwlanAvailable
                            + " IsCrossWfc: "
                            + mIsCrossWfc
                            + "/"
                            + info.mIsCrossWfc
                            + " NotifyIwlanDisabled: "
                            + mNotifyIwlanDisabled
                            + "/"
                            + info.mNotifyIwlanDisabled);
            return (mIwlanAvailable == info.mIwlanAvailable)
                    && (mIsCrossWfc == info.mIsCrossWfc)
                    && (mNotifyIwlanDisabled == info.mNotifyIwlanDisabled);
        }
    }

    final class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(sLogTag, "onAvailable: " + network);
            if (mConnectivityManager != null) {
                NetworkCapabilities nc = mConnectivityManager.getNetworkCapabilities(network);
                if (nc != null) {
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        mWifiToggleOn = true;
                        mWifiAvailable = true;
                        mConnectedDataSub = INVALID_SUB_ID;
                        notifyIwlanNetworkStatus();
                    } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        NetworkSpecifier specifier = nc.getNetworkSpecifier();
                        TransportInfo transportInfo = nc.getTransportInfo();
                        if (transportInfo instanceof VcnTransportInfo) {
                            mConnectedDataSub = ((VcnTransportInfo) transportInfo).getSubId();
                        } else if (specifier instanceof TelephonyNetworkSpecifier) {
                            mConnectedDataSub =
                                    ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
                        }
                        mWifiAvailable = false;
                        notifyIwlanNetworkStatus();
                    }
                }
            }
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link
         * android.net.ConnectivityManager.NetworkCallback#onAvailable} call with the new
         * replacement network for graceful handover. This method is not guaranteed to be called
         * before {@link android.net.ConnectivityManager.NetworkCallback#onLost} is called, for
         * example in case a network is suddenly disconnected.
         */
        @Override
        public void onLosing(Network network, int maxMsToLive) {
            Log.d(sLogTag, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or *
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(sLogTag, "onLost: " + network);
            if (mWifiAvailable) {
                mWifiAvailable = false;
            }
            if (mConnectedDataSub != INVALID_SUB_ID) {
                mConnectedDataSub = INVALID_SUB_ID;
            }
            sLinkProtocolType = LinkProtocolType.UNKNOWN;
            notifyIwlanNetworkStatus();
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(sLogTag, "onLinkPropertiesChanged: " + linkProperties);
            if (mWifiAvailable) {
                LinkProtocolType prevType = sLinkProtocolType;

                checkWifiLinkProtocolType(linkProperties);
                if (prevType != LinkProtocolType.IPV6
                        && sLinkProtocolType == LinkProtocolType.IPV6) {
                    notifyIwlanNetworkStatus(true);
                } else if (prevType != sLinkProtocolType) {
                    notifyIwlanNetworkStatus();
                }
            }
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            Log.d(sLogTag, "onBlockedStatusChanged: " + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                Network network, NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(sLogTag, "onCapabilitiesChanged: " + network);
            NetworkCapabilities nc = networkCapabilities;
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (!mWifiAvailable && mWifiToggleOn) {
                        mWifiAvailable = true;
                        mConnectedDataSub = INVALID_SUB_ID;
                        notifyIwlanNetworkStatus();
                    } else {
                        Log.d(sLogTag, "OnCapability : Wifi Available already true");
                    }
                } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    int activeDataSub = INVALID_SUB_ID;
                    mWifiAvailable = false;
                    NetworkSpecifier specifier = nc.getNetworkSpecifier();
                    TransportInfo transportInfo = nc.getTransportInfo();
                    if (transportInfo instanceof VcnTransportInfo) {
                        activeDataSub = ((VcnTransportInfo) transportInfo).getSubId();
                    } else if (specifier instanceof TelephonyNetworkSpecifier) {
                        activeDataSub = ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
                    }
                    if (activeDataSub != INVALID_SUB_ID && activeDataSub != mConnectedDataSub) {
                        mConnectedDataSub = activeDataSub;
                        notifyIwlanNetworkStatus();
                    }
                }
            }
        }
    }

    private void checkWifiLinkProtocolType(@NonNull LinkProperties linkProperties) {
        boolean hasIpv4 = false;
        boolean hasIpv6 = false;
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress inetAddress = linkAddress.getAddress();
            if (inetAddress instanceof Inet4Address) {
                hasIpv4 = true;
            } else if (inetAddress instanceof Inet6Address) {
                hasIpv6 = true;
            }
        }
        if (hasIpv4 && hasIpv6) {
            sLinkProtocolType = LinkProtocolType.IPV4V6;
        } else if (hasIpv4) {
            sLinkProtocolType = LinkProtocolType.IPV4;
        } else if (hasIpv6) {
            sLinkProtocolType = LinkProtocolType.IPV6;
        }
    }

    /**
     * This method returns if current country code is outside the home country.
     *
     * @return True if it is international roaming, otherwise false.
     */
    boolean isInternationalRoaming(int slotId) {
        boolean isInternationalRoaming = false;
        String simCountry = mTelephonyManager.createForSubscriptionId(slotId).getSimCountryIso();
        if (!TextUtils.isEmpty(simCountry) && !TextUtils.isEmpty(mLastKnownCountryCode)) {
            Log.d(
                    sLogTag,
                    "SIM country = " + simCountry + ", current country = " + mLastKnownCountryCode);
            isInternationalRoaming = !simCountry.equalsIgnoreCase(mLastKnownCountryCode);
        }
        return isInternationalRoaming;
    }

    /**
     * This method is to add country listener in order to receive country code from the detector.
     */
    private void startCountryDetector() {
        mCountryDetector = mContext.getSystemService(CountryDetector.class);
        if (mCountryDetector != null) {
            mCountryDetector.registerCountryDetectorCallback(
                    new QnsUtils.QnsExecutor(mNetCbHandler), this::updateCountryCode);
        }
    }

    /** This method is to update the last known country code if it is changed. */
    private void updateCountryCode(Country country) {
        if (country == null) {
            return;
        }
        if (country.getSource() == Country.COUNTRY_SOURCE_NETWORK
                || country.getSource() == Country.COUNTRY_SOURCE_LOCATION) {
            String newCountryCode = country.getCountryCode();
            if (!TextUtils.isEmpty(newCountryCode)
                    && (TextUtils.isEmpty(mLastKnownCountryCode)
                            || !mLastKnownCountryCode.equalsIgnoreCase(newCountryCode))) {
                mLastKnownCountryCode = newCountryCode;
                Log.d(sLogTag, "Update the last known country code = " + mLastKnownCountryCode);
            }
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
        pw.println(prefix + "IwlanNetworkStatusTracker:");
        pw.println(
                prefix
                        + "mWifiAvailable="
                        + mWifiAvailable
                        + ", mWifiToggleOn="
                        + mWifiToggleOn
                        + ", mConnectedDataSub="
                        + mConnectedDataSub
                        + ", mIwlanRegistered="
                        + mIwlanRegistered);
        pw.println(prefix + "sLinkProtocolType=" + sLinkProtocolType);
        pw.println(prefix + "mLastKnownCountryCode=" + mLastKnownCountryCode);
    }
}
