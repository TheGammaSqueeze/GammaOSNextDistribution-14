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

import com.android.queryable.Queryable;
import com.android.queryable.queries.Query;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Common implementation of {@link QueryableSetQuery}.
 */
public abstract class AbstractQueryableSetQuery<E, F extends Query<E>, G extends QueryableSet<E, F, ?>> implements QueryableSetQuery<E, F, G>, Queryable {
    private final Set<E> mSet;
    private F mQuery;

    public AbstractQueryableSetQuery(Set<E> set) {
        mSet = set;
    }

    @Override
    public F where() {
        return mQuery;
    }

    protected void setQuery(F query) {
        if (mQuery != null) {
            throw new IllegalStateException("Can only set query once");
        }
        mQuery = query;
    }

    @Override
    public boolean isEmptyQuery() {
        return mQuery.isEmptyQuery();
    }

    @Override
    public String describeQuery(String fieldName) {
        return mQuery.describeQuery(fieldName);
    }

    @Override
    public E get() {
        return mSet.stream().filter(mQuery::matches).findFirst().orElse(null);
    }

    @Override
    public G filter() {
        return createSet(mSet.stream().filter(mQuery::matches).collect(Collectors.toSet()));
    }

    /**
     * Implemented by the subclass to create the correct queryable set type.
     */
    abstract G createSet(Set<E> e);
}
