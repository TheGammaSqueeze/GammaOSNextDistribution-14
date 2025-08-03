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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.SerializableParcelWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementation of {@link BundleQuery}. */
public final class BundleQueryHelper<E extends Queryable> implements BundleQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final Map<String, BundleKeyQueryHelper<E>> mKeyQueryHelpers;

    public static final class BundleQueryBase extends
            QueryableBaseWithMatch<Bundle, BundleQueryHelper<BundleQueryBase>> {
        BundleQueryBase() {
            super();
            setQuery(new BundleQueryHelper<>(this));
        }

        BundleQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<BundleQueryHelper.BundleQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public BundleQueryHelper.BundleQueryBase createFromParcel(Parcel in) {
                        return new BundleQueryHelper.BundleQueryBase(in);
                    }

                    public BundleQueryHelper.BundleQueryBase[] newArray(int size) {
                        return new BundleQueryHelper.BundleQueryBase[size];
                    }
                };
    }

    public BundleQueryHelper(E query) {
        mQuery = query;
        mKeyQueryHelpers = new HashMap<>();
    }

    private BundleQueryHelper(Parcel in) {
        mQuery = null;
        mKeyQueryHelpers = in.readHashMap(BundleQueryHelper.class.getClassLoader());
    }

    @Override
    public BundleKeyQuery<E> key(String key) {
        if (!mKeyQueryHelpers.containsKey(key)) {
            mKeyQueryHelpers.put(key, new BundleKeyQueryHelper<>(mQuery));
        }
        return mKeyQueryHelpers.get(key);
    }

    @Override
    public boolean isEmptyQuery() {
        for (Map.Entry<String, BundleKeyQueryHelper<E>> keyQueries : mKeyQueryHelpers.entrySet()) {
            if (!Queryable.isEmptyQuery(keyQueries.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(Bundle value) {
        for (Map.Entry<String, BundleKeyQueryHelper<E>> keyQueries : mKeyQueryHelpers.entrySet()) {
            if (!keyQueries.getValue().matches(value, keyQueries.getKey())) {
                return false;
            }
        }

        return true;
    }

    public boolean matches(SerializableParcelWrapper<Bundle> serializableBundle) {
        if ((serializableBundle == null || serializableBundle.get() == null)) {
            if (mKeyQueryHelpers.isEmpty()) {
                return true;
            }
            return false;
        }

        return matches(serializableBundle.get());
    }

    public static boolean matches(BundleQueryHelper<?> bundleQueryHelper, Bundle value) {
        return bundleQueryHelper.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        for (Map.Entry<String, BundleKeyQueryHelper<E>> query : mKeyQueryHelpers.entrySet()) {
            queryStrings.add(query.getValue().describeQuery(fieldName + "." + query.getKey()));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeMap(mKeyQueryHelpers);
    }

    public static final Parcelable.Creator<BundleQueryHelper> CREATOR =
            new Parcelable.Creator<BundleQueryHelper>() {
                public BundleQueryHelper createFromParcel(Parcel in) {
                    return new BundleQueryHelper(in);
                }

                public BundleQueryHelper[] newArray(int size) {
                    return new BundleQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleQueryHelper)) return false;
        BundleQueryHelper<?> that = (BundleQueryHelper<?>) o;
        return Objects.equals(mKeyQueryHelpers, that.mKeyQueryHelpers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeyQueryHelpers);
    }
}
