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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.applications.ApplicationDetailsFragment;
import com.android.car.settings.applications.ApplicationListItemManager;
import com.android.car.settings.common.FragmentController;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;

/**
 * Controller for of list of preferences that enable / disable showing notifications for an
 * application.
 */
public class NotificationsAppListPreferenceController extends
        BaseNotificationsPreferenceController<PreferenceCategory> implements
        ApplicationListItemManager.AppListItemListener {

    private NotificationsFragment.NotificationSwitchListener mNotificationSwitchListener;

    public NotificationsAppListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
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
        getPreference().removeAll();
        for (ApplicationsState.AppEntry appEntry : apps) {
            getPreference().addPreference(
                    createPreference(appEntry.label, appEntry.icon, appEntry.info));
        }
    }

    private Preference createPreference(String title, Drawable icon, ApplicationInfo appInfo) {
        String packageName = appInfo.packageName;
        int uid = appInfo.uid;

        CarUiTwoActionSwitchPreference preference = createPreference();
        preference.setTitle(title);
        preference.setIcon(icon);
        preference.setKey(packageName);
        preference.setOnPreferenceClickListener(p -> onPrimaryActionClick(packageName));

        preference.setOnSecondaryActionClickListener((newValue) -> {
            onSecondaryActionClick(packageName, uid, newValue);
        });
        preference.setSecondaryActionChecked(areNotificationsEnabled(packageName, uid));
        preference.setSecondaryActionEnabled(areNotificationsChangeable(appInfo));

        return preference;
    }

    @VisibleForTesting
    CarUiTwoActionSwitchPreference createPreference() {
        return new CarUiTwoActionSwitchPreference(getContext());
    }

    @VisibleForTesting
    boolean onPrimaryActionClick(String packageName) {
        getFragmentController().launchFragment(
                ApplicationDetailsFragment.getInstance(packageName));
        return true;
    }
    @VisibleForTesting
    void onSecondaryActionClick(String packageName, int uid, boolean newValue) {
        toggleNotificationsSetting(packageName, uid, newValue);
        if (mNotificationSwitchListener != null) {
            mNotificationSwitchListener.onSwitchChanged();
        }
    }
}
