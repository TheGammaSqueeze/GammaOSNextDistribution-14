/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneConstants;
import com.android.mms.service.exception.MmsNetworkException;

/**
 * Manages the MMS network connectivity
 */
public class MmsNetworkManager {
    /** Device Config Keys */
    private static final String MMS_SERVICE_NETWORK_REQUEST_TIMEOUT_MILLIS =
            "mms_service_network_request_timeout_millis";
    private static final String MMS_ENHANCEMENT_ENABLED = "mms_enhancement_enabled";

    // Default timeout used to call ConnectivityManager.requestNetwork if the
    // MMS_SERVICE_NETWORK_REQUEST_TIMEOUT_MILLIS flag is not set.
    // Given that the telephony layer will retry on failures, this timeout should be high enough.
    private static final int DEFAULT_MMS_SERVICE_NETWORK_REQUEST_TIMEOUT_MILLIS = 30 * 60 * 1000;

    // Wait timeout for this class, this is an additional delay after waiting the network request
    // timeout to make sure we don't bail prematurely.
    private static final int ADDITIONAL_NETWORK_ACQUIRE_TIMEOUT_MILLIS = (5 * 1000);

    /* Event created when receiving ACTION_CARRIER_CONFIG_CHANGED */
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 1;
    /** Event when a WLAN network newly available despite of the existing available one. */
    private static final int EVENT_IWLAN_NETWORK_NEWLY_AVAILABLE = 2;

    private final Context mContext;

    // The requested MMS {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // MMS network is available.
    private Network mNetwork;
    /** Whether an Iwlan MMS network is available to use. */
    private boolean mIsLastAvailableNetworkIwlan;
    // The current count of MMS requests that require the MMS network
    // If mMmsRequestCount is 0, we should release the MMS network.
    private int mMmsRequestCount;
    // This is really just for using the capability
    private final NetworkRequest mNetworkRequest;
    // The callback to register when we request MMS network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private volatile ConnectivityManager mConnectivityManager;

    // The MMS HTTP client for this network
    private MmsHttpClient mMmsHttpClient;

    // The handler used for delayed release of the network
    private final Handler mReleaseHandler;

    // The task that does the delayed releasing of the network.
    private final Runnable mNetworkReleaseTask;

    // The SIM ID which we use to connect
    private final int mSubId;

    // The current Phone ID for this MmsNetworkManager
    private int mPhoneId;

    // If ACTION_SIM_CARD_STATE_CHANGED intent receiver is registered
    private boolean mSimCardStateChangedReceiverRegistered;

    private final Dependencies mDeps;

    private int mNetworkReleaseTimeoutMillis;
    private EventHandler mEventHandler;

    private final class EventHandler extends Handler {
        EventHandler() {
            super(Looper.getMainLooper());
        }

