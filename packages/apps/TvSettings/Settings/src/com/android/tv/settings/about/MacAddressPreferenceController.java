/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.about;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.deviceinfo.AbstractWifiMacAddressPreferenceController;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/**
 * Concrete subclass of WIFI MAC address preference controller
 */
public class MacAddressPreferenceController extends AbstractWifiMacAddressPreferenceController {
    private static final String TAG = "MacAddressPC";

    private final ConnectivityManager mConnectivityManager;
    private Preference mMacAddress;

    public MacAddressPreferenceController(Context context, Lifecycle lifecycle) {
        super(context, lifecycle);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mMacAddress = screen.findPreference(getPreferenceKey());
        super.displayPreference(screen);
    }

    @Override
    protected void updateConnectivity() {
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities = activeNetwork != null
                ? mConnectivityManager.getNetworkCapabilities(activeNetwork) : null;
        LinkProperties linkProperties = activeNetwork != null
                ? mConnectivityManager.getLinkProperties(activeNetwork) : null;
        if (mMacAddress == null || networkCapabilities == null || linkProperties == null
                || linkProperties.getInterfaceName() == null
                || !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            super.updateConnectivity();
            return;
        }

        // Find ethernet mac address from system.
        try {
            for (NetworkInterface networkInterface :
                    Collections.list(NetworkInterface.getNetworkInterfaces())) {
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null && linkProperties.getInterfaceName().equalsIgnoreCase(
                        networkInterface.getName())) {
                    StringBuilder macString = new StringBuilder();
                    for (byte b : mac) {
                        macString.append(String.format(macString.length() == 0
                                ? "%02X" : ":%02X", b));
                    }
                    mMacAddress.setSummary(macString);
                    return;
                }

            }
        } catch (SocketException e) {
            Log.e(TAG, "Unable to list network interfaces", e);
        }

        super.updateConnectivity();
    }
}
