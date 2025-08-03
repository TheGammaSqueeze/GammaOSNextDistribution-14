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

package com.android.libraries.pcc.chronicle.api.policy.builder

import com.android.libraries.pcc.chronicle.api.ChronicleHost
import com.android.libraries.pcc.chronicle.api.Flavor
import com.android.libraries.pcc.chronicle.api.error.PolicyNotFound
import com.android.libraries.pcc.chronicle.api.policy.Policy

/** A set of [Policy] for all the flavors this policy is shipped on. */
data class FlavoredPolicies(val name: String, val flavorPolicies: Map<Flavor, Policy>) {
  // Allows us to use the flavoredPolicies[flavor] on the top level [FlavoredPolicies] instance.
  operator fun get(flavor: Flavor): Policy? = flavorPolicies[flavor]

  fun getOrThrow(chronicleHost: ChronicleHost): Policy {
    val flavor = chronicleHost.flavor
    return this[flavor]
      ?: throw PolicyNotFound(
        "$name not allowed for use on build flavor=${flavor.name}. Missing " + "flavor() block?"
      )
  }
}
