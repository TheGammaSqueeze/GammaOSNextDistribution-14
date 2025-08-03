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

import static com.android.queryable.util.ParcelableUtils.readNullableBoolean;
import static com.android.queryable.util.ParcelableUtils.writeNullableBoolean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link BundleKeyQuery}. */
public final class BundleKeyQueryHelper<E extends Queryable> implements BundleKeyQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private Boolean mExpectsToExist = null;
    private StringQueryHelper<E> mStringQuery = null;
    private IntegerQueryHelper<E> mIntegerQuery = null;
    private LongQueryHelper<E> mLongQuery = null;
    private BooleanQueryHelper<E> mBooleanQuery = null;
    private SerializableQueryHelper<E> mSerializableQuery;
    private BundleQueryHelper<E> mBundleQuery;
    private ListQueryHelper<E, String> mStringListQuery;
    private ListQueryHelper<E, Integer> mIntegerListQuery;

    public BundleKeyQueryHelper(E query) {
        mQuery = query;
    }

    private BundleKeyQueryHelper(Parcel in) {
        mQuery = null;
        mExpectsToExist = readNullableBoolean(in);
        mStringQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mIntegerQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mLongQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mBooleanQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mSerializableQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mBundleQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mStringListQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mIntegerListQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
    }

    @Override
    public E exists() {
        if (mExpectsToExist != null) {
            throw new IllegalStateException(
                    "Cannot call exists() after calling exists() or doesNotExist()");
        }
        mExpectsToExist = true;
        return mQuery;
    }

    @Override
    public E doesNotExist() {
        if (mExpectsToExist != null) {
            throw new IllegalStateException(
                    "Cannot call doesNotExist() after calling exists() or doesNotExist()");
        }
        mExpectsToExist = false;
        return mQuery;
    }

    @Override
    public StringQuery<E> stringValue() {
        if (mStringQuery == null) {
            checkUntyped();
            mStringQuery = new StringQueryHelper<>(mQuery);
        }
        return mStringQuery;
    }

    @Override
    public SerializableQuery<E> serializableValue() {
        if (mSerializableQuery == null) {
            checkUntyped();
            mSerializableQuery = new SerializableQueryHelper<>(mQuery);
        }
        return mSerializableQuery;
    }

    @Override
    public BundleQuery<E> bundleValue() {
        if (mBundleQuery == null) {
            checkUntyped();
            mBundleQuery = new BundleQueryHelper<>(mQuery);
        }
        return mBundleQuery;
    }

    @Override
    public IntegerQuery<E> integerValue() {
        if (mIntegerQuery == null) {
            checkUntyped();
            mIntegerQuery = new IntegerQueryHelper<>(mQuery);
        }
        return mIntegerQuery;
    }

    @Override
    public LongQuery<E> longValue() {
        if (mLongQuery == null) {
            checkUntyped();
            mLongQuery = new LongQueryHelper<>(mQuery);
        }
        return mLongQuery;
    }

    @Override
    public BooleanQuery<E> booleanValue() {
        if (mBooleanQuery == null) {
            checkUntyped();
            mBooleanQuery = new BooleanQueryHelper<>(mQuery);
        }
        return mBooleanQuery;
    }

    @Override
    public ListQuery<E, String> stringListValue() {
        if (mStringListQuery == null) {
            checkUntyped();
            mStringListQuery = new ListQueryHelper<>(mQuery);
        }
        return mStringListQuery;
    }

    @Override
    public ListQuery<E, Integer> integerListValue() {
        if (mIntegerListQuery == null) {
            checkUntyped();
            mIntegerListQuery = new ListQueryHelper<>(mQuery);
        }
        return mIntegerListQuery;
    }

    private void checkUntyped() {
        if (mStringQuery != null || mSerializableQuery != null || mBundleQuery != null
                || mIntegerQuery != null || mLongQuery != null || mBooleanQuery != null
                || mStringListQuery != null || mIntegerListQuery != null) {
            throw new IllegalStateException("Each key can only be typed once");
        }
    }

    @Override
    public boolean isEmptyQuery() {
        return mExpectsToExist == null
                && Queryable.isEmptyQuery(mStringQuery)
                && Queryable.isEmptyQuery(mSerializableQuery)
                && Queryable.isEmptyQuery(mBundleQuery)
                && Queryable.isEmptyQuery(mIntegerQuery)
                && Queryable.isEmptyQuery(mLongQuery)
                && Queryable.isEmptyQuery(mBooleanQuery)
                && Queryable.isEmptyQuery(mStringListQuery)
                && Queryable.isEmptyQuery(mIntegerListQuery);
    }

    public boolean matches(Bundle value, String key) {
        if (mExpectsToExist != null && value.containsKey(key) != mExpectsToExist) {
            return false;
        }
        if (mStringQuery != null && !mStringQuery.matches(value.getString(key))) {
            return false;
        }
        if (mSerializableQuery != null && !mSerializableQuery.matches(value.getSerializable(key))) {
            return false;
        }
        if (mBundleQuery != null && !mBundleQuery.matches(value.getBundle(key))) {
            return false;
        }
        if (mIntegerQuery != null && !mIntegerQuery.matches(value.getInt(key))) {
            return false;
        }
        if (mLongQuery != null && !mLongQuery.matches(value.getLong(key))) {
            return false;
        }
        if (mBooleanQuery != null && !mBooleanQuery.matches(value.getBoolean(key))) {
            return false;
        }
        if (mStringListQuery != null && !mStringListQuery.matches(value.getStringArrayList(key))) {
            return false;
        }
        if (mIntegerListQuery != null && !mIntegerListQuery.matches(
                value.getIntegerArrayList(key))) {
            return false;
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mExpectsToExist != null) {
            queryStrings.add(fieldName + " exists");
        }
        if (mStringQuery != null) {
            queryStrings.add(mStringQuery.describeQuery(fieldName + ".stringValue"));
        }
        if (mSerializableQuery != null) {
            queryStrings.add(mSerializableQuery.describeQuery(fieldName + ".serializableValue"));
        }
        if (mBundleQuery != null) {
            queryStrings.add(mBundleQuery.describeQuery(fieldName + ".bundleValue"));
        }
        if (mIntegerQuery != null) {
            queryStrings.add(mIntegerQuery.describeQuery(fieldName + ".integerValue"));
        }
        if (mLongQuery != null) {
            queryStrings.add(mLongQuery.describeQuery(fieldName + ".longValue"));
        }
        if (mBooleanQuery != null) {
            queryStrings.add(mBooleanQuery.describeQuery(fieldName + ".booleanValue"));
        }
        if (mStringListQuery != null) {
            queryStrings.add(mStringListQuery.describeQuery(fieldName + ".stringListValue"));
        }
        if (mIntegerListQuery != null) {
            queryStrings.add(mIntegerListQuery.describeQuery(fieldName + ".integerListValue"));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        writeNullableBoolean(out, mExpectsToExist);
        out.writeParcelable(mStringQuery, flags);
        out.writeParcelable(mIntegerQuery, flags);
        out.writeParcelable(mLongQuery, flags);
        out.writeParcelable(mBooleanQuery, flags);
        out.writeParcelable(mSerializableQuery, flags);
        out.writeParcelable(mBundleQuery, flags);
        out.writeParcelable(mStringListQuery, flags);
        out.writeParcelable(mIntegerListQuery, flags);
    }

    public static final Parcelable.Creator<BundleKeyQueryHelper> CREATOR =
            new Parcelable.Creator<BundleKeyQueryHelper>() {
                public BundleKeyQueryHelper createFromParcel(Parcel in) {
                    return new BundleKeyQueryHelper(in);
                }

                public BundleKeyQueryHelper[] newArray(int size) {
                    return new BundleKeyQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleKeyQueryHelper)) return false;
        BundleKeyQueryHelper<?> that = (BundleKeyQueryHelper<?>) o;
        return Objects.equals(mExpectsToExist, that.mExpectsToExist)
                && Objects.equals(mStringQuery, that.mStringQuery)
                && Objects.equals(mIntegerQuery, that.mIntegerQuery)
                && Objects.equals(mLongQuery, that.mLongQuery) && Objects.equals(
                mBooleanQuery, that.mBooleanQuery) && Objects.equals(mSerializableQuery,
                that.mSerializableQuery) && Objects.equals(mBundleQuery, that.mBundleQuery)
                && Objects.equals(mStringListQuery, that.mStringListQuery)
                && Objects.equals(mIntegerListQuery, that.mIntegerListQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExpectsToExist, mStringQuery, mIntegerQuery, mLongQuery, mBooleanQuery,
                mSerializableQuery, mBundleQuery, mStringListQuery, mIntegerListQuery);
    }
}
