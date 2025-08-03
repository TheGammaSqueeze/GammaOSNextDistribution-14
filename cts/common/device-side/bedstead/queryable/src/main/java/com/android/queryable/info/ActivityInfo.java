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

package com.android.queryable.info;

import static com.android.queryable.util.ParcelableUtils.readBoolean;
import static com.android.queryable.util.ParcelableUtils.writeBoolean;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.collections.QueryableIntentFilterHashSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Wrapper for information about an {@link Activity}.
 *
 * <p>This is used instead of {@link Activity} so that it can be easily serialized.
 */
public final class ActivityInfo extends ClassInfo {

    private final boolean mExported;
    private final Set<IntentFilter> mIntentFilters;
    private final String mPermission;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(android.content.pm.ActivityInfo activityInfo) {
        return builder()
                .activityClass(activityInfo.name)
                .exported(activityInfo.exported)
                .permission(activityInfo.permission);
    }

    private ActivityInfo(String activityClass, boolean exported,
            Set<IntentFilter> intentFilters, String permission) {
        super(activityClass);
        mExported = exported;
        if (intentFilters == null) {
            mIntentFilters = new HashSet<>();
        } else {
            mIntentFilters = intentFilters;
        }
        mPermission = permission;
    }

    private ActivityInfo(Parcel parcel) {
        super((Parcel) parcel);
        mExported = readBoolean(parcel);
        List<IntentFilter> intentList = new ArrayList<>();
        parcel.readList(intentList, IntentFilter.class.getClassLoader());
        mIntentFilters = new HashSet<>(intentList);
        mPermission = parcel.readString();
    }

    public boolean exported() {
        return mExported;
    }

    /** Return the permission required to launch this activity. */
    public String permission() {
        return mPermission;
    }

    /** Return the intent filters of this activity.*/
    public QueryableIntentFilterHashSet intentFilters() {
        return new QueryableIntentFilterHashSet(mIntentFilters);
    }

    @Override
    public String toString() {
        return "Activity{"
                + "class=" + super.toString()
                + ", exported=" + mExported
                + ", intentFilters=" + mIntentFilters
                + ", permission=" + mPermission
                + "}";
    }

    public static final class Builder {
        String mActivityClass;
        boolean mExported;
        Set<IntentFilter> mIntentFilters;
        String mPermission;

        public Builder activityClass(String activityClassName) {
            mActivityClass = activityClassName;
            return this;
        }

        public Builder activityClass(Activity activity) {
            return activityClass(activity.getClass());
        }

        public Builder activityClass(Class<? extends Activity> activityClass) {
            return activityClass(activityClass.getName());
        }

        public Builder exported(boolean exported) {
            mExported = exported;
            return this;
        }

        /** Set the intent filters with the set of intent filters provided */
        public Builder intentFilters(Set<IntentFilter> intentFilters) {
            mIntentFilters = intentFilters;
            return this;
        }

        /** Set the permission for the activity. */
        public Builder permission(String permission) {
            mPermission = permission;
            return this;
        }

        public ActivityInfo build() {
            return new ActivityInfo(
                    mActivityClass,
                    mExported,
                    mIntentFilters,
                    mPermission
            );
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        writeBoolean(out, mExported);
        out.writeList(new ArrayList<>(mIntentFilters));
        out.writeString(mPermission);
    }

    public static final Parcelable.Creator<ActivityInfo> CREATOR =
            new Parcelable.Creator<ActivityInfo>() {
                public ActivityInfo createFromParcel(Parcel in) {
                    return new ActivityInfo(in);
                }

                public ActivityInfo[] newArray(int size) {
                    return new ActivityInfo[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivityInfo)) return false;
        if (!super.equals(o)) return false;
        ActivityInfo that = (ActivityInfo) o;
        return mExported == that.mExported && mIntentFilters.equals(that.mIntentFilters)
                && Objects.equals(mPermission, ((ActivityInfo) o).mPermission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mExported, mIntentFilters, mPermission);
    }
}
