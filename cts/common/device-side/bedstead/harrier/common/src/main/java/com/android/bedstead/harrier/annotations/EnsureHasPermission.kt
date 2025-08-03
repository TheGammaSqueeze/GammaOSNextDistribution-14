/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.harrier.annotations

import com.google.auto.value.AutoAnnotation

/**
 * Ensure that the given permission is granted before running the test.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnsureHasPermission(

    vararg val value: String,

    val failureMode: FailureMode = FailureMode.FAIL,

    /** The minimum version where this permission is required. */
    val minVersion: Int = 0,

    /** The maximum version where this permission is required. */
    val maxVersion: Int = Int.MAX_VALUE,

    /**
     * Priority sets the order that annotations will be resolved.
     *
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.EARLY
)

/**
 * Return an instance of the generated class that conforms to the specification of
 * [EnsureHasPermission]. See [AutoAnnotation].
 */
@AutoAnnotation
fun ensureHasPermission(vararg value: String): EnsureHasPermission {
    return AutoAnnotation_EnsureHasPermissionKt_ensureHasPermission(value)
}
