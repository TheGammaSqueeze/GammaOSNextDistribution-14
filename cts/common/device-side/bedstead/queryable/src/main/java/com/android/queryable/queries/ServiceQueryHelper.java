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

import android.content.IntentFilter;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.info.ServiceInfo;

import java.util.Objects;

/**
 * Implementation of {@link ServiceQuery}.
 *
 * @param <E> Type of query.
 */
public final class ServiceQueryHelper<E extends Queryable> implements ServiceQuery<E> {

    private final transient E mQuery;
    private final ClassQueryHelper<E> mServiceClassQueryHelper;
    private final SetQueryHelper<E, IntentFilter>
            mIntentFiltersQueryHelper;

    public static final class ServiceQueryBase extends
            QueryableBaseWithMatch<ServiceInfo, ServiceQueryHelper<ServiceQueryBase>> {
        ServiceQueryBase() {
            super();
            setQuery(new ServiceQueryHelper<>(this));
        }

        ServiceQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ServiceQueryHelper.ServiceQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public ServiceQueryHelper.ServiceQueryBase createFromParcel(Parcel in) {
                        return new ServiceQueryHelper.ServiceQueryBase(in);
                    }

                    public ServiceQueryHelper.ServiceQueryBase[] newArray(int size) {
                        return new ServiceQueryHelper.ServiceQueryBase[size];
                    }
                };
    }

    public ServiceQueryHelper(E query) {
        mQuery = query;
        mServiceClassQueryHelper = new ClassQueryHelper<>(query);
        mIntentFiltersQueryHelper = new SetQueryHelper<>(query);
    }

    private ServiceQueryHelper(Parcel in) {
        mQuery = null;
        mServiceClassQueryHelper = in.readParcelable(ServiceQueryHelper.class.getClassLoader());
        mIntentFiltersQueryHelper = in.readParcelable(ServiceQueryHelper.class.getClassLoader());
    }

    @Override
    public ClassQuery<E> serviceClass() {
        return mServiceClassQueryHelper;
    }

    @Override
    public SetQuery<E, IntentFilter> intentFilters() {
        return mIntentFiltersQueryHelper;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mServiceClassQueryHelper)
                && Queryable.isEmptyQuery(mIntentFiltersQueryHelper);
    }

    @Override
    public boolean matches(ServiceInfo value) {
        return mServiceClassQueryHelper.matches(value)
                && mIntentFiltersQueryHelper.matches(value.intentFilters());
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mServiceClassQueryHelper.describeQuery(fieldName + ".service"),
                mIntentFiltersQueryHelper.describeQuery(fieldName + ".intentFilters")
        );
    }

    /**
     * Check if a {@link ServiceQueryHelper} matches the service represented by a
     * {@link ServiceInfo}.
     */
    public static boolean matches(ServiceQueryHelper<?> serviceQueryHelper, ServiceInfo value) {
        return serviceQueryHelper.matches(value);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mServiceClassQueryHelper, flags);
        out.writeParcelable(mIntentFiltersQueryHelper, flags);
    }

    public static final Parcelable.Creator<ServiceQueryHelper> CREATOR =
            new Parcelable.Creator<ServiceQueryHelper>() {
                public ServiceQueryHelper createFromParcel(Parcel in) {
                    return new ServiceQueryHelper(in);
                }

                public ServiceQueryHelper[] newArray(int size) {
                    return new ServiceQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceQueryHelper)) return false;
        ServiceQueryHelper<?> that = (ServiceQueryHelper<?>) o;
        return Objects.equals(mServiceClassQueryHelper, that.mServiceClassQueryHelper)
                && Objects.equals(mIntentFiltersQueryHelper, that.mIntentFiltersQueryHelper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mServiceClassQueryHelper, mIntentFiltersQueryHelper);
    }
}
