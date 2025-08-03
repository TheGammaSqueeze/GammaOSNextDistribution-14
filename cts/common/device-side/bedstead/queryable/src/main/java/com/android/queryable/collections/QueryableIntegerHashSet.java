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

import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;

import java.util.Set;

/**
 * Implementation of {@link QueryableSet} for use with {@link Integer}.
 */
public final class QueryableIntegerHashSet extends QueryableHashSet<Integer, IntegerQuery<QueryableIntegerHashSet.QueryableIntegerHashSetQuery>, QueryableIntegerHashSet> {

    /**
     * {@link QueryableSetQuery} for {@link QueryableIntegerHashSet}.
     */
    public static final class QueryableIntegerHashSetQuery extends AbstractQueryableSetQuery<Integer, IntegerQuery<QueryableIntegerHashSetQuery>, QueryableIntegerHashSet> {
        public QueryableIntegerHashSetQuery(Set<Integer> set) {
            super(set);
            setQuery(new IntegerQueryHelper<>(this));
        }

        @Override
        QueryableIntegerHashSet createSet(Set<Integer> e) {
            return new QueryableIntegerHashSet(e);
        }
    }

    /**
     * Create a {@link QueryableIntegerHashSet}.
     */
    public static QueryableIntegerHashSet of(int... values) {
        QueryableIntegerHashSet set = new QueryableIntegerHashSet();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }

    public QueryableIntegerHashSet() {
        super();
    }

    public QueryableIntegerHashSet(Set<Integer> set) {
        super(set);
    }

    @Override
    public QueryableIntegerHashSetQuery query() {
        return new QueryableIntegerHashSetQuery(this);
    }
}
