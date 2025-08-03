/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.library.network;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

/**
 * Helper class that deals with Wi-fi configuration.
 */
public final class WifiHelper {
    private WifiHelper() {
    }

    /**
     * @param context Context of caller
     * @param config  The WiFi config.
     * @return true if Settings cannot modify the config due to lockDown.
     */
    public static boolean isNetworkLockedDown(Context context, WifiConfiguration config) {
        if (config == null) {
            return false;
        }

        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        final PackageManager pm = context.getPackageManager();
        final UserManager um = context.getSystemService(UserManager.class);

        // Check if device has DPM capability. If it has and dpm is still null, then we
        // treat this case with suspicion and bail out.
        if (pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) && dpm == null) {
            return true;
        }

        boolean isConfigEligibleForLockdown = false;
        if (dpm != null) {
            final ComponentName deviceOwner = dpm.getDeviceOwnerComponentOnAnyUser();
            if (deviceOwner != null) {
                final int deviceOwnerUserId = dpm.getDeviceOwnerUserId();
                try {
                    final int deviceOwnerUid = pm.getPackageUidAsUser(deviceOwner.getPackageName(),
                            deviceOwnerUserId);
                    isConfigEligibleForLockdown = deviceOwnerUid == config.creatorUid;
                } catch (PackageManager.NameNotFoundException e) {
                    // don't care
                }
            } else if (dpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                int profileOwnerUserId = getManagedProfileId(um, UserHandle.myUserId());
                final ComponentName profileOwner = dpm.getProfileOwnerAsUser(profileOwnerUserId);
                if (profileOwner != null) {
                    try {
                        final int profileOwnerUid = pm.getPackageUidAsUser(
                                profileOwner.getPackageName(), profileOwnerUserId);
                        isConfigEligibleForLockdown = profileOwnerUid == config.creatorUid;
                    } catch (PackageManager.NameNotFoundException e) {
                        // don't care
                    }
                }
            }
        }
        if (!isConfigEligibleForLockdown) {
            return false;
        }

        final ContentResolver resolver = context.getContentResolver();
        final boolean isLockdownFeatureEnabled = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) != 0;
        return isLockdownFeatureEnabled;
    }

    /**
     * Retrieves the id for the given user's  profile.
     *
     * @return the profile id or UserHandle.USER_NULL if there is none.
     */
    private static int getManagedProfileId(UserManager um, int parentUserId) {
        final int[] profileIds = um.getProfileIdsWithDisabled(parentUserId);
        for (int profileId : profileIds) {
            if (profileId != parentUserId && um.isManagedProfile(profileId)) {
                return profileId;
            }
        }
        return UserHandle.USER_NULL;
    }
}
