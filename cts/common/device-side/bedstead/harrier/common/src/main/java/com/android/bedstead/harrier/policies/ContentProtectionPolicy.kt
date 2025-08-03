/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.bedstead.harrier.policies

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy

/**
 * Policy for controlling content protection.
 *
 * Users of this policy are `DevicePolicyManager#getContentProtectionPolicy(ComponentName)`
 * and `DevicePolicyManager#setContentProtectionPolicy(ComponentName, int)`.
 */
@EnterprisePolicy(
        dpc = [
            EnterprisePolicy.APPLIED_BY_DEVICE_OWNER
                    or EnterprisePolicy.APPLIED_BY_PROFILE_OWNER
                    or EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER
                    or EnterprisePolicy.APPLIES_TO_OWN_USER
        ]
)
class ContentProtectionPolicy
