/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CAN_BE_DELEGATED;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_CERT_INSTALL;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policies around key management
 *
 * <p>This is used by methods such as
 * {@code DevicePolicyManager#installKeyPair(ComponentName, PrivateKey, Certificate, String)} and
 * {@code DevicePolicyManager#removeKeyPair(ComponentName, String)}.
 */
@EnterprisePolicy(dpc = {APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
        | CANNOT_BE_APPLIED_BY_ROLE_HOLDER,
        APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED
                | CANNOT_BE_APPLIED_BY_ROLE_HOLDER},
        delegatedScopes = DELEGATION_CERT_INSTALL)
public final class KeyManagementWithAdminReceiver {
}
