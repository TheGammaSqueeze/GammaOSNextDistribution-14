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

package com.android.bedstead.harrier.policies;

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_GLOBALLY;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_SECURITY_LOGGING;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_SECURITY_LOGGING;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policy for security logging which collects global logs.
 *
 * <p>This is used by {@code DevicePolicyManager#setSecurityLoggingEnabled},
 * {@code DevicePolicyManager#isSecurityLoggingEnabled},
 * {@code DevicePolicyManager#retrieveSecurityLogs},
 * and {@code DevicePolicyManager#retrievePreRebootSecurityLogs}.
 */
@EnterprisePolicy(dpc = APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY,
        delegatedScopes = DELEGATION_SECURITY_LOGGING)
public final class GlobalSecurityLogging {
}
