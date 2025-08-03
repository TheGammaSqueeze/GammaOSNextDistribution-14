/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.safetycenter.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.safetycenter.ui.SnakeCaseConverter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnakeCaseConverterTest {

    @Test
    fun fromCamelCase_withCamelCaseInput_returnsSnakeCaseString() {
        val input = "camelCase"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("camel_case")
    }

    @Test
    fun fromCamelCase_withEmptyInput_returnsEmptyString() {
        val input = ""

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("")
    }

    @Test
    fun fromCamelCase_withSnakeCaseInput_returnsSnakeCaseString() {
        val input = "snake_case"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("snake_case")
    }

    @Test
    fun fromCamelCase_withInputHavingUnderscores_preservesUnderscores() {
        val input = "camelCase_withUnderscores"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("camel_case_with_underscores")
    }

    @Test
    fun fromCamelCase_withPascalCaseInput_returnsSnakeCaseString() {
        val input = "PascalCase"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("pascal_case")
    }

    @Test
    fun fromCamelCase_withScreamingSnakeCaseInput_lowerCasesTheString() {
        val input = "SCREAMING_SNAKE_CASE"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("screaming_snake_case")
    }

    @Test
    fun fromCamelCase_withMixedCaseInput_returnsSnakeCaseString() {
        val input = "PascalCase_snake_case_camelCase"

        val result = SnakeCaseConverter.fromCamelCase(input)

        assertThat(result).isEqualTo("pascal_case_snake_case_camel_case")
    }
}
