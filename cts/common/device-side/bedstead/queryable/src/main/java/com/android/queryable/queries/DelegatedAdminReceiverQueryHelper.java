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
import com.android.queryable.info.DelegatedAdminReceiverInfo;

import java.util.Objects;

/** Implementation of {@link DelegatedAdminReceiverQuery}. */
public final class DelegatedAdminReceiverQueryHelper<E extends Queryable>
        implements DelegatedAdminReceiverQuery<E> {

    private final transient E mQuery;
    private final BroadcastReceiverQueryHelper<E> mBroadcastReceiverQueryHelper;

    public static final class DelegatedAdminReceiverQueryBase extends
            QueryableBaseWithMatch<DelegatedAdminReceiverInfo,
                    DelegatedAdminReceiverQueryHelper<DelegatedAdminReceiverQueryBase>> {
        DelegatedAdminReceiverQueryBase() {
            super();
            setQuery(new DelegatedAdminReceiverQueryHelper<>(this));
        }

        DelegatedAdminReceiverQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase createFromParcel(Parcel in) {
                        return new DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase(in);
                    }

                    public DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase[] newArray(int size) {
                        return new DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase[size];
                    }
                };
    }

    public DelegatedAdminReceiverQueryHelper(E query) {
        mQuery = query;
        mBroadcastReceiverQueryHelper = new BroadcastReceiverQueryHelper<>(query);
    }

    private DelegatedAdminReceiverQueryHelper(Parcel in) {
        mQuery = null;
        mBroadcastReceiverQueryHelper = in.readParcelable(
                DelegatedAdminReceiverQueryHelper.class.getClassLoader());
    }

    @Override
    public BroadcastReceiverQuery<E> broadcastReceiver() {
        return mBroadcastReceiverQueryHelper;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mBroadcastReceiverQueryHelper);
    }

    /** {@code true} if all filters are met by {@code value}. */
    @Override
    public boolean matches(DelegatedAdminReceiverInfo value) {
        return mBroadcastReceiverQueryHelper.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mBroadcastReceiverQueryHelper.describeQuery(fieldName + ".broadcastReceiver")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mBroadcastReceiverQueryHelper, flags);
    }

    public static final Parcelable.Creator<DelegatedAdminReceiverQueryHelper> CREATOR =
            new Parcelable.Creator<DelegatedAdminReceiverQueryHelper>() {
                public DelegatedAdminReceiverQueryHelper createFromParcel(Parcel in) {
                    return new DelegatedAdminReceiverQueryHelper(in);
                }

                public DelegatedAdminReceiverQueryHelper[] newArray(int size) {
                    return new DelegatedAdminReceiverQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DelegatedAdminReceiverQueryHelper)) return false;
        DelegatedAdminReceiverQueryHelper<?> that = (DelegatedAdminReceiverQueryHelper<?>) o;
        return Objects.equals(mBroadcastReceiverQueryHelper,
                that.mBroadcastReceiverQueryHelper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBroadcastReceiverQueryHelper);
    }
}
