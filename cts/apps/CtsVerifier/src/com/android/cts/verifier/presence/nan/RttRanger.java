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

package com.android.cts.verifier.presence.nan;

import android.content.Context;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;

/** Ranges to a given WiFi Aware Peer handle. */
public class RttRanger {
    private static final String TAG = RttRanger.class.getName();

    private final WifiRttManager wifiRttManager;
    private final Executor executor;

    private NanResultListener nanResultListener;
    private PeerHandle lastPeerHandle;

    public RttRanger(Context context, Executor executor) {
        this.executor = executor;
        this.wifiRttManager = context.getSystemService(WifiRttManager.class);
    }

    public void startRanging(PeerHandle peerHandle, NanResultListener nanResultListener) {
        if (!wifiRttManager.isAvailable()) {
            Log.w(TAG, "WifiRttManager is not available");
            return;
        }

        this.lastPeerHandle = peerHandle;
        this.nanResultListener = nanResultListener;
        wifiRttManager.startRanging(
                new RangingRequest.Builder().addWifiAwarePeer(peerHandle).build(),
                executor,
                createRangingResultCallback());
    }

    private RangingResultCallback createRangingResultCallback() {
        return new RangingResultCallback() {
            @Override
            public void onRangingFailure(int code) {
                Log.w(TAG, "RTT NAN ranging failed: " + code);
            }

            @Override
            public void onRangingResults(List<RangingResult> results) {
                Log.i(TAG, "RTT NAN ranging results: " + results);

                if (results.isEmpty()) {
                    Log.i(TAG, "Range results are empty");
                    return;
                }

                if (results.get(0).getStatus() == RangingResult.STATUS_FAIL) {
                    Log.w(TAG, "Failed to range");
                } else {
                    if (nanResultListener != null) {
                        RangingResult result = results.get(0);
                        Log.i(TAG,
                                "peerHandle=" + result.getPeerHandle() + ", distMm="
                                        + result.getDistanceMm());
                        nanResultListener.onNanResult(result);
                    }
                }

                startRanging(lastPeerHandle, nanResultListener);
            }
        };
    }

    /** Listener for range results. */
    public interface NanResultListener {
        void onNanResult(RangingResult result);
    }
}
