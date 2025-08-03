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
import android.os.UserHandle;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.SerializableParcelWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link UserHandleQuery}. */
public final class UserHandleQueryHelper<E extends Queryable>
        implements UserHandleQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private UserHandle mEqualsValue;
    private IntegerQueryHelper<E> mIdQuery;

    public static final class UserHandleQueryBase extends
            QueryableBaseWithMatch<UserHandle, UserHandleQueryHelper<UserHandleQueryBase>> {
        UserHandleQueryBase() {
            super();
            setQuery(new UserHandleQueryHelper<>(this));
        }

        UserHandleQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<UserHandleQueryHelper.UserHandleQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public UserHandleQueryHelper.UserHandleQueryBase createFromParcel(Parcel in) {
                        return new UserHandleQueryHelper.UserHandleQueryBase(in);
                    }

                    public UserHandleQueryHelper.UserHandleQueryBase[] newArray(int size) {
                        return new UserHandleQueryHelper.UserHandleQueryBase[size];
                    }
                };
    }

    public UserHandleQueryHelper(E query) {
        mQuery = query;
    }

    private UserHandleQueryHelper(Parcel in) {
        mQuery = null;
        mEqualsValue = in.readParcelable(UserHandleQueryHelper.class.getClassLoader());
        mIdQuery = in.readParcelable(UserHandleQueryHelper.class.getClassLoader());
    }

    @Override
    public E isEqualTo(UserHandle userHandle) {
        mEqualsValue = userHandle;
        return mQuery;
    }

    @Override
    public IntegerQuery<E> id() {
        if (mIdQuery == null) {
            mIdQuery = new IntegerQueryHelper<>(mQuery);
        }
        return mIdQuery;
    }

    @Override
    public boolean isEmptyQuery() {
        return mEqualsValue == null
                && Queryable.isEmptyQuery(mIdQuery);
    }

    /**
     * {@code true} if all filters are met by the {@link UserHandle}.
     */
    @Override
    public boolean matches(UserHandle value) {
        if (mEqualsValue != null && !mEqualsValue.equals(value)) {
            return false;
        }

        if (mIdQuery != null && !mIdQuery.matches(value.getIdentifier())) {
            return false;
        }

        return true;
    }

    /**
     * {@code true} if all filters are met by the {@link UserHandle} contained in
     * {@code serializableBundle}.
     */
    public boolean matches(SerializableParcelWrapper<UserHandle> serializableBundle) {
        if ((serializableBundle == null || serializableBundle.get() == null)) {
            return mEqualsValue == null && mIdQuery == null;
        }

        return matches(serializableBundle.get());
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualsValue != null) {
            queryStrings.add(fieldName + "=" + mEqualsValue);
        }
        if (mIdQuery != null) {
            queryStrings.add(mIdQuery.describeQuery(fieldName + ".id"));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mEqualsValue, flags);
        out.writeParcelable(mIdQuery, flags);
    }

    public static final Parcelable.Creator<UserHandleQueryHelper> CREATOR =
            new Parcelable.Creator<UserHandleQueryHelper>() {
                public UserHandleQueryHelper createFromParcel(Parcel in) {
                    return new UserHandleQueryHelper(in);
                }

                public UserHandleQueryHelper[] newArray(int size) {
                    return new UserHandleQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserHandleQueryHelper)) return false;
        UserHandleQueryHelper<?> that = (UserHandleQueryHelper<?>) o;
        return Objects.equals(mEqualsValue, that.mEqualsValue) && Objects.equals(
                mIdQuery, that.mIdQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualsValue, mIdQuery);
    }
}
