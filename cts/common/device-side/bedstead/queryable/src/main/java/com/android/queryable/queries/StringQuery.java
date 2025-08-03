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

/**
 * Query for a {@link String}.
 *
 * @param <E> Type of query
 */
public interface StringQuery<E extends Queryable> extends NullableQuery<E, String> {

    /** Queries a {@link String}. */
    static StringQueryHelper.StringQueryBase string() {
        return new StringQueryHelper.StringQueryBase();
    }

    /** Require the {@link String} starts with {@code string}. */
    E startsWith(String string);

    /** Require the {@link String} matches the requirements of the query annotation. */
    E matchesAnnotation(com.android.queryable.annotations.StringQuery queryAnnotation);

}
