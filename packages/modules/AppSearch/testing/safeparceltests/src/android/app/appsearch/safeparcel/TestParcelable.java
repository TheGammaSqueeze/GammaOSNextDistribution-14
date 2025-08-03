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

import android.os.Parcel;
import android.os.Parcelable;

public class TestParcelable implements Parcelable {
    public static final Creator<TestParcelable> CREATOR =
            new Creator<TestParcelable>() {

                @Override
                public TestParcelable createFromParcel(Parcel parcel) {
                    return new TestParcelable(parcel);
                }

                @Override
                public TestParcelable[] newArray(int size) {
                    return new TestParcelable[size];
                }
            };

    private int mInt;
    private float mFloat;
    private String mString;

    public TestParcelable(int intValue, float floatValue, String stringValue) {
        mInt = intValue;
        mFloat = floatValue;
        mString = stringValue;
    }

    public TestParcelable(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mInt);
        parcel.writeFloat(mFloat);
        parcel.writeString(mString);
    }

    private void readFromParcel(Parcel parcel) {
        mInt = parcel.readInt();
        mFloat = parcel.readFloat();
        mString = parcel.readString();
    }

    // TODO(b/37774152): implement hashCode() (go/equals-hashcode-lsc)
    @SuppressWarnings("EqualsHashCode")
    @Override
    public boolean equals(Object object) {
        if (object instanceof TestParcelable) {
            TestParcelable parcelable = (TestParcelable) object;
            return parcelable.mInt == mInt
                    && parcelable.mFloat == mFloat
                    && parcelable.mString.equals(mString);
        }
        return false;
    }
}
