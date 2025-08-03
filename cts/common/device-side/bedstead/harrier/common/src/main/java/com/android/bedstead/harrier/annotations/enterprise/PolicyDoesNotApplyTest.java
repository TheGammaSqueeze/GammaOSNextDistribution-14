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

package com.android.bedstead.harrier.annotations.enterprise;

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a test as testing the states where a policy is applied (by a Device Owner or Profile Owner)
 * and it should not apply to the user the test is running on.
 *
 * <p>This will generate parameterized runs for all matching states.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequiresBedsteadJUnit4
public @interface PolicyDoesNotApplyTest {
    /**
     * The policy being tested.
     *
     * <p>If multiple policies are specified, then they will be merged so that all valid states for
     * all specified policies are considered as valid.
     *
     * <p>This is used to calculate which states are required to be tested.
     */
    Class<?>[] policy();

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
    int priority() default PRECEDENCE_NOT_IMPORTANT;
}
