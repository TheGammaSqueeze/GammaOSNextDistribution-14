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

import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_KEY;
import static com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate.ENSURE_HAS_DELEGATE_PRIORITY;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensure that the given app op is granted to the test app before running the test.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EnsureTestAppHasAppOpGroup.class)
public @interface EnsureTestAppHasAppOp {
    String[] value();

    String testAppKey() default DEFAULT_KEY;

    /** The minimum version where this appOp is required. */
    int minVersion() default 0;

    /** The maximum version where this appOp is required. */
    int maxVersion() default Integer.MAX_VALUE;

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
    int priority() default ENSURE_HAS_DELEGATE_PRIORITY + 1;
}
