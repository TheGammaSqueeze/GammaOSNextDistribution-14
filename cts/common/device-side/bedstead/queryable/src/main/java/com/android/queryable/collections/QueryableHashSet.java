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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Implementation of {@link QueryableSet}.
 */
public abstract class QueryableHashSet<E, F extends Query<E>, G extends QueryableSet<E, F, ?>> implements QueryableSet<E, F, G> {

    protected Set<E> mSet = new HashSet<>();

    public QueryableHashSet() {

    }

    public QueryableHashSet(Set<E> existingSet) {
        mSet.addAll(existingSet);
    }

    @Override
    public int size() {
        return mSet.size();
    }

    @Override
    public boolean isEmpty() {
        return mSet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return mSet.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return mSet.iterator();
    }

    @Override
    public Object[] toArray() {
        return mSet.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return mSet.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return mSet.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return mSet.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return mSet.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return mSet.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return mSet.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return mSet.removeAll(c);
    }

    @Override
    public void clear() {
        mSet.clear();
    }
}
