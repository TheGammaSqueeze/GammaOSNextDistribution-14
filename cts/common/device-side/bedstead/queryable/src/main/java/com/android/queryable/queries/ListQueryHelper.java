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

import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ListQueryHelper<E extends Queryable, F>
        implements ListQuery<E, F>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final IntegerQueryHelper<E> mSizeQuery;
    private final List<Query<F>> mContainsByQuery = new ArrayList<>();
    private final List<Query<F>> mDoesNotContainByQuery = new ArrayList<>();
    private final List<F> mContainsByType = new ArrayList<>();
    private final List<F> mDoesNotContainByType = new ArrayList<>();

    public static final class ListQueryBase<T> extends
            QueryableBaseWithMatch<List<T>, ListQueryHelper<ListQueryHelper.ListQueryBase<T>, T>> {
        ListQueryBase() {
            super();
            setQuery(new ListQueryHelper<>(this));
        }

        ListQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ListQueryHelper.ListQueryBase<?>> CREATOR =
                new Parcelable.Creator<>() {
                    public ListQueryHelper.ListQueryBase<?> createFromParcel(Parcel in) {
                        return new ListQueryHelper.ListQueryBase<>(in);
                    }

                    public ListQueryHelper.ListQueryBase<?>[] newArray(int size) {
                        return new ListQueryHelper.ListQueryBase<?>[size];
                    }
                };
    }

    public ListQueryHelper(E query) {
        mQuery = query;
        mSizeQuery = new IntegerQueryHelper<>(mQuery);
    }

    private ListQueryHelper(Parcel in) {
        mQuery = null;
        mSizeQuery = in.readParcelable(ListQueryHelper.class.getClassLoader());
        in.readList(mContainsByQuery, ListQueryHelper.class.getClassLoader());
        in.readList(mDoesNotContainByQuery, ListQueryHelper.class.getClassLoader());

        in.readList(mContainsByType, ListQueryHelper.class.getClassLoader());
        in.readList(mDoesNotContainByType, ListQueryHelper.class.getClassLoader());
    }

    @Override
    public IntegerQuery<E> size() {
        return mSizeQuery;
    }

    @Override
    public E contains(Query<F>... objects) {
        mContainsByQuery.addAll(Arrays.asList(objects));
        return mQuery;
    }

    @Override
    public E contains(F... objects) {
        mContainsByType.addAll(Arrays.asList(objects));
        return mQuery;
    }

    @Override
    public E doesNotContain(Query<F>... objects) {
        mDoesNotContainByQuery.addAll(Arrays.asList(objects));
        return mQuery;
    }

    @Override
    public E doesNotContain(F... objects) {
        mDoesNotContainByType.addAll(Arrays.asList(objects));
        return mQuery;
    }

    @Override
    public <H extends Collection<F>> E containsAll(H... collections) {
        for (H collection : collections) {
            Iterator<F> iterator = collection.iterator();
            while (iterator.hasNext()) {
                contains(iterator.next());
            }
        }
        return  mQuery;
    }

    @Override
    public <H extends Collection<F>> E doesNotContainAny(H... collections) {
        for (H collection : collections) {
            Iterator<F> iterator = collection.iterator();
            while (iterator.hasNext()) {
                doesNotContain(iterator.next());
            }
        }
        return  mQuery;
    }

    @Override
    public E containsExactly(F... objects) {
        size().isEqualTo(objects.length);
        return contains(objects);
    }

    @Override
    public boolean isEmptyQuery() {
        for (Query q : mContainsByQuery) {
            if (!Queryable.isEmptyQuery(q)) {
                return false;
            }
        }

        for (Query q : mDoesNotContainByQuery) {
            if (!Queryable.isEmptyQuery(q)) {
                return false;
            }
        }

        return Queryable.isEmptyQuery(mSizeQuery)
                && mContainsByType.isEmpty()
                && mDoesNotContainByType.isEmpty();
    }

    @Override
    public boolean matches(List<F> value) {
        if (!mSizeQuery.matches(value.size())) {
            return false;
        }

        if (!checkContainsAtLeast(value)) {
            return false;
        }

        if (!(checkDoesNotContain(value))) {
            return false;
        }

        return true;
    }

    private boolean checkContainsAtLeast(List<F> value) {
        List<F> v = new ArrayList<>(value);

        for (F containsAtLeast : mContainsByType) {
            F match = findMatch(containsAtLeast, v);

            if (match == null) {
                return false;
            }
            v.remove(match);
        }

        for (Query<F> containsAtLeast : mContainsByQuery) {
            F match = findMatch(containsAtLeast, v);

            if (match == null) {
                return false;
            }
            v.remove(match);
        }

        return true;
    }

    private boolean checkDoesNotContain(List<F> value) {
        for (F doesNotContain : mDoesNotContainByType) {
            if (findMatch(doesNotContain, value) != null) {
                return false;
            }
        }

        for (Query<F> doesNotContain : mDoesNotContainByQuery) {
            if (findMatch(doesNotContain, value) != null) {
                return false;
            }
        }

        return true;
    }

    private F findMatch(Query<F> query, List<F> values) {
        for (F value : values) {
            if (query.matches(value)) {
                return value;
            }
        }

        return null;
    }

    private F findMatch(F object, List<F> values) {
        return values.contains(object) ? object : null;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        queryStrings.add(mSizeQuery.describeQuery(fieldName + ".size"));
        if (!mContainsByType.isEmpty()) {
            if (!mContainsByQuery.isEmpty()) {
                queryStrings.add(fieldName + " contains matches of ["
                        + mContainsByQuery.stream().map(t -> "{" + t.describeQuery("")
                        + "}").collect(Collectors.joining(", ")) + "]"
                        + mContainsByType.stream().map(t -> "{" + t.toString()
                        + "}").collect(Collectors.joining(", ")) + "]");
            } else {
                queryStrings.add(fieldName + " contains matches of ["
                        + mContainsByType.stream().map(t -> "{" + t.toString()
                        + "}").collect(Collectors.joining(", ")) + "]");
            }
        }
        if (!mDoesNotContainByQuery.isEmpty() && !mDoesNotContainByType.isEmpty()) {
            queryStrings.add(fieldName + " does not contain anything matching any of ["
                    + mDoesNotContainByQuery.stream().map(t -> "{" + t.describeQuery("")
                    + "}").collect(Collectors.joining(", "))
                    + mDoesNotContainByType.stream().map(t -> "{"
                    + t.toString() + "}").collect(Collectors.joining(", ")) + "]");
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mSizeQuery, flags);
        out.writeList(mContainsByQuery);
        out.writeList(mDoesNotContainByQuery);
        out.writeList(mContainsByType);
        out.writeList(mDoesNotContainByType);
    }

    public static final Parcelable.Creator<ListQueryHelper> CREATOR =
            new Parcelable.Creator<ListQueryHelper>() {
                public ListQueryHelper createFromParcel(Parcel in) {
                    return new ListQueryHelper(in);
                }

                public ListQueryHelper[] newArray(int size) {
                    return new ListQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListQueryHelper)) return false;
        ListQueryHelper<?, ?> that = (ListQueryHelper<?, ?>) o;
        return Objects.equals(mSizeQuery, that.mSizeQuery) && Objects.equals(
                mContainsByQuery, that.mContainsByQuery) && Objects.equals(
                mDoesNotContainByQuery, that.mDoesNotContainByQuery) && Objects.equals(
                mContainsByType, that.mContainsByType) && Objects.equals(
                mDoesNotContainByType, that.mDoesNotContainByType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSizeQuery, mContainsByQuery, mDoesNotContainByQuery, mContainsByType,
                mDoesNotContainByType);
    }
}
