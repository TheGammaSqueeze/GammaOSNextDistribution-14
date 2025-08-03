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

import static com.android.queryable.annotations.StringQuery.DEFAULT_STRING_QUERY_PARAMETERS_VALUE;
import static com.android.queryable.util.ParcelableUtils.writeStringSet;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.util.ParcelableUtils;

import com.google.auto.value.AutoAnnotation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Implementation of {@link StringQuery}. */
public final class StringQueryHelper<E extends Queryable>
        implements StringQuery<E>, Serializable{

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    // This is required because null is a valid value
    private boolean mEqualsIsSpecified = false;
    private String mEqualsValue;
    private Set<String> mNotEqualsValues = new HashSet<>();
    private String mStartsWithValue;

    public static final class StringQueryBase extends
            QueryableBaseWithMatch<String, StringQueryHelper<StringQueryBase>> {
        StringQueryBase() {
            super();
            setQuery(new StringQueryHelper<>(this));
        }

        StringQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<StringQueryHelper.StringQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public StringQueryHelper.StringQueryBase createFromParcel(Parcel in) {
                        return new StringQueryHelper.StringQueryBase(in);
                    }

                    public StringQueryHelper.StringQueryBase[] newArray(int size) {
                        return new StringQueryHelper.StringQueryBase[size];
                    }
                };
    }

    public StringQueryHelper(E query) {
        mQuery = query;
    }

    private StringQueryHelper(Parcel in) {
        mQuery = null;
        mEqualsIsSpecified = in.readBoolean();
        mEqualsValue = in.readString();
        mNotEqualsValues = ParcelableUtils.readStringSet(in);
        mStartsWithValue = in.readString();
    }

    @Override
    public E isEqualTo(String string) {
        mEqualsIsSpecified = true;
        mEqualsValue = string;
        return mQuery;
    }

    @Override
    public E isNotEqualTo(String string) {
        mNotEqualsValues.add(string);
        return mQuery;
    }

    @Override
    public E startsWith(String string) {
        mStartsWithValue = string;
        return mQuery;
    }

    @Override
    public E matchesAnnotation(com.android.queryable.annotations.StringQuery queryAnnotation) {
        if (!queryAnnotation.startsWith().equals(DEFAULT_STRING_QUERY_PARAMETERS_VALUE)) {
            startsWith(queryAnnotation.startsWith());
        }
        if (!queryAnnotation.isEqualTo().equals(DEFAULT_STRING_QUERY_PARAMETERS_VALUE)) {
            isEqualTo(queryAnnotation.isEqualTo());
        }
        if (!queryAnnotation.isNotEqualTo().equals(DEFAULT_STRING_QUERY_PARAMETERS_VALUE)) {
            isNotEqualTo(queryAnnotation.isNotEqualTo());
        }
        if (queryAnnotation.isNull().equals(OptionalBoolean.TRUE)) {
            isNull();
        }
        if (queryAnnotation.isNull().equals(OptionalBoolean.FALSE)) {
            isNotNull();
        }

        return mQuery;
    }

    public com.android.queryable.annotations.StringQuery toAnnotation() {
        return stringQuery(
                mStartsWithValue == null ? DEFAULT_STRING_QUERY_PARAMETERS_VALUE : mStartsWithValue,
                mEqualsIsSpecified ? mEqualsValue : DEFAULT_STRING_QUERY_PARAMETERS_VALUE);
    }

    @AutoAnnotation
    private static com.android.queryable.annotations.StringQuery stringQuery(
            String startsWith, String isEqualTo) {
        // TODO: Add support for isNotEqualTo
        // TODO: Add support for isNull/isNotNull
        return new AutoAnnotation_StringQueryHelper_stringQuery(
                startsWith, isEqualTo
        );
    }

    @Override
    public boolean isEmptyQuery() {
        return !mEqualsIsSpecified
                && mNotEqualsValues.isEmpty()
                && (mStartsWithValue == null || mStartsWithValue.isEmpty());
    }

    @Override
    public boolean matches(String value) {
        if (mEqualsIsSpecified && !Objects.equals(mEqualsValue, value)) {
            return false;
        }
        if (mNotEqualsValues.contains(value)) {
            return false;
        }
        if (mStartsWithValue != null && (value == null || !value.startsWith(mStartsWithValue))) {
            return false;
        }

        return true;
    }

    public static boolean matches(StringQueryHelper<?> stringQueryHelper, String value) {
        return stringQueryHelper.matches(value);
    }

    /**
     * True if this query has not been configured.
     */
    public boolean isEmpty() {
        return mEqualsValue == null && mNotEqualsValues.isEmpty();
    }

    /**
     * True if this query is for an exact string match.
     */
    public boolean isQueryingForExactMatch() {
        return mEqualsValue != null;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualsIsSpecified) {
            queryStrings.add(fieldName + "=\"" + mEqualsValue + "\"");
        }

        for (String notEquals : mNotEqualsValues) {
            queryStrings.add(fieldName + "!=\"" + notEquals + "\"");
        }

        if (mStartsWithValue != null) {
            queryStrings.add(fieldName + " starts with \"" + mStartsWithValue + "\"");
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(mEqualsIsSpecified);
        out.writeString(mEqualsValue);
        writeStringSet(out, mNotEqualsValues);
        out.writeString(mStartsWithValue);
    }

    public static final Parcelable.Creator<StringQueryHelper> CREATOR =
            new Parcelable.Creator<StringQueryHelper>() {
                public StringQueryHelper createFromParcel(Parcel in) {
                    return new StringQueryHelper(in);
                }

                public StringQueryHelper[] newArray(int size) {
                    return new StringQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringQueryHelper)) return false;
        StringQueryHelper<?> that = (StringQueryHelper<?>) o;
        return Objects.equals(mEqualsValue, that.mEqualsValue) && Objects.equals(
                mNotEqualsValues, that.mNotEqualsValues) && Objects.equals(
                mStartsWithValue, that.mStartsWithValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualsValue, mNotEqualsValues, mStartsWithValue);
    }
}
