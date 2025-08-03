/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.queryable.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utilities for {@link Parcelable}. */
public final class ParcelableUtils {
    private ParcelableUtils() {

    }

    /** Write a {@link java.lang.Boolean} value. */
    public static void writeNullableBoolean(Parcel parcel, Boolean b) {
        writeBoolean(parcel, b != null);
        if (b != null) {
            writeBoolean(parcel, b);
        }
    }

    /** Read a {@link java.lang.Boolean} value written with {@link #writeNullableBoolean}). */
    public static Boolean readNullableBoolean(Parcel parcel) {
        if (!readBoolean(parcel)) {
            return null;
        }
        return readBoolean(parcel);
    }

    /** Write a {@link java.lang.Integer} value. */
    public static void writeNullableInt(Parcel parcel, Integer integer) {
        writeBoolean(parcel, integer != null);
        if (integer != null) {
            parcel.writeInt(integer);
        }
    }

    /** Read a {@link java.lang.Integer} value written with {@link #writeNullableInt). */
    public static Integer readNullableInt(Parcel parcel) {
        if (!readBoolean(parcel)) {
            return null;
        }
        return parcel.readInt();
    }

    /** Write a {@link java.lang.Long} value. */
    public static void writeNullableLong(Parcel parcel, Long value) {
        writeBoolean(parcel, value != null);
        if (value != null) {
            parcel.writeLong(value);
        }
    }

    /** Read a {@link java.lang.Long} value written with {@link #writeNullableLong). */
    public static Long readNullableLong(Parcel parcel) {
        if (!readBoolean(parcel)) {
            return null;
        }
        return parcel.readLong();
    }

    /** Write a {@link Set} of {@link android.os.Parcelable} objects. */
    public static void writeParcelableSet(Parcel parcel, Set<? extends Parcelable> set, int flags) {
        parcel.writeList(new ArrayList<>(set));
    }

    /** Read a {@link Set} of {@link android.os.Parcelable} objects. */
    public static Set<? extends Parcelable> readParcelableSet(Parcel parcel) {
        List<? extends Parcelable> l = new ArrayList<>();
        parcel.readList(l, ParcelableUtils.class.getClassLoader());
        return new HashSet<>(l);
    }

    /** Write a {@link Set}. */
    public static void writeSet(Parcel parcel, Set<?> set) {
        parcel.writeList(new ArrayList<>(set));
    }

    /** Read a {@link Set}. */
    public static Set<?> readSet(Parcel parcel) {
        List<?> l = new ArrayList<>();
        parcel.readList(l, ParcelableUtils.class.getClassLoader());
        return new HashSet<>(l);
    }

    /** Write a {@link Set} of {@link String}. */
    public static void writeStringSet(Parcel parcel, Set<String> set) {
        parcel.writeStringList(new ArrayList<>(set));
    }

    /** Read a {@link Set} of {@link String}. */
    public static Set<String> readStringSet(Parcel parcel) {
        List<String> s = new ArrayList<>();
        parcel.readStringList(s);
        return new HashSet<>(s);
    }

    /** Write a {@link boolean}. */
    public static void writeBoolean(Parcel parcel, boolean b) {
        parcel.writeInt(b ? 1 : 0);
    }

    /** Read a {@link boolean}. */
    public static boolean readBoolean(Parcel parcel) {
        return parcel.readInt() == 1;
    }
}
