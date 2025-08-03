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

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.search.CarBaseSearchIndexProvider;
import com.android.car.ui.toolbar.ProgressBarController;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.settingslib.search.SearchIndexable;
import com.android.wifitrackerlib.WifiEntry;

import java.util.List;

/**
 * Main page to host Wifi related preferences.
 */
@SearchIndexable
public class WifiSettingsFragment extends SettingsFragment
        implements CarWifiManager.Listener {

    private static final int SEARCHING_DELAY_MILLIS = 1700;
    private static final String EXTRA_CONNECTED_WIFI_ENTRY_KEY = "connected_wifi_entry_key";

    private CarWifiManager mCarWifiManager;
    private ProgressBarController mProgressBar;
    @Nullable
    private String mConnectedWifiEntryKey;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_list_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCarWifiManager = new CarWifiManager(getContext(), getLifecycle());

        if (savedInstanceState != null) {
            mConnectedWifiEntryKey = savedInstanceState.getString(
                    EXTRA_CONNECTED_WIFI_ENTRY_KEY);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CONNECTED_WIFI_ENTRY_KEY, mConnectedWifiEntryKey);
    }

    @Override
    protected void setupToolbar(@NonNull ToolbarController toolbar) {
        super.setupToolbar(toolbar);

        mProgressBar = toolbar.getProgressBar();
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.addListener(this);
        onWifiStateChanged(mCarWifiManager.getWifiState());
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.removeListener(this);
        mProgressBar.setVisible(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onWifiEntriesChanged() {
        mProgressBar.setVisible(true);
        getView().postDelayed(() -> mProgressBar.setVisible(false), SEARCHING_DELAY_MILLIS);
        List<WifiEntry> connectedWifiEntries = mCarWifiManager.getConnectedWifiEntries();
        if (!connectedWifiEntries.isEmpty()) {
            String connectedWifiEntryKey = connectedWifiEntries.get(0).getKey();
            if (!connectedWifiEntryKey.equals(mConnectedWifiEntryKey)) {
                scrollToPreference(connectedWifiEntryKey);
                mConnectedWifiEntryKey = connectedWifiEntryKey;
            }
        } else {
            mConnectedWifiEntryKey = null;
        }
    }

    @Override
    public void onWifiStateChanged(int state) {
        mProgressBar.setVisible(state == WifiManager.WIFI_STATE_ENABLING);
    }

    /**
     * Data provider for Settings Search.
     */
    public static final CarBaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new CarBaseSearchIndexProvider(R.xml.wifi_list_fragment, Settings.ACTION_WIFI_SETTINGS);
}
