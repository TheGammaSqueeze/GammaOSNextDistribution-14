/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.networking.railway;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class CurrentEthernetNetworksViewModel extends AndroidViewModel {
    private static final String TAG = CurrentEthernetNetworksViewModel.class.getName();
    private final MutableLiveData<List<Network>> mNetworks =
            new MutableLiveData<>(new ArrayList<>());
    private final Application mApplication;
    private final ConnectivityManager mConnectivityManager;
    private final NetworkCallback mNetworkCallback = new CurrentEthernetNetworksCallback();
    private final Handler mHandler;

    private void runOnUiThread(Runnable r) {
        mHandler.post(r);
    }

    public LiveData<List<Network>> getNetworksLiveData() {
        return mNetworks;
    }

    public CurrentEthernetNetworksViewModel(Application application) {
        super(application);

        mApplication = application;
        mHandler = new Handler(Looper.getMainLooper());

        mConnectivityManager  =
                mApplication.getSystemService(ConnectivityManager.class);

        assert mConnectivityManager != null;
        NetworkRequest request = new NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(
                        NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
    }

    private void onNetworkChanged() {
        mNetworks.setValue(mNetworks.getValue());
        // setValue triggers an update on the observer that in turn updates the text view
    }

    private void addNetwork(Network network) {
        List<Network> networks = mNetworks.getValue();

        assert networks != null;
        networks.add(network);
        mNetworks.setValue(networks);
    }

    private void removeNetwork(Network network) {
        List<Network> networks = mNetworks.getValue();

        assert networks != null;
        networks.remove(network);
        mNetworks.setValue(networks);
    }

    public String getCurrentEthernetNetworksText(List<Network> networks) {
        StringBuilder sb = new StringBuilder();
        for (Network network : networks) {
            LinkProperties linkProperties = mConnectivityManager.getLinkProperties(network);
            NetworkCapabilities networkCapabilities =
                    mConnectivityManager.getNetworkCapabilities(network);

            if (linkProperties != null) {
                sb.append("- ");
                sb.append(linkProperties.getInterfaceName());

                sb.append("\n\t");
                sb.append("ip addresses: ");
                for (LinkAddress address : linkProperties.getLinkAddresses()) {
                    sb.append("\n\t\t").append(address.getAddress().getHostAddress());
                }
            }

            if (networkCapabilities != null) {
                sb.append("\n\t");
                sb.append("capabilities: ");
                sb.append(Arrays.toString(networkCapabilities.getCapabilities()));

                Set<Integer> allowedUids = networkCapabilities.getAllowedUids();
                if (allowedUids.size() > 0) {
                    sb.append("\n\t");
                    sb.append("allowed apps: ");
                    sb.append(
                            UidToPackageNameConverter.convertToPackageNames(mApplication,
                                    allowedUids));
                }
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private class CurrentEthernetNetworksCallback extends NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.d(TAG, "Network " + network + " available");

            runOnUiThread(() -> addNetwork(network));
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.d(TAG, "Network " + network + " lost");

            runOnUiThread(() -> removeNetwork(network));
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            Log.d(TAG, "Network " + network + " capabilities changed to "
                    + Arrays.toString(networkCapabilities.getCapabilities()));

            runOnUiThread(() -> onNetworkChanged());
        }

        @Override
        public void onLinkPropertiesChanged(Network network,
                LinkProperties linkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties);
            Log.d(TAG, "Network " + network + " link properties changed");

            runOnUiThread(() -> onNetworkChanged());
        }
    }
}
