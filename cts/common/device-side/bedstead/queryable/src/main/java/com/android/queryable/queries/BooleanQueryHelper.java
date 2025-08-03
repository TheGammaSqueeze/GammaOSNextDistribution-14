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
import static com.android.queryable.util.ParcelableUtils.readNullableBoolean;
import static com.android.queryable.util.ParcelableUtils.writeNullableBoolean;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.google.auto.value.AutoAnnotation;

import java.util.Objects;

public final class BooleanQueryHelper<E extends Queryable> implements BooleanQuery<E> {

    private final transient E mQuery;
    private Boolean mTargetValue = null;

    public static final class BooleanQueryBase extends
            QueryableBaseWithMatch<Boolean, BooleanQueryHelper<BooleanQueryBase>> {
        BooleanQueryBase() {
            super();
            setQuery(new BooleanQueryHelper<>(this));
        }

        BooleanQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<BooleanQueryHelper.BooleanQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public BooleanQueryHelper.BooleanQueryBase createFromParcel(Parcel in) {
                        return new BooleanQueryHelper.BooleanQueryBase(in);
                    }

                    public BooleanQueryHelper.BooleanQueryBase[] newArray(int size) {
                        return new BooleanQueryHelper.BooleanQueryBase[size];
                    }
                };
    }

    public BooleanQueryHelper(E query) {
        mQuery = query;
    }

    private BooleanQueryHelper(Parcel in) {
        mQuery = null;
        mTargetValue = readNullableBoolean(in);
    }

    @Override
    public E isTrue() {
        if (mTargetValue != null) {
            throw new IllegalStateException("Cannot set multiple boolean filters");
        }

        mTargetValue = true;

        return mQuery;
    }

    @Override
    public E isFalse() {
        if (mTargetValue != null) {
            throw new IllegalStateException("Cannot set multiple boolean filters");
        }

        mTargetValue = false;

        return mQuery;
    }

    @Override
    public E isEqualTo(boolean value) {
        if (mTargetValue != null) {
            throw new IllegalStateException("Cannot set multiple boolean filters");
        }

        mTargetValue = value;

        return mQuery;
    }

    @Override
    public boolean isEmptyQuery() {
        return mTargetValue == null;
    }

    @Override
    public boolean matches(Boolean value) {
        return (mTargetValue == null) || mTargetValue.equals(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        if (mTargetValue == null) {
            return null;
        }

        return fieldName + "=" + mTargetValue;
    }

    public static boolean matches(BooleanQuery<?> query, Boolean value) {
        return query.matches(value);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        writeNullableBoolean(out, mTargetValue);
    }

    public static final Parcelable.Creator<BooleanQueryHelper> CREATOR =
            new Parcelable.Creator<BooleanQueryHelper>() {
                public BooleanQueryHelper createFromParcel(Parcel in) {
                    return new BooleanQueryHelper(in);
                }

                public BooleanQueryHelper[] newArray(int size) {
                    return new BooleanQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanQueryHelper)) return false;
        BooleanQueryHelper<?> that = (BooleanQueryHelper<?>) o;
        return Objects.equals(mTargetValue, that.mTargetValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTargetValue);
    }

    @Override
    public E matchesAnnotation(com.android.queryable.annotations.BooleanQuery queryAnnotation) {
        if (queryAnnotation.isEqualTo() != OptionalBoolean.ANY) {
            isEqualTo(queryAnnotation.isEqualTo() == OptionalBoolean.TRUE);
        }

        return mQuery;
    }

    public com.android.queryable.annotations.BooleanQuery toAnnotation() {
        return booleanQuery(OptionalBoolean.Companion.from(mTargetValue));
    }

    @AutoAnnotation
    private static com.android.queryable.annotations.BooleanQuery booleanQuery(
            OptionalBoolean isEqualTo) {
        return new AutoAnnotation_BooleanQueryHelper_booleanQuery(
                isEqualTo
        );
    }
}
