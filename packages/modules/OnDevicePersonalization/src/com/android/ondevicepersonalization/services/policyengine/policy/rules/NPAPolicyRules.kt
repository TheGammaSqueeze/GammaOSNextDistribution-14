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

package com.android.ondevicepersonalization.services.policyengine.policy.rules

import com.android.libraries.pcc.chronicle.api.policy.contextrules.PolicyContextRule
import com.android.libraries.pcc.chronicle.util.TypedMap

/**
 * Defines the [PolicyContextRule]s related to NPA (No Personalized Ads) settings.
 *
 * NPA is enabled by default.
 */
object UnicornAccount : PolicyContextRule {
    override val name: String = "UnicornAccount"
    override val operands: List<PolicyContextRule> = emptyList()
    override fun invoke(context: TypedMap): Boolean {
      return context[KidStatusEnabled] == true
    }
}

object UserOptsInLimitedAdsTracking : PolicyContextRule {
    override val name: String = "LimitedAdsTracking"
    override val operands: List<PolicyContextRule> = emptyList()
    override fun invoke(context: TypedMap): Boolean {
      return context[LimitedAdsTrackingEnabled] == true
    }
}
