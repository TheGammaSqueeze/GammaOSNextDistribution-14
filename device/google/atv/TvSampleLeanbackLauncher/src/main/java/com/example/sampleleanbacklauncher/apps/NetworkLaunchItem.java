/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.apps;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import androidx.annotation.WorkerThread;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import com.example.sampleleanbacklauncher.R;

public class NetworkLaunchItem extends SettingsLaunchItem {

    private final Drawable mNetworkStateDrawable;
    private final CharSequence mNetworkStateLabel;

    @WorkerThread
    public NetworkLaunchItem(Context context, ResolveInfo info, SignalStrength signalStrength,
            long priority) {
        super(context, info, priority);
        final NetworkInfo networkInfo =
                context.getSystemService(ConnectivityManager.class).getActiveNetworkInfo();
        if (networkInfo == null) {
            mNetworkStateDrawable = context.getDrawable(R.drawable.wifi_not_connected_launcher);
            mNetworkStateLabel = context.getString(R.string.network_settings_disconnected);
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
            mNetworkStateDrawable = getEthernetDrawable(context, networkInfo);
            mNetworkStateLabel = context.getString(R.string.network_settings_disconnected);
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            final WifiInfo wifiInfo =
                    context.getSystemService(WifiManager.class).getConnectionInfo();
            mNetworkStateDrawable = getWifiDrawable(context, networkInfo, wifiInfo);
            mNetworkStateLabel = removeDoubleQuotes(wifiInfo.getSSID());
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            mNetworkStateDrawable = getCellularDrawable(context, networkInfo, signalStrength);
            mNetworkStateLabel = removeDoubleQuotes(
                    context.getSystemService(TelephonyManager.class).getNetworkOperatorName());
        } else {
            mNetworkStateDrawable = context.getDrawable(R.drawable.wifi_not_connected_launcher);
            mNetworkStateLabel = context.getString(R.string.network_settings_disconnected);
        }
    }

    private static Drawable getEthernetDrawable(Context context, NetworkInfo networkInfo) {
        if (networkInfo.isConnected()) {
            return context.getDrawable(R.drawable.ethernet_active_launcher);
        } else {
            return context.getDrawable(R.drawable.ethernet_no_internet_launcher);
        }
    }

    private static Drawable getWifiDrawable(Context context, NetworkInfo networkInfo,
            WifiInfo wifiInfo) {
        final int signal = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
        if (networkInfo.isConnected()) {
            switch (signal) {
                case 4:
                    return context.getDrawable(R.drawable.wifi_active_4_launcher);
                case 3:
                    return context.getDrawable(R.drawable.wifi_active_3_launcher);
                case 2:
                    return context.getDrawable(R.drawable.wifi_active_2_launcher);
                case 1:
                    return context.getDrawable(R.drawable.wifi_active_1_launcher);
                case 0:
                default:
                    return context.getDrawable(R.drawable.wifi_active_0_launcher);
            }
        } else {
            return context.getDrawable(R.drawable.wifi_no_internet_launcher);
        }
    }

    private static Drawable getCellularDrawable(Context context, NetworkInfo networkInfo,
            SignalStrength signalStrength) {
        final TelephonyManager telephonyManager =
                context.getSystemService(TelephonyManager.class);
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            return context.getDrawable(R.drawable.cellular_no_sim_launcher);
        } else if (signalStrength == null) {
            return context.getDrawable(R.drawable.cellular_null_launcher);
        } else {
            if (networkInfo.isConnected()) {
                switch (signalStrength.getLevel()) {
                    case 4:
                        return context.getDrawable(R.drawable.cellular_4_bar_launcher);
                    case 3:
                        return context.getDrawable(R.drawable.cellular_3_bar_launcher);
                    case 2:
                        return context.getDrawable(R.drawable.cellular_2_bar_launcher);
                    case 1:
                        return context.getDrawable(R.drawable.cellular_1_bar_launcher);
                    case 0:
                    default:
                        return context.getDrawable(R.drawable.cellular_0_bar_launcher);
                }
            } else {
                switch (signalStrength.getLevel()) {
                    case 4:
                        return context.getDrawable(R.drawable.cellular_no_internet_4_bar_launcher);
                    case 3:
                        return context.getDrawable(R.drawable.cellular_no_internet_3_bar_launcher);
                    case 2:
                        return context.getDrawable(R.drawable.cellular_no_internet_2_bar_launcher);
                    case 1:
                        return context.getDrawable(R.drawable.cellular_no_internet_1_bar_launcher);
                    case 0:
                    default:
                        return context.getDrawable(R.drawable.cellular_no_internet_0_bar_launcher);
                }
            }
        }
    }

    @Override
    public Drawable getIcon() {
        return mNetworkStateDrawable;
    }

    @Override
    public CharSequence getLabel() {
        return mNetworkStateLabel;
    }

    private static String removeDoubleQuotes(String string) {
        if (string == null) {
            return null;
        }
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

}
