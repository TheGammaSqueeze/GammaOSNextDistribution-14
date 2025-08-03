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
import com.android.libraries.pcc.chronicle.test.data.NestedPerson
import com.android.libraries.pcc.chronicle.test.data.NestedPet
import com.android.libraries.pcc.chronicle.api.policy.builder.policy
import com.android.libraries.pcc.chronicle.api.policy.builder.target
import com.android.libraries.pcc.chronicle.api.policy.builder.ConditionalUsage
import com.android.libraries.pcc.chronicle.api.policy.StorageMedium
import com.android.libraries.pcc.chronicle.api.policy.PolicyTarget
import com.android.libraries.pcc.chronicle.api.policy.PolicyField
import com.android.libraries.pcc.chronicle.api.FieldType
import com.android.libraries.pcc.chronicle.api.policy.UsageType
import com.android.libraries.pcc.chronicle.api.policy.builder.PolicyFieldBuilder
import com.android.libraries.pcc.chronicle.api.dataTypeDescriptor
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.test.assertFailsWith

/**
 * Test of building Chronicle [PolicyField] using [PolicyFieldBuilder].
 */
@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyFieldBuilderTest {

    val NESTED_PERSON_GENERATED_DTD = dataTypeDescriptor("NestedPerson", NestedPerson::class) {
        "name" to FieldType.String
        "age" to FieldType.Integer
        "pets" to dataTypeDescriptor("NestedPet", NestedPet::class) {
            "breed" to FieldType.String
        }
    }

    @Test
    fun testRawUsage() {
        val actual = PolicyFieldBuilder(null, listOf("foo"))
                .apply { rawUsage(UsageType.JOIN, UsageType.EGRESS) }
                .build()
        assertThat(actual.fieldPath).containsExactly("foo").inOrder()
        assertThat(actual.rawUsages).containsExactly(UsageType.JOIN, UsageType.EGRESS)
    }

    @Test
    fun testConditionalUsage() {
        val stringApi = PolicyFieldBuilder(null, listOf("foo"))
            .apply {
                conditionalUsage("bucketed", UsageType.JOIN, UsageType.EGRESS)
                conditionalUsage("truncatedToDays", UsageType.ANY)
            }
            .build()
        val enumApi = PolicyFieldBuilder(null, listOf("foo"))
            .apply {
                ConditionalUsage.Bucketed.whenever(UsageType.JOIN, UsageType.EGRESS)
                ConditionalUsage.TruncatedToDays.whenever(UsageType.ANY)
            }
            .build()

        setOf(stringApi, enumApi).forEach { actual ->
            assertThat(actual.annotations).isEmpty()
            assertThat(actual.fieldPath).containsExactly("foo").inOrder()
            assertThat(actual.rawUsages).isEmpty()
            assertThat(actual.redactedUsages)
                .containsExactly(
                "bucketed",
                setOf(UsageType.JOIN, UsageType.EGRESS),
                "truncatedToDays",
                setOf(UsageType.ANY)
                )
            assertThat(actual.subfields).isEmpty()
        }
    }

    @Test
    fun testPolicyFields() {
        val actual = PolicyFieldBuilder(NESTED_PERSON_GENERATED_DTD, listOf("person"))
            .apply { "name" { rawUsage(UsageType.EGRESS) } }
            .build()
        assertThat(actual.annotations).isEmpty()
        assertThat(actual.fieldPath).containsExactly("person").inOrder()
        assertThat(actual.rawUsages).isEmpty()
        assertThat(actual.redactedUsages).isEmpty()
        assertThat(actual.subfields).containsExactly(
            PolicyField(fieldPath = listOf("person", "name"), rawUsages = setOf(UsageType.EGRESS)),
        )
    }

    @Test
    fun testPolicyFieldsWithSubFields() {
        val actual = PolicyFieldBuilder(NESTED_PERSON_GENERATED_DTD, listOf("person"))
            .apply {
                "name" { rawUsage(UsageType.EGRESS) }
                "pets" { "breed" { rawUsage(UsageType.ANY) } }
            }
            .build()
        assertThat(actual.annotations).isEmpty()
        assertThat(actual.fieldPath).containsExactly("person").inOrder()
        assertThat(actual.rawUsages).isEmpty()
        assertThat(actual.redactedUsages).isEmpty()
        assertThat(actual.subfields).containsExactly(
            PolicyField(fieldPath = listOf("person", "name"), rawUsages = setOf(UsageType.EGRESS)),
            PolicyField(
                fieldPath = listOf("person", "pets"),
                subfields = listOf(
                    PolicyField(
                        fieldPath = listOf("person", "pets", "breed"),
                        rawUsages = setOf(UsageType.ANY),
                    )
                ),
            )
        )
    }

    @Test
    fun testInvalidFieldName() {
        val e = assertFailsWith<IllegalArgumentException> {
                PolicyFieldBuilder(NESTED_PERSON_GENERATED_DTD, listOf("persons"))
                .apply { "pet_typo" { "breed" { rawUsage(UsageType.ANY) } } }
                .build()
            }
        assertThat(e).hasMessageThat().contains("Field 'pet_typo' not found in '${NESTED_PERSON_GENERATED_DTD.name}'")
    }

    @Test
    fun testNonexistField() {
        val e = assertFailsWith<IllegalArgumentException> {
                PolicyFieldBuilder(NESTED_PERSON_GENERATED_DTD, listOf("persons"))
                .apply { "pets" { "breed" { "nonexist" { rawUsage(UsageType.ANY) } } } }
                .build()
            }
        assertThat(e).hasMessageThat().contains("Trying to lookup field 'nonexist' in a non-entity type.")
    }
}