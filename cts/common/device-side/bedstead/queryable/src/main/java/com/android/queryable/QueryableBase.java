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
import android.os.Parcelable;

import com.android.queryable.queries.Query;

import java.util.Objects;

/**
 * Superclass of classes which provide a {@code .where()} method.
 */
public abstract class QueryableBase<E extends Query<?>> implements Queryable, Parcelable {

    protected E mQuery;

    protected QueryableBase() {
    }

    protected QueryableBase(Parcel in) {
        mQuery = in.readParcelable(QueryableBase.class.getClassLoader());
    }

    protected void setQuery(E query) {
        if (mQuery != null) {
            throw new IllegalStateException("Cannot setQuery twice");
        }
        mQuery = query;
    }

    /**
     * Entry point to querying a particular property.
     */
    public E where() {
        if (mQuery == null) {
            throw new IllegalStateException("Must setQuery before querying");
        }
        return mQuery;
    }

    @Override
    public String describeQuery(String fieldName) {
        return mQuery.describeQuery(fieldName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mQuery, flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), mQuery);
    }

    @Override
    public boolean equals(Object obj) {
        return getClass().equals(obj.getClass()) && mQuery.equals(((QueryableBase<?>) obj).mQuery);
    }
}
