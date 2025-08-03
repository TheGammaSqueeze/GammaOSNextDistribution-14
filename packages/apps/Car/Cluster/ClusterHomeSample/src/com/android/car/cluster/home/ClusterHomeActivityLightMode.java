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

package com.android.car.cluster.home;

import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.os.Bundle;
import android.util.Log;

public class ClusterHomeActivityLightMode extends ClusterHomeActivity {

    private static final String TAG = ClusterHomeActivityLightMode.class.getSimpleName();
    private static final long HEARTBEAT_INTERVAL_MS = 1000; // 1 second interval.

    private ClusterHomeManager mClusterHomeManager;

    private final Runnable mSendHeartbeatsRunnable = () -> sendHeartbeats();

    /**
     * Returns true if the activity is designed to run in the LIGHT mode.
     *
     * <p>This activity is used for LIGHT mode only, thus always return {@code true}.
     *    Use {@link ClusterHomeActivity} for the FULL mode.
     */
    @Override
    public boolean isClusterInLightMode() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Car car = Car.createCar(getApplicationContext());
        mClusterHomeManager = (ClusterHomeManager) car.getCarManager(ClusterHomeManager.class);
    }

    @Override
    public void onStart() {
        super.onStart();

        mClusterHomeManager.startVisibilityMonitoring(this);
        Log.i(TAG, "Visibility monitoring started");

        // Clean up the handler queue.
        getMainThreadHandler().removeCallbacks(mSendHeartbeatsRunnable);
        // Start sending the heartbeats.
        sendHeartbeats();
    }

    @Override
    public void onStop() {
        getMainThreadHandler().removeCallbacks(mSendHeartbeatsRunnable);
        super.onStop();
    }

    private void sendHeartbeats() {
        mClusterHomeManager.sendHeartbeat(System.nanoTime(), /* appMetadata= */ null);

        getMainThreadHandler().postDelayed(mSendHeartbeatsRunnable, HEARTBEAT_INTERVAL_MS);
    }
}
