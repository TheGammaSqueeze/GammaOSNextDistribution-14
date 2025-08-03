/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.device.apps.specialaccess;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.widget.SwitchWithSoundPreference;

/**
 * Fragment for managing which apps are allowed to turn the screen on
 */
@Keep
public class TurnScreenOn extends ManageAppOp {
    private static final String TAG = TurnScreenOn.class.getSimpleName();
    private static final boolean DEBUG = false;

    @Override
    public int getAppOpsOpCode() {
        return AppOpsManager.OP_TURN_SCREEN_ON;
    }

    @Override
    public String getPermission() {
        return Manifest.permission.TURN_SCREEN_ON;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.turn_screen_on, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAppList();
    }

    @NonNull
    @Override
    public Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        final TwoStatePreference switchPref = (SwitchWithSoundPreference) preference;
        switchPref.setTitle(entry.label);
        switchPref.setKey("package:" + entry.info.uid + ":" + entry.info.packageName);
        switchPref.setIcon(entry.icon);
        switchPref.setChecked(((PermissionState) entry.extraInfo).isAllowed());
        switchPref.setOnPreferenceChangeListener((pref, newValue) -> {
            findEntriesUsingPackageName(entry.info.packageName)
                    .forEach(packageEntry -> setTurnScreenOnMode(packageEntry, (Boolean) newValue));
            return true;
        });
        switchPref.setSummaryOn(R.string.app_permission_summary_allowed);
        switchPref.setSummaryOff(R.string.app_permission_summary_not_allowed);
        return switchPref;
    }

    private void setTurnScreenOnMode(ApplicationsState.AppEntry entry, boolean newValue) {
        int newMode = newValue ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED;
        if (DEBUG) {
            Log.d(TAG, "setting OP_TURN_SCREEN_ON to " + newMode
                    + ", uid=" + entry.info.uid
                    + ", packageName=" + entry.info.packageName
                    + ", userId=" + UserHandle.getUserId(entry.info.uid)
                    + ", currentUser=" + ActivityManager.getCurrentUser());
        }
        getContext().getSystemService(AppOpsManager.class).setMode(getAppOpsOpCode(),
                entry.info.uid, entry.info.packageName, newMode);
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchWithSoundPreference(getPreferenceManager().getContext());
    }

    @NonNull
    @Override
    public Preference getEmptyPreference() {
        final Preference empty = new Preference(getPreferenceManager().getContext());
        empty.setKey("empty");
        empty.setTitle(R.string.noApplications);
        empty.setEnabled(false);
        return empty;
    }

    @NonNull
    @Override
    public PreferenceGroup getAppPreferenceGroup() {
        return getPreferenceScreen();
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.APPS_SPECIAL_APP_ACCESS_TURN_SCREEN_ON;
    }
}
