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

package com.android.bluetooth.util;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.util.List;

public final class DevicePolicyUtils {
    private static boolean isBluetoothWorkContactSharingDisabled(Context context) {
        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        final UserManager userManager = context.getSystemService(UserManager.class);
        final List<UserHandle> userHandleList = userManager.getAllProfiles();

        // Check each user.
        for (UserHandle uh : userHandleList) {
            if (!userManager.isManagedProfile(uh.getIdentifier())) {
                continue; // Not a managed user.
            }
            return dpm.getBluetoothContactSharingDisabled(uh);
        }
        // No managed profile, so this feature is disabled
        return true;
    }

    /**
     * Returns a URI to query all phones on the device. If a managed profile exists
     * and the policy allows, it will be a URI that supports the managed profile.
     */
    // TODO: Make primary profile can also support getBluetoothContactSharingDisabled()
    public static Uri getEnterprisePhoneUri(Context context) {
        return isBluetoothWorkContactSharingDisabled(context) ? Phone.CONTENT_URI
                : Phone.ENTERPRISE_CONTENT_URI;
    }
}
