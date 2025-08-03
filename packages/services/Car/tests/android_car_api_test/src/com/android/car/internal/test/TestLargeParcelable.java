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

import com.android.car.internal.LargeParcelableBase;

public final class TestLargeParcelable extends LargeParcelableBase {
    private static final String TAG = TestLargeParcelable.class.getSimpleName();

    public byte[] byteData;

    public TestLargeParcelable() {}

    public TestLargeParcelable(byte[] byteData) {
        this.byteData = byteData;
    }

    public TestLargeParcelable(Parcel in) {
        super(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    protected void serialize(Parcel dest, int flags) {
        dest.writeByteArray(byteData);
        Log.i(TAG, "serialize byte array:" + ((byteData == null) ? null : byteData.length));
    }

    @Override
    protected void serializeNullPayload(@NonNull Parcel dest) {
        dest.writeByteArray(null);
        Log.i(TAG, "serialize null byte array");
    }

    @Override
    protected void deserialize(Parcel src) {
        byteData = src.createByteArray();
        Log.i(TAG, "deserialize byte array:" + ((byteData == null) ? null : byteData.length));
    }

    public static final @NonNull Parcelable.Creator<TestLargeParcelable> CREATOR =
            new Parcelable.Creator<TestLargeParcelable>() {
                @Override
                public TestLargeParcelable[] newArray(int size) {
                    return new TestLargeParcelable[size];
                }

                @Override
                public TestLargeParcelable createFromParcel(@NonNull Parcel in) {
                    return new TestLargeParcelable(in);
                }
            };
}
