/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.info.ReceiverInfo;

import java.util.Objects;

/**
 * Implementation of {@link ReceiverQuery}.
 *
 * @param <E> Type of query.
 */
public class ReceiverQueryHelper<E extends Queryable> implements ReceiverQuery<E> {

    private final transient E mQuery;

    private final ClassQueryHelper<E> mNameQueryHelper;

    private final SetQueryHelper<E, Bundle> mMetadataQueryHelper;

    public static final class ReceiverQueryBase extends
            QueryableBaseWithMatch<ReceiverInfo, ReceiverQueryHelper<ReceiverQueryBase>> {
        ReceiverQueryBase() {
            super();
            setQuery(new ReceiverQueryHelper<>(this));
        }

        ReceiverQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ReceiverQueryHelper.ReceiverQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public ReceiverQueryHelper.ReceiverQueryBase createFromParcel(Parcel in) {
                        return new ReceiverQueryHelper.ReceiverQueryBase(in);
                    }

                    public ReceiverQueryHelper.ReceiverQueryBase[] newArray(int size) {
                        return new ReceiverQueryHelper.ReceiverQueryBase[size];
                    }
                };
    }

    public ReceiverQueryHelper(E query) {
        mQuery = query;
        mNameQueryHelper = new ClassQueryHelper<>(query);
        mMetadataQueryHelper = new SetQueryHelper<>(query);
    }

    private ReceiverQueryHelper(Parcel in) {
        mQuery = null;
        mNameQueryHelper = in.readParcelable(ReceiverQueryHelper.class.getClassLoader());
        mMetadataQueryHelper = in.readParcelable(ReceiverQueryHelper.class.getClassLoader());
    }

    @Override
    public ClassQuery<E> name() {
        return mNameQueryHelper;
    }

    @Override
    public SetQueryHelper<E, Bundle> metadata() {
        return mMetadataQueryHelper;
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mNameQueryHelper.describeQuery(fieldName + ".name"),
                mMetadataQueryHelper.describeQuery(fieldName + ".metadata"));
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mNameQueryHelper);
    }

    @Override
    public boolean matches(ReceiverInfo value) {
        return mNameQueryHelper.matches(value) &&
                mMetadataQueryHelper.matches(value.metadata());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mNameQueryHelper, flags);
        dest.writeParcelable(mMetadataQueryHelper, flags);
    }

    public static final Parcelable.Creator<ReceiverQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public ReceiverQueryHelper createFromParcel(Parcel in) {
                    return new ReceiverQueryHelper(in);
                }

                public ReceiverQueryHelper[] newArray(int size) {
                    return new ReceiverQueryHelper[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReceiverQueryHelper)) return false;
        ReceiverQueryHelper<?> that = (ReceiverQueryHelper<?>) o;
        return Objects.equals(mNameQueryHelper, that.mNameQueryHelper) &&
                Objects.equals(mMetadataQueryHelper, that.mMetadataQueryHelper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNameQueryHelper, mMetadataQueryHelper);
    }
}
