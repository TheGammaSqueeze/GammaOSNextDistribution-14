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
import android.text.format.Formatter;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.device.apps.AppsActivity;

/**
 * Fragment for listing options to free up storage.
 */
@Keep
public class FreeUpStorageFragment extends SettingsPreferenceFragment {
    private static final String KEY_CLEAR_CACHED_DATA = "ClearCachedData";
    private static final String KEY_STORAGE_UNINSTALL_APPS = "StorageUninstallApps";
    private AllAppsSession mAllAppsSessionExceptPreinstalled;
    private ClearCachedDataPreference mClearCachedDataPreference;
    private Preference mStorageUninstallAppsPreference;
    private boolean mAppsListCollected = false;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.free_up_storage, null);
        mStorageUninstallAppsPreference = findPreference(KEY_STORAGE_UNINSTALL_APPS);
        mStorageUninstallAppsPreference.setSummary(getContext().getString(R.string.computing_size));
        mAppsListCollected = false;

        final ApplicationsState applicationsState = ApplicationsState.getInstance(
                getActivity().getApplication());

        final String volumeUuid = getArguments().getString(AppsActivity.EXTRA_VOLUME_UUID);
        final String volumeName = getArguments().getString(AppsActivity.EXTRA_VOLUME_NAME);
        final AllAppsSession allAppsSession = new AllAppsSession(getActivity(), volumeUuid,
                volumeName, applicationsState, getLifecycle(),
                AllAppsSession.AppFilterType.ALL_APPS);
        mAllAppsSessionExceptPreinstalled = new AllAppsSession(getActivity(),
                volumeUuid,
                volumeName, applicationsState, getLifecycle(),
                AllAppsSession.AppFilterType.ALL_APPS_EXCEPT_PREINSTALLED);
        mAllAppsSessionExceptPreinstalled.setOnUpdateAppListListener((apps) -> {
            updateStorageUninstallAppsPrefSummary();
            mAppsListCollected = true;
        });

        mClearCachedDataPreference = findPreference(KEY_CLEAR_CACHED_DATA);
        mClearCachedDataPreference.initialize(allAppsSession, applicationsState,
                getContext().getPackageManager(), this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAppsListCollected) {
            updateStorageUninstallAppsPrefSummary();
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_STORAGE_FREE_UP_STORAGE;
    }

    private void updateStorageUninstallAppsPrefSummary() {
        if (mStorageUninstallAppsPreference != null && mAllAppsSessionExceptPreinstalled != null) {
            final long size = mAllAppsSessionExceptPreinstalled.getAllApps().stream().map(
                    app -> app.size).reduce(0L,
                    (subtotal, element) -> subtotal + (element > 0 ? element : 0));
            mStorageUninstallAppsPreference.setSummary(
                    Formatter.formatFileSize(getActivity(), size));
        }
    }
}
