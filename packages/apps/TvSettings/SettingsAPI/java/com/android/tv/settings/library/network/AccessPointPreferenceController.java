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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;
import com.android.wifitrackerlib.WifiEntry;

/** Preference controller for access point preference in NetworkState. */
public class AccessPointPreferenceController extends RestrictedPreferenceController {
    private final AccessPoint mAccessPoint;
    private static final String EXTRA_WIFI_SSID = "wifi_ssid";
    private static final String EXTRA_WIFI_SECURITY_NAME = "wifi_security_name";


    public AccessPointPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager, AccessPoint accessPoint,
            String[] key) {
        super(context, callback, stateIdentifier, preferenceCompatManager, key);
        mAccessPoint = accessPoint;
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return UserManager.DISALLOW_CONFIG_WIFI;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void init() {
        if (getAttrUserRestriction() != null) {
            checkRestrictionAndSetDisabled(getAttrUserRestriction(), UserHandle.myUserId());
        }
        mPreferenceCompat.setTitle(mAccessPoint.getTitle());
        mPreferenceCompat.setType(PreferenceCompat.TYPE_PREFERENCE_ACCESS_POINT);
        if (mAccessPoint.isActive()) {
            mPreferenceCompat.setSummary(ResourcesUtil.getString(mContext, "connected"));
        }
        if (mAccessPoint.isActive() && !isCaptivePortal(mAccessPoint)) {
            Bundle extras = new Bundle();
            WifiDetailsState.prepareArgs(extras, mAccessPoint);
            mPreferenceCompat.setExtras(extras);
            mPreferenceCompat.setNextState(ManagerUtil.STATE_WIFI_DETAILS);
            mPreferenceCompat.setIntent(null);
        } else {
            Intent i = new Intent("com.android.settings.wifi.action.WIFI_CONNECTION_SETTINGS")
                    .putExtra(EXTRA_WIFI_SSID, mAccessPoint.getSsidStr())
                    .putExtra(EXTRA_WIFI_SECURITY_NAME, mAccessPoint.getSecurity());
            mPreferenceCompat.setIntent(i);
        }
        mPreferenceCompat.addInfo(ManagerUtil.INFO_WIFI_SIGNAL_LEVEL, mAccessPoint.getLevel());
        mPreferenceCompat.setRestricted(true);
        update();
    }


    private boolean isCaptivePortal(AccessPoint accessPoint) {
        WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
        ConnectivityManager connectivityManager = mContext.getSystemService(
                ConnectivityManager.class);
        if (accessPoint.getWifiEntry().getConnectedState() != WifiEntry.CONNECTED_STATE_CONNECTED) {
            return false;
        }
        NetworkCapabilities nc = connectivityManager.getNetworkCapabilities(
                wifiManager.getCurrentNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
    }

    public boolean performClick(boolean status) {
        return handlePreferenceTreeClick(status);
    }
}
