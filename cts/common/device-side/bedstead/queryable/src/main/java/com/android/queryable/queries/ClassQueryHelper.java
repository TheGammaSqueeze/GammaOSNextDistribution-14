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
import com.android.queryable.info.ClassInfo;

import java.io.Serializable;
import java.util.Objects;

/** Implementation of {@link ClassQuery}. */
public final class ClassQueryHelper<E extends Queryable>
        implements ClassQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final StringQueryHelper<E> mClassName;
    private final StringQueryHelper<E> mSimpleName;

    public static final class ClassQueryBase extends
            QueryableBaseWithMatch<ClassInfo, ClassQueryHelper<ClassQueryBase>> {
        ClassQueryBase() {
            super();
            setQuery(new ClassQueryHelper<>(this));
        }

        ClassQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ClassQueryHelper.ClassQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public ClassQueryHelper.ClassQueryBase createFromParcel(Parcel in) {
                        return new ClassQueryHelper.ClassQueryBase(in);
                    }

                    public ClassQueryHelper.ClassQueryBase[] newArray(int size) {
                        return new ClassQueryHelper.ClassQueryBase[size];
                    }
                };
    }

    public ClassQueryHelper(E query) {
        mQuery = query;
        mClassName = new StringQueryHelper<>(query);
        mSimpleName = new StringQueryHelper<>(query);
    }

    private ClassQueryHelper(Parcel in) {
        mQuery = null;
        mClassName = in.readParcelable(ClassQueryHelper.class.getClassLoader());
        mSimpleName = in.readParcelable(ClassQueryHelper.class.getClassLoader());
    }

    @Override
    public E isSameClassAs(Class<?> clazz) {
        return className().isEqualTo(clazz.getName());
    }

    @Override
    public StringQuery<E> className() {
        return mClassName;
    }

    @Override
    public StringQuery<E> simpleName() {
        return mSimpleName;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mClassName)
                && Queryable.isEmptyQuery(mSimpleName);
    }

    @Override
    public boolean matches(ClassInfo value) {
        if (!mClassName.matches(value.className())) {
            return false;
        }

        if (!mSimpleName.matches(value.simpleName())) {
            return false;
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mClassName.describeQuery(fieldName + ".className"),
                mSimpleName.describeQuery(fieldName + ".simpleName")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mClassName, flags);
        out.writeParcelable(mSimpleName, flags);
    }

    public static final Parcelable.Creator<ClassQueryHelper> CREATOR =
            new Parcelable.Creator<ClassQueryHelper>() {
                public ClassQueryHelper createFromParcel(Parcel in) {
                    return new ClassQueryHelper(in);
                }

                public ClassQueryHelper[] newArray(int size) {
                    return new ClassQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassQueryHelper)) return false;
        ClassQueryHelper<?> that = (ClassQueryHelper<?>) o;
        return Objects.equals(mClassName, that.mClassName) && Objects.equals(
                mSimpleName, that.mSimpleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mClassName, mSimpleName);
    }
}
