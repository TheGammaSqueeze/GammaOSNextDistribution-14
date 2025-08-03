/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.notifications.BaseNotificationsPreferenceController;

/**
 * Controller for preference which enables / disables showing notifications for an application.
 */
public class NotificationsPreferenceController extends
        BaseNotificationsPreferenceController<TwoStatePreference> {

    private static final Logger LOG = new Logger(NotificationsPreferenceController.class);

    private String mPackageName;
    private int mUid;
    private ApplicationInfo mAppInfo;

    public NotificationsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /**
     * Set the package info of the application.
     */
    public void setPackageInfo(PackageInfo packageInfo) {
        mPackageName = packageInfo.packageName;
        mUid = packageInfo.applicationInfo.uid;
        mAppInfo = packageInfo.applicationInfo;
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(areNotificationsEnabled(mPackageName, mUid));
        preference.setEnabled(areNotificationsChangeable(mAppInfo));
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean enabled = (boolean) newValue;
        return toggleNotificationsSetting(mPackageName, mUid, enabled);
    }
}
