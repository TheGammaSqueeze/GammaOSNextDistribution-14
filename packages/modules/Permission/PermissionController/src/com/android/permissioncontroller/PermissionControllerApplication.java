/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.ArrayMap;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.privacysources.SafetyCenterAccessibilityListener;
import com.android.permissioncontroller.role.model.RoleParserInitializer;
import com.android.permissioncontroller.role.ui.SpecialAppAccessListActivity;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.Roles;

public final class PermissionControllerApplication extends Application {

    private static PermissionControllerApplication sInstance;

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        PackageItemInfo.forceSafeLabels();
        RoleParserInitializer.initialize();
        updateSpecialAppAccessListActivityEnabledState();
        if (SdkLevel.isAtLeastT()) {
            addAccessibilityListener();
        }
        if (Utils.isHealthPermissionUiEnabled()) {
            KotlinUtils.INSTANCE.addHealthPermissions(this);
        }
    }

    /**
     * Statically gets the {@link PermissionControllerApplication} instance
     */
    public static PermissionControllerApplication get() {
        return sInstance;
    }

    private void updateSpecialAppAccessListActivityEnabledState() {
        ArrayMap<String, Role> roles = Roles.get(this);
        boolean hasVisibleSpecialAppAccess = false;
        int rolesSize = roles.size();
        for (int i = 0; i < rolesSize; i++) {
            Role role = roles.valueAt(i);

            if (!role.isAvailableAsUser(Process.myUserHandle(), this)
                    || !role.isVisibleAsUser(Process.myUserHandle(), this)) {
                continue;
            }
            if (!role.isExclusive()) {
                hasVisibleSpecialAppAccess = true;
                break;
            }
        }

        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, SpecialAppAccessListActivity.class);
        int enabledState = hasVisibleSpecialAppAccess
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, enabledState,
                PackageManager.DONT_KILL_APP);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void addAccessibilityListener() {
        AccessibilityManager a11yManager = Utils.getSystemServiceSafe(
                this, AccessibilityManager.class);
        a11yManager.addAccessibilityServicesStateChangeListener(
                new SafetyCenterAccessibilityListener(this));
    }

}
