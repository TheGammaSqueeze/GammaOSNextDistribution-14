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
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import com.android.libraries.pcc.chronicle.api.policy.Policy
import com.android.libraries.pcc.chronicle.test.data.Foobar
import com.android.libraries.pcc.chronicle.test.data.SimpleData
import com.android.libraries.pcc.chronicle.api.policy.builder.policy
import com.android.libraries.pcc.chronicle.api.policy.builder.target
import com.android.libraries.pcc.chronicle.api.policy.StorageMedium
import com.android.libraries.pcc.chronicle.api.policy.PolicyTarget
import com.android.libraries.pcc.chronicle.api.policy.PolicyField
import com.android.libraries.pcc.chronicle.api.policy.PolicyRetention
import com.android.libraries.pcc.chronicle.api.FieldType
import com.android.libraries.pcc.chronicle.api.policy.UsageType
import com.android.libraries.pcc.chronicle.api.policy.builder.PolicyConfigBuilder
import com.android.libraries.pcc.chronicle.api.dataTypeDescriptor
import com.google.common.truth.Truth.assertThat
import java.time.Duration

/**
 * Test of building Chronicle [Policy] using [PolicyBuilder].
 */
@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyBuilderTest {

    val FOOBAR_DTD = dataTypeDescriptor("Foobar", Foobar::class) {
        "name" to FieldType.String
    }

    val SIMPLE_DATA_DTD = dataTypeDescriptor("SimpleData", SimpleData::class) {
        "a" to FieldType.Integer
        "b" to FieldType.Integer
    }

    val MY_POLICY = policy("MyPolicy", "Any") {
        description = "Testing policy builder"
        target(dataTypeDescriptor = FOOBAR_DTD, maxAge = Duration.ofMinutes(15)) {
            retention(StorageMedium.RAM)
            "name" { rawUsage(UsageType.ANY) }
        }

        target(dataTypeDescriptor = SIMPLE_DATA_DTD, maxAge = Duration.ofDays(2)) {
            retention(StorageMedium.DISK, encryptionRequired = true)
            "a" { rawUsage(UsageType.ANY) }
            "b" { rawUsage(UsageType.JOIN) }
        }

        config("test") { "key" to "value" }
    }

    @Test
    fun testPolicyBuilder() {
        assertThat(MY_POLICY.name).isEqualTo("MyPolicy")
        assertThat(MY_POLICY.egressType).isEqualTo("Any")
        // should be two targets
        assertThat(MY_POLICY.targets).containsExactly(
            target(dataTypeDescriptor = FOOBAR_DTD, maxAge = Duration.ofMinutes(15)) {
                retention(StorageMedium.RAM)
                "name" { rawUsage(UsageType.ANY) }
            },
            target(dataTypeDescriptor = SIMPLE_DATA_DTD, maxAge = Duration.ofDays(2)) {
                retention(StorageMedium.DISK, encryptionRequired = true)
                "a" { rawUsage(UsageType.ANY) }
                "b" { rawUsage(UsageType.JOIN) }
            }
        )
        // one config key-value pair
        assertThat(MY_POLICY.configs).containsExactly(
            "test",
            PolicyConfigBuilder().apply { "key" to "value" }.build()
        )
    }
}