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
 * Annotation indicating that a test method requires that at least one package on the device
 * responds to a specific Intent. The test will skip/fail if any package responds.
 *
 * If you'd rather ensures that at least one package on the device responds to a specific Intent
 * before running the test, see [EnsurePackageRespondsToIntent].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequirePackageRespondsToIntent (

    /** The intent to check if any package responds to. */
    val intent: Intent,

    /** The [UserType] from which the package needs to respond. */
    val user: UserType = UserType.INSTRUMENTED_USER,

    /** The action to be taken if any package on the device responds. */
    val failureMode: FailureMode = FailureMode.SKIP,

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
    val priority: Int = AnnotationPriorityRunPrecedence.MIDDLE,
)

/**
 * Return an instance of the generated class that conforms to the specification of
 * [RequirePackageRespondsToIntent]. See [AutoAnnotation].
 */
fun requirePackageRespondsToIntent(action: String): RequirePackageRespondsToIntent {
    return requirePackageRespondsToIntent(Intent(action), user = UserType.INSTRUMENTED_USER)
}

/**
 * Return an instance of the generated class that conforms to the specification of
 * [RequirePackageRespondsToIntent]. See [AutoAnnotation].
 */
fun requirePackageRespondsToIntent(action: String, user: UserType): RequirePackageRespondsToIntent {
    return requirePackageRespondsToIntent(Intent(action), user)
}

@AutoAnnotation
private fun requirePackageRespondsToIntent(intent: Intent, user: UserType): RequirePackageRespondsToIntent {
    return AutoAnnotation_RequirePackageRespondsToIntentKt_requirePackageRespondsToIntent(intent, user)
}
