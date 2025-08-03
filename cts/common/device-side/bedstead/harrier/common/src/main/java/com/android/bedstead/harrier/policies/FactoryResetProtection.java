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
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_GLOBALLY;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_FACTORY_RESET;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policy for {@code DevicePolicyManager#setFactoryResetProtection} and
 * {@code DevicePolicyManager#getFactoryResetProtection}.
 */
@EnterprisePolicy(dpc =
        APPLIED_BY_DEVICE_OWNER | APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
                | APPLIES_GLOBALLY |  CANNOT_BE_APPLIED_BY_ROLE_HOLDER)
public final class FactoryResetProtection {
}
