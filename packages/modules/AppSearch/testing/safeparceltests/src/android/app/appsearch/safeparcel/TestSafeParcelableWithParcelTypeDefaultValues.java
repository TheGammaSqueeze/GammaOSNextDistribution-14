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

@SafeParcelable.Class(
        creator = "TestSafeParcelableWithParcelTypeDefaultValuesCreator",
        doNotParcelTypeDefaultValues = false)
public class TestSafeParcelableWithParcelTypeDefaultValues extends AbstractSafeParcelable {

    public static final Creator<TestSafeParcelableWithParcelTypeDefaultValues> CREATOR =
            new TestSafeParcelableWithParcelTypeDefaultValuesCreator();

    @Field(id = 1)
    public boolean boolean1;

    @Field(id = 2)
    public String string1;

    @Field(id = 3)
    public int int1;

    @Field(id = 4)
    public char char1;

    @Field(id = 5)
    public byte byte1;

    @Field(id = 6)
    public short short1;

    @Field(id = 7)
    public long long1;

    @Field(id = 8)
    public float float1;

    @Field(id = 9)
    public double double1;

    @Field(id = 10)
    public byte[] array1;

    @Indicator(getter = "getIndicator")
    public HashSet<Integer> indicator;

    @Constructor
    public TestSafeParcelableWithParcelTypeDefaultValues(
            @Param(id = 1) boolean boolean1,
            @Param(id = 2) String string1,
            @Param(id = 3) int int1,
            @Param(id = 4) char char1,
            @Param(id = 5) byte byte1,
            @Param(id = 6) short short1,
            @Param(id = 7) long long1,
            @Param(id = 8) float float1,
            @Param(id = 9) double double1,
            @Param(id = 10) byte[] array1,
            @Indicator HashSet<Integer> indicator) {
        this.boolean1 = boolean1;
        this.string1 = string1;
        this.int1 = int1;
        this.char1 = char1;
        this.byte1 = byte1;
        this.short1 = short1;
        this.long1 = long1;
        this.float1 = float1;
        this.double1 = double1;
        this.array1 = array1;
        this.indicator = indicator;
    }

    public TestSafeParcelableWithParcelTypeDefaultValues() {
        indicator = new HashSet<>();
    }

    // Public only for testing purposes.
    public HashSet<Integer> getIndicator() {
        return indicator;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableWithParcelTypeDefaultValuesCreator.writeToParcel(this, out, flags);
    }
}
