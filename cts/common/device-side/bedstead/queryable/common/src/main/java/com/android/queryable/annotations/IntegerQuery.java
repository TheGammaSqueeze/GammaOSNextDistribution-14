/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.queryable.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Filter integer arguments in bedstead query annotations. */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntegerQuery {

    // If adding a new method to this annotation -
    // update {@code IntegerQueryHelper#matchesAnnotation}.

    // Set a small offset from min to avoid accidental clashes
    int DEFAULT_INT_QUERY_PARAMETERS_VALUE = Integer.MIN_VALUE + 4;

    /** Require the int is equal to value */
    int isEqualTo() default DEFAULT_INT_QUERY_PARAMETERS_VALUE;

    /** Require the int is greater than the value */
    int isGreaterThan() default DEFAULT_INT_QUERY_PARAMETERS_VALUE;

    /** Require the int is greater than or equal to the value */
    int isGreaterThanOrEqualTo() default DEFAULT_INT_QUERY_PARAMETERS_VALUE;

    /** Require the int is less than the value */
    int isLessThan() default DEFAULT_INT_QUERY_PARAMETERS_VALUE;

    /** Require the int is less than or equal to the value */
    int isLessThanOrEqualTo() default DEFAULT_INT_QUERY_PARAMETERS_VALUE;
}
