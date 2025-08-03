/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * A Serializable wrapper for a Parcelable object.
 * @param <T> a parcelable type
 */
public class ParcelWrapper<T extends Parcelable> implements Serializable {
    private final byte[] mParcelBytes;

    /** Wraps a Parcelable in a Serializable */
    public ParcelWrapper(T value) {
        if (value == null) {
            mParcelBytes = null;
        } else {
            Parcel parcel = Parcel.obtain();
            value.writeToParcel(parcel, 0);
            mParcelBytes = parcel.marshall();
        }
    }

    /** Unwraps the Parcelable. */
    public T get(Parcelable.Creator<T> creator) {
        if (mParcelBytes == null) {
            return null;
        } else {
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(mParcelBytes, 0, mParcelBytes.length);
            parcel.setDataPosition(0);
            return creator.createFromParcel(parcel);
        }
    }
}
