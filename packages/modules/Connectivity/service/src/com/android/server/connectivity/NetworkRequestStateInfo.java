/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.connectivity;

import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED;
import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED;
import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_UNKNOWN;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;

import com.android.net.module.util.BitUtils;


class NetworkRequestStateInfo {
    private final NetworkRequest mNetworkRequest;
    private final long mNetworkRequestReceivedTime;

    private enum NetworkRequestState {
        RECEIVED,
        REMOVED
    }
    private NetworkRequestState mNetworkRequestState;
    private int mNetworkRequestDurationMillis;
    private final Dependencies mDependencies;

    NetworkRequestStateInfo(NetworkRequest networkRequest,
            Dependencies deps) {
        mDependencies = deps;
        mNetworkRequest = networkRequest;
        mNetworkRequestReceivedTime = mDependencies.getElapsedRealtime();
        mNetworkRequestDurationMillis = 0;
        mNetworkRequestState = NetworkRequestState.RECEIVED;
    }

    NetworkRequestStateInfo(NetworkRequestStateInfo anotherNetworkRequestStateInfo) {
        mDependencies = anotherNetworkRequestStateInfo.mDependencies;
        mNetworkRequest = new NetworkRequest(anotherNetworkRequestStateInfo.mNetworkRequest);
        mNetworkRequestReceivedTime = anotherNetworkRequestStateInfo.mNetworkRequestReceivedTime;
        mNetworkRequestDurationMillis =
                anotherNetworkRequestStateInfo.mNetworkRequestDurationMillis;
        mNetworkRequestState = anotherNetworkRequestStateInfo.mNetworkRequestState;
    }

    public void setNetworkRequestRemoved() {
        mNetworkRequestState = NetworkRequestState.REMOVED;
        mNetworkRequestDurationMillis = (int) (
                mDependencies.getElapsedRealtime() - mNetworkRequestReceivedTime);
    }

    public int getNetworkRequestStateStatsType() {
        if (mNetworkRequestState == NetworkRequestState.RECEIVED) {
            return NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED;
        } else if (mNetworkRequestState == NetworkRequestState.REMOVED) {
            return NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED;
        } else {
            return NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_UNKNOWN;
        }
    }

    public int getRequestId() {
        return mNetworkRequest.requestId;
    }

    public int getPackageUid() {
        return mNetworkRequest.networkCapabilities.getRequestorUid();
    }

    public int getTransportTypes() {
        return (int) BitUtils.packBits(mNetworkRequest.networkCapabilities.getTransportTypes());
    }

    public boolean getNetCapabilityNotMetered() {
        return mNetworkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    public boolean getNetCapabilityInternet() {
        return mNetworkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public int getNetworkRequestDurationMillis() {
        return mNetworkRequestDurationMillis;
    }

    /** Dependency class */
    public static class Dependencies {
        // Returns a timestamp with the time base of SystemClock.elapsedRealtime to keep durations
        // relative to start time and avoid timezone change, including time spent in deep sleep.
        public long getElapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }
}
