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

package com.android.server.wifi;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Set;

/**
 * Network factory to handle untrusted wifi network requests.
 */
public class UntrustedWifiNetworkFactory extends NetworkFactory {
    private static final String TAG = "UntrustedWifiNetworkFactory";
    private static final int SCORE_FILTER = Integer.MAX_VALUE;

    private final WifiConnectivityManager mWifiConnectivityManager;
    private int mConnectionReqCount = 0;
    private final NetworkCapabilities mCapabilitiesFilter;

    public UntrustedWifiNetworkFactory(Looper l, Context c, NetworkCapabilities f,
                                       WifiConnectivityManager connectivityManager) {
        super(l, c, TAG, f);
        mWifiConnectivityManager = connectivityManager;
        mCapabilitiesFilter = f;
        setScoreFilter(SCORE_FILTER);
    }

    // package-private
    @TargetApi(Build.VERSION_CODES.S)
    void updateSubIdsInCapabilitiesFilter(Set<Integer> subIds) {
        if (SdkLevel.isAtLeastS()) {
            NetworkCapabilities newFilter =
                    new NetworkCapabilities.Builder(mCapabilitiesFilter)
                            .setSubscriptionIds(subIds).build();
            setCapabilityFilter(newFilter);
        }
    }

    @Override
    protected void needNetworkFor(NetworkRequest networkRequest) {
        if (!networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)) {
            if (++mConnectionReqCount == 1) {
                mWifiConnectivityManager.setUntrustedConnectionAllowed(true);
            }
        }
    }

    @Override
    protected void releaseNetworkFor(NetworkRequest networkRequest) {
        if (!networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)) {
            if (mConnectionReqCount == 0) {
                Log.e(TAG, "No valid network request to release");
                return;
            }
            if (--mConnectionReqCount == 0) {
                mWifiConnectivityManager.setUntrustedConnectionAllowed(false);
            }
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println(TAG + ": mConnectionReqCount " + mConnectionReqCount);
    }

    /**
     * Check if there is at-least one connection request.
     */
    public boolean hasConnectionRequests() {
        return mConnectionReqCount > 0;
    }
}

