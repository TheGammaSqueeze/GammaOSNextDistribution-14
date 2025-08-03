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
import android.os.PersistableBundle;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.SerializableParcelWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementation of {@link PersistableBundleQuery}. */
public final class PersistableBundleQueryHelper<E extends Queryable>
        implements PersistableBundleQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final Map<String, PersistableBundleKeyQueryHelper<E>> mKeyQueryHelpers;

    public static final class PersistableBundleQueryBase extends
            QueryableBaseWithMatch<PersistableBundle,
                    PersistableBundleQueryHelper<PersistableBundleQueryBase>> {
        PersistableBundleQueryBase() {
            super();
            setQuery(new PersistableBundleQueryHelper<>(this));
        }

        PersistableBundleQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<PersistableBundleQueryHelper.PersistableBundleQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public PersistableBundleQueryHelper.PersistableBundleQueryBase createFromParcel(
                            Parcel in) {
                        return new PersistableBundleQueryHelper.PersistableBundleQueryBase(in);
                    }

                    public PersistableBundleQueryHelper.PersistableBundleQueryBase[] newArray(
                            int size) {
                        return new PersistableBundleQueryHelper.PersistableBundleQueryBase[size];
                    }
                };
    }

    public PersistableBundleQueryHelper(E query) {
        mQuery = query;
        mKeyQueryHelpers = new HashMap<>();
    }

    private PersistableBundleQueryHelper(Parcel in) {
        mQuery = null;

        mKeyQueryHelpers =
                in.readHashMap(PersistableBundleQueryHelper.class.getClassLoader());
    }

    @Override
    public PersistableBundleKeyQuery<E> key(String key) {
        if (!mKeyQueryHelpers.containsKey(key)) {
            mKeyQueryHelpers.put(key, new PersistableBundleKeyQueryHelper<>(mQuery));
        }
        return mKeyQueryHelpers.get(key);
    }

    @Override
    public boolean isEmptyQuery() {
        for (Map.Entry<String, PersistableBundleKeyQueryHelper<E>> keyQueries :
                mKeyQueryHelpers.entrySet()) {
            if (!Queryable.isEmptyQuery(keyQueries.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(PersistableBundle value) {
        for (Map.Entry<String, PersistableBundleKeyQueryHelper<E>> keyQueries :
                mKeyQueryHelpers.entrySet()) {
            if (!keyQueries.getValue().matches(value, keyQueries.getKey())) {
                return false;
            }
        }

        return true;
    }

    public boolean matches(SerializableParcelWrapper<PersistableBundle> serializableBundle) {
        if ((serializableBundle == null || serializableBundle.get() == null)) {
            if (mKeyQueryHelpers.isEmpty()) {
                return true;
            }
            return false;
        }

        return matches(serializableBundle.get());
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        for (Map.Entry<String, PersistableBundleKeyQueryHelper<E>> query :
                mKeyQueryHelpers.entrySet()) {
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

    public static final Parcelable.Creator<PersistableBundleQueryHelper> CREATOR =
            new Parcelable.Creator<PersistableBundleQueryHelper>() {
                public PersistableBundleQueryHelper createFromParcel(Parcel in) {
                    return new PersistableBundleQueryHelper(in);
                }

                public PersistableBundleQueryHelper[] newArray(int size) {
                    return new PersistableBundleQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistableBundleQueryHelper)) return false;
        PersistableBundleQueryHelper<?> that = (PersistableBundleQueryHelper<?>) o;
        return Objects.equals(mKeyQueryHelpers, that.mKeyQueryHelpers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeyQueryHelpers);
    }
}
