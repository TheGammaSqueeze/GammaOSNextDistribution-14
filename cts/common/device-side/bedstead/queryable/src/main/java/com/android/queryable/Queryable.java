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

package com.android.queryable;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public interface Queryable {
    /**
     * Generate a string representation of the query.
     *
     * <p>For example, if {@code fieldName} was age, we might generate "age > 5, age < 10"
     */
    String describeQuery(String fieldName);

    /** True if this query has not had any filters applied. */
    boolean isEmptyQuery();

    /**
     * Join sub-parts of a query for use in {@link #describeQuery(String)}.
     *
     * <p>Queries which are not set should be null.
     */
    static String joinQueryStrings(String... queryStrings) {
        return Arrays.stream(queryStrings).filter(i -> i != null && !i.isEmpty())
                .collect(Collectors.joining(", "));
    }

    /**
     * Join sub-parts of a query for use in {@link #describeQuery(String)}.
     *
     * <p>Queries which are not set should be null.
     */
    static String joinQueryStrings(Collection<String> queryStrings) {
        return queryStrings.stream().filter(i -> i != null && !i.isEmpty())
                .collect(Collectors.joining(", "));
    }

    static boolean isEmptyQuery(Queryable queryable) {
        return queryable == null || queryable.isEmptyQuery();
    }
}
