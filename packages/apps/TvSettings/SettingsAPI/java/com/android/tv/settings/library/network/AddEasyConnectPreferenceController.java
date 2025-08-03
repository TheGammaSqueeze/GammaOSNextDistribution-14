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
import android.os.UserManager;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

/** Preference controller for easy connect preference in NetworkState. */
public class AddEasyConnectPreferenceController extends RestrictedPreferenceController {
    private static final String KEY_ADD_EASY_CONNECT = "wifi_add_easyconnect";
    private static final String ACTION_ADD_WIFI_NETWORK =
            "com.android.settings.wifi.action.ADD_WIFI_NETWORK";
    private static final String EXTRA_TYPE = "com.android.tv.settings.connectivity.type";
    private static final String EXTRA_TYPE_EASYCONNECT = "easyconnect";

    public AddEasyConnectPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        if (!mDisabledByAdmin) {
            Intent i = new Intent(ACTION_ADD_WIFI_NETWORK)
                    .putExtra(EXTRA_TYPE, EXTRA_TYPE_EASYCONNECT);
            mContext.startActivity(i);
            return true;
        }
        return super.handlePreferenceTreeClick(status);
    }

    @Override
    public String getAttrUserRestriction() {
        return UserManager.DISALLOW_CONFIG_WIFI;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_ADD_EASY_CONNECT};
    }
}
