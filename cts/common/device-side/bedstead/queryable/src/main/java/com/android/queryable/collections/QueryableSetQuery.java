/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.queryable.collections;

import com.android.queryable.queries.Query;

/**
 * Query for a {@link QueryableSet}.
 */
public interface QueryableSetQuery<E, F extends Query<E>, G extends QueryableSet<E, F, ?>> {
    /**
     * Add an option to the query.
     */
    F where();

    /**
     * Get any element matching the query, or {@code null} if nothing matches.
     */
    /* @Nullable */ E get();

    /**
     * Get all elements which match the query.
     */
    G filter();
}
