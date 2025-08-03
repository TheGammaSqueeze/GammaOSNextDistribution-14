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

package com.android.tv.settings.device.storage;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.device.apps.AppManagementFragment;
import com.android.tv.settings.device.apps.AppsActivity;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Fragment for listing and managing all installed apps on the device, except pre-installed
 * (including updated) or system apps .
 */
@Keep
public class StorageUninstallAppsFragment extends SettingsPreferenceFragment implements
        AllAppsSession.OnUpdateAppListListener {
    private static final String TAG = "StorageUninstallAppsFragment";
    private static final String KEY_ALL_APPS_PREFERENCE_GROUP = "AllAppsPreferenceGroup";

    private AllAppsSession mAllAppsSession;

    private PreferenceGroup mAllAppsPreferenceGroup;

    private final Handler mHandler = new Handler(/*callback=*/null, /*async=*/false);
    private final Map<PreferenceGroup,
            ArrayList<ApplicationsState.AppEntry>> mUpdateMap = new ArrayMap<>(3);
    private long mRunAt = Long.MIN_VALUE;
    private final Runnable mUpdateRunnable = () -> {
        for (final PreferenceGroup group : mUpdateMap.keySet()) {
            final ArrayList<ApplicationsState.AppEntry> entries = mUpdateMap.get(group);
            updateAppListInternal(group, entries);
        }
        mUpdateMap.clear();
        mRunAt = 0;
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ApplicationsState applicationsState = ApplicationsState.getInstance(
                getActivity().getApplication());

        final String volumeUuid = getArguments().getString(AppsActivity.EXTRA_VOLUME_UUID);
        final String volumeName = getArguments().getString(AppsActivity.EXTRA_VOLUME_NAME);
        mAllAppsSession = new AllAppsSession(getActivity(), volumeUuid, volumeName,
                applicationsState, getLifecycle(),
                AllAppsSession.AppFilterType.ALL_APPS_EXCEPT_PREINSTALLED);
        mAllAppsSession.setOnUpdateAppListListener(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.storage_uninstall_apps, null);
        mAllAppsPreferenceGroup = (PreferenceGroup) findPreference(
                KEY_ALL_APPS_PREFERENCE_GROUP);
    }

    @Override
    public void onUpdateAppList(@NonNull ArrayList<ApplicationsState.AppEntry> entries) {
        if (mAllAppsPreferenceGroup == null) {
            Log.d(TAG, "Not updating list for null group");
            return;
        }
        mUpdateMap.put(mAllAppsPreferenceGroup, entries);

        // We can get spammed with updates, so coalesce them to reduce jank and flicker
        if (mRunAt == Long.MIN_VALUE) {
            // First run, no delay
            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.post(mUpdateRunnable);
        } else {
            if (mRunAt == 0) {
                mRunAt = SystemClock.uptimeMillis() + 1000;
            }
            int delay = (int) (mRunAt - SystemClock.uptimeMillis());
            delay = delay < 0 ? 0 : delay;

            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.postDelayed(mUpdateRunnable, delay);
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_STORAGE_FREE_UP_STORAGE_UNINSTALL_APPS;
    }

    private void updateAppListInternal(PreferenceGroup group,
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            final Set<String> touched = new ArraySet<>(entries.size());
            group.removeAll();
            for (int i = 0; i < entries.size(); i++) {
                final ApplicationsState.AppEntry entry = entries.get(i);
                final String packageName = entry.info.packageName;
                final Preference newPref = bindPreference(
                        new Preference(getPreferenceManager().getContext()), entry);
                newPref.setOrder(i);
                group.addPreference(newPref);
                touched.add(packageName);
            }
            for (int i = 0; i < group.getPreferenceCount(); ) {
                final Preference pref = group.getPreference(i);
                if (touched.contains(pref.getKey())) {
                    i++;
                } else {
                    group.removePreference(pref);
                }
            }
        }
    }

    /**
     * Creates or updates a preference according to an {@link ApplicationsState.AppEntry} object
     *
     * @param preference If non-null, updates this preference object, otherwise creates a new one
     * @param entry      Info to populate preference
     * @return Updated preference entry
     */
    private Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        preference.setKey(entry.info.packageName);
        entry.ensureLabel(getContext());
        preference.setTitle(entry.label);
        preference.setSummary(entry.sizeStr);
        preference.setFragment(AppManagementFragment.class.getName());
        AppManagementFragment.prepareArgs(preference.getExtras(), entry.info.packageName);
        preference.setIcon(entry.icon);
        return preference;
    }
}
