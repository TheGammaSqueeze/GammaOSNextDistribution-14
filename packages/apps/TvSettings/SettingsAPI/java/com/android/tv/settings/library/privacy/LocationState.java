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

package com.android.tv.settings.library.privacy;

import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.ManagerUtil.STATE_LOCATION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.device.apps.AppManagementState;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.settingslib.RecentLocationApps;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provide data for location settings screen in TV settings.
 */
public class LocationState extends PreferenceControllerState {
    private static final String TAG = "LocationFragment";

    static final String KEY_LOCATION_MODE = "locationMode";
    private static final String KEY_WIFI_ALWAYS_SCAN = "wifi_always_scan";
    private static final String KEY_RECENT_LOCATION_REQUESTS = "recent_location_requests";
    private static final String KEY_NO_RECENT_APP = "key_no_recent_app";

    private static final String MODE_CHANGING_ACTION =
            "com.android.settings.location.MODE_CHANGING";
    private static final String CURRENT_MODE_KEY = "CURRENT_MODE";
    private static final String NEW_MODE_KEY = "NEW_MODE";

    private static final String LOCATION_MODE_WIFI = "wifi";
    private static final String LOCATION_MODE_OFF = "off";

    private LocationModePC mLocationMode;
    private PreferenceCompat mAlwaysScan;
    private PreferenceCompat mRecentLocationRequests;


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Received location mode change intent: " + intent);
            }
            mLocationMode.updateAndNotify();
        }
    };

    public LocationState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        if (FlavorUtils.isTwoPanel(mContext)) {
            mAlwaysScan = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_WIFI_ALWAYS_SCAN);
            mAlwaysScan.setTitle(ResourcesUtil.getString(mContext, "wifi_setting_always_scan"));
            mAlwaysScan.setSummary(
                    ResourcesUtil.getString(mContext, "wifi_setting_always_scan_context"));
            mAlwaysScan.setType(PreferenceCompat.TYPE_SWITCH);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAlwaysScan);
        }
        mRecentLocationRequests = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_RECENT_LOCATION_REQUESTS);
        mRecentLocationRequests.setTitle(
                ResourcesUtil.getString(mContext, "location_category_recent_location_requests"));
        mRecentLocationRequests.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);
        List<RecentLocationApps.Request> recentLocationRequests =
                new RecentLocationApps(mContext).getAppList(true);
        List<PreferenceCompat> recentLocationPrefs = new ArrayList<>(recentLocationRequests.size());
        for (final RecentLocationApps.Request request : recentLocationRequests) {
            PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_RECENT_LOCATION_REQUESTS, request.packageName});
            pref.setIcon(request.icon);
            pref.setTitle(request.label.toString());
            // Most Android TV devices don't have built-in batteries and we ONLY show "High/Low
            // battery use" for devices with built-in batteries when they are not plugged-in.
            final BatteryManager batteryManager = (BatteryManager) mContext
                    .getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null && !batteryManager.isCharging()) {
                if (request.isHighBattery) {
                    pref.setSummary(ResourcesUtil.getString(mContext, "location_high_battery_use"));
                } else {
                    pref.setSummary(ResourcesUtil.getString(mContext, "location_low_battery_use"));
                }
            }
            pref.setNextState(STATE_APP_MANAGEMENT);
            Bundle nextStateExtras = new Bundle();
            AppManagementState.prepareArgs(nextStateExtras, request.packageName);
            pref.setExtras(nextStateExtras);
            recentLocationPrefs.add(pref);
        }
        if (recentLocationRequests.size() > 0) {
            addPreferencesSorted(recentLocationPrefs);
        } else {
            // If there's no item to display, add a "No recent apps" item.
            PreferenceCompat banner = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_RECENT_LOCATION_REQUESTS, KEY_NO_RECENT_APP});
            banner.setTitle(ResourcesUtil.getString(mContext, "location_no_recent_apps"));
            banner.setSelectable(false);
            mRecentLocationRequests.addChildPrefCompat(banner);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mRecentLocationRequests);

        mContext.registerReceiver(mReceiver,
                new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        mLocationMode.updateAndNotify();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectivity();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(mReceiver);
    }

    private void addPreferencesSorted(List<PreferenceCompat> prefs) {
        // If there's some items to display, sort the items and add them to the container.
        prefs.sort(Comparator.comparing(lhs -> lhs.getTitle()));
        for (PreferenceCompat entry : prefs) {
            mRecentLocationRequests.addChildPrefCompat(entry);
        }
    }

    private void updateConnectivity() {
        if (FlavorUtils.isTwoPanel(mContext)) {
            int scanAlwaysAvailable = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
            mAlwaysScan.setChecked(scanAlwaysAvailable == 1);
        }
    }

    @Override
    public int getStateIdentifier() {
        return STATE_LOCATION;
    }


    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        if (TextUtils.equals(key[0], KEY_LOCATION_MODE)) {
            int mode = Settings.Secure.LOCATION_MODE_OFF;
            if (TextUtils.equals((CharSequence) newValue, LOCATION_MODE_WIFI)) {
                mode = Settings.Secure.LOCATION_MODE_ON;
            } else if (TextUtils.equals((CharSequence) newValue, LOCATION_MODE_OFF)) {
                mode = Settings.Secure.LOCATION_MODE_OFF;
            } else {
                Log.wtf(TAG, "Tried to set unknown location mode!");
            }

            writeLocationMode(mode);
            mLocationMode.updateAndNotify();
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (key[0].equals(KEY_WIFI_ALWAYS_SCAN)) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    status ? 1 : 0);
            updateConnectivity();
            return true;
        }
        return false;
    }

    private void writeLocationMode(int mode) {
        int currentMode = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        Intent intent = new Intent(MODE_CHANGING_ACTION);
        intent.putExtra(CURRENT_MODE_KEY, currentMode);
        intent.putExtra(NEW_MODE_KEY, mode);
        mContext.sendBroadcast(intent, android.Manifest.permission.WRITE_SECURE_SETTINGS);
        mContext.getSystemService(LocationManager.class).setLocationEnabledForUser(
                mode != Settings.Secure.LOCATION_MODE_OFF,
                Process.myUserHandle());
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> preferenceControllers = new ArrayList<>();
        mLocationMode = new LocationModePC(mContext, mUIUpdateCallback, getStateIdentifier(),
                mPreferenceCompatManager);
        preferenceControllers.add(mLocationMode);
        return preferenceControllers;
    }
}
