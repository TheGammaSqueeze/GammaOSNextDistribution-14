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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manage and retrieve app entries based on current user.
 */
public class AllAppsSession {
    private static final String TAG = "AllAppsSession";
    private Set<String> mSystemAppPackages;
    private OnUpdateAppListListener mOnUpdateAppListListener;

    @ApplicationsState.SessionFlags
    private static final int SESSION_FLAGS =
            ApplicationsState.FLAG_SESSION_REQUEST_HOME_APP
                    | ApplicationsState.FLAG_SESSION_REQUEST_ICONS
                    | ApplicationsState.FLAG_SESSION_REQUEST_SIZES
                    | ApplicationsState.FLAG_SESSION_REQUEST_LEANBACK_LAUNCHER;

    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ApplicationsState.AppFilter mFilterAllApps;
    private ApplicationsState.AppFilter mFilterAllAppsExceptPreinstalled;
    private final AppFilterType mAppFilterType;

    private ArrayList<ApplicationsState.AppEntry> mAllApps = new ArrayList<>();

    private static final ApplicationsState.AppFilter PREDEFINED_FILTER_ALL_APPS =
            new ApplicationsState.AppFilter() {
                @Override
                public void init() {
                }

                @Override
                public boolean filterApp(ApplicationsState.AppEntry info) {
                    return info.info != null;
                }
            };

    private final ApplicationsState.AppFilter mPredefinedFilterAllAppsExceptPreinstalled =
            new ApplicationsState.AppFilter() {
                @Override
                public void init() {
                }

                @Override
                public boolean filterApp(ApplicationsState.AppEntry info) {
                    return info.info != null
                            && info.info.enabled
                            && info.hasLauncherEntry
                            && info.launcherEntryEnabled
                            && !((info.info.privateFlags & ApplicationInfo.PRIVATE_FLAG_OEM)
                            == ApplicationInfo.PRIVATE_FLAG_OEM)
                            && !((info.info.privateFlags & ApplicationInfo.PRIVATE_FLAG_VENDOR)
                            == ApplicationInfo.PRIVATE_FLAG_VENDOR)
                            && !((info.info.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRODUCT)
                            == ApplicationInfo.PRIVATE_FLAG_PRODUCT)
                            && !mSystemAppPackages.contains(info.info.packageName);
                }
            };

    public enum AppFilterType {
        ALL_APPS,
        ALL_APPS_EXCEPT_PREINSTALLED
    }

    public AllAppsSession(Context context, String volumeUuid, String volumeName,
            ApplicationsState applicationsState, Lifecycle lifecycle, AppFilterType appFilterType) {
        mSystemAppPackages = Arrays.stream(context.getResources()
                .getStringArray(R.array.system_app_packages)).collect(Collectors.toSet());
        mApplicationsState = applicationsState;
        mAppFilterType = appFilterType;

        // The UUID of internal storage is null, so we check if there's a volume name to see if we
        // should only be showing the apps on the internal storage or all apps.
        if (!TextUtils.isEmpty(volumeUuid) || !TextUtils.isEmpty(volumeName)) {
            ApplicationsState.AppFilter volumeFilter =
                    new ApplicationsState.VolumeFilter(volumeUuid);

            mFilterAllApps =
                    new ApplicationsState.CompoundFilter(PREDEFINED_FILTER_ALL_APPS, volumeFilter);
            mFilterAllAppsExceptPreinstalled =
                    new ApplicationsState.CompoundFilter(mPredefinedFilterAllAppsExceptPreinstalled,
                            volumeFilter);
        } else {
            mFilterAllApps = PREDEFINED_FILTER_ALL_APPS;
            mFilterAllAppsExceptPreinstalled = mPredefinedFilterAllAppsExceptPreinstalled;
        }

        mSession = mApplicationsState.newSession(new RowUpdateCallbacks(), lifecycle);
        mSession.setSessionFlags(SESSION_FLAGS);
    }

    /**
     * A listener to responding app list updating event.
     */
    public interface OnUpdateAppListListener {
        /**
         * It will be called when the app list was updated.
         *
         * @param entries of the app list.
         */
        void onUpdateAppList(@NonNull ArrayList<ApplicationsState.AppEntry> entries);
    }

    public void setOnUpdateAppListListener(@Nullable OnUpdateAppListListener listener) {
        mOnUpdateAppListListener = listener;
    }

    public ArrayList<ApplicationsState.AppEntry> getAllApps() {
        return mAllApps;
    }

    private ApplicationsState.AppFilter getFilter() {
        switch (mAppFilterType) {
            case ALL_APPS:
                return mFilterAllApps;
            case ALL_APPS_EXCEPT_PREINSTALLED:
                return mFilterAllAppsExceptPreinstalled;
            default:
                break;
        }
        return null;
    }

    private void rebuildAllApps() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSession.rebuild(getFilter(), ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(apps);
        }
    }

    private void updateAppList(ArrayList<ApplicationsState.AppEntry> entries) {
        mAllApps = filterAppsInstalledInParentProfile(entries);
        // Sort the list by app size on descent.
        mAllApps.sort((c1, c2) -> (c1.size < c2.size ? 1 : c1.size == c2.size ? 0 : -1));
        if (mOnUpdateAppListListener != null) {
            mOnUpdateAppListListener.onUpdateAppList(mAllApps);
        }
    }

    private ArrayList<ApplicationsState.AppEntry> filterAppsInstalledInParentProfile(
            @Nullable ArrayList<ApplicationsState.AppEntry> appEntries) {
        if (appEntries == null) {
            return new ArrayList<>();
        } else {
            return appEntries.stream().filter(appEntry ->
                    UserHandle.getUserId(appEntry.info.uid) == UserHandle.myUserId())
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private class RowUpdateCallbacks implements ApplicationsState.Callbacks {
        @Override
        public void onRunningStateChanged(boolean running) {
            doRebuild();
        }

        @Override
        public void onPackageListChanged() {
            doRebuild();
        }

        @Override
        public void onPackageIconChanged() {
            doRebuild();
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            doRebuild();
        }

        @Override
        public void onAllSizesComputed() {
            doRebuild();
        }

        @Override
        public void onLauncherInfoChanged() {
            doRebuild();
        }

        @Override
        public void onLoadEntriesCompleted() {
            doRebuild();
        }

        @Override
        public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
            updateAppList(apps);
        }

        private void doRebuild() {
            rebuildAllApps();
        }
    }
}
