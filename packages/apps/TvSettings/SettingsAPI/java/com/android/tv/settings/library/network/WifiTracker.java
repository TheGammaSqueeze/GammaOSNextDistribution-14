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

package com.android.tv.settings.library.network;

import android.annotation.MainThread;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SimpleClock;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.tv.settings.library.util.ThreadUtils;
import com.android.wifitrackerlib.WifiEntry;
import com.android.wifitrackerlib.WifiPickerTracker;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks saved or available wifi networks and their state.
 *
 * WifiTracker/AccessPoint is no longer supported, and will be removed in a future
 * release. Clients that need a dynamic list of available wifi networks should migrate to one of the
 * newer tracker classes,
 * {@link com.android.wifitrackerlib.WifiPickerTracker},
 * {@link com.android.wifitrackerlib.SavedNetworkTracker},
 * {@link com.android.wifitrackerlib.NetworkDetailsTracker},
 * in conjunction with {@link com.android.wifitrackerlib.WifiEntry} to represent each wifi network.
 * TODO: Migrate this local copy to use WifiPickerTracker internally.
 */
public class WifiTracker implements DefaultLifecycleObserver {
    /**
     * Default maximum age in millis of cached scored networks in
     * {@link .AccessPoint#mScoredNetworkCache} to be used for speed label generation.
     */

    /** Maximum age of scan results to hold onto while actively scanning. **/
    @VisibleForTesting
    static final long MAX_SCAN_RESULT_AGE_MILLIS = 15000;

    private static final String TAG = "WifiTracker";

    private static final Clock ELAPSED_REALTIME_CLOCK = new SimpleClock(ZoneOffset.UTC) {
        @Override
        public long millis() {
            return SystemClock.elapsedRealtime();
        }
    };

    // TODO: Allow control of this?
    // Combo scans can take 5-6s to complete - set to 10s.
    private static final int WIFI_RESCAN_INTERVAL_MS = 10 * 1000;

    private final WifiTracker.WifiListener mListener;

    private final WifiPickerTracker mWifiPickerTracker;

    @VisibleForTesting
    Handler mWorkHandler;
    private final HandlerThread mWorkThread;

    private final LifecycleOwner mFallbackLifecycleOwner = new LifecycleOwner() {
        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return mFallbackLifecycle;
        }
    };

    private final WifiPickerTracker.WifiPickerTrackerCallback mPickerTrackerCallback =
            new WifiPickerTracker.WifiPickerTrackerCallback() {
        @Override
        public void onWifiEntriesChanged() {
            if (mListener != null) {
                mListener.onAccessPointsChanged();
            }
        }

        @Override
        public void onNumSavedNetworksChanged() {}

        @Override
        public void onNumSavedSubscriptionsChanged() {}

        @Override
        public void onWifiStateChanged() {
            mListener.onWifiStateChanged(mWifiPickerTracker.getWifiState());
        }
    };

    private final LifecycleRegistry mFallbackLifecycle = new LifecycleRegistry(
            mFallbackLifecycleOwner);

    private final LifecycleObserver lifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
           mWorkThread.quitSafely();
        }
    };

    public WifiTracker(Context context, WifiTracker.WifiListener wifiListener,
            Lifecycle lifecycle,
            WifiManager wifiManager, ConnectivityManager connectivityManager) {
        mListener = wifiListener;

        mWorkThread = new HandlerThread(TAG
                + "{" + Integer.toHexString(System.identityHashCode(this)) + "}",
                Process.THREAD_PRIORITY_BACKGROUND);
        mWorkThread.start();
        mWorkHandler = mWorkThread.getThreadHandler();

        if (lifecycle == null) {
            lifecycle = mFallbackLifecycle;
        }

        mWifiPickerTracker = new WifiPickerTracker(
                lifecycle,
                context, wifiManager, connectivityManager, ThreadUtils.getUiThreadHandler(),
                mWorkHandler, ELAPSED_REALTIME_CLOCK, MAX_SCAN_RESULT_AGE_MILLIS,
                WIFI_RESCAN_INTERVAL_MS, mPickerTrackerCallback);
        lifecycle.addObserver(lifecycleObserver);
    }

    @MainThread
    public void onDestroy() {
        mFallbackLifecycle.setCurrentState(Lifecycle.State.DESTROYED);
    }

    @MainThread
    public void onStart() {
        mFallbackLifecycle.setCurrentState(Lifecycle.State.STARTED);
    }

    @MainThread
    public void onStop() {
        mFallbackLifecycle.setCurrentState(Lifecycle.State.CREATED);
    }

    /**
     * Gets the current list of access points.
     */
    public List<AccessPoint> getAccessPoints() {
        List<AccessPoint> result = new ArrayList<>();
        WifiEntry connectedEntry = mWifiPickerTracker.getConnectedWifiEntry();
        if (connectedEntry != null) {
            result.add(new AccessPoint(connectedEntry));
        }

        List<WifiEntry> entries = mWifiPickerTracker.getWifiEntries();
        for (WifiEntry entry : entries) {
            result.add(new AccessPoint(entry));
        }

        return result;
    }

    /**
     * WifiListener interface that defines callbacks indicating state changes in WifiTracker.
     *
     * <p>All callbacks are invoked on the MainThread.
     */
    public interface WifiListener {
        /**
         * Called when the state of Wifi has changed, the state will be one of
         * the following.
         *
         * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
         * <p>
         *
         * @param state The new state of wifi.
         */
        void onWifiStateChanged(int state);

        /**
         * Called when the connection state of wifi has changed.
         */
        void onConnectedChanged();

        /**
         * Called to indicate the list of AccessPoints has been updated and
         * {@link WifiTracker#getAccessPoints()} should be called to get the updated list.
         */
        void onAccessPointsChanged();
    }
}
