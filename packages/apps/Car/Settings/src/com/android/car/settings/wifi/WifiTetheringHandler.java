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

package com.android.car.settings.wifi;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TetheringManager;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

/**
 * Consolidates Wifi tethering logic into one handler so we can have consistent logic across various
 * parts of the Settings app.
 */
public class WifiTetheringHandler {

    private final Context mContext;
    private final CarWifiManager mCarWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;
    private boolean mRestartBooked = false;
    private boolean mMonitorRestarts;

    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() {
        @Override
        public void onStateChanged(int state, int failureReason) {
            handleWifiApStateChanged(state);
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info,
                @NonNull List<WifiClient> clients) {
            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(clients.size());
        }
    };

    private final BroadcastReceiver mRestartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mCarWifiManager != null && mCarWifiManager.isWifiApEnabled()) {
                restartTethering();
            }
        }
    };

    public WifiTetheringHandler(Context context, Lifecycle lifecycle,
            WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        this(context, lifecycle, wifiTetherAvailabilityListener, /* monitorRestarts= */ true);
    }

    public WifiTetheringHandler(Context context, Lifecycle lifecycle,
            WifiTetheringAvailabilityListener wifiTetherAvailabilityListener,
            boolean monitorRestarts) {
        this(context, new CarWifiManager(context, lifecycle),
                context.getSystemService(TetheringManager.class), wifiTetherAvailabilityListener,
                /* monitorRestarts= */ monitorRestarts);
    }

    public WifiTetheringHandler(Context context, CarWifiManager carWifiManager,
            TetheringManager tetheringManager, WifiTetheringAvailabilityListener
            wifiTetherAvailabilityListener, boolean monitorRestarts) {
        mContext = context;
        mCarWifiManager = carWifiManager;
        mTetheringManager = tetheringManager;
        mWifiTetheringAvailabilityListener = wifiTetherAvailabilityListener;
        mMonitorRestarts = monitorRestarts;
    }

    /**
     * Handles operations that should happen in host's onStartInternal().
     */
    public void onStartInternal() {
        mCarWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
        if (mMonitorRestarts) {
            LocalBroadcastManager.getInstance(mContext).registerReceiver(mRestartReceiver,
                    new IntentFilter(
                            WifiTetherBasePreferenceController.ACTION_RESTART_WIFI_TETHERING));
        }
    }

    /**
     * Handles operations that should happen in host's onStopInternal().
     */
    public void onStopInternal() {
        mCarWifiManager.unregisterSoftApCallback(mSoftApCallback);
        if (mMonitorRestarts) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mRestartReceiver);
        }
    }

    /**
     * Returns whether wifi tethering is enabled
     * @return whether wifi tethering is enabled
     */
    public boolean isWifiTetheringEnabled() {
        return mCarWifiManager.isWifiApEnabled();
    }

    /**
     * Changes the Wifi tethering state
     *
     * @param enable Whether to attempt to turn Wifi tethering on or off
     */
    public void updateWifiTetheringState(boolean enable) {
        if (enable) {
            startTethering();
        } else {
            stopTethering();
        }
    }

    @VisibleForTesting
    void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mWifiTetheringAvailabilityListener.disablePreference();
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                mWifiTetheringAvailabilityListener.enablePreference();
                mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mWifiTetheringAvailabilityListener.disablePreference();
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                mWifiTetheringAvailabilityListener.enablePreference();
                if (mRestartBooked) {
                    // Hotspot was disabled as part of a restart request - we can now re-enable it
                    mWifiTetheringAvailabilityListener.disablePreference();
                    startTethering();
                    mRestartBooked = false;
                }
                break;
            default:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                mWifiTetheringAvailabilityListener.enablePreference();
                break;
        }
    }

    private void startTethering() {
        WifiTetherUtil.startTethering(mTetheringManager,
                new TetheringManager.StartTetheringCallback() {
                    @Override
                    public void onTetheringFailed(int error) {
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                        mWifiTetheringAvailabilityListener.enablePreference();
                    }
                });
    }

    private void stopTethering() {
        WifiTetherUtil.stopTethering(mTetheringManager);
    }

    private void restartTethering() {
        stopTethering();
        mRestartBooked = true;
    }

    /**
     * Interface for receiving Wifi tethering status updates
     */
    public interface WifiTetheringAvailabilityListener {
        /**
         * Callback for when Wifi tethering is available
         */
        void onWifiTetheringAvailable();

        /**
         * Callback for when Wifi tethering is unavailable
         */
        void onWifiTetheringUnavailable();

        /**
         * Callback for when the number of tethered devices has changed
         * @param clientCount number of connected clients
         */
        default void onConnectedClientsChanged(int clientCount){
        }

        /**
         * Listener should allow further changes to Wifi tethering
         */
        void enablePreference();

        /**
         * Listener should disallow further changes to Wifi tethering
         */
        void disablePreference();
    }
}
