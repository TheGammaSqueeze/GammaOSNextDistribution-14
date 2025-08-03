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

package com.android.tools.metalava

import org.junit.Assert.assertEquals
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [AnnotationFilter].
 *
 * There are two sets of inputs to testing [AnnotationFilter.matches], the sets of patterns used to
 * build the filter and the sets of annotations used to test the filter. There are also some
 * additional methods that need testing which have at least one of those two sets.
 *
 * This test is organized by having one test method for each method or annotations against which the
 * filter is created and parameterized by the set of patterns and the expected results of those
 * patterns in each test method. That ensures comprehensive test coverage at the expense of some
 * duplication of tests.
 */
@RunWith(Parameterized::class)
class AnnotationFilterTest(private val params: Params) {

    data class Params(
        val name: String,
        val patterns: List<String>,
        val expectedError: String? = null,
        val expectedIncludedAnnotationNames: Set<String> = emptySet(),
        val expectedEmpty: Boolean = false,
        val expectedMatchesSimple: Boolean = false,
        val expectedMatchesImplicitValue: Boolean = false,
        val expectedMatchesNamedValue: Boolean = false,
        val expectedMatchesNamedOther: Boolean = false,
        val expectedMatchesAnnotationName: Boolean = expectedMatchesSimple,
        val expectedMatchesOtherAnnotationName: Boolean = false,
        val expectedMatchesSuffix: Boolean = false,
    ) {
        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                Params(
                    name = "empty",
                    patterns = emptyList(),
                    expectedEmpty = true,
                ),
                Params(
                    name = "simple",
                    patterns = listOf("test.pkg.Annotation"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = true,
                    expectedMatchesImplicitValue = true,
                    expectedMatchesNamedValue = true,
                    expectedMatchesNamedOther = true,
                ),
                Params(
                    name = "simple-plus-parentheses",
                    patterns = listOf("test.pkg.Annotation()"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = true,
                    expectedMatchesImplicitValue = true,
                    expectedMatchesNamedValue = true,
                    expectedMatchesNamedOther = true,
                ),
                Params(
                    name = "simple-suffix-plus-value",
                    patterns = listOf("test.pkg.AnnotationSuffix(2)"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.AnnotationSuffix"),
                    expectedMatchesSuffix = true,
                ),
                Params(
                    name = "other-suffix-plus-value",
                    patterns = listOf("other.OtherAnnotationSuffix(2)"),
                    expectedIncludedAnnotationNames = setOf("other.OtherAnnotationSuffix"),
                    expectedMatchesSuffix = true,
                ),
                Params(
                    name = "implicit-value",
                    patterns = listOf("""test.pkg.Annotation("value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesImplicitValue = true,
                    expectedMatchesNamedValue = true,
                    expectedMatchesAnnotationName = true,
                ),
                Params(
                    name = "named-value",
                    patterns = listOf("""test.pkg.Annotation(value = "value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesImplicitValue = true,
                    expectedMatchesNamedValue = true,
                    expectedMatchesAnnotationName = true,
                ),
                Params(
                    name = "other-value",
                    patterns = listOf("""test.pkg.Annotation(other = "value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesAnnotationName = true,
                ),
                Params(
                    name = "everything-but-simple",
                    patterns =
                        listOf(
                            """test.pkg.Annotation(value = "value")""",
                            """test.pkg.Annotation(other = "other")""",
                            "other.OtherAnnotation",
                        ),
                    // This should probably be a sorted set not a list.
                    expectedIncludedAnnotationNames =
                        setOf(
                            "other.OtherAnnotation",
                            "test.pkg.Annotation",
                        ),
                    expectedMatchesImplicitValue = true,
                    expectedMatchesNamedValue = true,
                    expectedMatchesNamedOther = true,
                    expectedMatchesAnnotationName = true,
                    expectedMatchesOtherAnnotationName = true,
                ),
                Params(
                    name = "excluding-no-attributes",
                    patterns = listOf("!test.pkg.Annotation"),
                    expectedError =
                        "Exclude pattern '!test.pkg.Annotation' is invalid as it does not specify attributes",
                ),
                Params(
                    name = "excluding-no-excluding",
                    patterns =
                        listOf(
                            """!test.pkg.Annotation(value = "value")""",
                            """!test.pkg.Annotation(value = "other")""",
                        ),
                    expectedError =
                        "Patterns for 'test.pkg.Annotation' contains 2 excludes but no includes",
                ),
                Params(
                    name = "excluding-more-specific-first",
                    patterns =
                        listOf(
                            """!test.pkg.Annotation(value = "value")""",
                            "test.pkg.Annotation",
                        ),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = true,
                    expectedMatchesNamedOther = true,
                    expectedMatchesAnnotationName = true,
                ),
                Params(
                    name = "excluding-more-specific-last",
                    patterns =
                        listOf(
                            "test.pkg.Annotation",
                            """!test.pkg.Annotation(value = "value")""",
                        ),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = true,
                    expectedMatchesNamedOther = true,
                    expectedMatchesAnnotationName = true,
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun testParameters() = params
    }

    private fun buildFilter(): AnnotationFilter {
        var error: String? = null
        try {
            val builder = AnnotationFilterBuilder()
            params.patterns.forEach(builder::add)
            val filter = builder.build()
            if (params.expectedError == null) {
                return filter
            }
        } catch (e: IllegalStateException) {
            error = e.message
        }

        assertEquals(params.expectedError, error)
        throw AssumptionViolatedException("filter was not built")
    }

    @Test
    fun `Test empty and not empty`() {
        val filter = buildFilter()

        assertEquals("empty", params.expectedEmpty, filter.isEmpty())
        assertEquals("not empty", !params.expectedEmpty, filter.isNotEmpty())
    }

    @Test
    fun `Test match simple annotation no parentheses`() {
        val filter = buildFilter()

        assertEquals(params.expectedMatchesSimple, filter.matches("test.pkg.Annotation"))
    }

    @Test
    fun `Test match simple annotation, parentheses but no arguments`() {
        val filter = buildFilter()

        assertEquals(params.expectedMatchesSimple, filter.matches("test.pkg.Annotation()"))
    }

    @Test
    fun `Test match annotation, implicit property name`() {
        val filter = buildFilter()

        assertEquals(
            params.expectedMatchesImplicitValue,
            filter.matches("""test.pkg.Annotation("value")""")
        )
    }

    @Test
    fun `Test match annotation, value property`() {
        val filter = buildFilter()

        assertEquals(
            params.expectedMatchesNamedValue,
            filter.matches("""test.pkg.Annotation(value = "value")""")
        )
    }

    @Test
    fun `Test match annotation, other property`() {
        val filter = buildFilter()

        assertEquals(
            params.expectedMatchesNamedOther,
            filter.matches("""test.pkg.Annotation(other = "other")""")
        )
    }

    @Test
    fun `Test matches annotation name`() {
        val filter = buildFilter()

        assertEquals(
            params.expectedMatchesAnnotationName,
            filter.matchesAnnotationName("test.pkg.Annotation")
        )
    }

    @Test
    fun `Test does not match annotation name`() {
        val filter = buildFilter()

        assertEquals(
            params.expectedMatchesOtherAnnotationName,
            filter.matchesAnnotationName("other.OtherAnnotation")
        )
    }

    @Test
    fun `Test included names`() {
        val filter = buildFilter()

        // Although the names are a set the order matters, however equality of sets ignores order so
        // convert each set to a list and then compare.
        assertEquals(
            params.expectedIncludedAnnotationNames.toList(),
            filter.getIncludedAnnotationNames().toList()
        )
    }
}
