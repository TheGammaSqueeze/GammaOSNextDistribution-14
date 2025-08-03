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

package com.android.ondevicepersonalization.services.policyengine.api

import com.android.internal.annotations.VisibleForTesting;

import com.android.libraries.pcc.chronicle.analysis.DefaultChronicleContext
import com.android.libraries.pcc.chronicle.analysis.DefaultPolicySet
import com.android.libraries.pcc.chronicle.analysis.impl.ChroniclePolicyEngine
import com.android.libraries.pcc.chronicle.api.ConnectionProvider
import com.android.libraries.pcc.chronicle.api.flags.Flags
import com.android.libraries.pcc.chronicle.api.flags.FlagsReader
import com.android.libraries.pcc.chronicle.api.integration.DefaultChronicle
import com.android.libraries.pcc.chronicle.api.integration.DefaultDataTypeDescriptorSet
import com.android.libraries.pcc.chronicle.api.policy.DefaultPolicyConformanceCheck
import com.android.libraries.pcc.chronicle.api.policy.Policy
import com.android.libraries.pcc.chronicle.util.TypedMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [ChronicleManager] instance that connects to the Chronicle backend. */
class ChronicleManager private constructor (
    private val connectionProviders: Set<ConnectionProvider>,
    private val policies: Set<Policy>,
    private val connectionContext: TypedMap = TypedMap()
) {
    private val flags = MutableStateFlow(Flags())
    private val flagsReader: FlagsReader = object : FlagsReader {
        override val config: StateFlow<Flags> = this@ChronicleManager.flags
    }

    val chronicle = DefaultChronicle(
        chronicleContext =
            DefaultChronicleContext(
                connectionProviders = connectionProviders,
                processorNodes = emptySet(),
                policySet = DefaultPolicySet(policies),
                dataTypeDescriptorSet =
                    DefaultDataTypeDescriptorSet(
                    connectionProviders.map { it.dataType.descriptor }.toSet()
                ),
                connectionContext = connectionContext
            ),
        policyEngine = ChroniclePolicyEngine(),
        config =
            DefaultChronicle.Config(
                policyMode = DefaultChronicle.Config.PolicyMode.STRICT,
                policyConformanceCheck = DefaultPolicyConformanceCheck()
            ),
        flags = flagsReader
    )

    companion object {
        @JvmField
        @Volatile
        @VisibleForTesting
        var instance: ChronicleManager? = null

        @JvmOverloads
        @JvmStatic
        fun getInstance(connectionProviders: Set<ConnectionProvider>,
                policies: Set<Policy>,
                connectionContext: TypedMap = TypedMap()) =
            instance
                ?: synchronized(this) {
                    // double-checked locking
                    instance
                        ?: ChronicleManager(
                            connectionProviders, policies, connectionContext).also{ instance = it }
                }
    }

    fun failNewConnections(failNewConnections: Boolean) = updateFlags {
        it.copy(failNewConnections = failNewConnections)
    }

    private inline fun updateFlags(block: (Flags) -> Flags) {
        do {
            val before = flags.value
            val after = block(before)
        } while (!flags.compareAndSet(before, after))
    }
}
