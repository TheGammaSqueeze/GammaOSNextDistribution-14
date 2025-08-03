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
import com.android.queryable.QueryableBaseWithMatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link SerializableQuery}. */
public final class SerializableQueryHelper<E extends Queryable>
        implements SerializableQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private Serializable mEqualsValue;

    public static final class SerializableQueryBase extends
            QueryableBaseWithMatch<Serializable, SerializableQueryHelper<SerializableQueryBase>> {
        SerializableQueryBase() {
            super();
            setQuery(new SerializableQueryHelper<>(this));
        }

        SerializableQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<SerializableQueryHelper.SerializableQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public SerializableQueryHelper.SerializableQueryBase createFromParcel(
                            Parcel in) {
                        return new SerializableQueryHelper.SerializableQueryBase(in);
                    }

                    public SerializableQueryHelper.SerializableQueryBase[] newArray(int size) {
                        return new SerializableQueryHelper.SerializableQueryBase[size];
                    }
                };
    }

    public SerializableQueryHelper(E query) {
        mQuery = query;
    }

    private SerializableQueryHelper(Parcel in) {
        mQuery = null;
        mEqualsValue = in.readSerializable();
    }

    @Override
    public E isEqualTo(Serializable serializable) {
        this.mEqualsValue = serializable;
        return mQuery;
    }

    @Override
    public boolean isEmptyQuery() {
        return mEqualsValue == null;
    }

    @Override
    public boolean matches(Serializable value) {
        if (mEqualsValue != null && !mEqualsValue.equals(value)) {
            return false;
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualsValue != null) {
            queryStrings.add(fieldName + "=" + mEqualsValue);
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeSerializable(mEqualsValue);
    }

    public static final Parcelable.Creator<SerializableQueryHelper> CREATOR =
            new Parcelable.Creator<SerializableQueryHelper>() {
                public SerializableQueryHelper createFromParcel(Parcel in) {
                    return new SerializableQueryHelper(in);
                }

                public SerializableQueryHelper[] newArray(int size) {
                    return new SerializableQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SerializableQueryHelper)) return false;
        SerializableQueryHelper<?> that = (SerializableQueryHelper<?>) o;
        return Objects.equals(mEqualsValue, that.mEqualsValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualsValue);
    }
}
