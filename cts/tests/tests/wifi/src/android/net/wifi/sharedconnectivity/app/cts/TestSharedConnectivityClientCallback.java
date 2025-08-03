/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.sharedconnectivity.app.cts;

import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.app.SharedConnectivitySettingsState;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TestSharedConnectivityClientCallback implements SharedConnectivityClientCallback {
    private static final String TAG = "SharedConnectivityTestingCallback";

    private final CountDownLatch mServiceConnectedLatch = new CountDownLatch(1);
    private List<HotspotNetwork> mHotspotNetworksList = new ArrayList<>();
    private List<KnownNetwork> mKnownNetworksList = new ArrayList<>();
    private SharedConnectivitySettingsState mSharedConnectivitySettingsState;
    private HotspotNetworkConnectionStatus mHotspotNetworkConnectionStatus;
    private KnownNetworkConnectionStatus mKnownNetworkConnectionStatus;

    @Override
    public void onHotspotNetworksUpdated(List<HotspotNetwork> networks) {
        Log.i(TAG, "onHotspotNetworksUpdated");
        mHotspotNetworksList = networks;
    }

    @Override
    public void onKnownNetworksUpdated(List<KnownNetwork> networks) {
        Log.i(TAG, "onKnownNetworksUpdated");
        mKnownNetworksList = networks;
    }

    @Override
    public void onSharedConnectivitySettingsChanged(
            SharedConnectivitySettingsState state) {
        Log.i(TAG, "onSharedConnectivitySettingsChanged");
        mSharedConnectivitySettingsState = state;
    }

    @Override
    public void onHotspotNetworkConnectionStatusChanged(
            HotspotNetworkConnectionStatus status) {
        Log.i(TAG, "onHotspotNetworkConnectionStatusChanged");
        mHotspotNetworkConnectionStatus = status;
    }

    @Override
    public void onKnownNetworkConnectionStatusChanged(
            KnownNetworkConnectionStatus status) {
        Log.i(TAG, "onKnownNetworkConnectionStatusChanged");
        mKnownNetworkConnectionStatus = status;
    }

    @Override
    public void onServiceConnected() {
        Log.i(TAG, "onServiceConnected");
        mServiceConnectedLatch.countDown();
    }

    @Override
    public void onServiceDisconnected() {
        Log.i(TAG, "onServiceDisconnected");
    }

    @Override
    public void onRegisterCallbackFailed(Exception exception) {
        Log.i(TAG, "onRegisterCallbackFailed");
    }

    public CountDownLatch getServiceConnectedLatch() {
        return mServiceConnectedLatch;
    }

    public List<HotspotNetwork> getHotspotNetworksList() {
        return mHotspotNetworksList;
    }

    public List<KnownNetwork> getKnownNetworksList() {
        return mKnownNetworksList;
    }

    public SharedConnectivitySettingsState getSharedConnectivitySettingsState() {
        return mSharedConnectivitySettingsState;
    }

    public HotspotNetworkConnectionStatus getHotspotNetworkConnectionStatus() {
        return mHotspotNetworkConnectionStatus;
    }

    public KnownNetworkConnectionStatus getKnownNetworkConnectionStatus() {
        return mKnownNetworkConnectionStatus;
    }
}
