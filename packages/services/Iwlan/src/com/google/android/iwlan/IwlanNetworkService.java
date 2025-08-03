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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanNetworkService extends NetworkService {
    private static final String TAG = IwlanNetworkService.class.getSimpleName();
    private static Context mContext;
    private IwlanNetworkMonitorCallback mNetworkMonitorCallback;
    private IwlanOnSubscriptionsChangedListener mSubsChangeListener;
    private Handler mIwlanNetworkServiceHandler;
    private HandlerThread mIwlanNetworkServiceHandlerThread;
    private static boolean sNetworkConnected;
    private static final Map<Integer, IwlanNetworkServiceProvider> sIwlanNetworkServiceProviders =
            new ConcurrentHashMap<>();
    private static final int INVALID_SUB_ID = -1;

    // The current subscription with the active internet PDN. Need not be the default data sub.
    // If internet is over WiFi, this value will be INVALID_SUB_ID.
    private static int mConnectedDataSub = INVALID_SUB_ID;

    private static final int EVENT_BASE = IwlanEventListener.NETWORK_SERVICE_INTERNAL_EVENT_BASE;
    private static final int EVENT_NETWORK_REGISTRATION_INFO_REQUEST = EVENT_BASE;
    private static final int EVENT_CREATE_NETWORK_SERVICE_PROVIDER = EVENT_BASE + 1;
    private static final int EVENT_REMOVE_NETWORK_SERVICE_PROVIDER = EVENT_BASE + 2;

    @VisibleForTesting
    enum Transport {
        UNSPECIFIED_NETWORK,
        MOBILE,
        WIFI
    }

    private static Transport sDefaultDataTransport = Transport.UNSPECIFIED_NETWORK;

    // This callback runs in the same thread as IwlanNetworkServiceHandler
    final class IwlanNetworkMonitorCallback extends ConnectivityManager.NetworkCallback {
        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "onAvailable: " + network);
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link
         * ConnectivityManager.NetworkCallback#onAvailable} call with the new replacement network
         * for graceful handover. This method is not guaranteed to be called before {@link
         * ConnectivityManager.NetworkCallback#onLost} is called, for example in case a network is
         * suddenly disconnected.
         */
        @Override
        public void onLosing(Network network, int maxMsToLive) {
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(TAG, "onLost: " + network);
            IwlanNetworkService.setConnectedDataSub(INVALID_SUB_ID);
            IwlanNetworkService.setNetworkConnected(false, Transport.UNSPECIFIED_NETWORK);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + linkProperties);
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                Network network, NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(TAG, "onCapabilitiesChanged: " + network);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    IwlanNetworkService.setConnectedDataSub(
                            getConnectedDataSub(networkCapabilities));
                    IwlanNetworkService.setNetworkConnected(
                            true, IwlanNetworkService.Transport.MOBILE);
                } else if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    IwlanNetworkService.setConnectedDataSub(INVALID_SUB_ID);
                    IwlanNetworkService.setNetworkConnected(
                            true, IwlanNetworkService.Transport.WIFI);
                } else {
                    Log.w(TAG, "Network does not have cellular or wifi capability");
                }
            }
        }
    }

    final class IwlanOnSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {
        /**
         * Callback invoked when there is any change to any SubscriptionInfo. Typically, this method
         * invokes {@link SubscriptionManager#getActiveSubscriptionInfoList}
         */
        @Override
        public void onSubscriptionsChanged() {
            for (IwlanNetworkServiceProvider np : sIwlanNetworkServiceProviders.values()) {
                np.subscriptionChanged();
            }
        }
    }

    @VisibleForTesting
    class IwlanNetworkServiceProvider extends NetworkServiceProvider {
        private final IwlanNetworkService mIwlanNetworkService;
        private final String SUB_TAG;
        private boolean mIsSubActive = false;

        /**
         * Constructor
         *
         * @param slotIndex SIM slot id the data service provider associated with.
         */
        public IwlanNetworkServiceProvider(int slotIndex, IwlanNetworkService iwlanNetworkService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";
            mIwlanNetworkService = iwlanNetworkService;

            // Register IwlanEventListener
            List<Integer> events = new ArrayList<Integer>();
            events.add(IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT);
            IwlanEventListener.getInstance(mContext, slotIndex)
                    .addEventListener(events, getIwlanNetworkServiceHandler());
        }

        @Override
        public void requestNetworkRegistrationInfo(int domain, NetworkServiceCallback callback) {
            getIwlanNetworkServiceHandler()
                    .sendMessage(
                            getIwlanNetworkServiceHandler()
                                    .obtainMessage(
                                            EVENT_NETWORK_REGISTRATION_INFO_REQUEST,
                                            new NetworkRegistrationInfoRequestData(
                                                    domain, callback, this)));
        }

        /**
         * Called when the instance of network service is destroyed (e.g. got unbind or binder died)
         * or when the network service provider is removed. The extended class should implement this
         * method to perform cleanup works.
         */
        @Override
        public void close() {
            mIwlanNetworkService.removeNetworkServiceProvider(this);
            IwlanEventListener.getInstance(mContext, getSlotIndex())
                    .removeEventListener(getIwlanNetworkServiceHandler());
        }

        @VisibleForTesting
        void subscriptionChanged() {
            boolean subActive =
                    getSubscriptionManager()
                                    .getActiveSubscriptionInfoForSimSlotIndex(getSlotIndex())
                            != null;
            if (subActive == mIsSubActive) {
                return;
            }
            mIsSubActive = subActive;
            if (subActive) {
                Log.d(SUB_TAG, "sub changed from not_ready --> ready");
            } else {
                Log.d(SUB_TAG, "sub changed from ready --> not_ready");
            }

            notifyNetworkRegistrationInfoChanged();
        }
    }

    private final class IwlanNetworkServiceHandler extends Handler {
        private final String TAG = IwlanNetworkServiceHandler.class.getSimpleName();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "msg.what = " + eventToString(msg.what));

            IwlanNetworkServiceProvider iwlanNetworkServiceProvider;
            int slotId;

            switch (msg.what) {
                case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT:
                case IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT:
                    iwlanNetworkServiceProvider = getNetworkServiceProvider(msg.arg1);
                    iwlanNetworkServiceProvider.notifyNetworkRegistrationInfoChanged();
                    break;

                case EVENT_NETWORK_REGISTRATION_INFO_REQUEST:
                    NetworkRegistrationInfoRequestData networkRegistrationInfoRequestData =
                            (NetworkRegistrationInfoRequestData) msg.obj;
                    int domain = networkRegistrationInfoRequestData.mDomain;
                    NetworkServiceCallback callback = networkRegistrationInfoRequestData.mCallback;
                    iwlanNetworkServiceProvider =
                            networkRegistrationInfoRequestData.mIwlanNetworkServiceProvider;

                    if (callback == null) {
                        Log.d(TAG, "Error: callback is null. returning");
                        return;
                    }
                    if (domain != NetworkRegistrationInfo.DOMAIN_PS) {
                        callback.onRequestNetworkRegistrationInfoComplete(
                                NetworkServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
                        return;
                    }

                    NetworkRegistrationInfo.Builder nriBuilder =
                            new NetworkRegistrationInfo.Builder();
                    nriBuilder
                            .setAvailableServices(
                                    List.of(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                            .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                            .setEmergencyOnly(!iwlanNetworkServiceProvider.mIsSubActive)
                            .setDomain(NetworkRegistrationInfo.DOMAIN_PS);

                    slotId = iwlanNetworkServiceProvider.getSlotIndex();
                    if (!IwlanNetworkService.isNetworkConnected(
                            isActiveDataOnOtherSub(slotId),
                            IwlanHelper.isCrossSimCallingEnabled(mContext, slotId))) {
                        nriBuilder
                                .setRegistrationState(
                                        NetworkRegistrationInfo
                                                .REGISTRATION_STATE_NOT_REGISTERED_SEARCHING)
                                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UNKNOWN);
                        Log.d(
                                TAG + "[" + slotId + "]",
                                ": reg state" + " REGISTRATION_STATE_NOT_REGISTERED_SEARCHING");
                    } else {
                        nriBuilder
                                .setRegistrationState(
                                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN);
                        Log.d(TAG + "[" + slotId + "]", ": reg state REGISTRATION_STATE_HOME");
                    }

                    callback.onRequestNetworkRegistrationInfoComplete(
                            NetworkServiceCallback.RESULT_SUCCESS, nriBuilder.build());
                    break;

                case EVENT_CREATE_NETWORK_SERVICE_PROVIDER:
                    iwlanNetworkServiceProvider = (IwlanNetworkServiceProvider) msg.obj;

                    if (sIwlanNetworkServiceProviders.isEmpty()) {
                        initCallback();
                    }

                    addIwlanNetworkServiceProvider(iwlanNetworkServiceProvider);
                    break;

                case EVENT_REMOVE_NETWORK_SERVICE_PROVIDER:
                    iwlanNetworkServiceProvider = (IwlanNetworkServiceProvider) msg.obj;
                    slotId = iwlanNetworkServiceProvider.getSlotIndex();
                    IwlanNetworkServiceProvider nsp = sIwlanNetworkServiceProviders.remove(slotId);
                    if (nsp == null) {
                        Log.w(
                                TAG + "[" + slotId + "]",
                                "No NetworkServiceProvider exists for slot!");
                        return;
                    }
                    if (sIwlanNetworkServiceProviders.isEmpty()) {
                        deinitCallback();
                    }
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }

        IwlanNetworkServiceHandler(Looper looper) {
            super(looper);
        }
    }

    private static final class NetworkRegistrationInfoRequestData {
        final int mDomain;
        final NetworkServiceCallback mCallback;
        final IwlanNetworkServiceProvider mIwlanNetworkServiceProvider;

        private NetworkRegistrationInfoRequestData(
                int domain, NetworkServiceCallback callback, IwlanNetworkServiceProvider nsp) {
            mDomain = domain;
            mCallback = callback;
            mIwlanNetworkServiceProvider = nsp;
        }
    }

    /**
     * Create the instance of {@link NetworkServiceProvider}. Network service provider must override
     * this method to facilitate the creation of {@link NetworkServiceProvider} instances. The
     * system will call this method after binding the network service for each active SIM slot id.
     *
     * @param slotIndex SIM slot id the network service associated with.
     * @return Network service object. Null if failed to create the provider (e.g. invalid slot
     *     index)
     */
    @Override
    public NetworkServiceProvider onCreateNetworkServiceProvider(int slotIndex) {
        Log.d(TAG, "onCreateNetworkServiceProvider: slotidx:" + slotIndex);

        // TODO: validity check slot index

        IwlanNetworkServiceProvider np = new IwlanNetworkServiceProvider(slotIndex, this);
        getIwlanNetworkServiceHandler()
                .sendMessage(
                        getIwlanNetworkServiceHandler()
                                .obtainMessage(EVENT_CREATE_NETWORK_SERVICE_PROVIDER, np));
        return np;
    }

    static void setConnectedDataSub(int subId) {
        mConnectedDataSub = subId;
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

    static boolean isActiveDataOnOtherSub(int slotId) {
        int subId = IwlanHelper.getSubId(mContext, slotId);
        return mConnectedDataSub != INVALID_SUB_ID && subId != mConnectedDataSub;
    }

    public static boolean isNetworkConnected(boolean isActiveDataOnOtherSub, boolean isCstEnabled) {
        if (isActiveDataOnOtherSub && isCstEnabled) {
            // For cross-SIM IWLAN (Transport.MOBILE), an active data PDN must be maintained on the
            // other subscription.
            if (sNetworkConnected && (sDefaultDataTransport != Transport.MOBILE)) {
                Log.e(TAG, "Internet is on other slot, but default transport is not MOBILE!");
            }
            return sNetworkConnected;
        } else {
            // For all other cases, only wifi transport can be used.
            return ((sDefaultDataTransport == Transport.WIFI) && sNetworkConnected);
        }
    }

    public static void setNetworkConnected(boolean connected, Transport transport) {
        if (connected == sNetworkConnected && transport == sDefaultDataTransport) {
            return;
        }
        if (connected && (transport == IwlanNetworkService.Transport.UNSPECIFIED_NETWORK)) {
            return;
        }
        sNetworkConnected = connected;
        sDefaultDataTransport = transport;

        for (IwlanNetworkServiceProvider np : sIwlanNetworkServiceProviders.values()) {
            np.notifyNetworkRegistrationInfoChanged();
        }
    }

    void addIwlanNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        int slotIndex = np.getSlotIndex();
        if (sIwlanNetworkServiceProviders.containsKey(slotIndex)) {
            throw new IllegalStateException(
                    "NetworkServiceProvider already exists for slot " + slotIndex);
        }
        sIwlanNetworkServiceProviders.put(slotIndex, np);
    }

    public void removeNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        getIwlanNetworkServiceHandler()
                .sendMessage(
                        getIwlanNetworkServiceHandler()
                                .obtainMessage(EVENT_REMOVE_NETWORK_SERVICE_PROVIDER, np));
    }

    void initCallback() {
        // register for default network callback
        mNetworkMonitorCallback = new IwlanNetworkMonitorCallback();
        getConnectivityManager()
                .registerSystemDefaultNetworkCallback(
                        mNetworkMonitorCallback, getIwlanNetworkServiceHandler());
        Log.d(TAG, "Registered with Connectivity Service");

        /* register with subscription manager */
        mSubsChangeListener = new IwlanOnSubscriptionsChangedListener();
        getSubscriptionManager()
                .addOnSubscriptionsChangedListener(
                        new HandlerExecutor(getIwlanNetworkServiceHandler()), mSubsChangeListener);
        Log.d(TAG, "Registered with Subscription Service");
    }

    void deinitCallback() {
        // deinit network related stuff
        getConnectivityManager().unregisterNetworkCallback(mNetworkMonitorCallback);
        mNetworkMonitorCallback = null;

        // deinit subscription manager related stuff
        getSubscriptionManager().removeOnSubscriptionsChangedListener(mSubsChangeListener);
        mSubsChangeListener = null;
        if (mIwlanNetworkServiceHandlerThread != null) {
            mIwlanNetworkServiceHandlerThread.quit();
            mIwlanNetworkServiceHandlerThread = null;
        }
        mIwlanNetworkServiceHandler = null;
    }

    @VisibleForTesting
    void setAppContext(Context appContext) {
        mContext = appContext;
    }

    @VisibleForTesting
    IwlanNetworkServiceProvider getNetworkServiceProvider(int slotIndex) {
        return sIwlanNetworkServiceProviders.get(slotIndex);
    }

    @VisibleForTesting
    IwlanNetworkMonitorCallback getNetworkMonitorCallback() {
        return mNetworkMonitorCallback;
    }

    @VisibleForTesting
    @NonNull
    Handler getIwlanNetworkServiceHandler() {
        if (mIwlanNetworkServiceHandler == null) {
            mIwlanNetworkServiceHandler = new IwlanNetworkServiceHandler(getLooper());
        }
        return mIwlanNetworkServiceHandler;
    }

    @VisibleForTesting
    Looper getLooper() {
        mIwlanNetworkServiceHandlerThread = new HandlerThread("IwlanNetworkServiceThread");
        mIwlanNetworkServiceHandlerThread.start();
        return mIwlanNetworkServiceHandlerThread.getLooper();
    }

    private static String eventToString(int event) {
        switch (event) {
            case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT:
                return "CROSS_SIM_CALLING_ENABLE_EVENT";
            case IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT:
                return "CROSS_SIM_CALLING_DISABLE_EVENT";
            case EVENT_NETWORK_REGISTRATION_INFO_REQUEST:
                return "EVENT_NETWORK_REGISTRATION_INFO_REQUEST";
            case EVENT_CREATE_NETWORK_SERVICE_PROVIDER:
                return "EVENT_CREATE_NETWORK_SERVICE_PROVIDER";
            case EVENT_REMOVE_NETWORK_SERVICE_PROVIDER:
                return "EVENT_REMOVE_NETWORK_SERVICE_PROVIDER";
            default:
                return "Unknown(" + event + ")";
        }
    }

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "IwlanNetworkService onBind");
        return super.onBind(intent);
    }

    @NonNull
    ConnectivityManager getConnectivityManager() {
        return Objects.requireNonNull(mContext.getSystemService(ConnectivityManager.class));
    }

    @NonNull
    SubscriptionManager getSubscriptionManager() {
        return Objects.requireNonNull(mContext.getSystemService(SubscriptionManager.class));
    }
}
