/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.junit4

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.model.TestClass

/**
 * Test to make sure a [CustomizableParameterizedRunner] can provide its own parameters and pattern.
 */
@RunWith(CustomizableParameterizedParametersProviderTest.ParameterProvider::class)
@CustomParameters(1, 2, pattern = "{0}")
class CustomizableParameterizedParametersProviderTest {

    @get:Rule val testName = TestName()

    @JvmField @Parameter(0) var parameter: Int = -1

    @Test
    fun `Test that parameters are provided by the runner`() {
        // This method would not be run if the runner did not provide some parameters.
        assertThat(parameter).isGreaterThan(0)

        assertThat(testName.methodName)
            .isEqualTo("Test that parameters are provided by the runner[$parameter]")
    }

    class ParameterProvider(clazz: Class<*>) :
        CustomizableParameterizedRunner(clazz, ::mergeParameters) {
        companion object {
            private fun mergeParameters(
                testClass: TestClass,
                additionalParameters: List<Array<Any>>?,
            ): TestArguments {
                val customParameters = testClass.getAnnotation(CustomParameters::class.java)
                if (additionalParameters != null)
                    error("did not expect ${additionalParameters.size} additional parameters")
                return TestArguments(
                    pattern = customParameters.pattern,
                    argumentSets = customParameters.values.toList(),
                )
            }
        }
    }
}

/** Annotation used by [CustomizableParameterizedParametersProviderTest] to provide values. */
annotation class CustomParameters(vararg val values: Int, val pattern: String)
