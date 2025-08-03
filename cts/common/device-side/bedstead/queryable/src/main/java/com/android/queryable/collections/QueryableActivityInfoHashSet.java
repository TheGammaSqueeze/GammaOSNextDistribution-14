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

import com.android.queryable.info.ActivityInfo;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.ActivityQueryHelper;

import java.util.Set;

/**
 * Implementation of {@link QueryableSet} for use with {@link ActivityInfo}.
 */
public final class QueryableActivityInfoHashSet extends QueryableHashSet<ActivityInfo, ActivityQuery<QueryableActivityInfoHashSet.QueryableActivityInfoHashSetQuery>, QueryableActivityInfoHashSet> {

    /**
     * {@link QueryableSetQuery} for {@link QueryableActivityInfoHashSet}.
     */
    public static final class QueryableActivityInfoHashSetQuery extends AbstractQueryableSetQuery<ActivityInfo, ActivityQuery<QueryableActivityInfoHashSetQuery>, QueryableActivityInfoHashSet> {
        public QueryableActivityInfoHashSetQuery(Set<ActivityInfo> set) {
            super(set);
            setQuery(new ActivityQueryHelper<>(this));
        }

        @Override
        QueryableActivityInfoHashSet createSet(Set<ActivityInfo> e) {
            return new QueryableActivityInfoHashSet(e);
        }
    }

    /**
     * Create a {@link QueryableActivityInfoHashSet}.
     */
    public static QueryableActivityInfoHashSet of(ActivityInfo... values) {
        QueryableActivityInfoHashSet set = new QueryableActivityInfoHashSet();
        for (ActivityInfo value : values) {
            set.add(value);
        }
        return set;
    }

    public QueryableActivityInfoHashSet() {
        super();
    }

    public QueryableActivityInfoHashSet(Set<ActivityInfo> set) {
        super(set);
    }

    @Override
    public QueryableActivityInfoHashSetQuery query() {
        return new QueryableActivityInfoHashSetQuery(this);
    }
}
