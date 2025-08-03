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
import com.android.queryable.info.ActivityInfo;

import java.util.Objects;

/** Implementation of {@link ActivityQuery}. */
public class ActivityQueryHelper<E extends Queryable> implements ActivityQuery<E> {

    public static final class ActivityQueryBase extends
            QueryableBaseWithMatch<ActivityInfo, ActivityQueryHelper<ActivityQueryBase>> {
        ActivityQueryBase() {
            super();
            setQuery(new ActivityQueryHelper<>(this));
        }

        ActivityQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ActivityQueryHelper.ActivityQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public ActivityQueryHelper.ActivityQueryBase createFromParcel(Parcel in) {
                        return new ActivityQueryHelper.ActivityQueryBase(in);
                    }

                    public ActivityQueryHelper.ActivityQueryBase[] newArray(int size) {
                        return new ActivityQueryHelper.ActivityQueryBase[size];
                    }
                };
    }

    private final transient E mQuery;
    private final ClassQueryHelper<E> mActivityClassQueryHelper;
    private final BooleanQueryHelper<E> mExportedQueryHelper;
    private final SetQueryHelper<E, IntentFilter>
            mIntentFiltersQueryHelper;
    private StringQueryHelper<E> mPermission;

    public ActivityQueryHelper(E query) {
        mQuery = query;
        mActivityClassQueryHelper = new ClassQueryHelper<>(query);
        mExportedQueryHelper = new BooleanQueryHelper<>(query);
        mIntentFiltersQueryHelper = new SetQueryHelper<>(query);
    }

    private ActivityQueryHelper(Parcel in) {
        mQuery = null;
        mActivityClassQueryHelper = in.readParcelable(ActivityQueryHelper.class.getClassLoader());
        mExportedQueryHelper = in.readParcelable(ActivityQueryHelper.class.getClassLoader());
        mIntentFiltersQueryHelper = in.readParcelable(ActivityQueryHelper.class.getClassLoader());
    }

    @Override
    public ClassQuery<E> activityClass() {
        return mActivityClassQueryHelper;
    }

    @Override
    public BooleanQuery<E> exported() {
        return mExportedQueryHelper;
    }

    @Override
    public SetQuery<E, IntentFilter> intentFilters() {
        return mIntentFiltersQueryHelper;
    }

    @Override
    public StringQuery<E> permission() {
        if (mPermission == null) {
            mPermission = new StringQueryHelper<>(mQuery);
        }
        return mPermission;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mPermission)
                && Queryable.isEmptyQuery(mActivityClassQueryHelper)
                && Queryable.isEmptyQuery(mIntentFiltersQueryHelper);
    }

    @Override
    public boolean matches(ActivityInfo value) {
        if (mPermission == null) {
            if (value.permission() != null) {
                return false;
            }
        }

        return mActivityClassQueryHelper.matches(value)
                && mExportedQueryHelper.matches(value.exported())
                && mIntentFiltersQueryHelper.matches(value.intentFilters())
                && (mPermission == null || mPermission.matches(value.permission()));
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
          mActivityClassQueryHelper.describeQuery(fieldName + ".activity"),
          mExportedQueryHelper.describeQuery(fieldName + ".exported"),
          mIntentFiltersQueryHelper.describeQuery(fieldName + ".intentFilters")
        );
    }

    public static boolean matches(ActivityQueryHelper<?> activityQueryHelper, ActivityInfo value) {
        return activityQueryHelper.matches(value);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mActivityClassQueryHelper, flags);
        out.writeParcelable(mExportedQueryHelper, flags);
        out.writeParcelable(mIntentFiltersQueryHelper, flags);
    }

    public static final Parcelable.Creator<ActivityQueryHelper> CREATOR =
            new Parcelable.Creator<ActivityQueryHelper>() {
                public ActivityQueryHelper createFromParcel(Parcel in) {
                    return new ActivityQueryHelper(in);
                }

                public ActivityQueryHelper[] newArray(int size) {
                    return new ActivityQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivityQueryHelper)) return false;
        ActivityQueryHelper<?> that = (ActivityQueryHelper<?>) o;
        return Objects.equals(mActivityClassQueryHelper, that.mActivityClassQueryHelper)
                && Objects.equals(mExportedQueryHelper, that.mExportedQueryHelper)
                && Objects.equals(mIntentFiltersQueryHelper, that.mIntentFiltersQueryHelper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mActivityClassQueryHelper, mExportedQueryHelper,
                mIntentFiltersQueryHelper);
    }
}
