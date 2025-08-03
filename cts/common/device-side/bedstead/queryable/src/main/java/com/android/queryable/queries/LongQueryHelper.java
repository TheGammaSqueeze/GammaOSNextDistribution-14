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

import static com.android.queryable.util.ParcelableUtils.readNullableLong;
import static com.android.queryable.util.ParcelableUtils.writeNullableLong;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link LongQuery}. */
public final class LongQueryHelper<E extends Queryable> implements LongQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    @Nullable private Long mEqualToValue = null;
    @Nullable private Long mGreaterThanValue = null;
    @Nullable private Long mGreaterThanOrEqualToValue = null;
    @Nullable private Long mLessThanValue = null;
    @Nullable private Long mLessThanOrEqualToValue = null;

    private final transient E mQuery;

    public static final class LongQueryBase extends
            QueryableBaseWithMatch<Long, LongQueryHelper<LongQueryBase>> {
        LongQueryBase() {
            super();
            setQuery(new LongQueryHelper<>(this));
        }

        LongQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<LongQueryHelper.LongQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public LongQueryHelper.LongQueryBase createFromParcel(Parcel in) {
                        return new LongQueryHelper.LongQueryBase(in);
                    }

                    public LongQueryHelper.LongQueryBase[] newArray(int size) {
                        return new LongQueryHelper.LongQueryBase[size];
                    }
                };
    }

    public LongQueryHelper(E query) {
        mQuery = query;
    }

    private LongQueryHelper(Parcel in) {
        mQuery = null;
        mEqualToValue = readNullableLong(in);
        mGreaterThanValue = readNullableLong(in);
        mGreaterThanOrEqualToValue = readNullableLong(in);
        mLessThanValue = readNullableLong(in);
        mLessThanOrEqualToValue = readNullableLong(in);
    }

    @Override
    public E isEqualTo(long i) {
        mEqualToValue = i;
        return mQuery;
    }

    @Override
    public E isGreaterThan(long i) {
        if (mGreaterThanValue == null) {
            mGreaterThanValue = i;
        } else {
            mGreaterThanValue = Math.max(mGreaterThanValue, i);
        }
        return mQuery;
    }

    @Override
    public E isGreaterThanOrEqualTo(long i) {
        if (mGreaterThanOrEqualToValue == null) {
            mGreaterThanOrEqualToValue = i;
        } else {
            mGreaterThanOrEqualToValue = Math.max(mGreaterThanOrEqualToValue, i);
        }
        return mQuery;
    }

    @Override
    public E isLessThan(long i) {
        if (mLessThanValue == null) {
            mLessThanValue = i;
        } else {
            mLessThanValue = Math.min(mLessThanValue, i);
        }
        return mQuery;
    }

    @Override
    public E isLessThanOrEqualTo(long i) {
        if (mLessThanOrEqualToValue == null) {
            mLessThanOrEqualToValue = i;
        } else {
            mLessThanOrEqualToValue = Math.min(mLessThanOrEqualToValue, i);
        }
        return mQuery;
    }

    @Override
    public boolean matches(Long value) {
        return matches(value.longValue());
    }

    @Override
    public boolean isEmptyQuery() {
        return mEqualToValue == null
                && mGreaterThanValue == null
                && mGreaterThanOrEqualToValue == null
                && mLessThanValue == null
                && mLessThanOrEqualToValue == null;
    }

    /** {@code true} if all filters are met by {@code value}. */
    public boolean matches(long value) {
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
        writeNullableLong(out, mEqualToValue);
        writeNullableLong(out, mGreaterThanValue);
        writeNullableLong(out, mGreaterThanOrEqualToValue);
        writeNullableLong(out, mLessThanValue);
        writeNullableLong(out, mLessThanOrEqualToValue);
    }

    public static final Parcelable.Creator<LongQueryHelper> CREATOR =
            new Parcelable.Creator<LongQueryHelper>() {
                public LongQueryHelper createFromParcel(Parcel in) {
                    return new LongQueryHelper(in);
                }

                public LongQueryHelper[] newArray(int size) {
                    return new LongQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongQueryHelper)) return false;
        LongQueryHelper<?> that = (LongQueryHelper<?>) o;
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
