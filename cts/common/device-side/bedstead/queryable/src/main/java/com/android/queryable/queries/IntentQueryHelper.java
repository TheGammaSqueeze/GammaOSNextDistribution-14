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

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.SerializableParcelWrapper;

import java.io.Serializable;
import java.util.Objects;

/** Implementation of {@link IntentQuery}. */
public final class IntentQueryHelper<E extends Queryable> implements IntentQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final StringQueryHelper<E> mAction;
    private final BundleQueryHelper<E> mExtras;

    public static final class IntentQueryBase extends
            QueryableBaseWithMatch<Intent, IntentQueryHelper<IntentQueryBase>> {
        IntentQueryBase() {
            super();
            setQuery(new IntentQueryHelper<>(this));
        }

        IntentQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<IntentQueryHelper.IntentQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public IntentQueryHelper.IntentQueryBase createFromParcel(Parcel in) {
                        return new IntentQueryHelper.IntentQueryBase(in);
                    }

                    public IntentQueryHelper.IntentQueryBase[] newArray(int size) {
                        return new IntentQueryHelper.IntentQueryBase[size];
                    }
                };
    }

    public IntentQueryHelper(E query) {
        mQuery = query;
        mAction = new StringQueryHelper<>(query);
        mExtras = new BundleQueryHelper<>(query);
    }

    private IntentQueryHelper(Parcel in) {
        mQuery = null;
        mAction = in.readParcelable(IntentQueryHelper.class.getClassLoader());
        mExtras = in.readParcelable(IntentQueryHelper.class.getClassLoader());
    }

    @Override
    public StringQuery<E> action() {
        return mAction;
    }

    @Override
    public BundleQuery<E> extras() {
        return mExtras;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mAction)
                && Queryable.isEmptyQuery(mExtras);
    }

    /** {@code true} if all filters are met by {@code value}. */
    @Override
    public boolean matches(Intent value) {
        if (!mAction.matches(value.getAction())) {
            return false;
        }
        if (!mExtras.matches(value.getExtras())) {
            return false;
        }

        return true;
    }

    /**
     * {@code true} if all filters are met by the {@link Intent} contained in
     * {@code serializableIntent}.
     */
    public boolean matches(SerializableParcelWrapper<Intent> serializableIntent) {
        if ((serializableIntent == null || serializableIntent.get() == null)) {
            return false;
        }

        return matches(serializableIntent.get());
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mAction.describeQuery(fieldName + ".action"),
                mExtras.describeQuery(fieldName + ".extras")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mAction, flags);
        out.writeParcelable(mExtras, flags);
    }

    public static final Parcelable.Creator<IntentQueryHelper> CREATOR =
            new Parcelable.Creator<IntentQueryHelper>() {
                public IntentQueryHelper createFromParcel(Parcel in) {
                    return new IntentQueryHelper(in);
                }

                public IntentQueryHelper[] newArray(int size) {
                    return new IntentQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntentQueryHelper)) return false;
        IntentQueryHelper<?> that = (IntentQueryHelper<?>) o;
        return Objects.equals(mAction, that.mAction) && Objects.equals(mExtras,
                that.mExtras);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAction, mExtras);
    }
}
