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

import static com.android.queryable.annotations.IntegerQuery.DEFAULT_INT_QUERY_PARAMETERS_VALUE;
import static com.android.queryable.util.ParcelableUtils.readNullableInt;
import static com.android.queryable.util.ParcelableUtils.writeNullableInt;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;

import com.google.auto.value.AutoAnnotation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link IntegerQuery}. */
public final class IntegerQueryHelper<E extends Queryable> implements IntegerQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private Integer mEqualToValue = null;
    private Integer mGreaterThanValue = null;
    private Integer mGreaterThanOrEqualToValue = null;
    private Integer mLessThanValue = null;
    private Integer mLessThanOrEqualToValue = null;

    private final transient E mQuery;

    public static final class IntegerQueryBase extends
            QueryableBaseWithMatch<Integer, IntegerQueryHelper<IntegerQueryBase>> {
        IntegerQueryBase() {
            super();
            setQuery(new IntegerQueryHelper<>(this));
        }

        IntegerQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<IntegerQueryHelper.IntegerQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public IntegerQueryHelper.IntegerQueryBase createFromParcel(Parcel in) {
                        return new IntegerQueryHelper.IntegerQueryBase(in);
                    }

                    public IntegerQueryHelper.IntegerQueryBase[] newArray(int size) {
                        return new IntegerQueryHelper.IntegerQueryBase[size];
                    }
                };
    }

    public IntegerQueryHelper(E query) {
        mQuery = query;
    }

    private IntegerQueryHelper(Parcel in) {
        mQuery = null;
        mEqualToValue = readNullableInt(in);
        mGreaterThanValue = readNullableInt(in);
        mGreaterThanOrEqualToValue = readNullableInt(in);
        mLessThanValue = readNullableInt(in);
        mLessThanOrEqualToValue = readNullableInt(in);
    }

    @Override
    public E isEqualTo(int i) {
        mEqualToValue = i;
        return mQuery;
    }

    @Override
    public E isGreaterThan(int i) {
        if (mGreaterThanValue == null) {
            mGreaterThanValue = i;
        } else {
            mGreaterThanValue = Math.max(mGreaterThanValue, i);
        }
        return mQuery;
    }

    @Override
    public E isGreaterThanOrEqualTo(int i) {
        if (mGreaterThanOrEqualToValue == null) {
            mGreaterThanOrEqualToValue = i;
        } else {
            mGreaterThanOrEqualToValue = Math.max(mGreaterThanOrEqualToValue, i);
        }
        return mQuery;
    }

    @Override
    public E isLessThan(int i) {
        if (mLessThanValue == null) {
            mLessThanValue = i;
        } else {
            mLessThanValue = Math.min(mLessThanValue, i);
        }
        return mQuery;
    }

    @Override
    public E isLessThanOrEqualTo(int i) {
        if (mLessThanOrEqualToValue == null) {
            mLessThanOrEqualToValue = i;
        } else {
            mLessThanOrEqualToValue = Math.min(mLessThanOrEqualToValue, i);
        }
        return mQuery;
    }

    @Override
    public E matchesAnnotation(com.android.queryable.annotations.IntegerQuery queryAnnotation) {
        if (queryAnnotation.isEqualTo() != DEFAULT_INT_QUERY_PARAMETERS_VALUE) {
            isEqualTo(queryAnnotation.isEqualTo());
        }
        if (queryAnnotation.isGreaterThan() != DEFAULT_INT_QUERY_PARAMETERS_VALUE) {
            isGreaterThan(queryAnnotation.isGreaterThan());
        }
        if (queryAnnotation.isGreaterThanOrEqualTo() != DEFAULT_INT_QUERY_PARAMETERS_VALUE) {
            isGreaterThanOrEqualTo(queryAnnotation.isGreaterThanOrEqualTo());
        }
        if (queryAnnotation.isLessThan() != DEFAULT_INT_QUERY_PARAMETERS_VALUE) {
            isLessThan(queryAnnotation.isLessThan());
        }
        if (queryAnnotation.isLessThanOrEqualTo() != DEFAULT_INT_QUERY_PARAMETERS_VALUE) {
            isLessThanOrEqualTo(queryAnnotation.isLessThanOrEqualTo());
        }

        return mQuery;
    }

    public com.android.queryable.annotations.IntegerQuery toAnnotation() {
        return integerQuery(
                mEqualToValue == null ? DEFAULT_INT_QUERY_PARAMETERS_VALUE : mEqualToValue,
                mGreaterThanValue == null ? DEFAULT_INT_QUERY_PARAMETERS_VALUE : mGreaterThanValue,
                mGreaterThanOrEqualToValue == null ? DEFAULT_INT_QUERY_PARAMETERS_VALUE : mGreaterThanOrEqualToValue,
                mLessThanValue == null ? DEFAULT_INT_QUERY_PARAMETERS_VALUE : mLessThanValue,
                mLessThanOrEqualToValue == null ? DEFAULT_INT_QUERY_PARAMETERS_VALUE : mLessThanOrEqualToValue);
    }

    @AutoAnnotation
    private static com.android.queryable.annotations.IntegerQuery integerQuery(
            int isEqualTo, int isGreaterThan, int isGreaterThanOrEqualTo, int isLessThan, int isLessThanOrEqualTo) {
        return new AutoAnnotation_IntegerQueryHelper_integerQuery(
                isEqualTo, isGreaterThan, isGreaterThanOrEqualTo, isLessThan, isLessThanOrEqualTo
        );
    }

    @Override
    public boolean isEmptyQuery() {
        return mEqualToValue == null
                && mGreaterThanValue == null
                && mGreaterThanOrEqualToValue == null
                && mLessThanValue == null
                && mLessThanOrEqualToValue == null;
    }

    @Override
    public boolean matches(Integer value) {
        return matches(value.intValue());
    }

    /** {@code true} if all filters are met by {@code value}. */
    public boolean matches(int value) {
        if (mEqualToValue != null && mEqualToValue != value) {
            return false;
        }

        if (mGreaterThanValue != null && value <= mGreaterThanValue) {
            return false;
        }

        if (mGreaterThanOrEqualToValue != null && value < mGreaterThanOrEqualToValue) {
            return false;
        }

        if (mLessThanValue != null && value >= mLessThanValue) {
            return false;
        }

        if (mLessThanOrEqualToValue != null && value > mLessThanOrEqualToValue) {
            return false;
        }

        return true;
    }

    public static boolean matches(IntegerQuery<?> query, int value) {
        return query.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualToValue != null) {
            queryStrings.add(fieldName + "=" + mEqualToValue);
        }
        if (mGreaterThanValue != null) {
            queryStrings.add(fieldName + ">" + mGreaterThanValue);
        }
        if (mGreaterThanOrEqualToValue != null) {
            queryStrings.add(fieldName + ">=" + mGreaterThanOrEqualToValue);
        }
        if (mLessThanValue != null) {
            queryStrings.add(fieldName + "<" + mLessThanValue);
        }
        if (mLessThanOrEqualToValue != null) {
            queryStrings.add(fieldName + "<=" + mLessThanOrEqualToValue);
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        writeNullableInt(out, mEqualToValue);
        writeNullableInt(out, mGreaterThanValue);
        writeNullableInt(out, mGreaterThanOrEqualToValue);
        writeNullableInt(out, mLessThanValue);
        writeNullableInt(out, mLessThanOrEqualToValue);
    }

    public static final Parcelable.Creator<IntegerQueryHelper> CREATOR =
            new Parcelable.Creator<IntegerQueryHelper>() {
                public IntegerQueryHelper createFromParcel(Parcel in) {
                    return new IntegerQueryHelper(in);
                }

                public IntegerQueryHelper[] newArray(int size) {
                    return new IntegerQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegerQueryHelper)) return false;
        IntegerQueryHelper<?> that = (IntegerQueryHelper<?>) o;
        return Objects.equals(mEqualToValue, that.mEqualToValue) && Objects.equals(
                mGreaterThanValue, that.mGreaterThanValue) && Objects.equals(
                mGreaterThanOrEqualToValue, that.mGreaterThanOrEqualToValue)
                && Objects.equals(mLessThanValue, that.mLessThanValue)
                && Objects.equals(mLessThanOrEqualToValue, that.mLessThanOrEqualToValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualToValue, mGreaterThanValue, mGreaterThanOrEqualToValue,
                mLessThanValue, mLessThanOrEqualToValue);
    }
}
