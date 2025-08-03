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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.wifi.WifiUtil;
import com.android.wifitrackerlib.NetworkDetailsTracker;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows details about a Wi-Fi network, including actions related to the network,
 * e.g. ignore, disconnect, etc. The intent should include information about
 * Wi-Fi entry, use that to render UI, e.g. show SSID etc.
 */
public class WifiDetailsFragment extends SettingsFragment {
    private static final String TAG = "WifiDetailsFragment";
    private static final String CHOSEN_WIFIENTRY_KEY = "chosen_wifientry_key";
    private static final Logger LOG = new Logger(WifiDetailsFragment.class);

    private WifiEntry mWifiEntry;
    private List<WifiDetailsBasePreferenceController> mControllers = new ArrayList<>();

    private WifiInfoProvider mWifiInfoProvider;

    private NetworkDetailsTracker mNetworkDetailsTracker;
    private HandlerThread mWorkerThread;

    /**
     * Gets an instance of this class.
     */
    public static WifiDetailsFragment getInstance(WifiEntry wifiEntry) {
        WifiDetailsFragment wifiDetailsFragment = new WifiDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(CHOSEN_WIFIENTRY_KEY, wifiEntry.getKey());
        wifiDetailsFragment.setArguments(bundle);
        return wifiDetailsFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_detail_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        setUpNetworksDetailTracker(getArguments().getString(CHOSEN_WIFIENTRY_KEY));
        mWifiEntry = mNetworkDetailsTracker.getWifiEntry();
        LOG.d("Creating WifiInfoProvider for " + mWifiEntry);
        if (mWifiInfoProvider == null) {
            mWifiInfoProvider = new WifiInfoProvider(getContext(), mWifiEntry);
        }
        getLifecycle().addObserver(mWifiInfoProvider);

        LOG.d("Creating WifiInfoProvider.Listeners.");
        mControllers.add(use(
                WifiDetailsHeaderPreferenceController.class, R.string.pk_wifi_details_header)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(
                WifiDetailsActionButtonsPreferenceController.class,
                R.string.pk_wifi_details_action_buttons)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(
                WifiSignalStrengthPreferenceController.class, R.string.pk_wifi_signal_strength)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiFrequencyPreferenceController.class, R.string.pk_wifi_frequency)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiSecurityPreferenceController.class, R.string.pk_wifi_security)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiMacAddressPreferenceController.class, R.string.pk_wifi_mac_address)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiIpAddressPreferenceController.class, R.string.pk_wifi_ip).init(
                mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiGatewayPreferenceController.class, R.string.pk_wifi_gateway).init(
                mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiSubnetPreferenceController.class, R.string.pk_wifi_subnet_mask)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiDnsPreferenceController.class, R.string.pk_wifi_dns).init(
                mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiLinkSpeedPreferenceController.class, R.string.pk_wifi_link_speed)
                .init(mWifiEntry, mWifiInfoProvider));
        mControllers.add(use(WifiIpv6AddressPreferenceController.class, R.string.pk_wifi_ipv6).init(
                mWifiEntry, mWifiInfoProvider));
        LOG.d("Done init.");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        enableRotaryScroll();
    }

    public void onDetach() {
        super.onDetach();
        getLifecycle().removeObserver(mWifiInfoProvider);
    }

    @Override
    public void onDestroy() {
        if (mWorkerThread != null) {
            mWorkerThread.quit();
        }
        super.onDestroy();
    }

    private void setUpNetworksDetailTracker(String key) {
        if (mNetworkDetailsTracker != null) {
            return;
        }

        mWorkerThread = new HandlerThread(TAG
                + "{" + Integer.toHexString(System.identityHashCode(this)) + "}",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mNetworkDetailsTracker = WifiUtil.createNetworkDetailsTracker(getLifecycle(),
                getContext(), new Handler(Looper.getMainLooper()), mWorkerThread.getThreadHandler(),
                key);
    }
}
