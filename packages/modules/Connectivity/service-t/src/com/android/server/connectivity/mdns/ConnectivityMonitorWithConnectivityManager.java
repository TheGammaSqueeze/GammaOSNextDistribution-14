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

package com.android.server.connectivity.mdns;

import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.android.net.module.util.SharedLog;

/** Class for monitoring connectivity changes using {@link ConnectivityManager}. */
public class ConnectivityMonitorWithConnectivityManager implements ConnectivityMonitor {
    private static final String TAG = "ConnMntrWConnMgr";
    private final SharedLog sharedLog;
    private final Listener listener;
    private final ConnectivityManager.NetworkCallback networkCallback;
    private final ConnectivityManager connectivityManager;
    // TODO(b/71901993): Ideally we shouldn't need this flag. However we still don't have clues why
    // the receiver is unregistered twice yet.
    private boolean isCallbackRegistered = false;
    private Network lastAvailableNetwork = null;

    @SuppressWarnings({"nullness:assignment", "nullness:method.invocation"})
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ConnectivityMonitorWithConnectivityManager(Context context, Listener listener,
            SharedLog sharedLog) {
        this.listener = listener;
        this.sharedLog = sharedLog;

        connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        sharedLog.log("network available.");
                        lastAvailableNetwork = network;
                        notifyConnectivityChange();
                    }

                    @Override
                    public void onLost(Network network) {
                        sharedLog.log("network lost.");
                        notifyConnectivityChange();
                    }

                    @Override
                    public void onUnavailable() {
                        sharedLog.log("network unavailable.");
                        notifyConnectivityChange();
                    }
                };
    }

    @Override
    public void notifyConnectivityChange() {
        listener.onConnectivityChanged();
    }

    /**
     * Starts monitoring changes of connectivity of this device, which may indicate that the list of
     * network interfaces available for multi-cast messaging has changed.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void startWatchingConnectivityChanges() {
        sharedLog.log("Start watching connectivity changes");
        if (isCallbackRegistered) {
            return;
        }

        connectivityManager.registerNetworkCallback(
                new NetworkRequest.Builder().addTransportType(
                        NetworkCapabilities.TRANSPORT_WIFI).build(),
                networkCallback);
        isCallbackRegistered = true;
    }

    /** Stops monitoring changes of connectivity. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void stopWatchingConnectivityChanges() {
        sharedLog.log("Stop watching connectivity changes");
        if (!isCallbackRegistered) {
            return;
        }

        connectivityManager.unregisterNetworkCallback(networkCallback);
        isCallbackRegistered = false;
    }

    @Override
    @Nullable
    public Network getAvailableNetwork() {
        return lastAvailableNetwork;
    }
}