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

package com.android.role.controller.model;

import android.content.Context;
import android.content.res.Resources;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.role.controller.util.ResourceUtils;

/**
 * Mixin for {@link RoleBehavior#isVisibleAsUser(Role, UserHandle, Context)} that returns whether
 * the role should be visible from a corresponding boolean resource.
 */
public class VisibilityMixin {

    private static final String LOG_TAG = VisibilityMixin.class.getSimpleName();

    private VisibilityMixin() {}

    /**
     * Get the boolean resource value that represents whether a role is visible to the user.
     *
     * @param resourceName the name of the resource
     * @param isPermissionControllerResource if {@code true}, and if the current SDK level is at
     *        least V, get the resource from a PermissionController context for the given user.
     *        Otherwise, get the resource the provided context.
     * @param user the user to get the PermissionController context for
     * @param context the `Context` to retrieve the resource (and system services)
     *
     * @return whether this role should be visible to user
     */
    public static boolean isVisible(@NonNull String resourceName,
            boolean isPermissionControllerResource, @NonNull UserHandle user,
            @NonNull Context context) {
        Resources resources = isPermissionControllerResource
                ? ResourceUtils.getPermissionControllerResources(context) : context.getResources();
        String packageName = isPermissionControllerResource
                ? ResourceUtils.RESOURCE_PACKAGE_NAME_PERMISSION_CONTROLLER : "android";

        int resourceId = resources.getIdentifier(resourceName, "bool", packageName);
        if (resourceId == 0) {
            Log.w(LOG_TAG, "Cannot find resource for visibility: " + resourceName);
            return true;
        }
        try {
            return resources.getBoolean(resourceId);
        } catch (Resources.NotFoundException e) {
            Log.w(LOG_TAG, "Cannot get resource for visibility: " + resourceName, e);
            return true;
        }
    }
}
