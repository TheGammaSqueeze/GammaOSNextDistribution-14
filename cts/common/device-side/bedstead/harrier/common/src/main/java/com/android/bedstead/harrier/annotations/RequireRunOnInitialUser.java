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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;

import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;
import com.android.bedstead.nene.types.OptionalBoolean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that a test method should run on the initial user.
 *
 * <p>This requires the use of {@link com.android.bedstead.harrier.BedsteadJUnit4}. This will
 * replace this annotation with the {@code RequireRunOn} annotation for the initial user.
 *
 * <p>Note that in general this requires the test runs on the system user. On headless system user
 * devices, this will require running on the first non-system user.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequiresBedsteadJUnit4
public @interface RequireRunOnInitialUser {

    // Note that when generating the list of tests, BedsteadJUnit4 will replace this annotation with
    // the relevant annotation to ensure a test is run on the correct user for the given device

    /**
     * Should we ensure that we are switched to the given user.
     *
     * <p>ANY will be treated as TRUE if no other annotation has forced a switch.
     */
    OptionalBoolean switchedToUser() default TRUE;

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
    int priority() default REQUIRE_RUN_ON_PRECEDENCE;
}
