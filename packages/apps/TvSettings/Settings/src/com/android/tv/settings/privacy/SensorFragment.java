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

package com.android.tv.settings.privacy;

import static android.hardware.SensorPrivacyManager.Sources.SETTINGS;
import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_HARDWARE;
import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;

import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorPrivacyManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Keep;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.device.apps.AppManagementFragment;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.widget.SwitchWithSoundPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * The microphone/camera settings screen in TV settings.
 * Allows the user to turn of the respective sensor.
 */
@Keep
public class SensorFragment extends SettingsPreferenceFragment {

    private static final String TAG = "SensorFragment";
    private static final boolean DEBUG = true;

    public static final String TOGGLE_EXTRA = "toggle";
    /** How many recent apps should be shown when the list is collapsed. */
    private static final int MAX_RECENT_APPS_COLLAPSED = 2;
    private List<Preference> mAllRecentAppPrefs;

    protected static final String SENSOR_TOGGLE_KEY = "sensor_toggle";
    private PrivacyToggle mToggle;
    protected SwitchWithSoundPreference mSensorToggle;
    private Preference mPhysicalPrivacyEnabledInfo;

    private SensorPrivacyManager mSensorPrivacyManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mPrivacyChangedListener =
            (sensor, enabled) -> updateSensorPrivacyState();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mSensorPrivacyManager = (SensorPrivacyManager)
                getContext().getSystemService(Context.SENSOR_PRIVACY_SERVICE);

        mToggle = (PrivacyToggle) getArguments().get(TOGGLE_EXTRA);
        if (mToggle == null) {
            throw new IllegalArgumentException("PrivacyToggle extra missing");
        }

        super.onCreate(savedInstanceState);
        getPreferenceManager().setPreferenceComparisonCallback(
                new PreferenceManager.SimplePreferenceComparisonCallback());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateSensorPrivacyState();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context themedContext = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(themedContext);

        screen.setTitle(mToggle.screenTitle);

        addPhysicalPrivacyEnabledInfo(screen, themedContext);
        addSensorToggleWithInfo(screen, themedContext);
        addHardwareToggle(screen, themedContext);
        addRecentAppsGroup(screen, themedContext);
        addPermissionControllerPreference(screen, themedContext);
        updateSensorPrivacyState();

