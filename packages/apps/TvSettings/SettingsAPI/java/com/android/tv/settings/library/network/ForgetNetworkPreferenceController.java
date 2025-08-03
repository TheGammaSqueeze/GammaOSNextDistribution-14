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

import static com.android.tv.settings.library.network.WifiDetailsState.REQUEST_CODE_FORGET_NETWORK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

/** Preference controller for forget network preference in WifiDetailsState. */
public class ForgetNetworkPreferenceController extends RestrictedPreferenceController {
    static final String INTENT_CONFIRMATION = "android.settings.ui.CONFIRM";
    static final String EXTRA_GUIDANCE_TITLE = "guidancetitle";
    static final String EXTRA_GUIDANCE_SUBTITLE = "guidanceSubtitle";
    static final String EXTRA_GUIDANCE_BREADCRUMB = "guidanceBreadcrumb";
    static final String EXTRA_GUIDANCE_ICON = "guidanceIcon";
    private static final String KEY_FORGET_NETWORK = "forget_network";
    private final AccessPoint mAccessPoint;

    public ForgetNetworkPreferenceController(Context context,
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
    public boolean handlePreferenceTreeClick(boolean status) {
        if (!mDisabledByAdmin) {
            Intent forgetConfirmIntent = new Intent(INTENT_CONFIRMATION)
                    .putExtra(EXTRA_GUIDANCE_TITLE,
                            ResourcesUtil.getString(mContext, "wifi_forget_network"))
                    .putExtra(EXTRA_GUIDANCE_SUBTITLE, ResourcesUtil.getString(mContext,
                            "wifi_forget_network_description", mAccessPoint.getSsidStr()));
            ((Activity) mContext).startActivityForResult(forgetConfirmIntent,
                    ManagerUtil.calculateCompoundCode(mStateIdentifier,
                            REQUEST_CODE_FORGET_NETWORK));
            return true;
        }
        return super.handlePreferenceTreeClick(status);
    }

    @Override
    public void update() {
        WifiConfiguration wifiConfiguration = mAccessPoint.getConfig();
        mPreferenceCompat.setVisible(wifiConfiguration != null);
        WifiDetailsState.updateRestrictedPreference(
                mPreferenceCompat, mContext, mAccessPoint, this);
        super.update();
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_FORGET_NETWORK};
    }
}
