/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.behavior;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.telecom.TelecomManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.model.EncryptionUnawareConfirmationMixin;
import com.android.role.controller.model.Role;

import java.util.Objects;

/***
 * Class for UI behavior of Dialer role
 */
public class DialerRoleUiBehavior implements RoleUiBehavior {

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        String systemPackageName = telecomManager.getSystemDialerPackage();
        if (Objects.equals(applicationInfo.packageName, systemPackageName)) {
            preference.setSummary(R.string.default_app_system_default);
        } else {
            preference.setSummary(null);
        }
    }

    @Nullable
    @Override
    public CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return EncryptionUnawareConfirmationMixin.getConfirmationMessage(role, packageName,
                context);
    }
}
