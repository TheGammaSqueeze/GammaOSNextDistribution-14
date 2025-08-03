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

package com.android.tv.settings.library.device.apps.specialaccess;

import android.annotation.NonNull;
import android.app.Application;
import android.content.Context;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.device.apps.ApplicationsState;
import com.android.tv.settings.library.util.lifecycle.Lifecycle;
import com.android.tv.settings.library.util.lifecycle.LifecycleObserver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Create the list of {@link PreferenceCompat} based upon {@link ApplicationsState.AppEntry}
 */
public class ManageApplicationsController implements LifecycleObserver {
    /**
     * Use this preference key for a header pref not removed during refresh
     */
    public static final String HEADER_KEY = "header";

    private final Lifecycle mLifecycle;
    private final ApplicationsState.AppFilter mFilter;
    private final Comparator<ApplicationsState.AppEntry> mComparator;
    private final Callback mCallback;
    private final ApplicationsState.Session mAppSession;
    private final ApplicationsState mApplicationsState;
    private final int mStateIdentifier;
    private final UIUpdateCallback mUIUpdateCallback;

    private final ApplicationsState.Callbacks mAppSessionCallbacks =
            new ApplicationsState.Callbacks() {

                @Override
                public void onRunningStateChanged(boolean running) {
                    updateAppList();
                }

                @Override
                public void onPackageListChanged() {
                    updateAppList();
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                    updateAppList(apps);
                }

                @Override
                public void onPackageIconChanged() {
                    updateAppList();
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                    updateAppList();
                }

                @Override
                public void onAllSizesComputed() {
                    updateAppList();
                }

                @Override
                public void onLauncherInfoChanged() {
                    updateAppList();
                }

                @Override
                public void onLoadEntriesCompleted() {
                    updateAppList();
                }
            };

    public ManageApplicationsController(@NonNull Context context, int stateIdentifier,
            @NonNull Lifecycle lifecycle, ApplicationsState.AppFilter filter,
            Comparator<ApplicationsState.AppEntry> comparator, Callback callback,
            UIUpdateCallback uiUpdateCallback) {
        mStateIdentifier = stateIdentifier;
        lifecycle.addObserver(this);
        mLifecycle = lifecycle;
        mFilter = filter;
        mComparator = comparator;
        mApplicationsState = ApplicationsState.getInstance(
                (Application) context.getApplicationContext());
        mAppSession = mApplicationsState.newSession(mAppSessionCallbacks, mLifecycle);
        mCallback = callback;
        mUIUpdateCallback = uiUpdateCallback;
        updateAppList();
    }

    /**
     * Call this method to trigger the app list to refresh.
     */
    public void updateAppList() {
        ApplicationsState.AppFilter filter = new ApplicationsState.CompoundFilter(
                mFilter, ApplicationsState.FILTER_NOT_HIDE);
        ArrayList<ApplicationsState.AppEntry> apps = mAppSession.rebuild(filter, mComparator);
        if (apps != null) {
            updateAppList(apps);
        }
    }

    private void updateAppList(ArrayList<ApplicationsState.AppEntry> apps) {
        final List<PreferenceCompat> newList = new ArrayList<>(apps.size() + 1);
        for (final ApplicationsState.AppEntry entry : apps) {
            mApplicationsState.ensureIcon(entry);
            newList.add(mCallback.createAppPreference(entry));
        }
        newList.add(new PreferenceCompat(HEADER_KEY));
        if (newList.size() == 1) {
            newList.add(mCallback.getEmptyPreference());
        }
        mUIUpdateCallback.notifyUpdateAll(mStateIdentifier, newList);
    }

    /**
     * Callback interface for this class to manipulate the list of app preferences.
     */
    public interface Callback {
        /**
         * Create a new instance of a {@link PreferenceCompat} subclass to be used to display an
         * {@link ApplicationsState.AppEntry}
         *
         * @return New Preference object
         */
        @NonNull
        PreferenceCompat createAppPreference(ApplicationsState.AppEntry entry);

        /**
         * @return {@link PreferenceCompat} object to be used as an empty state placeholder
         */
        @NonNull
        PreferenceCompat getEmptyPreference();
    }
}

