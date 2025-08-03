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

package com.android.tv.settings.connectivity.security;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.GuidedAction;
import androidx.lifecycle.ViewModelProviders;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.WifiConfigHelper;
import com.android.tv.settings.connectivity.setup.AdvancedOptionsFlowInfo;
import com.android.tv.settings.connectivity.setup.UserChoiceInfo;
import com.android.tv.settings.library.network.AccessPoint;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.List;

/** Helper class to handle Wi-Fi security */
public class WifiSecurityHelper {
    public static List<GuidedAction> getSecurityTypes(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        List<GuidedAction> securityTypes = new ArrayList<>();
        securityTypes.add(new GuidedAction.Builder(context)
                .title(context.getString(R.string.wifi_security_type_none))
                .id(AccessPoint.SECURITY_NONE)
                .build());
        if (wifiManager.isEnhancedOpenSupported()) {
            securityTypes.add(new GuidedAction.Builder(context)
                    .title(context.getString(R.string.wifi_security_owe))
                    .id(AccessPoint.SECURITY_OWE)
                    .build());
        }
        securityTypes.add(new GuidedAction.Builder(context)
                .title(context.getString(R.string.wifi_security_wep))
                .id(AccessPoint.SECURITY_WEP)
                .build());
        securityTypes.add(new GuidedAction.Builder(context)
                .title(context.getString(R.string.wifi_security_wpa_wpa2))
                .id(AccessPoint.SECURITY_PSK)
                .build());
        if (wifiManager.isWpa3SaeSupported()) {
            securityTypes.add(new GuidedAction.Builder(context)
                    .title(context.getString(R.string.wifi_security_sae))
                    .id(AccessPoint.SECURITY_SAE)
                    .build());
        }
        return securityTypes;
    }

    public static String getSsid(FragmentActivity context) {
        UserChoiceInfo userChoiceInfo = ViewModelProviders.of(context).get(UserChoiceInfo.class);
        String savedSsid = userChoiceInfo.getPageSummary(UserChoiceInfo.SSID);
        return savedSsid != null
                ? savedSsid : userChoiceInfo.getWifiConfiguration().getPrintableSsid();
    }

    public static Integer getSecurity(FragmentActivity context) {
        UserChoiceInfo userChoiceInfo = ViewModelProviders.of(context).get(UserChoiceInfo.class);
        Integer savedSecurity = userChoiceInfo.getChoice(UserChoiceInfo.SECURITY);
        return savedSecurity != null ? savedSecurity : userChoiceInfo.getWifiSecurity();
    }

    public static WifiConfiguration getConfig(FragmentActivity context) {
        UserChoiceInfo userChoiceInfo = ViewModelProviders.of(context).get(UserChoiceInfo.class);
        AdvancedOptionsFlowInfo advancedOptionsFlowInfo = ViewModelProviders.of(context).get(
                AdvancedOptionsFlowInfo.class);
        WifiConfiguration config = userChoiceInfo.getWifiConfiguration();
        String ssid = userChoiceInfo.getPageSummary(UserChoiceInfo.SSID);
        if (ssid != null) {
            config.SSID = AccessPoint.convertToQuotedString(
                    userChoiceInfo.getPageSummary(UserChoiceInfo.SSID));
        }
        Integer security = userChoiceInfo.getChoice(UserChoiceInfo.SECURITY);
        if (security != null) {
            WifiConfigHelper.setConfigKeyManagementBySecurity(config, security);
        }

        String password = userChoiceInfo.getPageSummary(UserChoiceInfo.PASSWORD);
        int passwordLength = password != null ? password.length() : 0;

        if (passwordLength > 0)  {
            switch (getSecurity(context)) {
                case WifiEntry.SECURITY_WEP:
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((passwordLength == 10 || passwordLength == 26 || passwordLength == 58)
                            && password.matches("[0-9A-Fa-f]*")) {
                        config.wepKeys[0] = password;
                    } else {
                        config.wepKeys[0] = '"' + password + '"';
                    }
                    break;
                case WifiEntry.SECURITY_PSK:
                    if (password.matches("[0-9A-Fa-f]{64}")) {
                        config.preSharedKey = password;
                    } else {
                        config.preSharedKey = '"' + password + '"';
                    }
                    break;
                case WifiEntry.SECURITY_SAE:
                    config.preSharedKey = '"' + password + '"';
                    break;
            }
        }

        if (advancedOptionsFlowInfo.getIpConfiguration() != null) {
            config.setIpConfiguration(advancedOptionsFlowInfo.getIpConfiguration());
        }
        return config;
    }
}
