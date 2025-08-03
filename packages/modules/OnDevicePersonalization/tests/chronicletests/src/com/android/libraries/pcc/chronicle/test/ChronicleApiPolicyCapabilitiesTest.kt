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
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capabilities
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Encryption
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Persistence
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Queryable
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Range
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Shareable
import com.android.libraries.pcc.chronicle.api.policy.capabilities.Capability.Ttl
import com.android.libraries.pcc.chronicle.api.policy.annotation.Annotation

/**
 * Test of [Capabilities.kt].
 */
@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyCapabilitiesTest {
    @Test
    fun testCapabilitiesEmpty() {
        assertThat(Capabilities().isEmpty).isTrue()
        assertThat(Capabilities.fromAnnotations(emptyList<Annotation>()).isEmpty).isTrue()
        assertThat(Capabilities(Persistence.ON_DISK).isEmpty).isFalse()
        assertThat(Capabilities(listOf(Persistence.ON_DISK)).isEmpty).isFalse()
    }

    @Test
    fun testCapabilitiesUnique() {
        assertFailsWith<IllegalArgumentException> {
        Capabilities(listOf(Ttl.Days(1).toRange(), Ttl.Hours(3)))
        }
    }

    @Test
    fun testCapabilitiesFromAnnotationsPersistent() {
        val persistent = Capabilities.fromAnnotation(Annotation.createCapability("persistent"))
        assertThat(persistent.persistence).isEqualTo(Persistence.ON_DISK)
        assertThat(persistent.isEncrypted).isNull()
        assertThat(persistent.ttl).isNull()
        assertThat(persistent.isQueryable).isNull()
        assertThat(persistent.isShareable).isNull()
    }

    @Test
    fun testCapabilitiesFromAnnotationsTtl() {
        val ttl30d = Capabilities.fromAnnotation(Annotation.createTtl("30d"))
        assertThat(ttl30d.persistence).isNull()
        assertThat(ttl30d.isEncrypted).isNull()
        assertThat(ttl30d.ttl).isEqualTo(Capability.Ttl.Days(30))
        assertThat(ttl30d.isQueryable).isNull()
        assertThat(ttl30d.isShareable).isNull()
    }

    @Test
    fun testCapabilitiesFromAnnotationsPersistentAndTtl() {
        val persistentAndTtl30d =
        Capabilities.fromAnnotations(
            listOf(Annotation.createCapability("persistent"), Annotation.createTtl("30d"))
        )
        assertThat(persistentAndTtl30d.persistence).isEqualTo(Persistence.ON_DISK)
        assertThat(persistentAndTtl30d.isEncrypted).isNull()
        assertThat(persistentAndTtl30d.ttl).isEqualTo(Capability.Ttl.Days(30))
        assertThat(persistentAndTtl30d.isQueryable).isNull()
        assertThat(persistentAndTtl30d.isShareable).isNull()
    }

    @Test
    fun testCapabilitiesFromAnnotationsQueryableAndEncrypted() {
        val queryableEncrypted =
        Capabilities.fromAnnotations(
            listOf(Annotation.createCapability("encrypted"), Annotation.createCapability("queryable"))
        )
        assertThat(queryableEncrypted.persistence).isNull()
        assertThat(queryableEncrypted.isEncrypted).isTrue()
        assertThat(queryableEncrypted.ttl).isNull()
        assertThat(queryableEncrypted.isQueryable).isTrue()
        assertThat(queryableEncrypted.isShareable).isNull()
    }

    @Test
    fun testCapabilitiesFromAnnotationsTiedToRuntimeAndTtl() {
        val tiedToRuntime = Capabilities.fromAnnotation(Annotation.createCapability("tiedToRuntime"))
        assertThat(tiedToRuntime.persistence).isEqualTo(Persistence.IN_MEMORY)
        assertThat(tiedToRuntime.isEncrypted).isNull()
        assertThat(tiedToRuntime.ttl).isNull()
        assertThat(tiedToRuntime.isQueryable).isNull()
        assertThat(tiedToRuntime.isShareable).isTrue()
    }

    @Test
    fun testCapabilitiesContains() {
        val capabilities = Capabilities(
            listOf<Capability.Range>(
                Persistence.ON_DISK.toRange(),
                Capability.Range(Capability.Ttl.Days(30), Capability.Ttl.Hours(1)),
                Capability.Queryable(true).toRange()
            )
        )
        assertThat(capabilities.contains(Persistence.ON_DISK)).isTrue()
        assertThat(capabilities.contains(Persistence.UNRESTRICTED)).isFalse()
        assertThat(capabilities.contains(Persistence.IN_MEMORY)).isFalse()
        assertThat(capabilities.contains(Capability.Ttl.Minutes(15))).isFalse()
        assertThat(capabilities.contains(Capability.Ttl.Hours(2))).isTrue()
        assertThat(capabilities.contains(Capability.Ttl.Days(30))).isTrue()
        assertThat(
            capabilities.contains(Capability.Range(Capability.Ttl.Days(20), Capability.Ttl.Hours(15)))
        )
        .isTrue()
        assertThat(capabilities.contains(Capability.Queryable(true))).isTrue()
        assertThat(capabilities.contains(Capability.Queryable(false))).isFalse()
        assertThat(capabilities.contains(Capability.Encryption(true))).isFalse()
        assertThat(capabilities.contains(Capability.Encryption(false))).isFalse()

        assertThat(capabilities.containsAll(capabilities)).isTrue()
        assertThat(
            capabilities.containsAll(
                Capabilities(
                    listOf<Capability.Range>(
                        Persistence.ON_DISK.toRange(),
                        Capability.Ttl.Days(10).toRange()
                    )
                )
            )
        )
        .isTrue()
        assertThat(
            capabilities.containsAll(
                Capabilities(
                    listOf<Capability.Range>(
                        Capability.Ttl.Days(10).toRange(),
                        Capability.Shareable(true).toRange()
                    )
                )
            )
        )
        .isFalse()
        assertThat(
            capabilities.containsAll(Capabilities(listOf<Capability.Range>(Capability.Queryable.ANY)))
        )
        .isFalse()
    }

    @Test
    fun testCapabilitiesIsEquivalent() {
        val capabilities = Capabilities(listOf(Capability.Range(Ttl.Days(10), Ttl.Days(2))))
        assertThat(capabilities.contains(Ttl.Days(5))).isTrue()
        assertThat(capabilities.contains(Capability.Range(Ttl.Days(9), Ttl.Days(2)))).isTrue()
        assertThat(capabilities.containsAll(Capabilities(listOf(Ttl.Days(5))))).isTrue()
        assertThat(
            capabilities.containsAll(Capabilities(listOf(Capability.Range(Ttl.Days(9), Ttl.Days(2)))))
        )
        .isTrue()
        assertThat(capabilities.isEquivalent(Capabilities(listOf(Ttl.Days(5))))).isFalse()
        assertThat(
            capabilities.isEquivalent(Capabilities(listOf(Capability.Range(Ttl.Days(9), Ttl.Days(2)))))
        )
        .isFalse()
        assertThat(capabilities.hasEquivalent(Capability.Range(Ttl.Days(10), Ttl.Days(2)))).isTrue()
        assertThat(
            capabilities.isEquivalent(Capabilities(listOf(Capability.Range(Ttl.Days(10), Ttl.Days(2)))))
        )
        .isTrue()
    }

    @Test
    fun testCapabilitiesIsEquivalentMultipleRanges() {
        val capabilities = Capabilities(listOf(Persistence.ON_DISK, Capability.Range(Ttl.Days(10), Ttl.Days(2))))
        assertThat(capabilities.contains(Ttl.Days(5))).isTrue()
        assertThat(capabilities.contains(Capability.Range(Ttl.Days(9), Ttl.Days(2)))).isTrue()
        assertThat(capabilities.containsAll(Capabilities(listOf(Ttl.Days(5))))).isTrue()
        assertThat(
            capabilities.containsAll(Capabilities(listOf(Capability.Range(Ttl.Days(9), Ttl.Days(2)))))
        )
        .isTrue()
        assertThat(capabilities.isEquivalent(Capabilities(listOf(Ttl.Days(5))))).isFalse()
        assertThat(
            capabilities.isEquivalent(Capabilities(listOf(Capability.Range(Ttl.Days(9), Ttl.Days(2)))))
        )
        .isFalse()
        assertThat(
            capabilities.isEquivalent(Capabilities(listOf(Capability.Range(Ttl.Days(10), Ttl.Days(2)))))
        )
        .isFalse()
        assertThat(capabilities.hasEquivalent(Capability.Range(Ttl.Days(10), Ttl.Days(2)))).isTrue()
        assertThat(capabilities.hasEquivalent(Persistence.IN_MEMORY)).isFalse()
        assertThat(capabilities.hasEquivalent(Persistence.ON_DISK)).isTrue()
        assertThat(capabilities.containsAll(Capabilities(listOf(Persistence.ON_DISK, Ttl.Days(10)))))
        .isTrue()
        assertThat(
            capabilities.containsAll(Capabilities(listOf(Persistence.ON_DISK, Encryption(true))))
        )
        .isFalse()
        assertThat(capabilities.isEquivalent(Capabilities(listOf(Persistence.ON_DISK, Ttl.Days(10)))))
        .isFalse()
        assertThat(
            capabilities.isEquivalent(
            Capabilities(listOf(Persistence.ON_DISK, Capability.Range(Ttl.Days(10), Ttl.Days(2))))
            )
        )
        .isTrue()
    }
}