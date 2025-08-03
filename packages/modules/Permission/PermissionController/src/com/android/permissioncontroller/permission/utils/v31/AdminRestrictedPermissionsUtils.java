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

package com.android.permissioncontroller.permission.utils.v31;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.permission.utils.PermissionMapping;

/**
 * A class for dealing with permissions that the admin may not grant in certain configurations.
 */
public final class AdminRestrictedPermissionsUtils {
    /**
     * A set of permissions that the Profile Owner cannot grant and that the Device Owner
     * could potentially grant (depending on opt-out state).
     */
    private static final ArraySet<String> ADMIN_RESTRICTED_SENSORS_PERMISSIONS = new ArraySet<>();

    static {
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.CAMERA);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.ACTIVITY_RECOGNITION);
        ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.BODY_SENSORS);
        // New S permissions - do not add unless running on S and above.
        if (SdkLevel.isAtLeastS()) {
            ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.BACKGROUND_CAMERA);
            ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.RECORD_BACKGROUND_AUDIO);
        }
        // New T permissions - do not add unless running on T and above.
        if (SdkLevel.isAtLeastT()) {
            ADMIN_RESTRICTED_SENSORS_PERMISSIONS.add(Manifest.permission.BODY_SENSORS_BACKGROUND);
        }
    }

    /**
     * Returns true if the admin may grant this permission, false otherwise.
     */
    public static boolean mayAdminGrantPermission(Context context, String permission, int userId) {
        if (!SdkLevel.isAtLeastS()) {
            return true;
        }
        Context userContext = context.createContextAsUser(UserHandle.of(userId), /* flags= */0);
        DevicePolicyManager dpm = userContext.getSystemService(DevicePolicyManager.class);
        UserManager um = userContext.getSystemService(UserManager.class);
        if (um.isManagedProfile(userId) && Manifest.permission.READ_SMS.equals(permission)) {
            return mayManagedProfileAdminGrantReadSms(dpm);
        }
        if (!ADMIN_RESTRICTED_SENSORS_PERMISSIONS.contains(permission)) {
            return true;
        }

        return dpm.canAdminGrantSensorsPermissions();
    }

    /**
     * Returns true if the admin may grant this permission, false otherwise.
     */
    public static boolean mayAdminGrantPermission(String permission, String permissionGroup,
            boolean canAdminGrantSensorsPermissions, boolean isManagedProfile,
            DevicePolicyManager dpm) {
        if (!SdkLevel.isAtLeastS()) {
            return true;
        }
        if (isManagedProfile && Manifest.permission.READ_SMS.equals(permission)) {
            return mayManagedProfileAdminGrantReadSms(dpm);
        }
        boolean isAdminRestrictedSensorPermissionGroup = permissionGroup != null
                && PermissionMapping.getPlatformPermissionNamesOfGroup(permissionGroup).stream()
                .anyMatch(ADMIN_RESTRICTED_SENSORS_PERMISSIONS::contains);

        if (!ADMIN_RESTRICTED_SENSORS_PERMISSIONS.contains(permission)
                && !isAdminRestrictedSensorPermissionGroup) {
            return true;
        }

        return canAdminGrantSensorsPermissions;
    }

    private static boolean mayManagedProfileAdminGrantReadSms(DevicePolicyManager dpm) {
        return SdkLevel.isAtLeastU() && dpm.isOrganizationOwnedDeviceWithManagedProfile()
                && dpm.getManagedSubscriptionsPolicy().getPolicyType()
                == ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS;
    }
}