        /**
         * Handles events coming from the phone stack. Overridden from handler.
         *
         * @param msg the message to handle
         */
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CARRIER_CONFIG_CHANGED:
                    // Reload mNetworkReleaseTimeoutMillis from CarrierConfigManager.
                    handleCarrierConfigChanged();
                    break;
                case EVENT_IWLAN_NETWORK_NEWLY_AVAILABLE:
                    onIwlanNetworkNewlyAvailable();
                    break;
                default:
                    LogUtil.e("MmsNetworkManager: ignoring message of unexpected type " + msg.what);
            }
        }
    }

    /**
     * This receiver listens to ACTION_SIM_CARD_STATE_CHANGED after starting a new NetworkRequest.
     * If ACTION_SIM_CARD_STATE_CHANGED with SIM_STATE_ABSENT for a SIM card corresponding to the
     * current NetworkRequest is received, it just releases the NetworkRequest without waiting for
     * timeout.
     */
    private final BroadcastReceiver mSimCardStateChangedReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final int simState =
                            intent.getIntExtra(
                                    TelephonyManager.EXTRA_SIM_STATE,
                                    TelephonyManager.SIM_STATE_UNKNOWN);
                    final int phoneId =
                            intent.getIntExtra(
                                    PhoneConstants.PHONE_KEY,
                                    SubscriptionManager.INVALID_PHONE_INDEX);
                    LogUtil.i("MmsNetworkManager: received ACTION_SIM_CARD_STATE_CHANGED"
                            + ", state=" + simStateString(simState) + ", phoneId=" + phoneId);

                    if (mPhoneId == phoneId && simState == TelephonyManager.SIM_STATE_ABSENT) {
                        synchronized (MmsNetworkManager.this) {
                            releaseRequestLocked(mNetworkCallback);
                            MmsNetworkManager.this.notifyAll();
                        }
                    }
                }
            };

    private static String simStateString(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_UNKNOWN:
                return "UNKNOWN";
            case TelephonyManager.SIM_STATE_ABSENT:
                return "ABSENT";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR:
                return "CARD_IO_ERROR";
            case TelephonyManager.SIM_STATE_CARD_RESTRICTED:
                return "CARD_RESTRICTED";
            case TelephonyManager.SIM_STATE_PRESENT:
                return "PRESENT";
            default:
                return "INVALID";
        }
    }

    /**
     * This receiver listens to ACTION_CARRIER_CONFIG_CHANGED. Whenever receiving this event,
     * mNetworkReleaseTimeoutMillis needs to be reloaded from CarrierConfigManager.
     */
    private final BroadcastReceiver mCarrierConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)
                    && mSubId == intent.getIntExtra(
                            CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                            SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)) {
                mEventHandler.sendMessage(mEventHandler.obtainMessage(
                        EVENT_CARRIER_CONFIG_CHANGED));
            }
        }
    };

    /**
     * Called when a WLAN network newly available. This new WLAN network should replace the
     * existing network and retry sending traffic on this network.
     */
    private void onIwlanNetworkNewlyAvailable() {
        if (mMmsHttpClient == null || mNetwork == null) return;
        LogUtil.d("onIwlanNetworkNewlyAvailable net " + mNetwork.getNetId());
        mMmsHttpClient.disconnectAllUrlConnections();
        populateHttpClientWithCurrentNetwork();
    }

    private void handleCarrierConfigChanged() {
        final CarrierConfigManager configManager =
                (CarrierConfigManager)
                        mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        final PersistableBundle config = configManager.getConfigForSubId(mSubId);
        mNetworkReleaseTimeoutMillis =
                config.getInt(CarrierConfigManager.KEY_MMS_NETWORK_RELEASE_TIMEOUT_MILLIS_INT);
        LogUtil.d("MmsNetworkManager: handleCarrierConfigChanged() mNetworkReleaseTimeoutMillis "
                + mNetworkReleaseTimeoutMillis);
    }

    /**
     * Network callback for our network request
     */
    private class NetworkRequestCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onLost(Network network) {
            super.onLost(network);
            LogUtil.w("NetworkCallbackListener.onLost: network=" + network);
            synchronized (MmsNetworkManager.this) {
                // Wait for other available network. Not notify.
                if (network.equals(mNetwork)) {
                    mNetwork = null;
                    mMmsHttpClient = null;
                }
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            LogUtil.w("NetworkCallbackListener.onUnavailable");
            synchronized (MmsNetworkManager.this) {
                releaseRequestLocked(this);
                MmsNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            // onAvailable will always immediately be followed by a onCapabilitiesChanged. Check
            // network status here is enough.
            super.onCapabilitiesChanged(network, nc);
            final NetworkInfo networkInfo = getConnectivityManager().getNetworkInfo(network);
            // wlan network is preferred over wwan network, because its existence meaning it's
            // recommended by QualifiedNetworksService.
            final boolean isWlan = networkInfo != null
                    && networkInfo.getSubtype() == TelephonyManager.NETWORK_TYPE_IWLAN;
            LogUtil.w("NetworkCallbackListener.onCapabilitiesChanged: network="
                    + network + ", isWlan=" + isWlan + ", nc=" + nc);
            synchronized (MmsNetworkManager.this) {
                final boolean isAvailable =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
                if (network.equals(mNetwork) && !isAvailable) {
                    // Current network becomes suspended.
                    mNetwork = null;
                    mMmsHttpClient = null;
                    // Not notify. Either wait for other available network or current network to
                    // become available again.
                    return;
                }

                // Use new available network
                if (isAvailable) {
                    if (mNetwork == null) {
                        mNetwork = network;
                        MmsNetworkManager.this.notifyAll();
                    } else if (mDeps.isMmsEnhancementEnabled()
                            // Iwlan network newly available, try send MMS over the new network.
                            && !mIsLastAvailableNetworkIwlan && isWlan) {
                        mNetwork = network;
                        mEventHandler.sendEmptyMessage(EVENT_IWLAN_NETWORK_NEWLY_AVAILABLE);
                    }
                    mIsLastAvailableNetworkIwlan = isWlan;
                }
            }
        }
    }

    /**
     * Dependencies of MmsNetworkManager, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** Get phone Id from the given subId */
        public int getPhoneId(int subId) {
            return SubscriptionManager.getPhoneId(subId);
        }

        // Timeout used to call ConnectivityManager.requestNetwork. Given that the telephony layer
        // will retry on failures, this timeout should be high enough.
        public int getNetworkRequestTimeoutMillis() {
            return DeviceConfig.getInt(
                    DeviceConfig.NAMESPACE_TELEPHONY, MMS_SERVICE_NETWORK_REQUEST_TIMEOUT_MILLIS,
                    DEFAULT_MMS_SERVICE_NETWORK_REQUEST_TIMEOUT_MILLIS);
        }

        public boolean isMmsEnhancementEnabled() {
            return DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_TELEPHONY, MMS_ENHANCEMENT_ENABLED, true);
        }

        public int getAdditionalNetworkAcquireTimeoutMillis() {
            return ADDITIONAL_NETWORK_ACQUIRE_TIMEOUT_MILLIS;
        }
    }

    @VisibleForTesting
    protected MmsNetworkManager(Context context, int subId, Dependencies dependencies) {
        mContext = context;
        mDeps = dependencies;
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mConnectivityManager = null;
        mMmsHttpClient = null;
        mSubId = subId;
        mReleaseHandler = new Handler(Looper.getMainLooper());
        mNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                        .setSubscriptionId(mSubId).build())
                .build();

        mNetworkReleaseTask = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (mMmsRequestCount < 1) {
                        releaseRequestLocked(mNetworkCallback);
                    }
                }
            }
        };

        mEventHandler = new EventHandler();
        // Register a receiver to listen to ACTION_CARRIER_CONFIG_CHANGED
        mContext.registerReceiver(
                mCarrierConfigChangedReceiver,
                new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        handleCarrierConfigChanged();
    }

    public MmsNetworkManager(Context context, int subId) {
        this(context, subId, new Dependencies());
    }

    /**
     * Acquire the MMS network
     *
     * @param requestId request ID for logging
     * @throws com.android.mms.service.exception.MmsNetworkException if we fail to acquire it
     */
    public void acquireNetwork(final String requestId) throws MmsNetworkException {
        int networkRequestTimeoutMillis = mDeps.getNetworkRequestTimeoutMillis();

        synchronized (this) {
            // Since we are acquiring the network, remove the network release task if exists.
            mReleaseHandler.removeCallbacks(mNetworkReleaseTask);
            mMmsRequestCount += 1;
            if (mNetwork != null) {
                // Already available
                LogUtil.d(requestId, "MmsNetworkManager: already available");
                return;
            }

            if (!mSimCardStateChangedReceiverRegistered) {
                mPhoneId = mDeps.getPhoneId(mSubId);
                if (mPhoneId == SubscriptionManager.INVALID_PHONE_INDEX
                        || mPhoneId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
                    throw new MmsNetworkException("Invalid Phone Id: " + mPhoneId);
                }

                // Register a receiver to listen to ACTION_SIM_CARD_STATE_CHANGED
                mContext.registerReceiver(
                        mSimCardStateChangedReceiver,
                        new IntentFilter(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED));
                mSimCardStateChangedReceiverRegistered = true;
            }

            // Not available, so start a new request if not done yet
            if (mNetworkCallback == null) {
                LogUtil.d(requestId, "MmsNetworkManager: start new network request");
                startNewNetworkRequestLocked(networkRequestTimeoutMillis);
            }

            try {
                this.wait(networkRequestTimeoutMillis
                        + mDeps.getAdditionalNetworkAcquireTimeoutMillis());
            } catch (InterruptedException e) {
                LogUtil.w(requestId, "MmsNetworkManager: acquire network wait interrupted");
            }

            if (mSimCardStateChangedReceiverRegistered) {
                // Unregister the receiver.
                mContext.unregisterReceiver(mSimCardStateChangedReceiver);
                mSimCardStateChangedReceiverRegistered = false;
            }

            if (mNetwork != null) {
                // Success
                return;
            }

            if (mNetworkCallback != null) { // Timed out
                LogUtil.e(requestId,
                        "MmsNetworkManager: timed out with networkRequestTimeoutMillis="
                                + networkRequestTimeoutMillis
                                + " and ADDITIONAL_NETWORK_ACQUIRE_TIMEOUT_MILLIS="
                                + mDeps.getAdditionalNetworkAcquireTimeoutMillis());
                // Release the network request and wake up all the MmsRequests for fast-fail
                // together.
                // TODO: Start new network request for remaining MmsRequests?
                releaseRequestLocked(mNetworkCallback);
                this.notifyAll();
            }

            throw new MmsNetworkException("Acquiring network failed");
        }
    }

    /**
     * Release the MMS network when nobody is holding on to it.
     *
     * @param requestId          request ID for logging.
     * @param canRelease         whether the request can be released. An early release of a request
     *                           can result in unexpected network torn down, as that network is used
     *                           for immediate retry.
     * @param shouldDelayRelease whether the release should be delayed for a carrier-configured
     *                           timeout (default 5 seconds), the regular use case is to delay this
     *                           for DownloadRequests to use the network for sending an
     *                           acknowledgement on the same network.
     */
    public void releaseNetwork(final String requestId, final boolean canRelease,
            final boolean shouldDelayRelease) {
        synchronized (this) {
            if (mMmsRequestCount > 0) {
                mMmsRequestCount -= 1;
                LogUtil.d(requestId, "MmsNetworkManager: release, count=" + mMmsRequestCount
                        + " canRelease=" + canRelease);
                if (mMmsRequestCount < 1 && canRelease) {
                    if (shouldDelayRelease) {
                        // remove previously posted task and post a delayed task on the release
                        // handler to release the network
                        mReleaseHandler.removeCallbacks(mNetworkReleaseTask);
                        mReleaseHandler.postDelayed(mNetworkReleaseTask,
                                mNetworkReleaseTimeoutMillis);
                    } else {
                        releaseRequestLocked(mNetworkCallback);
                    }
                }
            }
        }
    }

    /**
     * Start a new {@link android.net.NetworkRequest} for MMS
     */
    private void startNewNetworkRequestLocked(int networkRequestTimeoutMillis) {
        final ConnectivityManager connectivityManager = getConnectivityManager();
        mNetworkCallback = new NetworkRequestCallback();
        connectivityManager.requestNetwork(
                mNetworkRequest, mNetworkCallback, networkRequestTimeoutMillis);
    }

    /**
     * Release the current {@link android.net.NetworkRequest} for MMS
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequestLocked(ConnectivityManager.NetworkCallback callback) {
        if (callback != null) {
            final ConnectivityManager connectivityManager = getConnectivityManager();
            try {
                connectivityManager.unregisterNetworkCallback(callback);
            } catch (IllegalArgumentException e) {
                // It is possible ConnectivityManager.requestNetwork may fail silently due
                // to RemoteException. When that happens, we may get an invalid
                // NetworkCallback, which causes an IllegalArgumentexception when we try to
                // unregisterNetworkCallback. This exception in turn causes
                // MmsNetworkManager to skip resetLocked() in the below. Thus MMS service
                // would get stuck in the bad state until the device restarts. This fix
                // catches the exception so that state clean up can be executed.
                LogUtil.w("Unregister network callback exception", e);
            }
        }
        resetLocked();
    }

    /**
     * Reset the state
     */
    private void resetLocked() {
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mMmsHttpClient = null;
    }

    private @NonNull ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    /**
     * Get an MmsHttpClient for the current network
     *
     * @return The MmsHttpClient instance
     */
    public MmsHttpClient getOrCreateHttpClient() {
        synchronized (this) {
            if (mMmsHttpClient == null) {
                populateHttpClientWithCurrentNetwork();
            }
            return mMmsHttpClient;
        }
    }

    // Create new MmsHttpClient for the current Network
    private void populateHttpClientWithCurrentNetwork() {
        if (mNetwork != null) {
            mMmsHttpClient = new MmsHttpClient(mContext, mNetwork, mConnectivityManager);
        }
    }

    /**
     * Get the APN name for the active network
     *
     * @return The APN name if available, otherwise null
     */
    public String getApnName() {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                return null;
            }
            network = mNetwork;
        }
        String apnName = null;
        final ConnectivityManager connectivityManager = getConnectivityManager();
        final NetworkInfo mmsNetworkInfo = connectivityManager.getNetworkInfo(network);
        if (mmsNetworkInfo != null) {
            apnName = mmsNetworkInfo.getExtraInfo();
        }
        return apnName;
    }

    @VisibleForTesting
    protected int getNetworkReleaseTimeoutMillis() {
        return mNetworkReleaseTimeoutMillis;
    }
}
