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

package android.telecom.cts;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 *  A test Parcelable that is used for testing out Bundle impls.
 */
public class TestParcelable implements Parcelable {

    public static final String VAL_1_KEY = "val1";
    public static final String VAL_2_KEY = "val2";
    public static final String VAL_3_KEY = "val3";

    public final int mVal1;
    public final String mVal2;
    public final IBinder mVal3;

    public TestParcelable(int val1, String val2, IBinder val3) {
        mVal1 = val1;
        mVal2 = val2;
        mVal3 = val3;
    }

    private TestParcelable(Parcel in) {
        mVal1 = in.readInt();
        mVal2 = in.readString();
        mVal3 = in.readStrongBinder();
    }

    public static final Creator<TestParcelable> CREATOR =  new Creator<>() {
        @Override
        public TestParcelable createFromParcel(Parcel source) {
            return new TestParcelable(source);
        }

        @Override
        public TestParcelable[] newArray(int size) {
            return new TestParcelable[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mVal1);
        dest.writeString(mVal2);
        dest.writeStrongBinder(mVal3);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestParcelable)) return false;
        TestParcelable that = (TestParcelable) o;
        return mVal1 == that.mVal1 && Objects.equals(mVal2, that.mVal2)
                && Objects.equals(mVal3, that.mVal3);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mVal1, mVal2, mVal3);
    }

    public void copyIntoBundle(Bundle b) {
        b.putParcelable(TestParcelable.class.getSimpleName(),
                new TestParcelable(mVal1, mVal2, mVal3));
    }

    public static TestParcelable getFromBundle(Bundle b) {
        return b.getParcelable(TestParcelable.class.getSimpleName(), TestParcelable.class);
    }
}
