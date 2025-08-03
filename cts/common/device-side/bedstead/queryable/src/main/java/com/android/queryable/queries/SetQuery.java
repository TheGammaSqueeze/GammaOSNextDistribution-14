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

import androidx.annotation.CheckResult;

import com.android.queryable.Queryable;

import java.util.Collection;
import java.util.Set;

/** Query for a {@link Set}. */
public interface SetQuery<E extends Queryable, F> extends Query<Set<F>> {

    /** Queries a {@link Set}. */
    static <E> SetQueryHelper.SetQueryBase<E> set(Class<E> cls) {
        return new SetQueryHelper.SetQueryBase<E>();
    }

    /** Queries the size of the set. */
    @CheckResult
    IntegerQuery<E> size();
    E isEmpty();
    E isNotEmpty();

    /**
     * Used to query whether a set contains certain objects.
     */
    E contains(Query<F>... objects);

    /**
     * Used to query whether a set contains certain objects.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    E contains(F... objects);

    /**
     * Used to query whether a set does not contain certain objects.
     */
    E doesNotContain(Query<F>... objects);

    /**
     * Used to query whether a set does not contain certain objects.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    E doesNotContain(F... objects);

    /**
     * Used to query whether a set contains all of the elements from a collection.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    <H extends Collection<F>> E containsAll(H... collections);

    /**
     * Used to query whether a set does not contain any elements given in a collection.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    <H extends Collection<F>> E doesNotContainAny(H... collections);
}