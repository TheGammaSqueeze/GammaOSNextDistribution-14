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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.testing.KnownSourceFiles
import com.google.common.truth.Truth.*

class NullabilityCodebaseContext(
    codebaseContext: BaseModelTest.CodebaseContext<Codebase>,
    /**
     * True if nullness information came from annotations, false if it came from kotlin null
     * suffixes.
     */
    val nullabilityFromAnnotations: Boolean,
) : BaseModelTest.CodebaseContext<Codebase> by codebaseContext

/**
 * Runs a test where it matters whether nullability is provided by annotations (which it is in
 * [javaSource] and [annotatedSignature]) or kotlin null suffixes (which it is in [kotlinSource] and
 * [kotlinNullsSignature]).
 *
 * Runs [test] for the nullability-through-annotations inputs with `true` as the boolean parameter,
 * and runs [test] for the nullability-through-suffixes inputs with `false` as the boolean
 * parameter.
 */
internal fun BaseModelTest.runNullabilityTest(
    javaSource: TestFile,
    annotatedSignature: TestFile,
    kotlinSource: TestFile,
    kotlinNullsSignature: TestFile,
    test: NullabilityCodebaseContext.() -> Unit
) {
    runCodebaseTest(
        inputSet(
            javaSource,
            // Access nullability annotations which are not type use.
            KnownSourceFiles.notTypeUseNullableSource,
            KnownSourceFiles.notTypeUseNonNullSource,
            // Libcore nullability are type use.
            KnownSourceFiles.libcoreNullableSource,
            KnownSourceFiles.libcoreNonNullSource,
        ),
        inputSet(annotatedSignature)
    ) {
        val context = NullabilityCodebaseContext(this, true)
        context.test()
    }

    runCodebaseTest(kotlinSource, kotlinNullsSignature) {
        val context = NullabilityCodebaseContext(this, false)
        context.test()
    }
}

/**
 * Make sure that this [TypeItem] has [TypeNullability.NONNULL] and check to make sure that it has
 * (or does not have depending on [expectAnnotation]) an [AnnotationItem.isNonNull] annotation.
 *
 * @param expectAnnotation `true` if an appropriate annotation is expected, `false` if it is not,
 *   `null` disables the annotation check.
 */
internal fun TypeItem.assertHasNonNullNullability(
    expectAnnotation: Boolean? = null,
    message: String? = null,
) {
    assertWithMessage(message ?: "")
        .that(modifiers.nullability())
        .isEqualTo(TypeNullability.NONNULL)
    val nullabilityAnnotations = modifiers.annotations().filter { it.isNullnessAnnotation() }
    when (expectAnnotation) {
        true -> assertThat(nullabilityAnnotations.single().isNonNull()).isTrue()
        false -> assertThat(nullabilityAnnotations).isEmpty()
        else -> {}
    }
}

/**
 * Make sure that this [TypeItem] has [TypeNullability.NULLABLE] and check to make sure that it has
 * (or does not have depending on [expectAnnotation]) an [AnnotationItem.isNullable] annotation.
 *
 * @param expectAnnotation `true` if an appropriate annotation is expected, `false` if it is not,
 *     * `null` disables the annotation check.
 */
internal fun TypeItem.assertHasNullableNullability(expectAnnotation: Boolean? = null) {
    assertThat(modifiers.nullability()).isEqualTo(TypeNullability.NULLABLE)
    val nullabilityAnnotations = modifiers.annotations().filter { it.isNullnessAnnotation() }
    when (expectAnnotation) {
        true -> assertThat(nullabilityAnnotations.single().isNullable()).isTrue()
        false -> assertThat(nullabilityAnnotations).isEmpty()
        else -> {}
    }
}

/** Make sure that this [TypeItem] has [TypeNullability.PLATFORM]. */
internal fun TypeItem.assertHasPlatformNullability() {
    assertThat(modifiers.nullability()).isEqualTo(TypeNullability.PLATFORM)
}

/** Make sure that this [TypeItem] has [TypeNullability.UNDEFINED]. */
internal fun TypeItem.assertHasUndefinedNullability() {
    assertThat(modifiers.nullability()).isEqualTo(TypeNullability.UNDEFINED)
}
