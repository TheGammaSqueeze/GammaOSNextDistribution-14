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

package com.android.bedstead.harrier.annotations

import com.android.bedstead.harrier.UserType
import com.google.auto.value.AutoAnnotation

/**
 * Annotation indicating that a test method ensures that no package on the device responds to a
 * specific Intent.
 *
 * If you'd rather the test was skipped or failed when no package on the device responds
 * to a specific Intent before running the test, see [RequireNoPackageRespondsToIntent].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnsureNoPackageRespondsToIntent (

    /** The intent to check if any package responds to. */
    val intent: Intent,

    /** The [UserType] from which the package should not respond. */
    val user: UserType = UserType.INSTRUMENTED_USER,

    /**
     * Priority sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * <p>If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Priority can be set to a {@link AnnotationPriorityRunPrecedence} constant, or to any {@link int}.
     */
    val priority: Int = AnnotationPriorityRunPrecedence.EARLY,
)

/**
 * Return an instance of the generated class that conforms to the specification of
 * [EnsureNoPackageRespondsToIntent]. See [AutoAnnotation].
 */
fun ensureNoPackageRespondsToIntent(action: String): EnsureNoPackageRespondsToIntent {
    return ensureNoPackageRespondsToIntent(Intent(action))
}

@AutoAnnotation
private fun ensureNoPackageRespondsToIntent(intent: Intent): EnsureNoPackageRespondsToIntent {
    return AutoAnnotation_EnsureNoPackageRespondsToIntentKt_ensureNoPackageRespondsToIntent(intent)
}
