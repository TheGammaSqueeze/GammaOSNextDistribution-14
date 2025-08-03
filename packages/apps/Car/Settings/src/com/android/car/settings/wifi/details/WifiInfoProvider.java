/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.wifi.details;

import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.settings.common.Logger;
import com.android.wifitrackerlib.WifiEntry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides Wifi related info.
 */
public class WifiInfoProvider implements DefaultLifecycleObserver, WifiEntry.WifiEntryCallback {
    private static final Logger LOG = new Logger(WifiInfoProvider.class);

    /**
     * Observers of Wifi info changes.
     */
    interface Listener {
        /**
         * Called when WifiEntry is updated.
         */
        void onWifiEntryUpdated();

        /**
         * Called when network is lost.
         */
        void onLost(Network network);

        /**
         * Called when NetworkCapabilities changed.
         */
        void onCapabilitiesChanged(Network network, NetworkCapabilities nc);

        /**
         * Called when LinkProperties changed.
         */
        void onLinkPropertiesChanged(Network network, LinkProperties lp);
    }

    private final WifiEntry mWifiEntry;
    private final Context mContext;
    private final Set<Listener> mListeners = new HashSet<>();
    private final WifiManager mWifiManager;
    private final IntentFilter mFilter;
    private final ConnectivityManager mConnectivityManager;
    private final Handler mHandler;
    @Nullable
    private final Network mNetwork;
    @Nullable
    private LinkProperties mLinkProperties;
    @Nullable
    private NetworkCapabilities mNetworkCapabilities;
    @Nullable
    private WifiConfiguration mWifiConfig;
    @Nullable
    private NetworkInfo mNetworkInfo;
    private WifiInfo mWifiInfo;

    private final NetworkRequest mNetworkRequest = new NetworkRequest.Builder()
            .clearCapabilities().addTransportType(TRANSPORT_WIFI).build();

    // Must be run on the UI thread since it directly manipulates UI state.
    private final NetworkCallback mNetworkCallback = new NetworkCallback() {
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
            if (network.equals(mNetwork) && !lp.equals(mLinkProperties)) {
                mLinkProperties = lp;
                for (Listener listener : getListenersCopy()) {
                    listener.onLinkPropertiesChanged(mNetwork, mLinkProperties);
                }
            }
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            // If the network just validated or lost Internet access, refresh network state.
            if (network.equals(mNetwork) && !nc.equals(mNetworkCapabilities)) {
                mNetworkCapabilities = nc;
                for (Listener listener : getListenersCopy()) {
                    listener.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);
                }
            }
        }

        @Override
        public void onLost(Network network) {
            if (network.equals(mNetwork)) {
                for (Listener listener : getListenersCopy()) {
                    listener.onLost(mNetwork);
                }
            }
        }
    };

    WifiInfoProvider(Context context, WifiEntry wifiEntry) {
        mContext = context;
        mWifiEntry = wifiEntry;
        mWifiEntry.setListener(this);
        mHandler = new Handler(Looper.getMainLooper());
        mConnectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mWifiConfig = mWifiEntry.getWifiConfiguration();
        mNetwork = mWifiManager.getCurrentNetwork();
        mLinkProperties = mConnectivityManager.getLinkProperties(mNetwork);
        mNetworkCapabilities = mConnectivityManager.getNetworkCapabilities(mNetwork);
        updateInfo();
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
    }

    /**
     * Adds a {@link Listener} to be notified of changes to Wi-Fi information.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes a {@link Listener} so that it no longer receives updates.
     */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Removes all {@link Listener} instances added via {@link #addListener(Listener)}.
     */
    public void clearListeners() {
        mListeners.clear();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        LOG.d("onStart");
        mConnectivityManager.registerNetworkCallback(mNetworkRequest, mNetworkCallback,
                mHandler);
        for (Listener listener : getListenersCopy()) {
            listener.onWifiEntryUpdated();
        }
        LOG.d("Done onStart");
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        LOG.d("onStop");
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        LOG.d("done onStop");
    }

    /**
     * Indicates the state of the WifiEntry has changed and clients may retrieve updates through
     * the WifiEntry getter methods.
     */
    @Override
    public void onUpdated() {
        for (Listener listener : getListenersCopy()) {
            listener.onWifiEntryUpdated();
        }
    }

    /**
     * Returns a {@link NetworkInfo} object containing connection status information about the
     * current Wi-Fi network or {@code null} if no network is available.
     */
    @Nullable
    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    /**
     * Returns dynamic information about the current Wi-Fi connection.
     */
    public WifiInfo getWifiInfo() {
        return mWifiInfo;
    }

    /**
     * Returns the {@link Network} object of the current Wi-Fi network or {@code null} if no network
     * is available.
     */
    @Nullable
    public Network getNetwork() {
        return mNetwork;
    }

    /**
     * Returns the {@link NetworkCapabilities} of the current Wi-Fi network or {@code null} if no
     * network is available.
     */
    @Nullable
    public NetworkCapabilities getNetworkCapabilities() {
        return mNetworkCapabilities;
    }

    /**
     * Returns the {@link WifiConfiguration} of the current Wi-Fi entry or {@code null} if
     * unavailable.
     */
    @Nullable
    public WifiConfiguration getNetworkConfiguration() {
        return mWifiConfig;
    }

    /**
     * Returns the {@link LinkProperties} of the current Wi-Fi network or {@link null} if no network
     * is available.
     */
    @Nullable
    public LinkProperties getLinkProperties() {
        return mLinkProperties;
    }

    private Collection<Listener> getListenersCopy() {
        return Collections.unmodifiableCollection(mListeners);
    }

    private void updateInfo() {
        // No need to fetch LinkProperties and NetworkCapabilities, they are updated by the
        // callbacks.
        mNetworkInfo = mConnectivityManager.getNetworkInfo(mNetwork);
        mWifiInfo = mWifiManager.getConnectionInfo();
    }
}
