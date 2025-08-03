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
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.INHERITABLE;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_INPUT_METHODS;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policy for setting permitted input methods - with the restriction that it only permits setting
 * either system apps enabled or all apps enabled (with no option to specify particular apps).
 *
 * <p>See {@code DevicePolicyManager#setPermittedInputMethods(ComponentName, List<String>)}
 * for more detail.
 */
@EnterprisePolicy(dpc = APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER
        | CANNOT_BE_APPLIED_BY_ROLE_HOLDER | INHERITABLE)
public class PermittedSystemInputMethods {
}
