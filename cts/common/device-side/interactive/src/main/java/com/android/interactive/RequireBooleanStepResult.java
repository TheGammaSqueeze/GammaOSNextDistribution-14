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

package com.android.interactive;

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.LAST;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should only run when a boolean Step returns the expected value.
 *
 * <p>You can use {@code DeviceState} to ensure that the test is only run in the correct state.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@UsesAnnotationExecutor(InteractiveAnnotationExecutor.class)
@Repeatable(RequireBooleanStepResultGroup.class)
public @interface RequireBooleanStepResult {
    /** The step to execute. */
    Class<? extends Step<Boolean>> step();

    /** The expected result of the step. */
    boolean expectedResult();

    /** The reason for this requirement. */
    String reason();

    FailureMode failureMode() default FailureMode.SKIP;

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
    int priority() default LAST;
}
