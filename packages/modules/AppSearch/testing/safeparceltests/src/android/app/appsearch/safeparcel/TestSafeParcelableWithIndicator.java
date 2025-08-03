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

import java.util.HashSet;

@SafeParcelable.Class(creator = "TestSafeParcelableWithIndicatorCreator")
public class TestSafeParcelableWithIndicator extends AbstractSafeParcelable {
    private static final int VERSION_CODE = 1;

    public static final Creator<TestSafeParcelableWithIndicator> CREATOR =
            new TestSafeParcelableWithIndicatorCreator();

    @VersionField(id = 1)
    public final int versionCode;

    @Field(id = 2)
    public String string1;

    @Field(id = 3)
    public int int1;

    @Field(id = 4)
    public String string2;

    @Field(id = 5)
    public int int2;

    @Indicator(getter = "getIndicator")
    private final HashSet<Integer> mIndicator;

    @Constructor
    public TestSafeParcelableWithIndicator(
            @Param(id = 1) int versionCode,
            @Param(id = 2) String string1,
            @Param(id = 3) int int1,
            @Param(id = 4) String string2,
            @Param(id = 5) int int2,
            @Indicator HashSet<Integer> indicator) {
        this.versionCode = versionCode;
        this.string1 = string1;
        this.int1 = int1;
        this.string2 = string2;
        this.int2 = int2;
        mIndicator = indicator;
    }

    public TestSafeParcelableWithIndicator() {
        versionCode = VERSION_CODE;
        mIndicator = new HashSet<Integer>();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableWithIndicatorCreator.writeToParcel(this, out, flags);
    }

    // Public only for testing purposes.
    public HashSet<Integer> getIndicator() {
        return mIndicator;
    }
}
