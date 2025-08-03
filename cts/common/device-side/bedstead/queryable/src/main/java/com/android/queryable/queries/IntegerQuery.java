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

package com.android.queryable.queries;

import com.android.queryable.Queryable;

/** Query for a {@link Integer}. */
public interface IntegerQuery<E extends Queryable> extends Query<Integer> {

    /** Queries a {@link Integer}. */
    static IntegerQueryHelper.IntegerQueryBase integer() {
        return new IntegerQueryHelper.IntegerQueryBase();
    }

    /** Require the {@link Integer} is equal to {@code i}. */
    E isEqualTo(int i);

    /** Require the {@link Integer} is greater than {@code i}. */
    E isGreaterThan(int i);

    /** Require the {@link Integer} is greater than or equal to {@code i}. */
    E isGreaterThanOrEqualTo(int i);

    /** Require the {@link Integer} is less than {@code i}. */
    E isLessThan(int i);

    /** Require the {@link Integer} is less than or equal to {@code i}. */
    E isLessThanOrEqualTo(int i);

    /** Require the {@link Integer} matches the requirements of the query annotation. */
    E matchesAnnotation(com.android.queryable.annotations.IntegerQuery queryAnnotation);
}
