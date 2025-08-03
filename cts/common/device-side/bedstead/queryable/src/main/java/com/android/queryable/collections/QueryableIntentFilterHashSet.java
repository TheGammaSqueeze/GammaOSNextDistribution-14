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

import android.content.IntentFilter;

import com.android.queryable.queries.IntentFilterQuery;
import com.android.queryable.queries.IntentFilterQueryHelper;

import java.util.Set;

/**
 * Implementation of {@link QueryableSet} for use with {@link IntentFilter}.
 */
public final class QueryableIntentFilterHashSet extends QueryableHashSet<IntentFilter, IntentFilterQuery<QueryableIntentFilterHashSet.QueryableIntentFilterHashSetQuery>, QueryableIntentFilterHashSet> {

    /**
     * {@link QueryableSetQuery} for {@link QueryableIntentFilterHashSet}.
     */
    public static final class QueryableIntentFilterHashSetQuery extends AbstractQueryableSetQuery<IntentFilter, IntentFilterQuery<QueryableIntentFilterHashSetQuery>, QueryableIntentFilterHashSet> {
        public QueryableIntentFilterHashSetQuery(Set<IntentFilter> set) {
            super(set);
            setQuery(new IntentFilterQueryHelper<>(this));
        }

        @Override
        QueryableIntentFilterHashSet createSet(Set<IntentFilter> e) {
            return new QueryableIntentFilterHashSet(e);
        }
    }

    /**
     * Create a {@link QueryableIntentFilterHashSet}.
     */
    public static QueryableIntentFilterHashSet of(IntentFilter... values) {
        QueryableIntentFilterHashSet set = new QueryableIntentFilterHashSet();
        for (IntentFilter value : values) {
            set.add(value);
        }
        return set;
    }

    public QueryableIntentFilterHashSet() {
        super();
    }

    public QueryableIntentFilterHashSet(Set<IntentFilter> set) {
        super(set);
    }

    @Override
    public QueryableIntentFilterHashSetQuery query() {
        return new QueryableIntentFilterHashSetQuery(this);
    }
}
