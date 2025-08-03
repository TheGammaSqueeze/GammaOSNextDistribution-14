/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_ADD_WIFI_CONFIG_FAILURE;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_SUCCESS;

import android.net.wifi.WifiManager;

import java.util.Objects;

public class NetworkUpdateResult {
    private final int mNetId;
    private final boolean mIpChanged;
    private final boolean mProxyChanged;
    private final boolean mCredentialChanged;
    private final boolean mIsNewNetwork;
    private final @WifiManager.AddNetworkResult.AddNetworkStatusCode int mStatusCode;

    public NetworkUpdateResult(int netId) {
        this(netId, netId != INVALID_NETWORK_ID ? STATUS_SUCCESS : STATUS_ADD_WIFI_CONFIG_FAILURE,
                false, false, false, false);
    }

    public NetworkUpdateResult(int netId, int addNetworkFailureCode) {
        this(netId, addNetworkFailureCode, false, false, false, false);
    }

    public NetworkUpdateResult(
            int netId,
            int addNetworkFailureCode,
            boolean ip,
            boolean proxy,
            boolean credential,
            boolean isNewNetwork) {
        mNetId = netId;
        mStatusCode = addNetworkFailureCode;
        mIpChanged = ip;
        mProxyChanged = proxy;
        mCredentialChanged = credential;
        mIsNewNetwork = isNewNetwork;
    }

    /** Make an instance of NetworkUpdateResult whose {@link #isSuccess()} method returns false. */
    public static NetworkUpdateResult makeFailed() {
        return new NetworkUpdateResult(INVALID_NETWORK_ID);
    }

    public int getNetworkId() {
        return mNetId;
    }

    public boolean hasIpChanged() {
        return mIpChanged;
    }

    public boolean hasProxyChanged() {
        return mProxyChanged;
    }

    public boolean hasCredentialChanged() {
        return mCredentialChanged;
    }

    public boolean isNewNetwork() {
        return mIsNewNetwork;
    }

    public boolean isSuccess() {
        return mStatusCode == STATUS_SUCCESS;
    }

    public @WifiManager.AddNetworkResult.AddNetworkStatusCode int getStatusCode() {
        return mStatusCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkUpdateResult that = (NetworkUpdateResult) o;
        return mNetId == that.mNetId
                && mIpChanged == that.mIpChanged
                && mProxyChanged == that.mProxyChanged
                && mCredentialChanged == that.mCredentialChanged
                && mIsNewNetwork == that.mIsNewNetwork;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNetId, mIpChanged, mProxyChanged, mCredentialChanged, mIsNewNetwork);
    }

    @Override
    public String toString() {
        return "NetworkUpdateResult{"
                + "mNetId=" + mNetId
                + ", mIpChanged=" + mIpChanged
                + ", mProxyChanged=" + mProxyChanged
                + ", mCredentialChanged=" + mCredentialChanged
                + ", mIsNewNetwork=" + mIsNewNetwork
                + '}';
    }
}
