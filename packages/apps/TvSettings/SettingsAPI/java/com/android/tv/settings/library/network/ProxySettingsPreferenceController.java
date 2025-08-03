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


import static com.android.tv.settings.library.network.WifiDetailsState.EXTRA_NETWORK_ID;

import android.content.Context;
import android.content.Intent;
import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

/** Preference controller for proxy settings preference in WifiDetailsState. */
public class ProxySettingsPreferenceController extends RestrictedPreferenceController {
    private static final String INTENT_EDIT_PROXY_SETTINGS =
            "com.android.settings.wifi.action.EDIT_PROXY_SETTINGS";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private final AccessPoint mAccessPoint;

    public ProxySettingsPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager, AccessPoint accessPoint) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mAccessPoint = accessPoint;
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    @Override
    public void update() {
        WifiConfiguration wifiConfiguration = mAccessPoint.getConfig();
        if (wifiConfiguration != null) {
            final int networkId = wifiConfiguration.networkId;
            IpConfiguration.ProxySettings proxySettings =
                    wifiConfiguration.getIpConfiguration().getProxySettings();
            mPreferenceCompat.setSummary(proxySettings == IpConfiguration.ProxySettings.NONE
                    ? ResourcesUtil.getString(mContext, "wifi_action_proxy_none")
                    : ResourcesUtil.getString(mContext, "wifi_action_proxy_manual"));
            mPreferenceCompat.setIntent(new Intent(INTENT_EDIT_PROXY_SETTINGS)
                    .putExtra(EXTRA_NETWORK_ID, networkId));
        }
        WifiDetailsState.updateRestrictedPreference(
                mPreferenceCompat, mContext, mAccessPoint, this);
        super.update();
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_PROXY_SETTINGS};
    }

}
