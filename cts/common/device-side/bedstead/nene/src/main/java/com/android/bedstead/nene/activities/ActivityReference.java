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

package com.android.bedstead.nene.activities;

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

/**
 * A representation of an activity on device which may or may not exist.
 */
public final class ActivityReference extends ComponentReference {
    public ActivityReference(Package packageName, String className) {
        super(packageName, className);
    }

    public ActivityReference(ComponentName component) {
        super(component);
    }

    public ActivityReference(ComponentReference component) {
        super(component.componentName());
    }

    /**
     * Is this activity enabled.
     */
    public boolean isEnabled(UserReference user) {
        // This currently uses activity-specific methods - it'd be great to have this at the top
        // level and work for any component
        try (PermissionContext p = TestApis.permissions()
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return TestApis.context().androidContextAsUser(user)
                    .getPackageManager()
                    .getActivityInfo(componentName(), MATCH_DISABLED_COMPONENTS)
                    .enabled;
        } catch (PackageManager.NameNotFoundException e) {
            throw new NeneException("Activity does not exist or is not activity " + this, e);
        }
    }

    /**
     * Is this activity enabled.
     */
    public boolean isEnabled() {
        return isEnabled(TestApis.users().instrumented());
    }

    /**
     * Is this activity exported.
     */
    public boolean isExported(UserReference user) {
        // This currently uses activity-specific methods - it'd be great to have this at the top
        // level and work for any component
        try {
            return TestApis.context().androidContextAsUser(user)
                    .getPackageManager()
                    .getActivityInfo(componentName(), 0)
                    .exported;
        } catch (PackageManager.NameNotFoundException e) {
            throw new NeneException("Activity does not exist or is not activity " + this, e);
        }
    }

    /**
     * Is this activity exported.
     */
    public boolean isExported() {
        return isExported(TestApis.users().instrumented());
    }

    @Override
    public String toString() {
        return "ActivityReference{component=" + super.toString() + "}";
    }
}
