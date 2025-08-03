/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings;

import static com.android.tv.settings.overlay.FlavorUtils.X_EXPERIENCE_FLAVORS_MASK;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;

import com.android.tv.settings.overlay.FlavorUtils;

import java.util.List;

/** BroadcastReceiver to handle the request from setup */
public class DeviceSettingBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "DeviceSettingsReceiver";
    private static final String ACTION_REBOOT_DEVICE = "com.android.tv.settings.REBOOT_DEVICE";
    private static final String ACTION_REMOVE_WIFI = "com.android.tv.settings.REMOVE_WIFI";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ((FlavorUtils.getFlavor(context) & X_EXPERIENCE_FLAVORS_MASK) == 0) {
            Log.w(TAG, "Not supported in this flavor.");
            return;
        }
        switch (intent.getAction()) {
            case ACTION_REBOOT_DEVICE:
                restartDevice(context);
                break;
            case ACTION_REMOVE_WIFI:
                removeWifis(context);
                break;
            default:
                // no-op
        }
    }

    private void restartDevice(Context context) {
        final PowerManager pm = context.getSystemService(PowerManager.class);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                pm.reboot(null);
                return null;
            }
        }.execute();
    }

    private void removeWifis(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        List<WifiConfiguration> wifiList = wifiManager.getConfiguredNetworks();
        if (wifiList != null && !wifiList.isEmpty()) {
            for (WifiConfiguration wifiConfiguration : wifiList) {
                wifiManager.removeNetwork(wifiConfiguration.networkId);
            }
        }
    }
}
