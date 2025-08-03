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

package com.android.libraries.pcc.chronicle.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import com.android.libraries.pcc.chronicle.api.policy.Policy
import com.android.libraries.pcc.chronicle.api.policy.UsageType
import com.android.libraries.pcc.chronicle.api.policy.StorageMedium
import com.android.libraries.pcc.chronicle.api.policy.PolicyTarget
import com.android.libraries.pcc.chronicle.api.policy.PolicyField
import com.android.libraries.pcc.chronicle.api.policy.PolicyRetention
import com.google.common.truth.Truth.assertThat
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capabilities
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Encryption
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Persistence
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Ttl

/**
 * Test of Chronicle's [Policy] and its APIs.
 */
@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyTest {
    @Test
    fun testPolicyAllFields() {
        val child = PolicyField(listOf("parent", "child"))
        val parent = PolicyField(listOf("parent"), subfields = listOf(child))
        val other = PolicyField(listOf("other"))
        val policy = Policy(
            name = "MyPolicy",
            targets =
            listOf(
                PolicyTarget("target1", fields = listOf(parent)),
                PolicyTarget("target2", fields = listOf(other))
            ),
            egressType = "Logging"
        )

        assertThat(policy.allFields).containsExactly(child, parent, other)
    }

    @Test
    fun testPolicyAllRedactionLabels() {
        val child =
        PolicyField(
            fieldPath = listOf("parent", "child"),
            redactedUsages =
            mapOf("label1" to setOf(UsageType.EGRESS), "label2" to setOf(UsageType.JOIN))
        )
        val parent =
        PolicyField(
            fieldPath = listOf("parent"),
            subfields = listOf(child),
            redactedUsages =
            mapOf("label2" to setOf(UsageType.EGRESS), "label3" to setOf(UsageType.EGRESS))
        )
        val other =
        PolicyField(
            fieldPath = listOf("other"),
            redactedUsages = mapOf("label4" to setOf(UsageType.EGRESS))
        )
        val policy =
        Policy(
            name = "MyPolicy",
            targets =
            listOf(
                PolicyTarget("target1", fields = listOf(parent)),
                PolicyTarget("target2", fields = listOf(other))
            ),
            egressType = "Logging"
        )

        assertThat(policy.allRedactionLabels).containsExactly("label1", "label2", "label3", "label4")
    }

    @Test
    fun testPolicyTargetToCapabilities() {
        val policyTarget = PolicyTarget(
            schemaName = "schema",
            maxAgeMs = 2 * 60 * Ttl.MILLIS_IN_MIN, // 2 hours
            retentions =
            listOf(
                PolicyRetention(medium = StorageMedium.DISK, encryptionRequired = true),
                PolicyRetention(medium = StorageMedium.RAM)
            ),
            fields = emptyList(),
            annotations = emptyList()
        )
        val capabilities = policyTarget.toCapabilities()
        assertThat(capabilities).hasSize(2)
        assertThat(
            capabilities[0].isEquivalent(
                Capabilities(listOf(Persistence.ON_DISK, Encryption(true), Ttl.Minutes(120)))
            )
        )
        .isTrue()
        assertThat(
            capabilities[1].isEquivalent(Capabilities(listOf(Persistence.IN_MEMORY, Ttl.Minutes(120))))
        )
        .isTrue()
    }
}