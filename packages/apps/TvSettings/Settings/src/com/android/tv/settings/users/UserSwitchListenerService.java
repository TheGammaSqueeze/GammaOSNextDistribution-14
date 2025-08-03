/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.users;

import android.app.ActivityManager;
import android.app.Service;
import android.app.UserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

public class UserSwitchListenerService extends Service {

    private static final boolean DEBUG = false;
    private static final String TAG = "RestrictedProfile";

    private static final String RESTRICTED_PROFILE_LAUNCHER_ENTRY_ACTIVITY =
            "com.android.tv.settings.users.RestrictedProfileActivityLauncherEntry";
    private static final String SHARED_PREFERENCES_NAME = "RestrictedProfileSharedPreferences";
    private static final String
            ON_BOOT_USER_ID_PREFERENCE = "UserSwitchOnBootBroadcastReceiver.userId";

    private final UserSwitchObserver mUserSwitchObserver = new UserSwitchObserver() {
        @Override
        public void onUserSwitchComplete(int newUserId) {
            if (DEBUG) {
                Log.d(TAG, "user has been foregrounded: " + newUserId);
            }
            setBootUser(UserSwitchListenerService.this, newUserId);
        }
    };

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            int bootUserId = getBootUser(context);
            if (DEBUG) {
                Log.d(TAG, "boot completed, user is " + UserHandle.myUserId()
                        + " boot user id: " + bootUserId);
            }
            if (UserManager.get(context).isSystemUser()) {
                if (UserHandle.myUserId() != bootUserId) {
                    switchUserNow(bootUserId);
                }
            }
            onUserCreatedOrDeleted(context);
        }
    }

    /** The UserSwitchListenerService is only ever needed when there is a restricted profile. */
    private static boolean hasRestrictedProfile(Context context) {
        return new RestrictedProfileModel(context).getUser() != null;
    }

    /**
     * Enable or disable the restricted profile launcher entry activity as well as the
     * {@link UserSwitchListenerService} depending on whether there is a restricted profile.
     */
    public static void onUserCreatedOrDeleted(final Context context) {
        final boolean restrictedProfile = hasRestrictedProfile(context);
        if (DEBUG) {
            Log.d(TAG, "updating restricted profile : " + restrictedProfile);
        }

        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context,
                        RESTRICTED_PROFILE_LAUNCHER_ENTRY_ACTIVITY), restrictedProfile
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        if (restrictedProfile) {
            context.startServiceAsUser(new Intent(context, UserSwitchListenerService.class),
                    UserHandle.SYSTEM);
        } else {
            context.stopServiceAsUser(new Intent(context, UserSwitchListenerService.class),
                    UserHandle.SYSTEM);
        }
    }

    static void setBootUser(Context context, int userId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE).edit();
        editor.putInt(ON_BOOT_USER_ID_PREFERENCE, userId);
        editor.apply();
    }

    static int getBootUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        return prefs.getInt(ON_BOOT_USER_ID_PREFERENCE, UserHandle.USER_SYSTEM);
    }

    private static void switchUserNow(int userId) {
        try {
            ActivityManager.getService().switchUser(userId);
        } catch (RemoteException re) {
            Log.e(TAG, "Caught exception while switching user! ", re);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ActivityManager.getService().registerUserSwitchObserver(
                    mUserSwitchObserver,
                    UserSwitchListenerService.class.getName());
        } catch (RemoteException e) {
            Log.e(TAG, "Caught exception while registering UserSwitchObserver", e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            ActivityManager.getService().unregisterUserSwitchObserver(mUserSwitchObserver);
        } catch (RemoteException e) {
            // Not much we can do here
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasRestrictedProfile(this)) {
            stopSelf();
            Log.w(TAG, "no restricted profiles found! Immediately finishing "
                    + UserSwitchListenerService.class.getSimpleName());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
