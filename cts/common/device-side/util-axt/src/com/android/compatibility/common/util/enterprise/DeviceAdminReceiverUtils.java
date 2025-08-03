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
package com.android.compatibility.common.util.enterprise;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Helper class for {@link android.app.admin.DeviceAdminReceiver} implementations.
 */
public final class DeviceAdminReceiverUtils {

    private static final String TAG = DeviceAdminReceiverUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String ACTION_DISABLE_SELF = "disable_self";

    /**
     * Disables itself as profile / owner upon receiving a {@value #ACTION_DISABLE_SELF} intent.
     *
     * <p>This is useful during {@code CTS} development, so the admin can be disabled without a
     * factory reset, when for some reason the test fails to disable it.
     *
     * <p>Typical usage:
     * <pre><code>
       public void onReceive(Context context, Intent intent) {
            if (DeviceAdminReceiverUtils.disableSelf(context, intent)) return;
            super.onReceive(context, intent);
       }
     * </code></pre>
     *
     * <p>Then {@code adb shell am broadcast --user USER -a disable_self PACKAGE/RECEIVER}.
     * <b>Note:</b> caller needs the {@code BIND_DEVICE_ADMIN} permission, so you need to call
     * {@code adb root} first.
     *
     * @return whether the intent was processed or not.
     */
    public static boolean disableSelf(Context context, Intent intent) {
        String action = intent.getAction();
        int userId = context.getUserId();
        if (!action.equals(ACTION_DISABLE_SELF)) {
            if (DEBUG) Log.d(TAG, "Ignoring " + action + " for user " + userId);
            return false;
        }
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        String packageName = context.getPackageName();
        if (dpm.isDeviceOwnerApp(packageName)) {
            Log.i(TAG, "Disabling " + packageName + " as device owner for user " + userId);
            dpm.clearDeviceOwnerApp(packageName);
            if (DEBUG) Log.d(TAG, "Disabled");
        } else if (dpm.isProfileOwnerApp(packageName)) {
            ComponentName admin = dpm.getProfileOwner();
            Log.i(TAG, "Disabling " + admin.flattenToShortString() + " as profile owner for user "
                    + userId);
            dpm.clearProfileOwner(admin);
            if (DEBUG) Log.d(TAG, "Disabled");
        } else {
            Log.e(TAG, "Package " + packageName + " is neither device nor profile owner for user "
                    + userId);
        }
        return true;
    }

    private DeviceAdminReceiverUtils() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
