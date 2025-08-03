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

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.MIDDLE;

import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.queryable.annotations.Query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires the given test app to be installed on the given user.
 *
 * <p>You should use {@code DeviceState} to ensure that the device enters
 * the correct state for the method.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EnsureTestAppInstalledGroup.class)
public @interface EnsureTestAppInstalled {

    int ENSURE_TEST_APP_INSTALLED_PRIORITY = MIDDLE;

    String DEFAULT_KEY = "testApp";

    /**
     * A key which uniquely identifies the test app for the test.
     *
     * <p>This can be used with e.g. {@code DeviceState#testApp} and
     * {@link AdditionalQueryParameters}.
     */
    String key() default DEFAULT_KEY;

    /** Query specifying the testapp. Defaults to any test app. */
    Query query() default @Query();

    /** The user the testApp should be installed on. */
    UserType onUser() default UserType.INSTRUMENTED_USER;

    /**
     * Whether this testApp should be returned by calls to {@code DeviceState#dpc()}.
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
    int priority() default ENSURE_TEST_APP_INSTALLED_PRIORITY;
}
