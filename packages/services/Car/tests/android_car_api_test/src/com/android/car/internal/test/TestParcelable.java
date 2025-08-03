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

package com.android.car.internal.test;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public final class TestParcelable implements Parcelable {
    private static final String TAG = TestParcelable.class.getSimpleName();

    public final byte[] byteData;

    public TestParcelable(byte[] byteData) {
        this.byteData = byteData;
    }

    public TestParcelable(Parcel in) {
        byteData = in.createByteArray();
        Log.i(TAG, "read byte array:" + ((byteData == null) ? null : byteData.length));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(byteData);
        Log.i(TAG, "write byteData:" + ((byteData == null) ? null : byteData.length));
    }

    public static final @NonNull Parcelable.Creator<TestParcelable> CREATOR =
            new Parcelable.Creator<TestParcelable>() {
                @Override
                public TestParcelable[] newArray(int size) {
                    return new TestParcelable[size];
                }

                @Override
                public TestParcelable createFromParcel(@NonNull Parcel in) {
                    return new TestParcelable(in);
                }
            };
}
