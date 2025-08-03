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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;

import com.android.bedstead.harrier.annotations.meta.RequireRunOnUserAnnotation;
import com.android.bedstead.nene.types.OptionalBoolean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run on the primary user.
 *
 * <p>Your test configuration should be such that this test is only run on the primary user
 *
 * <p>Optionally, you can guarantee that these methods do not run outside of the primary
 * user by using {@code Devicestate}.
 *
 * <p>Note that in practice this requires that the test runs on the system user, but excludes
 * headless system users. To mark that a test should run on the system user, including headless
 * system users, see {@link RequireRunOnSystemUser} - or to run on the first user used by a
 * human (which will be the primary user on non-headless-system-user devices, or a secondary user
 * otherwise), use {@link RequireRunOnInitialUser}.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequireRunOnUserAnnotation("android.os.usertype.full.SYSTEM")
public @interface RequireRunOnPrimaryUser {
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
