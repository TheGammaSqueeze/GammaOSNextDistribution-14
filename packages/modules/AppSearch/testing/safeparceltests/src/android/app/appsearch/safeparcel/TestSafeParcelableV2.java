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

import android.content.ContentValues;
import android.os.Bundle;
import android.os.Parcel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@SafeParcelable.Class(creator = "TestSafeParcelableV2Creator")
public class TestSafeParcelableV2 extends AbstractSafeParcelable {
    private static final int VERSION_CODE = 2;

    public static final Creator<TestSafeParcelableV2> CREATOR = new TestSafeParcelableV2Creator();

    public TestSafeParcelableV2() {
        mVersionCode = VERSION_CODE;
    }

    public TestSafeParcelableV2(String stringVal1, String stringVal2, String stringVal3) {
        mVersionCode = VERSION_CODE;
        string1 = stringVal1;
        string2 = stringVal2;
        string3 = stringVal3;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableV2Creator.writeToParcel(this, out, flags);
    }

    @VersionField(id = 1000)
    final int mVersionCode;

    @Field(id = 1)
    public String string1;

    @Field(id = 3)
    public String string3;

    @Field(id = 2)
    public String string2;

    @Field(id = 4)
    public byte[] byteArray;

    @Field(id = 5)
    public boolean booleanVar;

    @Field(id = 6)
    public byte byteVar;

    @Field(id = 7)
    public char charVar;

    @Field(id = 8)
    public short shortVar;

    @Field(id = 9)
    public int intVar;

    @Field(id = 10)
    public long longVar;

    @Field(id = 11)
    public float floatVar;

    @Field(id = 12)
    public double doubleVar;

    @Field(id = 13)
    public TestSafeParcelable parcelableVar;

    @Field(id = 14)
    public Bundle bundleVar;

    @Field(id = 15)
    public char[] charArray;

    @Field(id = 16)
    public int[] intArray;

    @Field(id = 17)
    public long[] longArray;

    @Field(id = 18)
    public float[] floatArray;

    @Field(id = 19)
    public double[] doubleArray;

    @Field(id = 20)
    public String[] stringArray;

    @Field(id = 21)
    public List<String> stringList;

    @Field(id = 22)
    public TestSafeParcelable[] parcelableArray;

    @Field(id = 23)
    public List<TestSafeParcelable> parcelableList;

    @Field(id = 24)
    /* package */ String mPackageString;

    @Field(id = 25)
    /* package */ int mPackageInt;

    @Field(id = 26)
    public ContentValues contentValues;

    @Field(id = 27)
    public Parcel parcel;

    @Field(id = 28)
    public byte[][] byteArrayArray;

    @Field(id = 29)
    public BigInteger bigInteger;

    @Field(id = 30)
    public BigDecimal bigDecimal;

    @Field(id = 31)
    public BigInteger[] bigIntegerArray;

    @Field(id = 32)
    public BigDecimal[] bigDecimalArray;

    @Field(id = 33)
    public boolean[] booleanArray;

    @Field(id = 34)
    public List<Parcel> parcelList;

    @Field(id = 35)
    public Integer integerObject;

    @Field(id = 36)
    public Boolean booleanObject;

    @Field(id = 37)
    public Long longObject;

    @Field(id = 38)
    public Float floatObject;

    @Field(id = 39)
    public Double doubleObject;

    @Field(id = 40)
    public List<Boolean> booleanList;

    @Field(id = 41)
    public List<Integer> integerList;

    @Field(id = 42)
    public List<Long> longList;

    @Field(id = 43)
    public List<Float> floatList;

    @Field(id = 44)
    public List<Double> doubleList;

