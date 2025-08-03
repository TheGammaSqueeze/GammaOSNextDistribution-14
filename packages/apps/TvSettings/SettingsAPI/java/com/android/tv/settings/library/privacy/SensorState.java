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

import static android.hardware.SensorPrivacyManager.Sources.SETTINGS;

import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.ManagerUtil.STATE_SENSOR;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorPrivacyManager;
import android.os.Bundle;
import android.util.Log;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.device.apps.AppManagementState;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/**
 * Provide data for microphone/camera settings screen in TV settings.
 */
public class SensorState extends PreferenceControllerState {
    private static final String TAG = "SensorState";
    private static final boolean DEBUG = true;

    public static final String TOGGLE_EXTRA = "toggle";

    private static final String KEY_SENSOR_TOGGLE = "sensor_toggle";
    private static final String KEY_SENSOR_TOGGLE_INFO = "sensor_toggle_info";
    private static final String KEY_RECENT_REQUESTS = "recent_requests";
    private static final String KEY_NO_RECENT = "no_recent";
    private static final String KEY_OPEN_PERMISSION_CONTROLLER = "open_permission_controller";
    private PrivacyToggle mToggle;
    private PreferenceCompat mSensorToggle;
    private PreferenceCompat mSensorToggleInfo;
    private PreferenceCompat mRecentAppsCategory;
    private PreferenceCompat mOpenPermissionController;

    private SensorPrivacyManager mSensorPrivacyManager;

    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mPrivacyChangedListener =
            (sensor, enabled) -> {
                if (mSensorToggle != null) {
                    mSensorToggle.setChecked(!enabled);
                }
            };

    public SensorState(Context context,
            UIUpdateCallback callback) {

        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        mSensorPrivacyManager = (SensorPrivacyManager)
                mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);

        mToggle = (PrivacyToggle) extras.get(TOGGLE_EXTRA);
        if (mToggle == null) {
            throw new IllegalArgumentException("PrivacyToggle extra missing");
        }
        mUIUpdateCallback.notifyUpdateScreenTitle(getStateIdentifier(),
                ResourcesUtil.getString(mContext, mToggle.screenTitle));
        addSensorToggleWithInfo();
        addRecentAppsGroup();
        addPermissionControllerPreference();
        super.onCreate(extras);
    }

    /**
     * Adds the sensor toggle with an InfoFragment (in two-panel mode) or an info text below (in
     * one-panel mode).
     */
    private void addSensorToggleWithInfo() {
        mSensorToggle = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SENSOR_TOGGLE);
        mSensorToggle.setTitle(ResourcesUtil.getString(mContext, mToggle.toggleTitle));
        mSensorToggle.setType(PreferenceCompat.TYPE_SWITCH);
        mSensorToggle.setSummary(ResourcesUtil.getString(mContext, "sensor_toggle_description"));

        // If privacy is enabled, the sensor access is turned off
        mSensorToggle.setChecked(
                !mSensorPrivacyManager.isSensorPrivacyEnabled(mToggle.sensor));
        mSensorPrivacyManager.addSensorPrivacyListener(mToggle.sensor,
                mPrivacyChangedListener);

        if (!FlavorUtils.isTwoPanel(mContext)) {
            // Show the toggle info text beneath instead.
            mSensorToggleInfo = mPreferenceCompatManager.getOrCreatePrefCompat(
                    KEY_SENSOR_TOGGLE_INFO);
            mSensorToggleInfo.setSummary(ResourcesUtil.getString(mContext, mToggle.toggleInfoText));
            mSensorToggleInfo.setSelectable(false);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mSensorToggleInfo);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mSensorToggle);
    }

    /**
     * Adds section that shows an expandable list of apps that have recently accessed the sensor.
     */
    private void addRecentAppsGroup() {
        // Create the Recently Accessed By section.
        mRecentAppsCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_RECENT_REQUESTS);
        mRecentAppsCategory.setTitle(
                ResourcesUtil.getString(mContext, "recently_accessed_by_category"));
        mRecentAppsCategory.clearChildPrefCompats();
        // Get recent accesses.
        List<RecentlyAccessedByUtils.App> recentApps = RecentlyAccessedByUtils.getAppList(
                mContext, mToggle.appOps);
        if (DEBUG) Log.v(TAG, "recently accessed by " + recentApps.size() + " apps");

        // Create a preference for each access.
        for (RecentlyAccessedByUtils.App app : recentApps) {
            if (DEBUG) Log.v(TAG, "last access: " + app.mLastAccess);
            PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_RECENT_REQUESTS, app.mPackageName});
            pref.setTitle(app.mLabel.toString());
            pref.setIcon(app.mIcon);
            pref.setNextState(STATE_APP_MANAGEMENT);
            pref.setExtras(new Bundle());
            AppManagementState.prepareArgs(pref.getExtras(), app.mPackageName);
            mRecentAppsCategory.addChildPrefCompat(pref);
        }

        if (mRecentAppsCategory.getChildPrefsCount() == 0) {
            PreferenceCompat banner = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_RECENT_REQUESTS, KEY_NO_RECENT});
            banner.setSummary(ResourcesUtil.getString(mContext, "no_recent_sensor_accesses"));
            banner.setSelectable(false);
            mRecentAppsCategory.addChildPrefCompat(banner);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mRecentAppsCategory);
    }

    /**
     * Adds a preference that opens the overview of the PermissionGroup pertaining to the sensor.
     */
    private void addPermissionControllerPreference() {
        mOpenPermissionController = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_OPEN_PERMISSION_CONTROLLER);
        mOpenPermissionController.setTitle(
                ResourcesUtil.getString(mContext, mToggle.appPermissionsTitle));
        Intent showSensorPermissions = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS);
        showSensorPermissions.putExtra(Intent.EXTRA_PERMISSION_NAME,
                mToggle.permissionsGroupName);
        mOpenPermissionController.setIntent(showSensorPermissions);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mOpenPermissionController);
    }


    @Override
    public void onDestroy() {
        mSensorPrivacyManager.removeSensorPrivacyListener(mToggle.sensor, mPrivacyChangedListener);
        super.onDestroy();
    }

    @Override
    public int getStateIdentifier() {
        return STATE_SENSOR;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (KEY_SENSOR_TOGGLE.equals(key[0])) {
            mSensorPrivacyManager.setSensorPrivacy(SETTINGS, mToggle.sensor, !status);
            return true;
        }
        return super.onPreferenceTreeClick(key, status);
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}
