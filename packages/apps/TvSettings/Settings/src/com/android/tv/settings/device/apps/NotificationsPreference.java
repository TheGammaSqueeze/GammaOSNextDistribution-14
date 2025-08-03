/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.device.apps;

import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.INotificationManager;
import android.app.NotificationManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.SwitchPreference;

import com.android.settingslib.Utils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

public class NotificationsPreference extends SwitchPreference {
    private static final String TAG = "NotificationsPreference";

    private final INotificationManager mNotificationManager;
    private ApplicationsState.AppEntry mEntry;

    public NotificationsPreference(Context context, ApplicationsState.AppEntry entry) {
        super(context);
        mEntry = entry;

        mNotificationManager = NotificationManager.getService();

        refresh();
    }

    /**
     * Set entry and refresh pref.
     *
     * @param entry entry
     */
    public void setEntry(@NonNull ApplicationsState.AppEntry entry) {
        mEntry = entry;
        refresh();
    }

    public void refresh() {
        setTitle(R.string.device_apps_app_management_notifications);
        setEnabled(isBlockable(getContext(), mEntry.info));
        try {
            super.setChecked(
                    mNotificationManager.areNotificationsEnabledForPackage(mEntry.info.packageName,
                            mEntry.info.uid));
        } catch (RemoteException e) {
            Log.d(TAG, "Remote exception while checking notifications for package "
                    + mEntry.info.packageName, e);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (!isEnabled()) {
            return;
        }
        logToggleInteracted(TvSettingsEnums.APPS_ALL_APPS_APP_ENTRY_NOTIFICATIONS, checked);
        if (setNotificationsEnabled(checked)) {
            super.setChecked(checked);
        }
    }

    private boolean setNotificationsEnabled(boolean enabled) {
        boolean result = true;
        if (isChecked() != enabled) {
            try {
                mNotificationManager.setNotificationsEnabledForPackage(
                        mEntry.info.packageName, mEntry.info.uid, enabled);
            } catch (RemoteException ex) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Returns if changes to notifications for an app should be allowed in TV settings. If the app
     * is NOT blockable disabling notifications for the app should be disallowed.
     */
    private boolean isBlockable(Context context, ApplicationInfo info) {
        final boolean blocked = getNotificationsBanned(info.packageName, info.uid);
        final boolean systemApp = isSystemApp(context, info);
        // allow Notifications setting change if not a system app
        // or if a system app, but somehow notifications are turned off atm
        return !systemApp || (systemApp && blocked);
    }

    private boolean getNotificationsBanned(String pkg, int uid) {
        try {
            final boolean enabled = mNotificationManager.areNotificationsEnabledForPackage(pkg,
                    uid);
            return !enabled;
        } catch (RemoteException e) {
            Log.w(TAG, "Error calling NotificationManager ", e);
            return false;
        }
    }

    /**
     * In this context a system app is either an actual system app or on the
     * config_nonBlockableNotificationPackages list of packages
     */
    private boolean isSystemApp(Context context, ApplicationInfo app) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    app.packageName, PackageManager.GET_SIGNATURES);
            return Utils.isSystemPackage(context.getResources(), context.getPackageManager(), info)
                    || isNonBlockablePackage(info);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isNonBlockablePackage(PackageInfo app) {
        try {
            return mNotificationManager.isImportanceLocked(app.packageName,
                    app.applicationInfo.uid);
        } catch (RemoteException e) {
            Log.w(TAG, "Error calling NotificationManager ", e);
        }
        return false;
    }
}
