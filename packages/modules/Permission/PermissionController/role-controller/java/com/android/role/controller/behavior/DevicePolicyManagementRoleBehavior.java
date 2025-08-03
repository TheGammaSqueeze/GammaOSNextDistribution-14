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

package com.android.role.controller.behavior;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;

/**
 * Class for behavior of the device policy management role.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class DevicePolicyManagementRoleBehavior implements RoleBehavior {

    @Override
    public Boolean shouldAllowBypassingQualification(@NonNull Role role,
                                                     @NonNull Context context) {
        DevicePolicyManager devicePolicyManager =
                context.getSystemService(DevicePolicyManager.class);
        return devicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
    }
}
