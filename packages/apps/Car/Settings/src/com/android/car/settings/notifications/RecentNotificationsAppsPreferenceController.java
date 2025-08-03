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

package com.android.car.settings.notifications;

import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.notification.NotifyingApp;
import android.text.format.DateUtils;

import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationDetailsFragment;
import com.android.car.settings.applications.ApplicationListItemManager;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * This controller displays a list of recently used apps. Only non-system apps are displayed.
 * This class is largely taken from
 * {@link com.android.settings.notification.RecentNotifyingAppsPreferenceController}
 */
public class RecentNotificationsAppsPreferenceController extends
        BaseNotificationsPreferenceController<PreferenceCategory> implements
        ApplicationListItemManager.AppListItemListener {

    private static final Logger LOG = new Logger(RecentNotificationsAppsPreferenceController.class);

    private static final String KEY_PLACEHOLDER = "app";

    @VisibleForTesting
    IUsageStatsManager mUsageStatsManager;

    private final Integer mUserId;
    private final int mRecentAppsMaxCount;
    private final int mDaysThreshold;
    private List<NotifyingApp> mApps;
    private ApplicationsState mApplicationsState;
    private NotificationsFragment.NotificationSwitchListener mNotificationSwitchListener;

    public RecentNotificationsAppsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUsageStatsManager = IUsageStatsManager.Stub.asInterface(
                ServiceManager.getService(Context.USAGE_STATS_SERVICE));
        mUserId = context.getUserId();
        mRecentAppsMaxCount = context.getResources()
                .getInteger(R.integer.recent_notifications_apps_list_count);
        mDaysThreshold = context.getResources()
                .getInteger(R.integer.recent_notifications_days_threshold);
    }

    public void setApplicationsState(ApplicationsState applicationsState) {
        mApplicationsState = applicationsState;
    }

    public void setNotificationSwitchListener(
            NotificationsFragment.NotificationSwitchListener listener) {
        mNotificationSwitchListener = listener;
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    public void onDataLoaded(ArrayList<ApplicationsState.AppEntry> apps) {
        // App entries updated, refresh since filtered apps may have changed
        refresh();
    }

    @Override
    public void updateState(PreferenceCategory preference) {
        super.updateState(preference);
        refresh();
    }

    private void refresh() {
        ThreadUtils.postOnBackgroundThread(() -> {
            reloadData();
            List<NotifyingApp> recentApps = getDisplayableRecentAppList();
            ThreadUtils.postOnMainThread(() -> {
                if (recentApps != null && !recentApps.isEmpty()) {
                    getPreference().setVisible(true);
                    displayRecentApps(recentApps);
                } else {
                    getPreference().setVisible(false);
                    getPreference().removeAll();
                }
            });
        });
    }

    private void reloadData() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -mDaysThreshold);
        UsageEvents events = null;
        try {
            events = mUsageStatsManager.queryEventsForUser(calendar.getTimeInMillis(),
                    System.currentTimeMillis(), mUserId, getContext().getPackageName());
        } catch (RemoteException e) {
            LOG.e("Failed querying user events", e);
        }

        if (events != null) {
            ArrayMap<String, NotifyingApp> aggregatedStats = new ArrayMap<>();

            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.NOTIFICATION_INTERRUPTION) {
                    NotifyingApp app =
                            aggregatedStats.get(getKey(mUserId, event.getPackageName()));
                    if (app == null) {
                        app = new NotifyingApp();
                        aggregatedStats.put(getKey(mUserId, event.getPackageName()), app);
                        app.setPackage(event.getPackageName());
                        app.setUserId(mUserId);
                    }
                    if (event.getTimeStamp() > app.getLastNotified()) {
                        app.setLastNotified(event.getTimeStamp());
                    }
                }
            }
            mApps = new ArrayList<>();
            mApps.addAll(aggregatedStats.values());
        }
    }

    private List<NotifyingApp> getDisplayableRecentAppList() {
        Collections.sort(mApps);
        List<NotifyingApp> displayableApps = new ArrayList<>(mRecentAppsMaxCount);
        int count = 0;
        for (NotifyingApp app : mApps) {
            try {
                ApplicationsState.AppEntry appEntry = mApplicationsState.getEntry(
                        app.getPackage(), app.getUserId());
                if (appEntry == null || isSystemApp(appEntry)) {
                    continue;
                }
                displayableApps.add(app);
                count++;
                if (count >= mRecentAppsMaxCount) {
                    break;
                }
            } catch (Exception e) {
                LOG.e("Failed to find app " + app.getPackage() + "/" + app.getUserId(), e);
            }
        }
        return displayableApps;
    }

    private void displayRecentApps(List<NotifyingApp> recentApps) {
        int keyIndex = 1;
        int recentAppsCount = recentApps.size();
        for (int i = 0; i < recentAppsCount; i++, keyIndex++) {
            NotifyingApp app = recentApps.get(i);
            // Bind recent apps to existing prefs if possible, or create a new pref.
            String pkgName = app.getPackage();
            ApplicationsState.AppEntry appEntry =
                    mApplicationsState.getEntry(app.getPackage(), app.getUserId());
            if (appEntry == null || appEntry.label == null) {
                continue;
            }

            CarUiTwoActionSwitchPreference pref = getPreference()
                    .findPreference(KEY_PLACEHOLDER + keyIndex);
            if (pref == null) {
                pref = new CarUiTwoActionSwitchPreference(getContext());
                pref.setKey(KEY_PLACEHOLDER + keyIndex);
                getPreference().addPreference(pref);
            }
            pref.setTitle(appEntry.label);
            pref.setIcon(appEntry.icon);
            pref.setSummary(DateUtils.getRelativeTimeSpanString(app.getLastNotified(),
                    System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
            pref.setOnPreferenceClickListener(p -> {
                getFragmentController().launchFragment(
                        ApplicationDetailsFragment.getInstance(pkgName));
                return true;
            });

            pref.setOnSecondaryActionClickListener((newValue) -> {
                toggleNotificationsSetting(pkgName, appEntry.info.uid, newValue);
                if (mNotificationSwitchListener != null) {
                    mNotificationSwitchListener.onSwitchChanged();
                }
            });
            pref.setSecondaryActionChecked(areNotificationsEnabled(pkgName, appEntry.info.uid));
        }
        // If there are less than SHOW_RECENT_APP_COUNT recent apps, remove placeholders
        for (int i = keyIndex; i <= mRecentAppsMaxCount; i++) {
            getPreference().removePreferenceRecursively(KEY_PLACEHOLDER + i);
        }
    }

    private String getKey(int userId, String pkg) {
        return userId + "|" + pkg;
    }

    /** Returns true if the app for the given package name is a system app for this device */
    private boolean isSystemApp(ApplicationsState.AppEntry appEntry) {
        return !ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT.filterApp(appEntry);
    }
}
