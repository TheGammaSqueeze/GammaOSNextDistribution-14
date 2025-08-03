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

package com.android.tv.settings.device.apps.specialaccess;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.widget.SwitchWithSoundPreference;

/**
 * Settings screen for managing "All files access" permission
 */
@Keep
public class AllFilesAccess extends ManageAppOp {

    @Override
    public int getAppOpsOpCode() {
        return AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE;
    }

    @Override
    public String getPermission() {
        return Manifest.permission.MANAGE_EXTERNAL_STORAGE;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.all_files_access, null);
    }

    @NonNull
    @Override
    public Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        final TwoStatePreference switchPref = (SwitchWithSoundPreference) preference;
        switchPref.setTitle(entry.label);
        switchPref.setKey(entry.info.packageName);
        switchPref.setIcon(entry.icon);
        switchPref.setOnPreferenceChangeListener((pref, grant) -> {
            findEntriesUsingPackageName(entry.info.packageName)
                    .forEach(packageEntry -> setMode(entry, (Boolean) grant));
            return true;
        });
        switchPref.setSummary(getPreferenceSummary(entry));
        switchPref.setChecked(((PermissionState) entry.extraInfo).isAllowed());

        return switchPref;
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchWithSoundPreference(getPreferenceManager().getContext());
    }

    @NonNull
    @Override
    public PreferenceGroup getAppPreferenceGroup() {
        return getPreferenceScreen();
    }

    private CharSequence getPreferenceSummary(ApplicationsState.AppEntry entry) {
        if (entry.extraInfo instanceof PermissionState) {
            return getContext().getText(((PermissionState) entry.extraInfo).isAllowed()
                    ? R.string.app_permission_summary_allowed
                    : R.string.app_permission_summary_not_allowed);
        } else {
            return null;
        }
    }

    private void setMode(ApplicationsState.AppEntry entry, boolean grant) {
        getContext().getSystemService(AppOpsManager.class)
                .setMode(getAppOpsOpCode(), entry.info.uid, entry.info.packageName,
                        grant ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.APPS_SPECIAL_APP_ACCESS_ALL_FILES_ACCESS;
    }
}
