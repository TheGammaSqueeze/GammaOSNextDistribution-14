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


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.DataBinder;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

import java.util.ArrayList;
import java.util.List;

public class WifiDetailsState extends PreferenceControllerState implements
        ConnectivityListener.Listener, ConnectivityListener.WifiNetworkListener {
    private static final String TAG = "WifiDetailsState";

    private static final String ARG_ACCESS_POINT_STATE = "apBundle";
    private static final String KEY_CONNECTION_STATUS = "connection_status";
    private static final String KEY_IP_ADDRESS = "ip_address";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_RANDOM_MAC = "random_mac";
    private static final String VALUE_MAC_RANDOM = "random";
    private static final String VALUE_MAC_DEVICE = "device";
    public static final String EXTRA_NETWORK_ID = "network_id";

    public static final int REQUEST_CODE_FORGET_NETWORK = 1;

    private NetworkModule mNetworkModule;
    private AccessPoint mAccessPoint;
    PreferenceCompatManager mPreferenceCompatManager;
    private PreferenceCompat mConnectionStatusPref;
    private PreferenceCompat mIpAddressPref;
    private PreferenceCompat mMacAddressPref;
    private PreferenceCompat mSignalStrengthPref;
    private PreferenceCompat mRandomMacPref;
    private AbstractPreferenceController mProxySettingsPrefController;
    private AbstractPreferenceController mIpSettingsPrefController;
    private AbstractPreferenceController mForgetNetworkPrefController;

    public WifiDetailsState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
        mNetworkModule = NetworkModule.getInstance(mContext);
    }

    public static void prepareArgs(@NonNull Bundle args, AccessPoint accessPoint) {
        args.putBinder(ARG_ACCESS_POINT_STATE, DataBinder.with(accessPoint));
    }

    @Override
    public void onCreate(Bundle extras) {
        mNetworkModule = NetworkModule.getInstance(mContext);
        mPreferenceCompatManager = new PreferenceCompatManager();
        mAccessPoint = DataBinder.getData(extras.getBinder(ARG_ACCESS_POINT_STATE));
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdateScreenTitle(getStateIdentifier(),
                    String.valueOf(mAccessPoint.getSsid()));
        }
        mConnectionStatusPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_CONNECTION_STATUS);
        mIpAddressPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_IP_ADDRESS);
        mMacAddressPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MAC_ADDRESS);
        mSignalStrengthPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SIGNAL_STRENGTH);
        mRandomMacPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_RANDOM_MAC);
        mRandomMacPref.setType(PreferenceCompat.TYPE_LIST);
        super.onCreate(extras);
    }

    @Override
    public void onStart() {
        super.onStart();
        mNetworkModule.addState(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Override
    public void onStop() {
        super.onStop();
        mNetworkModule.getConnectivityListener().stop();
        mNetworkModule.removeState(this);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        return super.onPreferenceTreeClick(key, status);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FORGET_NETWORK) {
            if (resultCode == Activity.RESULT_OK) {
                WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
                wifiManager.forget(mAccessPoint.getConfig().networkId, null);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        mNetworkModule.getConnectivityListener().applyMacRandomizationSetting(
                mAccessPoint,
                VALUE_MAC_RANDOM.equals(newValue));
        return true;
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_WIFI_DETAILS;
    }

    @Override
    public void onConnectivityChange() {
        update();
    }

    @Override
    public void onWifiListChanged() {
        final List<AccessPoint> accessPoints = mNetworkModule
                .getConnectivityListener().getAvailableNetworks();
        for (final AccessPoint accessPoint : accessPoints) {
            if (TextUtils.equals(mAccessPoint.getSsidStr(), accessPoint.getSsidStr())
                    && mAccessPoint.getSecurity() == accessPoint.getSecurity()) {
                // Make sure we're not holding on to the one we inflated from the bundle, because
                // it won't be updated
                mAccessPoint = accessPoint;
                break;
            }
        }
        update();
    }

    private void update() {
        List<PreferenceCompat> preferenceCompats = new ArrayList<>();
        if (mAccessPoint == null) {
            return;
        }
        final boolean active = mAccessPoint.isActive();

        mConnectionStatusPref.setSummary(active
                ? ResourcesUtil.getString(mContext, "connected")
                : ResourcesUtil.getString(mContext, "not_connected"));
        mIpAddressPref.setVisible(active);
        mSignalStrengthPref.setVisible(active);
        preferenceCompats.add(mConnectionStatusPref);
        preferenceCompats.add(mIpAddressPref);
        preferenceCompats.add(mSignalStrengthPref);
        preferenceCompats.add(mMacAddressPref);

        if (active) {
            mIpAddressPref.setSummary(mNetworkModule.getConnectivityListener().getWifiIpAddress());
            mSignalStrengthPref.setSummary(getSignalStrength());
        }

        // Mac address related Preferences (info entry and random mac setting entry)
        String macAddress = mNetworkModule.getConnectivityListener()
                .getWifiMacAddress(mAccessPoint);
        if (active && !TextUtils.isEmpty(macAddress)) {
            mMacAddressPref.setVisible(true);
            updateMacAddressPref(macAddress);
            updateRandomMacPref();
        } else {
            mMacAddressPref.setVisible(false);
            mRandomMacPref.setVisible(false);
        }

        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdateAll(getStateIdentifier(), preferenceCompats);
        }
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        mProxySettingsPrefController = new ProxySettingsPreferenceController(context,
                mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager, mAccessPoint);
        mIpSettingsPrefController = new IpSettingsPreferenceController(context,
                mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager, mAccessPoint);
        mForgetNetworkPrefController = new ForgetNetworkPreferenceController(context,
                mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager, mAccessPoint);
        controllers.add(mProxySettingsPrefController);
        controllers.add(mIpSettingsPrefController);
        controllers.add(mForgetNetworkPrefController);
        return controllers;
    }

    private void updateMacAddressPref(String macAddress) {
        if (WifiInfo.DEFAULT_MAC_ADDRESS.equals(macAddress)) {
            mMacAddressPref.setSummary(
                    ResourcesUtil.getString(mContext, "mac_address_not_available"));
        } else {
            mMacAddressPref.setSummary(macAddress);
        }
        if (mAccessPoint == null || mAccessPoint.getConfig() == null) {
            return;
        }
        // For saved Passpoint network, framework doesn't have the field to keep the MAC choice
        // persistently, so Passpoint network will always use the default value so far, which is
        // randomized MAC address, so don't need to modify title.
        if (mAccessPoint.isPasspoint()) {
            return;
        }
        mMacAddressPref.setTitle(
                (mAccessPoint.getConfig().macRandomizationSetting
                        == WifiConfiguration.RANDOMIZATION_PERSISTENT)
                        ? ResourcesUtil.getString(mContext, "title_randomized_mac_address")
                        : ResourcesUtil.getString(mContext, "title_mac_address"));
    }

    private void updateRandomMacPref() {
        ConnectivityListener connectivityListener = mNetworkModule.getConnectivityListener();
        mRandomMacPref.setVisible(connectivityListener.isMacAddressRandomizationSupported());
        boolean isMacRandomized =
                (connectivityListener.getWifiMacRandomizationSetting(mAccessPoint)
                        == WifiConfiguration.RANDOMIZATION_PERSISTENT);
        mRandomMacPref.setValue(isMacRandomized ? VALUE_MAC_RANDOM : VALUE_MAC_DEVICE);
        if (!mAccessPoint.getWifiEntry().canSetPrivacy()) {
            mRandomMacPref.setSelectable(PreferenceCompat.STATUS_OFF);
            mRandomMacPref.setSummary(ResourcesUtil.getString(
                    mContext, "mac_address_ephemeral_summary"));
        } else {
            mRandomMacPref.setSelectable(PreferenceCompat.STATUS_ON);
            String[] entries = ResourcesUtil.getStringArray(
                    mContext, "random_mac_settings_entries");
            mRandomMacPref.setHasOnPreferenceChangeListener(true);
            mRandomMacPref.setSummary(entries[isMacRandomized ? 0 : 1]);
        }
    }

    private String getSignalStrength() {
        String[] signalLevels = ResourcesUtil
                .getStringArray(mContext, "wifi_signal_strength");
        if (signalLevels != null) {
            int strength = mNetworkModule.getConnectivityListener()
                    .getWifiSignalStrength(signalLevels.length);
            return signalLevels[strength];
        }
        return "";
    }

    public static void updateRestrictedPreference(
            PreferenceCompat preferenceCompat, Context context, AccessPoint accessPoint,
            RestrictedPreferenceController restrictedPreferenceController) {
        WifiConfiguration wifiConfiguration = accessPoint.getConfig();
        boolean canModifyNetwork = !WifiHelper.isNetworkLockedDown(
                context, wifiConfiguration);
        preferenceCompat.setVisible(wifiConfiguration != null);
        if (canModifyNetwork) {
            restrictedPreferenceController.setDisabledByAdmin(null);
            restrictedPreferenceController.setEnabled(true);
        } else {
            RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtils.getProfileOrDeviceOwner(
                    context,
                    UserHandle.of(UserHandle.myUserId()));
            restrictedPreferenceController.setDisabledByAdmin(admin);
        }
    }
}
