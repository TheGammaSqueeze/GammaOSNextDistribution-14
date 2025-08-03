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

import java.util.Collection;
import java.util.List;

/** Query for a {@link java.util.List}. */
public interface ListQuery<E extends Queryable, F> extends Query<List<F>> {

    /** Queries a {@link List}. */
    static <E> ListQueryHelper.ListQueryBase<E> list(Class<E> cls) {
        return new ListQueryHelper.ListQueryBase<E>();
    }

    IntegerQuery<E> size();

    /**
     * Used to query whether a list contains certain objects.
     */
    E contains(Query<F>... objects);

    /**
     * Used to query whether a list contains certain objects.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    E contains(F... objects);

    /**
     * Used to query whether a list does not contain certain objects.
     */
    E doesNotContain(Query<F>... objects);

    /**
     * Used to query whether a list does not contain certain objects.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals}. If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    E doesNotContain(F... objects);

    /**
     * Used to query whether a list contains all of the elements from a collection.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    <H extends Collection<F>> E containsAll(H... collection);

    /**
     * Used to query whether a list does not contain any elements given in a collection.
     *
     * <p>There are no bounds on the type for this method and therefore to find matches objects are
     * compared using {@link Object#equals} If you are not checking for equality use
     * {@link #contains(Query[])}.
     */
    <H extends Collection<F>> E doesNotContainAny(H... collections);

    /**
     * Used to query whether a list contains exactly certain objects only.
     */
    E containsExactly(F... objects);
}
