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

package com.android.bedstead.harrier.policies;


import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CAN_BE_DELEGATED;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_PERMISSION_GRANT;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_RUNTIME_PERMISSIONS;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policies around setting the grant state of a SMS related permission.
 *
 * <p>This is used by
 * {@code DevicePolicyManager#setPermissionGrantState(ComponentName, String, String, int)} when
 * granting sms-related permissions.
 */
// TODO(198311372): Check if APPLIED_BY_PROFILE_OWNER_USER is expected
@EnterprisePolicy(
        dpc = {APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIED_BY_PROFILE_OWNER_USER
                | CAN_BE_DELEGATED | CANNOT_BE_APPLIED_BY_ROLE_HOLDER
        },
        delegatedScopes = DELEGATION_PERMISSION_GRANT,
        permissions = @EnterprisePolicy.Permission(
        appliedWith = MANAGE_DEVICE_POLICY_RUNTIME_PERMISSIONS,
        appliesTo = APPLIES_TO_OWN_USER))
public final class SetSmsPermissionGranted {
}
