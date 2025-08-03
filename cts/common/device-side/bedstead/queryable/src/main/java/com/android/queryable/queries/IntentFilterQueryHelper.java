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

import android.content.IntentFilter;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of {@link IntentFilterQuery}.
 *
 * @param <E> Type of query.
 */
public final class IntentFilterQueryHelper<E extends Queryable> implements IntentFilterQuery<E> {

    public static final class IntentFilterQueryBase extends
            QueryableBaseWithMatch<IntentFilter, IntentFilterQueryHelper<IntentFilterQueryBase>> {
        IntentFilterQueryBase() {
            super();
            setQuery(new IntentFilterQueryHelper<>(this));
        }

        IntentFilterQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<IntentFilterQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public IntentFilterQueryBase createFromParcel(Parcel in) {
                        return new IntentFilterQueryBase(in);
                    }

                    public IntentFilterQueryBase[] newArray(int size) {
                        return new IntentFilterQueryBase[size];
                    }
                };
    }

    private final transient E mQuery;
    private final SetQueryHelper<E, String> mActionsQueryHelper;
    private final SetQueryHelper<E, String> mCategoriesQueryHelper;

    public IntentFilterQueryHelper(E query) {
        mQuery = query;
        mActionsQueryHelper = new SetQueryHelper<>(query);
        mCategoriesQueryHelper = new SetQueryHelper<>(query);
    }

    private IntentFilterQueryHelper(Parcel in) {
        mQuery = null;
        mActionsQueryHelper = in.readParcelable(IntentFilterQueryHelper.class.getClassLoader());
        mCategoriesQueryHelper = in.readParcelable(IntentFilterQueryHelper.class.getClassLoader());
    }

    @Override
    public SetQuery<E, String> actions() {
        return mActionsQueryHelper;
    }

    @Override
    public SetQuery<E, String> categories() {
        return mCategoriesQueryHelper;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mActionsQueryHelper)
                && Queryable.isEmptyQuery(mCategoriesQueryHelper);
    }

    @Override
    public boolean matches(IntentFilter value) {
        Set<String> actions = new HashSet<>();
        Set<String> categories = new HashSet<>();

        if (value.countActions() > 0) {
            Iterator<String> actionsIterator = value.actionsIterator();

            while (actionsIterator.hasNext()) {
                actions.add(actionsIterator.next());
            }
        }
        if (value.countCategories() > 0) {
            Iterator<String> categoriesIterator = value.categoriesIterator();

            while (categoriesIterator.hasNext()) {
                categories.add(categoriesIterator.next());
            }
        }


        return mActionsQueryHelper.matches(actions)
                && mCategoriesQueryHelper.matches(categories);
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mActionsQueryHelper.describeQuery(fieldName + ".actions"),
                mCategoriesQueryHelper.describeQuery(fieldName + ".categories")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mActionsQueryHelper, flags);
        out.writeParcelable(mCategoriesQueryHelper, flags);
    }

    public static final Parcelable.Creator<IntentFilterQueryHelper> CREATOR =
            new Parcelable.Creator<IntentFilterQueryHelper>() {
                public IntentFilterQueryHelper createFromParcel(Parcel in) {
                    return new IntentFilterQueryHelper(in);
                }

                public IntentFilterQueryHelper[] newArray(int size) {
                    return new IntentFilterQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntentFilterQueryHelper)) return false;
        IntentFilterQueryHelper<?> that = (IntentFilterQueryHelper<?>) o;
        return Objects.equals(mActionsQueryHelper, that.mActionsQueryHelper)
                && Objects.equals(mCategoriesQueryHelper, that.mCategoriesQueryHelper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mActionsQueryHelper, mCategoriesQueryHelper);
    }
}
