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

/**
 * A {@link Query} for a nullable type.
 *
 *
 * @param <E> Type of query
 * @param <F> Type of object being queried
 */
public interface NullableQuery<E, F> extends Query<F> {
    /** Require the {@link F} is equal to {@code string}. */
    E isEqualTo(F value);

    /** Require the {@link F} is not equal to {@code string}. */
    E isNotEqualTo(F value);

    /** Require the {@link F} is null. */
    default E isNull() {
        return isEqualTo(null);
    }

    /** Require the {@link F} is not null. */
    default E isNotNull() {
        return isNotEqualTo(null);
    }
}
