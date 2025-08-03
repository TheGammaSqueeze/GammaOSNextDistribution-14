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
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.ArrayMap;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.LibUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** State to provide data for rendering NetworkFragment. */
public class NetworkState extends PreferenceControllerState implements
        AccessPoint.AccessPointListener,
        ConnectivityListener.WifiNetworkListener, ConnectivityListener.Listener {
    private static final String TAG = "NetworkMainState";
    private static final boolean DEBUG = true;
    private static final String KEY_WIFI_ENABLE = "wifi_enable";
    private static final String KEY_WIFI_LIST = "wifi_list";
    private static final String KEY_WIFI_COLLAPSE = "wifi_collapse";
    private static final String KEY_WIFI_OTHER = "wifi_other";
    private static final String KEY_WIFI_ADD = "wifi_add";
    private static final String KEY_WIFI_ADD_EASYCONNECT = "wifi_add_easyconnect";
    private static final String KEY_WIFI_ALWAYS_SCAN = "wifi_always_scan";
    private static final String KEY_ETHERNET = "ethernet";
    private static final String KEY_ETHERNET_STATUS = "ethernet_status";
    private static final String KEY_ETHERNET_PROXY = "ethernet_proxy";
    private static final String KEY_ETHERNET_DHCP = "ethernet_dhcp";
    private static final String KEY_NETWORK_DIAGNOSTICS = "network_diagnostics";
    private static final String NETWORK_DIAGNOSTICS_ACTION =
            "com.android.tv.settings.network.NETWORK_DIAGNOSTICS";
    private static final int INITIAL_UPDATE_DELAY = 500;

    private PreferenceCompat mEnableWifiPref;
    private PreferenceCompat mAddPref;
    private PreferenceCompat mEthernetCategory;
    private PreferenceCompat mEthernetStatusPref;
    private PreferenceCompat mEthernetProxyPref;
    private PreferenceCompat mAlwaysScan;
    private PreferenceCompat mWifiNetworkCategoryPref;
    private PreferenceCompat mDataSaverSlicePref;
    private PreferenceCompat mDataAlertSlicePref;
    private PreferenceCompat mNetworkDiagnosticsPref;
    private AbstractPreferenceController mAddNetworkPreferenceController;
    private AbstractPreferenceController mEasyConnectPreferenceController;

    private PreferenceCompatManager mPreferenceCompatManager;
    private NetworkModule mNetworkModule;
    private ConnectivityManager mConnectivityManager;
    private final Handler mHandler = new Handler();
    private long mNoWifiUpdateBeforeMillis;
    private final Map<String, AccessPointPreferenceController> mAccessPointPrefControllers =
            new ArrayMap<>();

    private final Runnable mInitialUpdateWifiListRunnable = new Runnable() {
        @Override
        public void run() {
            mNoWifiUpdateBeforeMillis = 0;
            updateWifiList();
        }
    };

    public NetworkState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mNetworkModule = NetworkModule.getInstance(mContext);
        mPreferenceCompatManager = new PreferenceCompatManager();
        mEnableWifiPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_WIFI_ENABLE);
        mAlwaysScan = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_WIFI_ALWAYS_SCAN);
        mAddPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_WIFI_ADD);
        mEthernetCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_ETHERNET);
        mEthernetStatusPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_ETHERNET_STATUS);
        mEthernetProxyPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_ETHERNET_PROXY);
        mWifiNetworkCategoryPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_WIFI_LIST);
        mWifiNetworkCategoryPref.setType(PreferenceCompat.TYPE_PREFERENCE_COLLAPSE_CATEGORY);
        mNetworkDiagnosticsPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_NETWORK_DIAGNOSTICS);
        Intent networkDiagnosticsIntent = makeNetworkDiagnosticsIntent();
        if (networkDiagnosticsIntent != null) {
            mNetworkDiagnosticsPref.setVisible(true);
            mNetworkDiagnosticsPref.setIntent(networkDiagnosticsIntent);
        } else {
            mNetworkDiagnosticsPref.setVisible(false);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mNetworkDiagnosticsPref);
        updateVisibilityForDataSaver();
        super.onCreate(extras);
    }

    @Override
    public void onStart() {
        super.onStart();
        mNetworkModule.addState(this);
        mNoWifiUpdateBeforeMillis = SystemClock.elapsedRealtime() + INITIAL_UPDATE_DELAY;
        updateWifiList();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectivity();
    }

    @Override
    public void onStop() {
        super.onStop();
        mNetworkModule.getConnectivityListener().stop();
        mNetworkModule.removeState(this);
    }

    private void updateVisibilityForDataSaver() {
        mDataSaverSlicePref.setVisible(isConnected());
        mDataAlertSlicePref.setVisible(isConnected());
    }

    private boolean isConnected() {
        NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected()
                && ConnectivityManager.TYPE_ETHERNET != activeNetworkInfo.getType();
    }

    private void updateWifiList() {
        if (!mNetworkModule.isWifiHardwarePresent()
                || !mNetworkModule.getConnectivityListener().isWifiEnabledOrEnabling()) {
            mNoWifiUpdateBeforeMillis = 0;
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        if (mNoWifiUpdateBeforeMillis > now) {
            mHandler.removeCallbacks(mInitialUpdateWifiListRunnable);
            mHandler.postDelayed(mInitialUpdateWifiListRunnable,
                    mNoWifiUpdateBeforeMillis - now);
            return;
        }

        final Collection<AccessPoint> accessPoints =
                mNetworkModule.getConnectivityListener().getAvailableNetworks();
        mWifiNetworkCategoryPref.initChildPreferences();
        for (final AccessPoint accessPoint : accessPoints) {
            accessPoint.setListener(this);
            // Use preference controller but do not attach to lifecycle methods, manually call
            // required methods to create preference compat.
            AccessPointPreferenceController controller = new AccessPointPreferenceController(
                    mContext, mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager,
                    accessPoint, new String[]{KEY_WIFI_LIST, accessPoint.getKey()});
            controller.init();
            PreferenceCompat accessPointPrefCompat = controller.getPrefCompat();
            mAccessPointPrefControllers.put(
                    PreferenceCompatManager.getKey(accessPointPrefCompat.getKey()), controller);
            mWifiNetworkCategoryPref.addChildPrefCompat(accessPointPrefCompat);
        }
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mWifiNetworkCategoryPref);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        boolean handled = true;
        if (mAccessPointPrefControllers.containsKey(PreferenceCompatManager.getKey(key))) {
            return mAccessPointPrefControllers.get(PreferenceCompatManager.getKey(key))
                    .performClick(status);
        }
        switch (key[0]) {
            case KEY_WIFI_ENABLE:
                mNetworkModule.getConnectivityListener().setWifiEnabled(status);
                mEnableWifiPref.setChecked(status);
                break;
            case KEY_WIFI_ALWAYS_SCAN:
                mAlwaysScan.setChecked(status);
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                        status ? 1 : 0);
                break;
            case KEY_ETHERNET_STATUS:
            case KEY_ETHERNET_DHCP:
            case KEY_ETHERNET_PROXY:
                break;
            default:
                handled = false;
        }
        handled = super.onPreferenceTreeClick(key, status) | handled;
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(),
                    mPreferenceCompatManager.getOrCreatePrefCompat(key));
        }
        return handled;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    private void updateConnectivity() {
        List<PreferenceCompat> preferenceCompats = new ArrayList<>();
        final boolean wifiEnabled = mNetworkModule.isWifiHardwarePresent()
                && mNetworkModule.getConnectivityListener().isWifiEnabledOrEnabling();
        mEnableWifiPref.setChecked(wifiEnabled);
        preferenceCompats.add(mEnableWifiPref);

        mWifiNetworkCategoryPref.setVisible(wifiEnabled);
        preferenceCompats.add(mWifiNetworkCategoryPref);

        mAddPref.setVisible(wifiEnabled);
        preferenceCompats.add(mAddPref);

        if (!wifiEnabled) {
            updateWifiList();
        }

        int scanAlwaysAvailable = 0;
        try {
            scanAlwaysAvailable = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE);
        } catch (Settings.SettingNotFoundException e) {
            // Ignore
        }

        mAlwaysScan.setChecked(scanAlwaysAvailable == 1);
        mAlwaysScan.setContentDescription(
                ResourcesUtil.getString(mContext, "wifi_setting_always_scan_content_description"));

        final boolean ethernetAvailable =
                mNetworkModule.getConnectivityListener().isEthernetAvailable();
        mEthernetCategory.setVisible(ethernetAvailable);
        mEthernetStatusPref.setVisible(ethernetAvailable);
        mEthernetProxyPref.setVisible(ethernetAvailable);
        preferenceCompats.add(mEthernetCategory);
        preferenceCompats.add(mEthernetStatusPref);
        preferenceCompats.add(mEthernetProxyPref);

        if (ethernetAvailable) {
            final boolean ethernetConnected =
                    mNetworkModule.getConnectivityListener().isEthernetConnected();
            mEthernetStatusPref.setTitle(ethernetConnected
                    ? ResourcesUtil.getString(mContext, "connected")
                    : ResourcesUtil.getString(mContext, "not_connected"));
            mEthernetStatusPref.setSummary(
                    mNetworkModule.getConnectivityListener().getEthernetIpAddress());
        }

        updateVisibilityForDataSaver();
        preferenceCompats.add(mDataSaverSlicePref);
        preferenceCompats.add(mDataAlertSlicePref);
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdateAll(getStateIdentifier(), preferenceCompats);
        }
    }

    @Override
    public void onAccessPointChanged(AccessPoint accessPoint) {
        PreferenceCompat accessPointPref = new PreferenceCompat(
                new String[]{KEY_WIFI_LIST, accessPoint.getKey()});
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), accessPointPref);
        }
    }

    @Override
    public void onLevelChanged(AccessPoint accessPoint) {
        PreferenceCompat accessPointPref = new PreferenceCompat(
                new String[]{KEY_WIFI_LIST, accessPoint.getKey()});
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), accessPointPref);
        }
    }

    @Override
    public void onWifiListChanged() {
        updateWifiList();
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_NETWORK;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        mAddNetworkPreferenceController = new AddWifiPreferenceController(context,
                mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager);
        mEasyConnectPreferenceController = new AddEasyConnectPreferenceController(context,
                mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager);
        controllers.add(mAddNetworkPreferenceController);
        controllers.add(mEasyConnectPreferenceController);
        return controllers;
    }


    @Override
    public void onConnectivityChange() {
        updateConnectivity();
    }

    private Intent makeNetworkDiagnosticsIntent() {
        Intent intent = new Intent();
        intent.setAction(NETWORK_DIAGNOSTICS_ACTION);

        ResolveInfo resolveInfo = LibUtils.systemIntentIsHandled(mContext, intent);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }

        intent.setPackage(resolveInfo.activityInfo.packageName);

        return intent;
    }
}
