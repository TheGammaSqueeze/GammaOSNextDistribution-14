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

package com.android.car.internal;

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Wrapper for a parcelable object <V>
 *
 * @param <V> refer to a Parcelable object.
 * @hide
 */
public final class ResultWrapper<V> implements Parcelable {

    @Nullable
    private final V mResult;

    public ResultWrapper(V result) {
        mResult = result;
    }

    ResultWrapper(Parcel in) {
        @SuppressWarnings("unchecked")
        V safeCast = (V) in.readValue(getClass().getClassLoader());
        mResult = safeCast;
    }

    public V getResult() {
        return mResult;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mResult);
    }

    public static final Parcelable.Creator<ResultWrapper> CREATOR =
            new Parcelable.Creator<ResultWrapper>() {
        public ResultWrapper createFromParcel(Parcel in) {
            return new ResultWrapper(in);
        }

        public ResultWrapper[] newArray(int size) {
            return new ResultWrapper[size];
        }
    };

}
