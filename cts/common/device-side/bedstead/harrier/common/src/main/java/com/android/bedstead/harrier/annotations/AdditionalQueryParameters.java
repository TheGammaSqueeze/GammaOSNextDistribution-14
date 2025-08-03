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

import static com.android.bedstead.harrier.annotations.EnsureHasWorkProfileKt.ENSURE_HAS_WORK_PROFILE_PRIORITY;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.queryable.annotations.Query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Register additional query parameters which should be applied to test apps for this test.
 *
 * <p>This is used in conjunction with e.g. @PolicyApplies test to restrict the DPCs used.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdditionalQueryParameters {

    /**
     * The test app to apply these query parameters to.
     *
     * <p>There should be a constant defined in the annotation you are trying to influence for the
     * test apps they use.
     */
    String forTestApp();

    /** The additional query to apply. */
    Query query();

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
    int priority() default ENSURE_HAS_WORK_PROFILE_PRIORITY - 1;
}
