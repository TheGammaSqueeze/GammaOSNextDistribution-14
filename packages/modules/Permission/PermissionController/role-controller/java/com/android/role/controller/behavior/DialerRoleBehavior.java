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

package com.android.role.controller.behavior;

import android.content.Context;
import android.os.UserHandle;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.model.Permissions;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.model.VisibilityMixin;
import com.android.role.controller.util.PackageUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Class for behavior of the dialer role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultPhonePreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultPhonePicker
 */
public class DialerRoleBehavior implements RoleBehavior {

    /**
     * Permissions to be granted if the application fulfilling the dialer role is also a system app.
     */
    private static final List<String> SYSTEM_DIALER_PERMISSIONS = Arrays.asList(
            android.Manifest.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE
    );

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        return telephonyManager.isVoiceCapable();
    }

    @Override
    public void grantAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (SdkLevel.isAtLeastS()) {
            if (PackageUtils.isSystemPackageAsUser(packageName, user, context)) {
                Permissions.grantAsUser(packageName, SYSTEM_DIALER_PERMISSIONS, false, false,
                        true, false, false, user, context);
            }
        }
    }

    @Override
    public void revokeAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (SdkLevel.isAtLeastS()) {
            Permissions.revokeAsUser(packageName, SYSTEM_DIALER_PERMISSIONS, true, false, false,
                    user, context);
        }
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDialerRole", true, user, context);
    }
}
