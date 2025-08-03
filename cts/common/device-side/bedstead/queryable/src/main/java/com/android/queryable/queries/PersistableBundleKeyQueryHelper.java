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

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

import com.android.queryable.Queryable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link PersistableBundleKeyQuery}. */
public final class PersistableBundleKeyQueryHelper<E extends Queryable>
        implements PersistableBundleKeyQuery<E> {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private Boolean mExpectsToExist = null;
    private StringQueryHelper<E> mStringQuery = null;
    private BooleanQueryHelper<E> mBooleanQuery = null;
    private PersistableBundleQueryHelper<E> mPersistableBundleQuery;

    public PersistableBundleKeyQueryHelper(E query) {
        mQuery = query;
    }

    private PersistableBundleKeyQueryHelper(Parcel in) {
        mQuery = null;
        mExpectsToExist = readNullableBoolean(in);
        mStringQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mBooleanQuery = in.readParcelable(BundleKeyQueryHelper.class.getClassLoader());
        mPersistableBundleQuery = in.readParcelable(
                PersistableBundleKeyQueryHelper.class.getClassLoader());
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
    public BooleanQuery<E> booleanValue() {
        if (mBooleanQuery == null) {
            checkUntyped();
            mBooleanQuery = new BooleanQueryHelper<>(mQuery);
        }
        return mBooleanQuery;
    }

    @Override
    public PersistableBundleQuery<E> persistableBundleValue() {
        if (mPersistableBundleQuery == null) {
            checkUntyped();
            mPersistableBundleQuery = new PersistableBundleQueryHelper<>(mQuery);
        }
        return mPersistableBundleQuery;
    }

    private void checkUntyped() {
        if (mStringQuery != null || mPersistableBundleQuery != null || mBooleanQuery != null) {
            throw new IllegalStateException("Each key can only be typed once");
        }
    }

    @Override
    public boolean isEmptyQuery() {
        return mExpectsToExist == null
                && Queryable.isEmptyQuery(mStringQuery)
                && Queryable.isEmptyQuery(mPersistableBundleQuery)
                && Queryable.isEmptyQuery(mBooleanQuery);
    }

    public boolean matches(PersistableBundle value, String key) {
        if (mExpectsToExist != null && value.containsKey(key) != mExpectsToExist) {
            return false;
        }
        if (mStringQuery != null && !mStringQuery.matches(value.getString(key))) {
            return false;
        }
        if (mBooleanQuery != null && !mBooleanQuery.matches(value.getBoolean(key))) {
            return false;
        }
        if (mPersistableBundleQuery != null
                && !mPersistableBundleQuery.matches(value.getPersistableBundle(key))) {
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
        if (mBooleanQuery != null) {
            queryStrings.add(mBooleanQuery.describeQuery(fieldName + ".booleanValue"));
        }
        if (mPersistableBundleQuery != null) {
            queryStrings.add(mPersistableBundleQuery.describeQuery(
                    fieldName + ".persistableBundleValue"));
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
        out.writeParcelable(mBooleanQuery, flags);
        out.writeParcelable(mPersistableBundleQuery, flags);
    }

    public static final Parcelable.Creator<PersistableBundleKeyQueryHelper> CREATOR =
            new Parcelable.Creator<PersistableBundleKeyQueryHelper>() {
                public PersistableBundleKeyQueryHelper createFromParcel(Parcel in) {
                    return new PersistableBundleKeyQueryHelper(in);
                }

                public PersistableBundleKeyQueryHelper[] newArray(int size) {
                    return new PersistableBundleKeyQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistableBundleKeyQueryHelper)) return false;
        PersistableBundleKeyQueryHelper<?> that = (PersistableBundleKeyQueryHelper<?>) o;
        return Objects.equals(mExpectsToExist, that.mExpectsToExist)
                && Objects.equals(mStringQuery, that.mStringQuery)
                && Objects.equals(mBooleanQuery, that.mBooleanQuery)
                && Objects.equals(mPersistableBundleQuery, that.mPersistableBundleQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExpectsToExist, mStringQuery, mBooleanQuery, mPersistableBundleQuery);
    }
}