    @Constructor
    TestSafeParcelableV2(
            @Param(id = 1000) int versionCode,
            @Param(id = 1) String string1,
            @Param(id = 3) String string3,
            @Param(id = 2) String string2,
            @Param(id = 4) byte[] byteArray,
            @Param(id = 5) boolean booleanVar,
            @Param(id = 6) byte byteVar,
            @Param(id = 7) char charVar,
            @Param(id = 8) short shortVar,
            @Param(id = 9) int intVar,
            @Param(id = 10) long longVar,
            @Param(id = 11) float floatVar,
            @Param(id = 12) double doubleVar,
            @Param(id = 13) TestSafeParcelable parcelableVar,
            @Param(id = 14) Bundle bundleVar,
            @Param(id = 15) char[] charArray,
            @Param(id = 16) int[] intArray,
            @Param(id = 17) long[] longArray,
            @Param(id = 18) float[] floatArray,
            @Param(id = 19) double[] doubleArray,
            @Param(id = 20) String[] stringArray,
            @Param(id = 21) List<String> stringList,
            @Param(id = 22) TestSafeParcelable[] parcelableArray,
            @Param(id = 23) List<TestSafeParcelable> parcelableList,
            @Param(id = 24) String mPackageString,
            @Param(id = 25) int mPackageInt,
            @Param(id = 26) ContentValues contentValues,
            @Param(id = 27) Parcel parcel,
            @Param(id = 28) byte[][] byteArrayArray,
            @Param(id = 29) BigInteger bigInteger,
            @Param(id = 30) BigDecimal bigDecimal,
            @Param(id = 31) BigInteger[] bigIntegerArray,
            @Param(id = 32) BigDecimal[] bigDecimalArray,
            @Param(id = 33) boolean[] booleanArray,
            @Param(id = 34) List<Parcel> parcelList,
            @Param(id = 35) Integer integerObject,
            @Param(id = 36) Boolean booleanObject,
            @Param(id = 37) Long longObject,
            @Param(id = 38) Float floatObject,
            @Param(id = 39) Double doubleObject,
            @Param(id = 40) List<Boolean> booleanList,
            @Param(id = 41) List<Integer> integerList,
            @Param(id = 42) List<Long> longList,
            @Param(id = 43) List<Float> floatList,
            @Param(id = 44) List<Double> doubleList) {
        mVersionCode = versionCode;
        this.string1 = string1;
        this.string3 = string3;
        this.string2 = string2;
        this.byteArray = byteArray;
        this.booleanVar = booleanVar;
        this.byteVar = byteVar;
        this.charVar = charVar;
        this.shortVar = shortVar;
        this.intVar = intVar;
        this.longVar = longVar;
        this.floatVar = floatVar;
        this.doubleVar = doubleVar;
        this.parcelableVar = parcelableVar;
        this.bundleVar = bundleVar;
        this.charArray = charArray;
        this.intArray = intArray;
        this.longArray = longArray;
        this.floatArray = floatArray;
        this.doubleArray = doubleArray;
        this.stringArray = stringArray;
        this.stringList = stringList;
        this.parcelableArray = parcelableArray;
        this.parcelableList = parcelableList;
        this.mPackageString = mPackageString;
        this.mPackageInt = mPackageInt;
        this.contentValues = contentValues;
        this.parcel = parcel;
        this.byteArrayArray = byteArrayArray;
        this.bigInteger = bigInteger;
        this.bigDecimal = bigDecimal;
        this.bigIntegerArray = bigIntegerArray;
        this.bigDecimalArray = bigDecimalArray;
        this.booleanArray = booleanArray;
        this.parcelList = parcelList;
        this.integerObject = integerObject;
        this.booleanObject = booleanObject;
        this.longObject = longObject;
        this.floatObject = floatObject;
        this.doubleObject = doubleObject;
        this.booleanList = booleanList;
        this.integerList = integerList;
        this.longList = longList;
        this.floatList = floatList;
        this.doubleList = doubleList;
    }

    public void setPackageString(String string) {
        mPackageString = string;
    }

    public String getPackageString() {
        return mPackageString;
    }

    public void setPackageInt(int integer) {
        mPackageInt = integer;
    }

    public int getPackageInt() {
        return mPackageInt;
    }
}
