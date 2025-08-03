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

package com.android.car.settings.wifi.details;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import com.android.car.settings.R;
import com.android.car.settings.common.EntityHeaderPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.wifi.WifiUtil;

/**
 * Shows Wi-Fi network header with icon, SSID, and Wi-Fi entry summary.
 */
public class WifiDetailsHeaderPreferenceController extends
        WifiDetailsBasePreferenceController<EntityHeaderPreference> {

    private static final int[] STATE_SECURED = {com.android.settingslib.R.attr.state_encrypted};
    private static final int[] STATE_NONE = {};
    private static final int[] sWifiSignalAttributes = {com.android.settingslib.R.attr.wifi_signal};
    private final StateListDrawable mWifiSld;
    private final String mSummaryPlaceholder;

    public WifiDetailsHeaderPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mWifiSld = (StateListDrawable) context.getTheme()
                .obtainStyledAttributes(sWifiSignalAttributes).getDrawable(0);
        mSummaryPlaceholder = context.getString(R.string.empty_placeholder);
    }

    @Override
    protected Class<EntityHeaderPreference> getPreferenceType() {
        return EntityHeaderPreference.class;
    }

    @Override
    protected void updateState(EntityHeaderPreference preference) {
        preference.setTitle(getWifiEntry().getSsid());
        preference.setIcon(getWifiEntryIcon());
        String summary = getWifiEntry().getSummary(/* concise= */ false);
        if (TextUtils.isEmpty(summary)) {
            // If the summary is currently empty, use the placeholder string to prevent
            // vertical shifting during the initial render.
            summary = mSummaryPlaceholder;
        }
        preference.setSummary(summary);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!WifiUtil.isWifiAvailable(getContext())) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return AVAILABLE;
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
        refreshUi();
    }

    private Drawable getWifiEntryIcon() {
        if (mWifiSld == null) {
            return null;
        }
        mWifiSld.setState(
                WifiUtil.isOpenNetwork(getWifiEntry().getSecurity())
                        ? STATE_NONE
                        : STATE_SECURED);
        Drawable drawable = mWifiSld.getCurrent();
        drawable.setLevel(getWifiEntry().getLevel());
        return drawable;
    }
}
