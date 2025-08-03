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

package com.android.car.settings.wifi;

import android.content.Context;

import com.android.car.settings.R;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents Security protocol for WifiEntry.
 */
public class WifiEntrySecurity {
    public static final int SECURITY_NONE_POSITION = 0;
    private final int mSecurityType;
    private final Context mContext;
    private static final List<Integer> SECURITY_TYPES = Arrays.asList(
            WifiEntry.SECURITY_NONE,
            WifiEntry.SECURITY_WEP,
            WifiEntry.SECURITY_PSK,
            WifiEntry.SECURITY_EAP,
            WifiEntry.SECURITY_SAE,
            WifiEntry.SECURITY_OWE,
            WifiEntry.SECURITY_EAP_SUITE_B);

    /**
     * Returns a list of security protocols for WifiEntries.
     */
    public static List<WifiEntrySecurity> getSecurityTypes(Context context) {
        List<WifiEntrySecurity> securities = new ArrayList<>();
        for (int security : SECURITY_TYPES) {
            securities.add(new WifiEntrySecurity(context, security));
        }
        return securities;
    }

    private WifiEntrySecurity(Context context, int securityType) {
        mContext = context;
        mSecurityType = securityType;
    }

    public int getSecurityType() {
        return mSecurityType;
    }

    @Override
    public String toString() {
        switch(mSecurityType) {
            case WifiEntry.SECURITY_EAP_SUITE_B:
                return mContext.getString(R.string.wifi_security_eap_suiteb);
            case WifiEntry.SECURITY_EAP:
                return mContext.getString(R.string.wifi_security_eap);
            case WifiEntry.SECURITY_SAE:
                return mContext.getString(R.string.wifi_security_sae);
            case WifiEntry.SECURITY_PSK:
                return mContext.getString(R.string.wifi_security_psk_generic);
            case WifiEntry.SECURITY_WEP:
                return mContext.getString(R.string.wifi_security_wep);
            case WifiEntry.SECURITY_OWE:
                return mContext.getString(R.string.wifi_security_owe);
            case WifiEntry.SECURITY_NONE:
            default:
                return mContext.getString(R.string.wifi_security_none);
        }
    }
}
