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

package com.android.bedstead.harrier.test;

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;

import com.android.bedstead.harrier.PolicyArguments;
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

import java.util.Set;

/**
 * Policy for keyguard disable features
 *
 * See {@code DevicePolicyManager#setKeyguardDisabledFeatures(ComponentName, int)} for more
 * details.
 */
@EnterprisePolicy(
        dpc = APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER)
public final class TestPolicy extends PolicyArguments<Integer> {

    public static final int POLICY_ARGUMENT_ONE = 2;
    public static final int POLICY_ARGUMENT_TWO = 4;

    @Override
    public Set<Integer> validArguments() {
        return Set.of(
                POLICY_ARGUMENT_ONE,
                POLICY_ARGUMENT_TWO);
    }
}
