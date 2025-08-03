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

package com.android.bedstead.nene.utils;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.os.Parcelable;

/** Utilities for testing {@link android.os.Parcelable} objects. */
public final class ParcelTest {

    private ParcelTest() {

    }

    /**
     * Check that a given type parcels and unparcels correctly.
     *
     * <p>This uses {@code Object#equals} to check for equality. For custom equality checks use
     * {@link #parcelAndUnparcel}.
     */
    public static <T extends Parcelable> void assertParcelsCorrectly(Class<T> cls, T obj) {
        T newInstance = parcelAndUnparcel(cls, obj);
        assertThat(newInstance).isEqualTo(obj);
    }

    /**
     * Check that a given type parcels and unparcels correctly and return the unparceled instance.
     *
     * <p>This does not confirm the unparceled instance is correct. For that see
     * {@link #assertParcelsCorrectly}.
     */
    public static <T extends Parcelable> T parcelAndUnparcel(Class<T> cls, T obj) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeParcelable(obj, /* flags= */ 0);

            int parcelSize = parcel.dataPosition();
            parcel.setDataPosition(0);

            T newInstance = parcel.readParcelable(ParcelTest.class.getClassLoader());
            assertThat(parcel.dataPosition()).isEqualTo(parcelSize);
            return newInstance;
        } finally {
            parcel.recycle();
        }
    }
}
