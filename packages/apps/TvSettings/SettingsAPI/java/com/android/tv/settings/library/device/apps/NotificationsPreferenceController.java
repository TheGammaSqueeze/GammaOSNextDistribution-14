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

package com.android.tv.settings.library.device.apps;

import static com.android.tv.settings.library.PreferenceCompat.STATUS_OFF;
import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;

import android.app.INotificationManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.LibUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

/** Preference controller to handle notifications preference. */
public class NotificationsPreferenceController extends AbstractPreferenceController {
    private static final String TAG = "NotificationsPreference";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private final INotificationManager mNotificationManager;
    private ApplicationsState.AppEntry mAppEntry;

    public NotificationsPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            ApplicationsState.AppEntry appEntry, PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mNotificationManager = NotificationManager.getService();
        mAppEntry = appEntry;
    }

    /**
     * Set entry and refresh pref.
     *
     * @param entry entry
     */
    public void setEntry(@NonNull ApplicationsState.AppEntry entry) {
        mAppEntry = entry;
        updateAndNotify();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected void init() {
        mPreferenceCompat.setTitle(ResourcesUtil.getString(mContext,
                "device_apps_app_management_notifications"));
        update();
    }

    @Override
    public void update() {
        mPreferenceCompat.setEnabled(isBlockable(mContext, mAppEntry.info));
        try {
            mPreferenceCompat.setChecked(
                    mNotificationManager.areNotificationsEnabledForPackage(
                            mAppEntry.info.packageName, mAppEntry.info.uid));
        } catch (RemoteException e) {
            Log.d(TAG, "Remote exception while checking notifications for package "
                    + mAppEntry.info.packageName, e);
        }
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_NOTIFICATIONS};
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
            return LibUtils.isSystemPackage(
                    context.getResources(), context.getPackageManager(), info)
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

    public boolean handlePreferenceTreeClick(PreferenceCompat preferenceCompat, boolean status) {
        if (!(preferenceCompat.getEnabled() == STATUS_ON)) {
            return false;
        }
        return setNotificationsEnabled(status);

    }

    private boolean setNotificationsEnabled(boolean checked) {
        boolean result = true;
        byte status = checked ? STATUS_ON : STATUS_OFF;
        if (mPreferenceCompat.getChecked() != status) {
            try {
                mNotificationManager.setNotificationsEnabledForPackage(
                        mAppEntry.info.packageName, mAppEntry.info.uid, checked);
                mPreferenceCompat.setChecked(checked);
                mUIUpdateCallback.notifyUpdate(mStateIdentifier, mPreferenceCompat);
            } catch (RemoteException ex) {
                result = false;
            }
        }
        return result;
    }
}
