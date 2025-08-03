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
import com.android.libraries.pcc.chronicle.test.data.Foobar
import com.android.libraries.pcc.chronicle.api.policy.builder.policy
import com.android.libraries.pcc.chronicle.api.policy.builder.target
import com.android.libraries.pcc.chronicle.api.policy.StorageMedium
import com.android.libraries.pcc.chronicle.api.policy.PolicyTarget
import com.android.libraries.pcc.chronicle.api.policy.PolicyRetention
import com.android.libraries.pcc.chronicle.api.FieldType
import com.android.libraries.pcc.chronicle.api.policy.UsageType
import com.android.libraries.pcc.chronicle.api.policy.builder.PolicyFieldBuilder
import com.android.libraries.pcc.chronicle.api.dataTypeDescriptor
import com.google.common.truth.Truth.assertThat
import java.time.Duration

/**
 * Test of building Chronicle [PolicyTarget] using [PolicyTargetBuilder].
 */
@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyTargetBuilderTest {

    val FOOBAR_DTD = dataTypeDescriptor("Foobar", Foobar::class) {
        "name" to FieldType.String
    }

    val FOOBAR_TARGET = target(dataTypeDescriptor = FOOBAR_DTD, maxAge = Duration.ofMinutes(15)) {
        retention(StorageMedium.RAM)
        "name" { rawUsage(UsageType.ANY) }
    }

    @Test
    fun testPolicyTargetBuilder() {
        // check all target fields for simple data structure
        assertThat(FOOBAR_TARGET.schemaName).isEqualTo("Foobar")
        // 15 minutes TTL
        assertThat(FOOBAR_TARGET.maxAgeMs).isEqualTo(900000)
        // RAM storage
        assertThat(FOOBAR_TARGET.retentions).containsExactly(
            PolicyRetention(StorageMedium.RAM)
        )
        // raw usage on a primitive fieldtype
        assertThat(FOOBAR_TARGET.fields).containsExactly(
            PolicyFieldBuilder(FOOBAR_DTD, listOf("name")).apply { rawUsage(UsageType.ANY) }.build()
        )
        assertThat(FOOBAR_TARGET.annotations).isEmpty()
    }
}