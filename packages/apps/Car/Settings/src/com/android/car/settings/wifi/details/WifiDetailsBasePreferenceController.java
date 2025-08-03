/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.wifi.WifiUtil;
import com.android.wifitrackerlib.WifiEntry;

/**
 * Controller for logic pertaining to displaying Wifi information. Only available when wifi is
 * active.
 *
 * <p>Subclasses should use
 * {@link com.android.car.settings.common.PreferenceController#updateState(Preference)} to render UI
 * with latest info if desired.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class WifiDetailsBasePreferenceController<V extends Preference> extends
        PreferenceController<V> implements WifiInfoProvider.Listener {

    private WifiEntry mWifiEntry;
    private WifiInfoProvider mWifiInfoProvider;

    public WifiDetailsBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /**
     * Sets all parameters for the controller to run, need to get called as early as possible.
     */
    public WifiDetailsBasePreferenceController init(
            WifiEntry wifiEntry, WifiInfoProvider wifiInfoProvider) {
        mWifiEntry = wifiEntry;
        mWifiInfoProvider = wifiInfoProvider;
        return this;
    }

    /** Gets the Wi-Fi entry that this controller was initialized with. */
    protected WifiEntry getWifiEntry() {
        return mWifiEntry;
    }

    /** Gets the Wi-Fi info provider that this controller was initialized with. */
    protected WifiInfoProvider getWifiInfoProvider() {
        return mWifiInfoProvider;
    }

    @Override
    protected void onStartInternal() {
        mWifiInfoProvider.addListener(this);
    }

    @Override
    protected void onStopInternal() {
        mWifiInfoProvider.removeListener(this);
    }

    @Override
    public void onWifiEntryUpdated() {
        getPreference().setEnabled(true);
        refreshUi();
    }

    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
        refreshUi();
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
    }

    @Override
    public void onLost(Network network) {
        getPreference().setEnabled(false);
        refreshUi();
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!WifiUtil.isWifiAvailable(getContext())) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return WifiUtil.isWifiEntryConnectedOrConnecting(getWifiEntry())
                ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }
}
