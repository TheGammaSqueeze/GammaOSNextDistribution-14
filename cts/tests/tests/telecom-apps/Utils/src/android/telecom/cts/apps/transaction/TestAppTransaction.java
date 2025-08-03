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

package android.telecom.cts.apps;

import android.os.Parcel;
import android.os.Parcelable;

public enum TestAppTransaction implements Parcelable {
    Success,
    Failure;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(toInteger());
    }

    public static final Parcelable.Creator<TestAppTransaction> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public TestAppTransaction createFromParcel(Parcel in) {
                    return TestAppTransaction.fromInteger(in.readInt());
                }

                @Override
                public TestAppTransaction[] newArray(int size) {
                    return new TestAppTransaction[size];
                }
            };

    public int toInteger() {
        return this.ordinal();
    }

    public static TestAppTransaction fromInteger(int value) {
        return values()[value];
    }
}
