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
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.utils;

import android.util.EventLog;

import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup;
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission;

import java.util.List;

public final class SafetyNetLogger {

    // The log tag used by SafetyNet to pick entries from the event log.
    private static final int SNET_NET_EVENT_LOG_TAG = 0x534e4554;

    // Log tag for the result of permissions request.
    private static final String PERMISSIONS_REQUESTED = "individual_permissions_requested";

    // Log tag for the result of permissions toggling.
    private static final String PERMISSIONS_TOGGLED = "individual_permissions_toggled";

    private SafetyNetLogger() {
        /* do nothing */
    }

    /**
     * Log that permission groups have been requested for the purpose of safety net.
     *
     * <p>The groups might refer to different permission groups and different apps.
     *
     * @param packageName The name of the package for which permissions were requested
     * @param uid The uid of the package
     * @param groups The permission groups which were requested
     */
    public static void logPermissionsRequested(String packageName, int uid,
            List<LightAppPermGroup> groups) {
        EventLog.writeEvent(SNET_NET_EVENT_LOG_TAG, PERMISSIONS_REQUESTED, uid,
                buildChangedPermissionForPackageMessageNew(packageName, groups));
    }

    /**
     * Log that a permission group has been toggled for the purpose of safety net.
     *
     * @param group The group which was toggled. This group must represent the current state, not
     * the old state
     * @param logOnlyBackground Whether to log only background permissions, or foreground and
     * background
     */
    public static void logPermissionToggled(LightAppPermGroup group, boolean logOnlyBackground) {
        StringBuilder builder = new StringBuilder();
        buildChangedPermissionForGroup(group, logOnlyBackground, builder);
        EventLog.writeEvent(SNET_NET_EVENT_LOG_TAG, PERMISSIONS_TOGGLED,
                android.os.Process.myUid(), builder.toString());
    }

    /**
     * Log that a permission group has been toggled for the purpose of safety net. Logs both
     * background and foreground permissions.
     *
     * @param group The group which was toggled. This group must represent the current state, not
     * the old state
     */
    public static void logPermissionToggled(LightAppPermGroup group) {
        logPermissionToggled(group, false);
    }

    private static void buildChangedPermissionForGroup(LightAppPermGroup group,
            boolean logOnlyBackground, StringBuilder builder) {

        builder.append(group.getPackageInfo().getPackageName()).append(':');

        for (LightPermission permission: group.getPermissions().values()) {
            if (logOnlyBackground
                    && !group.getBackgroundPermNames().contains(permission.getName())) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(';');
            }

            builder.append(permission.getName()).append('|');
            builder.append(permission.isGranted()).append('|');
            builder.append(permission.getFlags());
        }
    }

    private static String buildChangedPermissionForPackageMessageNew(String packageName,
            List<LightAppPermGroup> groups) {
        StringBuilder builder = new StringBuilder();

        builder.append(packageName).append(':');
        for (LightAppPermGroup group: groups) {
            buildChangedPermissionForGroup(group, false, builder);
        }
        return builder.toString();
    }
}
