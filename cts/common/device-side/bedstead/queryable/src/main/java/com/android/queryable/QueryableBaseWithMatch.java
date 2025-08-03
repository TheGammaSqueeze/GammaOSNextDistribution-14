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

package com.android.queryable;

import android.os.Parcel;

import com.android.queryable.queries.Query;

/**
 * {@link QueryableBase} with an additional {@link #matches(Object)} method.
 *
 * <p>This makes subclasses compatible with collection queries.
 */
public abstract class QueryableBaseWithMatch<E, F extends Query<E>> extends QueryableBase<F> implements Query<E> {

    protected QueryableBaseWithMatch() {
        super();
    }

    protected QueryableBaseWithMatch(Parcel in) {
        super(in);
    }

    @Override
    public boolean isEmptyQuery() {
        return mQuery.isEmptyQuery();
    }

    /**
     * {@code true} if the {@code value} meets the requirements of the query.
     */
    public boolean matches(E value) {
        return mQuery.matches(value);
    }
}