        setPreferenceScreen(screen);
    }

    @Override
    public void onStart() {
        super.onStart();
        mSensorPrivacyManager.addSensorPrivacyListener(mToggle.sensor,
                mPrivacyChangedListener);
    }

    @Override
    public void onStop() {
        mSensorPrivacyManager.removeSensorPrivacyListener(mToggle.sensor, mPrivacyChangedListener);
        super.onStop();
    }

    protected void addHardwareToggle(PreferenceScreen screen, Context themedContext) {
        // no-op
    }

    protected void updateHardwareToggle() {
        // no-nop
    }

    private void addPhysicalPrivacyEnabledInfo(PreferenceScreen screen, Context themedContext) {
        mPhysicalPrivacyEnabledInfo = new Preference(themedContext);
        mPhysicalPrivacyEnabledInfo.setLayoutResource(
                R.layout.sensor_physical_privacy_enabled_info);
        mPhysicalPrivacyEnabledInfo.setSelectable(true);
        mPhysicalPrivacyEnabledInfo.setTitle(mToggle.physicalPrivacyEnabledInfoTitle);
        mPhysicalPrivacyEnabledInfo.setSummary(mToggle.physicalPrivacyEnabledInfoText);
        mPhysicalPrivacyEnabledInfo.setIcon(mToggle.physicalPrivacyEnabledIcon);

        // Use InfoFragment when using 2-panel settings
        if (FlavorUtils.getFlavor(getContext()) == FLAVOR_CLASSIC) {
            mPhysicalPrivacyEnabledInfo.setFragment(PhysicalPrivacyUnblockFragment.class.getName());
            mPhysicalPrivacyEnabledInfo.getExtras().putObject(
                    PhysicalPrivacyUnblockFragment.TOGGLE_EXTRA, mToggle);
        } else {
            mPhysicalPrivacyEnabledInfo.setFragment(
                    PhysicalPrivacyUnblockInfoFragment.class.getName());
            mPhysicalPrivacyEnabledInfo.getExtras().putObject(
                    PhysicalPrivacyUnblockInfoFragment.TOGGLE_EXTRA, mToggle);
        }

        screen.addPreference(mPhysicalPrivacyEnabledInfo);
    }

    /**
     * Adds the sensor toggle with an InfoFragment (in two-panel mode) or an info text below (in
     * one-panel mode).
     */
    private void addSensorToggleWithInfo(PreferenceScreen screen, Context themedContext) {
        mSensorToggle = new SwitchWithSoundPreference(themedContext);
        screen.addPreference(mSensorToggle);
        mSensorToggle.setKey(SENSOR_TOGGLE_KEY);
        mSensorToggle.setTitle(mToggle.toggleTitle);
        mSensorToggle.setSummary(R.string.sensor_toggle_description);
        mSensorToggle.setFragment(SensorToggleInfoFragment.class.getName());
        mSensorToggle.getExtras().putObject(SensorToggleInfoFragment.TOGGLE_EXTRA, mToggle);

        if (!FlavorUtils.isTwoPanel(themedContext)) {
            // Show the toggle info text beneath instead.
            Preference toggleInfo = new Preference(themedContext);
            toggleInfo.setLayoutResource(R.layout.sensor_toggle_info);
            toggleInfo.setSummary(mToggle.toggleInfoText);
            toggleInfo.setSelectable(false);
            screen.addPreference(toggleInfo);
        }
    }

    private void updateSensorPrivacyState() {
        boolean softwarePrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                TOGGLE_TYPE_SOFTWARE, mToggle.sensor);
        boolean physicalPrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                TOGGLE_TYPE_HARDWARE, mToggle.sensor);

        if (DEBUG) {
            Log.v(TAG,
                    "softwarePrivacyEnabled=" + softwarePrivacyEnabled + ", physicalPrivacyEnabled="
                            + physicalPrivacyEnabled);
        }
        // If privacy is enabled, the sensor access is turned off
        mSensorToggle.setChecked(!softwarePrivacyEnabled && !physicalPrivacyEnabled);
        mSensorToggle.setEnabled(!physicalPrivacyEnabled);
        mPhysicalPrivacyEnabledInfo.setVisible(physicalPrivacyEnabled);

        if (physicalPrivacyEnabled) {
            selectPreference(mPhysicalPrivacyEnabledInfo);
        }
        updateHardwareToggle();
    }

    private void selectPreference(Preference preference) {
        scrollToPreference(preference);
        if (getListView() instanceof VerticalGridView) {
            VerticalGridView listView = (VerticalGridView) getListView();
            PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) (listView.getAdapter());

            ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.post(() -> {
                        int position = adapter.getPreferenceAdapterPosition(preference);
                        listView.setSelectedPositionSmooth(position);
                    });
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            };
            listView.getViewTreeObserver().addOnPreDrawListener(listener);
        }
    }

    /**
     * Adds section that shows an expandable list of apps that have recently accessed the sensor.
     */
    private void addRecentAppsGroup(PreferenceScreen screen, Context themedContext) {
        // Create the Recently Accessed By section.
        PreferenceCategory recentRequests = new PreferenceCategory(themedContext);
        recentRequests.setTitle(R.string.recently_accessed_by_category);
        screen.addPreference(recentRequests);

        // Get recent accesses.
        List<RecentlyAccessedByUtils.App> recentApps = RecentlyAccessedByUtils.getAppList(
                themedContext, mToggle.appOps);
        if (DEBUG) Log.v(TAG, "recently accessed by " + recentApps.size() + " apps");

        // Create a preference for each access.
        mAllRecentAppPrefs = new ArrayList<>(recentApps.size());
        for (RecentlyAccessedByUtils.App app : recentApps) {
            if (DEBUG) Log.v(TAG, "last access: " + app.mLastAccess);
            Preference pref = new Preference(themedContext);
            pref.setTitle(app.mLabel);
            pref.setIcon(app.mIcon);
            pref.setFragment(AppManagementFragment.class.getName());
            AppManagementFragment.prepareArgs(pref.getExtras(), app.mPackageName);
            mAllRecentAppPrefs.add(pref);
        }

        for (int i = 0; i < MAX_RECENT_APPS_COLLAPSED; i++) {
            if (mAllRecentAppPrefs.size() > i) {
                recentRequests.addPreference(mAllRecentAppPrefs.get(i));
            }
        }
        if (mAllRecentAppPrefs.size() > MAX_RECENT_APPS_COLLAPSED) {
            Preference showAllRecent = new Preference(themedContext);
            showAllRecent.setTitle(R.string.recently_accessed_show_all);
            showAllRecent.setOnPreferenceClickListener(preference -> {
                preference.setVisible(false);
                for (int i = MAX_RECENT_APPS_COLLAPSED; i < mAllRecentAppPrefs.size(); i++) {
                    recentRequests.addPreference(mAllRecentAppPrefs.get(i));
                }
                return false;
            });
            recentRequests.addPreference(showAllRecent);
        }

        if (mAllRecentAppPrefs.size() == 0) {
            Preference banner = new Preference(themedContext);
            banner.setSummary(R.string.no_recent_sensor_accesses);
            banner.setSelectable(false);
            recentRequests.addPreference(banner);
        }
    }

    /**
     * Adds a preference that opens the overview of the PermissionGroup pertaining to the sensor.
     */
    private void addPermissionControllerPreference(PreferenceScreen screen, Context themedContext) {
        Preference openPermissionController = new Preference(themedContext);
        openPermissionController.setTitle(mToggle.appPermissionsTitle);
        Intent showSensorPermissions = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS);
        showSensorPermissions.putExtra(Intent.EXTRA_PERMISSION_NAME,
                mToggle.permissionsGroupName);
        openPermissionController.setIntent(showSensorPermissions);
        screen.addPreference(openPermissionController);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (SENSOR_TOGGLE_KEY.equals(preference.getKey())) {
            boolean physicalPrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                    TOGGLE_TYPE_HARDWARE, mToggle.sensor);
            if (!physicalPrivacyEnabled) {
                mSensorPrivacyManager.setSensorPrivacy(SETTINGS, mToggle.sensor,
                        !mSensorToggle.isChecked());
            }
            updateSensorPrivacyState();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
