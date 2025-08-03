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
package com.google.android.car.networking.preferenceupdater.components;

import static android.provider.Settings.Global.NETSTATS_UID_BUCKET_DURATION;

import static java.util.concurrent.TimeUnit.HOURS;

import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import com.google.android.car.networking.preferenceupdater.fragments.ManagerFragment;

import java.util.Timer;
import java.util.TimerTask;

/**
 * This component provides information about traffic that goes through OEM owned and regular network
 */
public final class MetricDisplay {
    private static final String TAG = MetricDisplay.class.getSimpleName();

    // How often we want to update Metric Display values
    private static final int OEM_METRIC_UPDATE_INTERVAL_MILLIS = 1000;

    // Network match rules array
    private static final int[] NETWORK_MATCHING_RULES =
            new int[] {
                NetworkTemplate.MATCH_WIFI,
                NetworkTemplate.MATCH_MOBILE,
                NetworkTemplate.MATCH_ETHERNET,
                NetworkTemplate.MATCH_BLUETOOTH,
            };

    private ConnectivityManager mConnectivityManager;
    private NetworkStatsManager mNetStatsMan;

    private ManagerFragment mFragment;

    private Context mContext;
    @Nullable private Timer mTimer;
    private long mStartTime;

    private final Handler mMetricMessageHandler;

    public MetricDisplay(Context context, Handler handler) {
        mContext = context;
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mNetStatsMan = context.getSystemService(NetworkStatsManager.class);
        mMetricMessageHandler = handler;

        /* If the start time is set to the current time without accounting for bucket duration,
           the reported rx/tx bytes will be inaccurate, since bucket sizes are on the order of
           hours. This will manifest itself as the rx/tx values slowly climbing, even when there is
           no network traffic. Setting the start time to one bucket duration prior to the current
           time seems to fix this issue. */
        mStartTime = System.currentTimeMillis() - Settings.Global.getLong(
                mContext.getContentResolver(), NETSTATS_UID_BUCKET_DURATION, HOURS.toMillis(2));

        scanAndPrintOemNetworksIfExist();
    }

    public void setMainActivity(ManagerFragment fragment) {
        mFragment = fragment;
    }

    public void scanAndPrintOemNetworksIfExist() {
        for (Network net : mConnectivityManager.getAllNetworks()) {
            NetworkCapabilities netCapabilities = mConnectivityManager.getNetworkCapabilities(net);
            if (netCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)) {
                Log.i(TAG, "Found OEM Paid network!");
            } else if (netCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)) {
                Log.i(TAG, "Found OEM Private network!");
            }
        }
    }

    public void startWatching() {
        // Let's create a timer instance first
        mTimer = new Timer();
        // Let's schedule a timer that will update OEM Usage metric once every
        // OEM_METRIC_UPDATE_INTERVAL_MILLIS
        mTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        updateStats();
                    }
                },
                /*delay=*/ 0,
                OEM_METRIC_UPDATE_INTERVAL_MILLIS);
    }

    public void stopWatching() {
        // Do nothing if mTimer is null
        if (mTimer == null) {
            return;
        }
        // Cancel all tasks, it should be only one task.
        // Based on docs: "Once a timer has been terminated, its execution thread terminates
        // gracefully, and no more tasks may be scheduled on it."
        mTimer.cancel();
    }

    private Pair<Long, Long> calculateTraffic(NetworkStats stats) {
        if (!stats.hasNextBucket()) {
            return null;
        }
        NetworkStats.Bucket displayBucket = new NetworkStats.Bucket();
        long rx = 0;
        long tx = 0;
        while (stats.hasNextBucket()) {
            if (!stats.getNextBucket(displayBucket)) {
                return null;
            }
            rx += displayBucket.getRxBytes();
            tx += displayBucket.getTxBytes();
        }
        return new Pair(rx, tx);
    }

    private Pair<Long, Long> trafficFor(int matchRule, int oemManaged) {
        NetworkTemplate template = new NetworkTemplate.Builder(matchRule)
                .setOemManaged(oemManaged).build();
        try {
            return calculateTraffic(
                    mNetStatsMan.querySummary(template, mStartTime, System.currentTimeMillis()));
        } catch (Exception e) {
            return null;
        }
    }

    private Pair<Long, Long> combinedTrafficFor(int oemManaged) {
        Pair<Long, Long> traffic;
        long rx = 0;
        long tx = 0;
        for (int matchRule : NETWORK_MATCHING_RULES) {
            traffic = trafficFor(matchRule, oemManaged);
            if (traffic != null) {
                rx += traffic.first;
                tx += traffic.second;
            }
        }
        return new Pair(rx, tx);
    }

    @WorkerThread
    private void sendMessage(int type, Pair<Long, Long> traffic) {
        if (traffic == null) {
            return;
        }
        Message msg = mMetricMessageHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putInt(ManagerFragment.METRIC_MSG_OEM_PREFERENCE_KEY, type);
        bundle.putLong(ManagerFragment.METRIC_MSG_OEM_PREFERENCE_RX_KEY, traffic.first);
        bundle.putLong(ManagerFragment.METRIC_MSG_OEM_PREFERENCE_TX_KEY, traffic.second);
        msg.setData(bundle);
        mMetricMessageHandler.sendMessage(msg);
    }

    @WorkerThread
    private void updateStats() {
        sendMessage(NetworkTemplate.OEM_MANAGED_PAID,
                combinedTrafficFor(NetworkTemplate.OEM_MANAGED_PAID));
        sendMessage(NetworkTemplate.OEM_MANAGED_PRIVATE,
                combinedTrafficFor(NetworkTemplate.OEM_MANAGED_PRIVATE));
        sendMessage(
                NetworkTemplate.OEM_MANAGED_YES,
                combinedTrafficFor(NetworkTemplate.OEM_MANAGED_YES));
    }
}
