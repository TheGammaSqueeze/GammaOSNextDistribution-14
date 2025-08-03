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

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for information about a {@link Class}.
 *
 * <p>This is used instead of {@link Class} so that it can be easily serialized.
 */
public class ClassInfo implements Serializable, Parcelable {

    private static final long serialVersionUID = 1;

    private final String mClassName;

    public ClassInfo(Object obj) {
        this(obj.getClass());
    }

    public ClassInfo(Class<?> clazz) {
        this(clazz.getName());
    }

    public ClassInfo(String className) {
        mClassName = className;
    }

    ClassInfo(Parcel in) {
        mClassName = in.readString();
    }

    public String className() {
        return mClassName;
    }

    public String simpleName() {
        return getSimpleName(mClassName);
    }

    private static String getSimpleName(String name) {
        // First deal with inner classes
        int dollar = name.lastIndexOf("$");
        if (dollar > 0) {
            return name.substring(dollar + 1); // strip the package name
        }

        int dot = name.lastIndexOf(".");
        if (dot > 0) {
            return name.substring(dot + 1); // strip the package name
        }
        return name;
    }

    @Override
    public String toString() {
        return "Class{"
                + "className=" + className()
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mClassName);
    }

    public static final Parcelable.Creator<ClassInfo> CREATOR =
            new Parcelable.Creator<ClassInfo>() {
                public ClassInfo createFromParcel(Parcel in) {
                    return new ClassInfo(in);
                }

                public ClassInfo[] newArray(int size) {
                    return new ClassInfo[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassInfo)) return false;
        ClassInfo classInfo = (ClassInfo) o;
        return mClassName.equals(classInfo.mClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mClassName);
    }
}
