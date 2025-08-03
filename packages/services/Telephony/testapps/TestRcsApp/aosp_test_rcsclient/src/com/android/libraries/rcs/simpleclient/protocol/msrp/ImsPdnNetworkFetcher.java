/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.RequiresPermission;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.stream.Collectors;

/** A really incomplete implementation for fetching networks from ConnectivityManager. */
public final class ImsPdnNetworkFetcher {

    private final Context context;

    public ImsPdnNetworkFetcher(Context context) {
        this.context = context;
    }

    private static NetworkRequest createNetworkRequest() {
        NetworkRequest.Builder builder =
                new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        return builder.build();
    }

    ListenableFuture<Network> getImsPdnNetwork() {
        return requestNetwork();
    }

    ListenableFuture<List<String>> getImsPdnIpAddresses() {
        return FluentFuture.from(getImsPdnNetwork())
                .transform(this::getNetworkIpAddresses, MoreExecutors.directExecutor());
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    List<String> getNetworkIpAddresses(Network network) {
        return getConnectivityManager().getLinkProperties(network).getLinkAddresses().stream()
                .map(link -> link.getAddress().getHostAddress())
                .collect(Collectors.toList());
    }

    private ListenableFuture<Network> requestNetwork() {
        SettableFuture<Network> result = SettableFuture.create();
        ConnectivityManager cm = getConnectivityManager();

        ConnectivityManager.NetworkCallback cb =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        if (!result.isDone() && !result.isCancelled()) {
                            result.set(network);
                        }
                        cm.unregisterNetworkCallback(this);
                    }
                };

        cm.requestNetwork(createNetworkRequest(), cb);
        return result;
    }

    ConnectivityManager getConnectivityManager() {
        return context.getSystemService(ConnectivityManager.class);
    }
}
