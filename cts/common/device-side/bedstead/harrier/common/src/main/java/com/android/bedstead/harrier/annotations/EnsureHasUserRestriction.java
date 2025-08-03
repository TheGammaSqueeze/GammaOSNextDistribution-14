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

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.LATE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ADMIN;

import com.android.bedstead.harrier.UserType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires a given user restriction be set.
 *
 * <p>You should use {@code DeviceState} to ensure that the device enters
 * the correct state for the method.
 *
 * <p>Note that when relying on {@code DeviceState} to enforce this policy, it will make use of a
 * Profile Owner. This should not be used in states where no profile owner is wanted on the
 * user the restriction is required on.
 */
// TODO(264844667): Enforce no use of @EnsureHasNoProfileOwner
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EnsureHasUserRestrictionGroup.class)
// This is only required because the user restrictions are applied by a Device Admin.
@RequireFeature(FEATURE_DEVICE_ADMIN)
public @interface EnsureHasUserRestriction {

    int ENSURE_HAS_USER_RESTRICTION_PRIORITY = LATE;

    /** The restriction to be set. */
    String value();

    /** The user the restriction should be set on. */
    UserType onUser() default UserType.INSTRUMENTED_USER;

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
    int priority() default ENSURE_HAS_USER_RESTRICTION_PRIORITY;
}
