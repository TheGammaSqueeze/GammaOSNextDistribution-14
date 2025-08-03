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

package com.android.car.settings.applications;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.storage.AppStorageSettingsDetailsFragment;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;

/** Business logic for the storage entry in the application details settings. */
public class StoragePreferenceController extends PreferenceController<Preference> {

    private ApplicationsState mApplicationsState;
    private ApplicationsState.AppEntry mAppEntry;
    private ApplicationsState.Session mSession;
    private String mPackageName;

    @VisibleForTesting
    final ApplicationsState.Callbacks mApplicationStateCallbacks =
            new ApplicationsState.Callbacks() {
                @Override
                public void onRunningStateChanged(boolean running) {
                }

                @Override
                public void onPackageListChanged() {
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                }

                @Override
                public void onPackageIconChanged() {
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                    if (packageName.equals(mPackageName)) {
                        refreshUi();
                    }
                }

                @Override
                public void onAllSizesComputed() {
                    refreshUi();
                }

                @Override
                public void onLauncherInfoChanged() {
                }

                @Override
                public void onLoadEntriesCompleted() {
                }
            };

    public StoragePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }


    /** Sets the {@link ApplicationsState.AppEntry} which is used to load the app size. */
    public StoragePreferenceController setAppEntry(ApplicationsState.AppEntry appEntry) {
        mAppEntry = appEntry;
        return this;
    }

    /** Sets the {@link ApplicationsState} which is used to load the app size. */
    public StoragePreferenceController setAppState(ApplicationsState applicationsState) {
        mApplicationsState = applicationsState;
        return this;
    }

    /**
     * Set the packageName, which is used to open the AppStorageSettingsDetailsFragment
     */
    public StoragePreferenceController setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    @Override
    protected void checkInitialized() {
        if (mAppEntry == null || mApplicationsState == null || mPackageName == null) {
            throw new IllegalStateException(
                    "AppEntry, ApplicationsState and PackageName should be set before calling this "
                            + "function");
        }
    }

    @Override
    protected void onCreateInternal() {
        mSession = mApplicationsState.newSession(mApplicationStateCallbacks);
    }

    @Override
    protected void onStartInternal() {
        mSession.onResume();
    }

    @Override
    protected void onStopInternal() {
        mSession.onPause();
    }

    @Override
    protected void updateState(Preference preference) {
        refreshAppEntry();
        if (mAppEntry == null) {
            getFragmentController().goBack();
        } else if (mAppEntry.sizeStr == null) {
            preference.setSummary(
                    getContext().getString(R.string.memory_calculating_size));
        } else {
            preference.setSummary(
                    getContext().getString(R.string.storage_type_internal, mAppEntry.sizeStr));
        }
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().launchFragment(
                AppStorageSettingsDetailsFragment.getInstance(mPackageName));
        return true;
    }

    // TODO(b/201351382): Remove after SettingsLib investigation
    private void refreshAppEntry() {
        mAppEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());
    }
}
