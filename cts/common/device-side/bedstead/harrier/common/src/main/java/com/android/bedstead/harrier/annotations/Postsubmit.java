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

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that a test method should not be run as part of multi-user presubmit, as defined by
 * tests using multi-user annotations that opt them into presubmit, like
 * {@link RequireRunOnWorkProfile}.
 *
 * <p>This annotation should be used on any new tests running in a multi-user module. Only after
 * the test has been in postsubmit for some time, demonstrating it is fast and reliable, should the
 * annotation be removed to allow it to run as part of presubmit.
 *
 * <p>It can also be used for tests which don't meet the requirements to be part of multi-user
 * presubmits.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Postsubmit {
    String reason();

    int priority() default PRECEDENCE_NOT_IMPORTANT;
}
