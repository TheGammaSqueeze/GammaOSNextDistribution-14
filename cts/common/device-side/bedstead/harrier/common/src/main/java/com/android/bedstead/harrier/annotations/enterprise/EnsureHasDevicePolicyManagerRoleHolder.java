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

import static com.android.bedstead.harrier.UserType.INSTRUMENTED_USER;
import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.LATE;

import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires a test Device Policy Manager role holder.
 *
 * <p>You should use {@code DeviceState} to ensure that the device enters
 * the correct state for the method. You can use {@code DeviceState#dpmRoleHolder()} to interact
 * with the role holder.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
// TODO(b/206441366): Add instant app support
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
// TODO(276740719): Support custom queries
public @interface EnsureHasDevicePolicyManagerRoleHolder {

    // We want the isPrimary here to take precedence over any other
    int ENSURE_HAS_DEVICE_POLICY_MANAGER_ROLE_HOLDER_PRIORITY = LATE;

    /** Which user type the device policy manager role holder should be installed on. */
    UserType onUser() default INSTRUMENTED_USER;

    /**
     * Whether this delegate should be returned by calls to {@code DeviceState#dpc()}.
     *
     * <p>Only one policy manager per test should be marked as primary.
     */
    boolean isPrimary() default false;

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
    int priority() default ENSURE_HAS_DEVICE_POLICY_MANAGER_ROLE_HOLDER_PRIORITY;
}
