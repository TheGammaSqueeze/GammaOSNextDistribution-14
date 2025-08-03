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

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.nene.flags.CommonFlags.KEYGUARD_DISABLE_SECURE_CAMERA;

import com.android.bedstead.harrier.PolicyArguments;
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

import java.util.Collections;
import java.util.Set;

/**
 * Policy for keyguard disable features which are only available to parent instance of
 * organization owned profile owners
 *
 * See {@code DevicePolicyManager#setKeyguardDisabledFeatures(ComponentName, int)} for more
 * details.
 */
@EnterprisePolicy(
        dpc = APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE
                | CANNOT_BE_APPLIED_BY_ROLE_HOLDER
                | APPLIES_TO_OWN_USER)
public final class KeyguardDisableFeaturesForOrgOwnedParentProfileOwner
        extends PolicyArguments<Integer> {

    @Override
    public Set<Integer> validArguments() {
        return Collections.singleton(KEYGUARD_DISABLE_SECURE_CAMERA);
    }
}
