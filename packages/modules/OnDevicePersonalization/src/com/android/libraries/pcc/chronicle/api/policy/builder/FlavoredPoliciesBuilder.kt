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

import com.android.libraries.pcc.chronicle.api.DataTypeDescriptor
import com.android.libraries.pcc.chronicle.api.Flavor
import com.android.libraries.pcc.chronicle.api.policy.Policy
import com.android.libraries.pcc.chronicle.api.policy.PolicyConfig
import com.android.libraries.pcc.chronicle.api.policy.PolicyTarget
import com.android.libraries.pcc.chronicle.api.policy.contextrules.PolicyContextRule
import java.time.Duration

/**
 * Basic flavored policy builder that is the baseline all policies builders use. It can be
 * subclassed to offer mutators specific to a particular [PolicyType] or mutators that apply to
 * individual flavors in a [FlavorConfigurator].
 *
 * This process works by: 1) flavoredPolicies builder delegates to the correct
 * [FlavoredPoliciesBuilder] subclass based on the [PolicyType] value. 2) The user provides a
 * receiver func that makes the base policy by calling funcs that mutate the base [PolicyBuilder].
 * 3) The user calls flavors() passing in a FlavorConfigurator.() sub-block which further customizes
 * a clone of the base [PolicyBuilder]. That block is saved into the flavorPolicies map. 4) Finally
 * the whole thing is built into a FlavoredPolicies data class when buildAll() is called.
 */
abstract class FlavoredPoliciesBuilder<FlavorT : FlavorConfigurator>(
  private val name: String,
  private val egressType: String
) {
  protected val basePolicyBuilder = PolicyBuilder(name, egressType)
  private val flavorBlocks = mutableMapOf<Flavor, FlavorT.() -> Unit>()

  /** The human facing description of what this policy is for. */
  var description: String by basePolicyBuilder::description

  /** Specifies the contexts allowed for this policy */
  var allowedContext: PolicyContextRule by basePolicyBuilder::allowedContext

  /** Attaches required consent for collection or storage. */
  fun consentRequiredForCollectionOrStorage(consent: Consent) {
    basePolicyBuilder.configs[Consent.POLICY_CFG_SECTION_KEY] =
      mapOf("value" to consent.serializedValue)
  }

  /** A type that this policy allows. */
  fun target(
    schema: DataTypeDescriptor,
    maxAge: Duration,
    block: PolicyTargetBuilder.() -> Unit
  ): PolicyTarget {
    return basePolicyBuilder.target(schema, maxAge, block)
  }

  fun config(configName: String, block: PolicyConfigBuilder.() -> Unit): PolicyConfig {
    return basePolicyBuilder.config(configName, block)
  }

  /**
   * Includes the specified policy in [flavors]. Some [PolicyType] extend this with additional
   * options that can be configured per flavor.
   */
  fun flavors(vararg flavors: Flavor) {
    flavors.forEach { flavor -> setFlavor(flavor) {} }
  }

  /** Includes this policy on the specified flavors, with the selection variations. */
  fun flavors(vararg flavors: Flavor, flavorCfgBlock: FlavorT.() -> Unit) {
    flavors.forEach { flavor -> setFlavor(flavor, flavorCfgBlock) }
  }

  /** Builds the [FlavoredPolicies]. */
  fun buildAll(): FlavoredPolicies {
    val flavoredPolicies: Map<Flavor, Policy> =
      flavorBlocks.mapValues { (_, flavorBlock) ->
        // Clone the basePolicyBuilder and then let the flavor mutate it.
        val policyBuilder = PolicyBuilder(basePolicyBuilder)
        newFlavorConfigurator(policyBuilder).apply(flavorBlock)
        policyBuilder.build()
      }
    return FlavoredPolicies(name, flavoredPolicies)
  }

  /** Returns an instance of the FlavorConfigurator. */
  protected abstract fun newFlavorConfigurator(policyBuilder: PolicyBuilder): FlavorT

  /**
   * Ensures that each flavor is only configured once, preventing a malformed policy which specifies
   * conflicting variations for the same flavor.
   */
  private fun setFlavor(flavor: Flavor, flavorCfgBlock: FlavorT.() -> Unit) {
    require(!flavorBlocks.containsKey(flavor)) { "Flavor $flavor configured more than once." }
    flavorBlocks[flavor] = flavorCfgBlock
  }
}
