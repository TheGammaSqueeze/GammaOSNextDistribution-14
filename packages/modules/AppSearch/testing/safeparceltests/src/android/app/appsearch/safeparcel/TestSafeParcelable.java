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

@SafeParcelable.Class(creator = "TestSafeParcelableCreator")
public class TestSafeParcelable extends AbstractSafeParcelable {

    public static final Creator<TestSafeParcelable> CREATOR = new TestSafeParcelableCreator();

    @Constructor
    public TestSafeParcelable(
            @Param(id = 1000) int versionCode,
            @Param(id = 1) String stringVal1,
            @Param(id = 2) String stringVal2) {
        mVersionCode = versionCode;
        stringField1 = stringVal1;
        stringField2 = stringVal2;
    }

    public TestSafeParcelable(String stringVal1, String stringVal2) {
        this(1, stringVal1, stringVal2);
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableCreator.writeToParcel(this, out, flags);
    }

    @VersionField(id = 1000, getter = "getVersionCode")
    private final int mVersionCode;

    @Field(id = 1)
    public final String stringField1;

    @Field(id = 2)
    public final String stringField2;

    // TODO(b/37774152): implement hashCode() (go/equals-hashcode-lsc)
    @SuppressWarnings("EqualsHashCode")
    @Override
    public boolean equals(Object object) {
        if (object instanceof TestSafeParcelable) {
            TestSafeParcelable parcelable = (TestSafeParcelable) object;
            return stringField1.equals(parcelable.stringField1)
                    && stringField2.equals(parcelable.stringField2);
        }
        return false;
    }
}
