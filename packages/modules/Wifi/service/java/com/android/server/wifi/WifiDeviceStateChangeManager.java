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

package com.android.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.ArraySet;

import java.util.Set;

/** A centralized manager to handle all the device state changes */
public class WifiDeviceStateChangeManager {
    private final Handler mHandler;
    private final Context mContext;

    private final PowerManager mPowerManager;
    private final Set<StateChangeCallback> mChangeCallbackList = new ArraySet<>();
    private boolean mIsWifiServiceStarted = false;

    /**
     * Callback to receive the device state change event. Caller should implement the method to
     * listen to the interested event
     */
    public interface StateChangeCallback {
        /**
         * Called when the screen state changes
         *
         * @param screenOn true for ON, false otherwise
         */
        default void onScreenStateChanged(boolean screenOn) {}
    }

    /** Create the instance of WifiDeviceStateChangeManager. */
    public WifiDeviceStateChangeManager(Context context, Handler handler) {
        mHandler = handler;
        mContext = context;
        mPowerManager = mContext.getSystemService(PowerManager.class);
    }

    /** Handle the boot completed event. Start to register the receiver and callback. */
    public void handleBootCompleted() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (TextUtils.equals(action, Intent.ACTION_SCREEN_ON)) {
                            handleScreenStateChanged(true);
                        } else if (TextUtils.equals(action, Intent.ACTION_SCREEN_OFF)) {
                            handleScreenStateChanged(false);
                        }
                    }
                },
                filter,
                null,
                mHandler);
        handleScreenStateChanged(mPowerManager.isInteractive());
        mIsWifiServiceStarted = true;
    }

    /**
     * Register a state change callback. When the state is changed, caller with receive the callback
     * event
     */
    public void registerStateChangeCallback(StateChangeCallback callback) {
        mChangeCallbackList.add(callback);
        if (!mIsWifiServiceStarted) return;
        callback.onScreenStateChanged(mPowerManager.isInteractive());
    }

    /**
     * Unregister a state change callback when caller is not interested the state change anymore.
     */
    public void unregisterStateChangeCallback(StateChangeCallback callback) {
        mChangeCallbackList.remove(callback);
    }

    private void handleScreenStateChanged(boolean screenOn) {
        for (StateChangeCallback callback : mChangeCallbackList) {
            callback.onScreenStateChanged(screenOn);
        }
    }
}
