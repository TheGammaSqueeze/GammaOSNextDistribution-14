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
package android.app.appsearch.safeparcel;

import android.os.Bundle;
import android.os.Parcel;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

@SafeParcelable.Class(creator = "TestSafeParcelableWithSparseArrayCreator")
public class TestSafeParcelableWithSparseArray extends AbstractSafeParcelable {
    public static final Creator<TestSafeParcelableWithSparseArray> CREATOR =
            new TestSafeParcelableWithSparseArrayCreator();

    @Field(id = 1)
    final SparseBooleanArray mBooleans;

    @Field(id = 2)
    final SparseIntArray mIntegers;

    @Field(id = 3)
    final SparseLongArray mLongs;

    @Field(id = 4)
    final SparseArray<Float> mFloats;

    @Field(id = 5)
    final SparseArray<Double> mDoubles;

    @Field(id = 6)
    final SparseArray<String> mStrings;

    @Field(id = 7)
    final SparseArray<Bundle> mBundles;

    @Field(id = 8)
    final SparseArray<byte[]> mBytes;

    @Constructor
    TestSafeParcelableWithSparseArray(
            @Param(id = 1) SparseBooleanArray booleans,
            @Param(id = 2) SparseIntArray integers,
            @Param(id = 3) SparseLongArray longs,
            @Param(id = 4) SparseArray<Float> floats,
            @Param(id = 5) SparseArray<Double> doubles,
            @Param(id = 6) SparseArray<String> strings,
            @Param(id = 7) SparseArray<Bundle> bundles,
            @Param(id = 8) SparseArray<byte[]> bytes) {
        mBooleans = booleans;
        mIntegers = integers;
        mLongs = longs;
        mFloats = floats;
        mDoubles = doubles;
        mStrings = strings;
        mBundles = bundles;
        mBytes = bytes;
    }
    ;

    @SuppressWarnings("static-access")
    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableWithSparseArrayCreator.writeToParcel(this, out, flags);
    }
}
