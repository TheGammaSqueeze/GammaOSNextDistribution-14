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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.SerializableParcelWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link UriQuery}. */
public final class UriQueryHelper<E extends Queryable>
        implements UriQuery<E>, Serializable{

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private Uri mEqualsValue;
    private final StringQueryHelper<E> mStringValue;

    public static final class UriQueryBase extends
            QueryableBaseWithMatch<Uri, UriQueryHelper<UriQueryBase>> {
        UriQueryBase() {
            super();
            setQuery(new UriQueryHelper<>(this));
        }

        UriQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<UriQueryHelper.UriQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public UriQueryHelper.UriQueryBase createFromParcel(Parcel in) {
                        return new UriQueryHelper.UriQueryBase(in);
                    }

                    public UriQueryHelper.UriQueryBase[] newArray(int size) {
                        return new UriQueryHelper.UriQueryBase[size];
                    }
                };
    }

    public UriQueryHelper(E query) {
        mQuery = query;
        mStringValue = new StringQueryHelper<>(query);
    }

    private UriQueryHelper(Parcel in) {
        mQuery = null;
        mEqualsValue = in.readParcelable(UriQueryHelper.class.getClassLoader());
        mStringValue = in.readParcelable(UriQueryHelper.class.getClassLoader());
    }

    @Override
    public E isEqualTo(Uri uri) {
        this.mEqualsValue = uri;
        return mQuery;
    }

    @Override
    public StringQuery<E> stringValue() {
        return mStringValue;
    }

    @Override
    public boolean isEmptyQuery() {
        return mEqualsValue == null
                && Queryable.isEmptyQuery(mStringValue);
    }

    @Override
    public boolean matches(Uri value) {
        if (mEqualsValue != null && !mEqualsValue.equals(value)) {
            return false;
        }
        if (!mStringValue.matches(value.toString())) {
            return false;
        }

        return true;
    }

    /**
     * {@code true} if all filters are met by the {@link Uri} contained in
     * {@code serializableUri}.
     */
    public boolean matches(SerializableParcelWrapper<Uri> serializableUri) {
        if ((serializableUri == null || serializableUri.get() == null)) {
            return false;
        }

        return matches(serializableUri.get());
    }

    /**
     * @see #matches(Uri).
     */
    public static boolean matches(UriQueryHelper<?> uriQueryHelper, Uri value) {
        return uriQueryHelper.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualsValue != null) {
            queryStrings.add(fieldName + "=" + mEqualsValue);
        }
        if (mStringValue != null) {
            queryStrings.add(mStringValue.describeQuery(fieldName + ".stringValue"));
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
        out.writeParcelable(mStringValue, flags);
    }

    public static final Parcelable.Creator<UriQueryHelper> CREATOR =
            new Parcelable.Creator<UriQueryHelper>() {
                public UriQueryHelper createFromParcel(Parcel in) {
                    return new UriQueryHelper(in);
                }

                public UriQueryHelper[] newArray(int size) {
                    return new UriQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriQueryHelper)) return false;
        UriQueryHelper<?> that = (UriQueryHelper<?>) o;
        return Objects.equals(mEqualsValue, that.mEqualsValue) && Objects.equals(
                mStringValue, that.mStringValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualsValue, mStringValue);
    }
}
