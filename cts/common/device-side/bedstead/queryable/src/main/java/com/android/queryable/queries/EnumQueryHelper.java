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
import com.android.queryable.util.ParcelableUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class EnumQueryHelper <E extends Queryable, F> implements EnumQuery<E, F>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private Set<F> mIsEqualTo = null;
    private Set<F> mIsNotEqualTo = null;

    public EnumQueryHelper(E query) {
        mQuery = query;
    }

    private EnumQueryHelper(Parcel in) {
        mQuery = null;
        mIsEqualTo = (Set<F>) ParcelableUtils.readSet(in);
        mIsNotEqualTo = (Set<F>) ParcelableUtils.readSet(in);
    }

    @Override
    public E isEqualTo(F value) {
        if (mIsEqualTo != null) {
            throw new IllegalStateException("isEqualTo or isOneOf already specified");
        }
        mIsEqualTo = new HashSet<>();
        mIsEqualTo.add(value);

        return mQuery;
    }

    @Override
    public E isNotEqualTo(F value) {
        if (mIsNotEqualTo != null) {
            throw new IllegalStateException("isNotEqualTo or isNotOneOf already specified");
        }
        mIsNotEqualTo = new HashSet<>();
        mIsNotEqualTo.add(value);

        return mQuery;
    }

    @Override
    public E isOneOf(F... values) {
        if (mIsEqualTo != null) {
            throw new IllegalStateException("isEqualTo or isOneOf already specified");
        }
        mIsEqualTo = new HashSet<>();
        mIsEqualTo.addAll(Arrays.asList(values));

        return mQuery;
    }

    @Override
    public E isNotOneOf(F... values) {
        if (mIsNotEqualTo != null) {
            throw new IllegalStateException("isNotEqualTo or isNotOneOf already specified");
        }
        mIsNotEqualTo = new HashSet<>();
        mIsNotEqualTo.addAll(Arrays.asList(values));

        return mQuery;
    }

    @Override
    public boolean isEmptyQuery() {
        return mIsEqualTo == null
                && (mIsNotEqualTo == null || mIsNotEqualTo.isEmpty());
    }

    @Override
    public boolean matches(F value) {
        if (!checkIsEqualTo(value)) {
            return false;
        }

        if (!checkIsNotEqualTo(value)) {
            return false;
        }

        return true;
    }

    private boolean checkIsEqualTo(F value) {
        if (mIsEqualTo == null) {
            return true;
        }

        for (F v : mIsEqualTo) {
            if (v == value) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIsNotEqualTo(F value) {
        if (mIsNotEqualTo == null) {
            return true;
        }

        for (F v : mIsNotEqualTo) {
            if (v == value) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mIsEqualTo != null) {
            if (mIsEqualTo.size() == 1) {
                queryStrings.add(fieldName + "=" + mIsEqualTo);
            } else {
                queryStrings.add(fieldName + " in {"
                        + mIsEqualTo.stream().map(Object::toString).collect(Collectors.joining(", "))
                        + "}");
            }
        }

        if (mIsNotEqualTo != null) {
            if (mIsNotEqualTo.size() == 1) {
                queryStrings.add(fieldName + "!=" + mIsNotEqualTo);
            } else {
                queryStrings.add(fieldName + " not in {"
                        + mIsNotEqualTo.stream().map(Object::toString).collect(Collectors.joining(", "))
                        + "}");
            }
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        ParcelableUtils.writeSet(out, mIsEqualTo);
        ParcelableUtils.writeSet(out, mIsNotEqualTo);
    }

    public static final Parcelable.Creator<EnumQueryHelper> CREATOR =
            new Parcelable.Creator<EnumQueryHelper>() {
                public EnumQueryHelper createFromParcel(Parcel in) {
                    return new EnumQueryHelper(in);
                }

                public EnumQueryHelper[] newArray(int size) {
                    return new EnumQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnumQueryHelper)) return false;
        EnumQueryHelper<?, ?> that = (EnumQueryHelper<?, ?>) o;
        return Objects.equals(mIsEqualTo, that.mIsEqualTo) && Objects.equals(
                mIsNotEqualTo, that.mIsNotEqualTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIsEqualTo, mIsNotEqualTo);
    }
}
