/*
 * Copyright (C) 2018 The Android Open Source Project
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

package test_package;

import test_package.ByteEnum;
import test_package.Foo;
import test_package.IEmpty;
import test_package.IntEnum;
import test_package.LongEnum;
import test_package.RegularPolygon;
import test_package.GenericBar;
import test_package.ExtendableParcelable;
import test_package.SimpleUnion;
import android.os.PersistableBundle;

// This test interface is used in order to test the all of the things that AIDL can generate which
// build on top of the NDK.
//
// Repeat => return the same value. This is used to keep the clients/tests simple.
interface ITest {
    const int kZero = 0;
    const int kOne = 1;
    const int kOnes = 0xffffffff;
    const byte kByteOne = 1;
    const long kLongOnes = 0xffffffffffffffff;
    const String kEmpty = "";
    const String kFoo = "foo";

    String GetName();

    void TestVoidReturn();
    oneway void TestOneway();

    int GiveMeMyCallingPid();
    int GiveMeMyCallingUid();

    // This must be called before calling one of the give-me methods below
    oneway void CacheCallingInfoFromOneway();
    int GiveMeMyCallingPidFromOneway();
    int GiveMeMyCallingUidFromOneway();

    // Sending/receiving primitive types.
    int RepeatInt(int value);
    long RepeatLong(long value);
    float RepeatFloat(float value);
    double RepeatDouble(double value);
    boolean RepeatBoolean(boolean value);
    char RepeatChar(char value);
    byte RepeatByte(byte value);
    ByteEnum RepeatByteEnum(ByteEnum value);
    IntEnum RepeatIntEnum(IntEnum value);
    LongEnum RepeatLongEnum(LongEnum value);

    IBinder RepeatBinder(IBinder value);
    @nullable IBinder RepeatNullableBinder(@nullable IBinder value);
    IEmpty RepeatInterface(IEmpty value);
    @nullable IEmpty RepeatNullableInterface(@nullable IEmpty value);

    ParcelFileDescriptor RepeatFd(in ParcelFileDescriptor fd);
    @nullable ParcelFileDescriptor RepeatNullableFd(in @nullable ParcelFileDescriptor fd);

    String RepeatString(String value);
    @nullable String RepeatNullableString(@nullable String value);

    RegularPolygon RepeatPolygon(in RegularPolygon value);
    @nullable RegularPolygon RepeatNullablePolygon(in @nullable RegularPolygon value);

    PersistableBundle RepeatPersistableBundle(in PersistableBundle input);

    // Testing inout
    void RenamePolygon(inout RegularPolygon value, String newName);

    // Arrays
    boolean[] RepeatBooleanArray(in boolean[] input, out boolean[] repeated);
    byte[] RepeatByteArray(in byte[] input, out byte[] repeated);
    char[] RepeatCharArray(in char[] input, out char[] repeated);
    int[] RepeatIntArray(in int[] input, out int[] repeated);
    long[] RepeatLongArray(in long[] input, out long[] repeated);
    float[] RepeatFloatArray(in float[] input, out float[] repeated);
    double[] RepeatDoubleArray(in double[] input, out double[] repeated);
    ByteEnum[] RepeatByteEnumArray(in ByteEnum[] input, out ByteEnum[] repeated);
    IntEnum[] RepeatIntEnumArray(in IntEnum[] input, out IntEnum[] repeated);
    LongEnum[] RepeatLongEnumArray(in LongEnum[] input, out LongEnum[] repeated);
    String[] RepeatStringArray(in String[] input, out String[] repeated);
    RegularPolygon[] RepeatRegularPolygonArray(in RegularPolygon[] input, out RegularPolygon[] repeated);
    ParcelFileDescriptor[] RepeatFdArray(in ParcelFileDescriptor[] input, out ParcelFileDescriptor[] repeated);
    IBinder[] RepeatBinderArray(in IBinder[] input, out IBinder[] repeated);
    IEmpty[] RepeatInterfaceArray(in IEmpty[] input, out IEmpty[] repeated);

    // Lists
    List<String> Repeat2StringList(in List<String> input, out List<String> repeated);
    List<RegularPolygon> Repeat2RegularPolygonList(in List<RegularPolygon> input, out List<RegularPolygon> repeated);

    // Nullable Arrays
    @nullable boolean[] RepeatNullableBooleanArray(in @nullable boolean[] input);
    @nullable byte[] RepeatNullableByteArray(in @nullable byte[] input);
    @nullable char[] RepeatNullableCharArray(in @nullable char[] input);
    @nullable int[] RepeatNullableIntArray(in @nullable int[] input);
    @nullable long[] RepeatNullableLongArray(in @nullable long[] input);
    @nullable float[] RepeatNullableFloatArray(in @nullable float[] input);
    @nullable double[] RepeatNullableDoubleArray(in @nullable double[] input);
    @nullable ByteEnum[] RepeatNullableByteEnumArray(in @nullable ByteEnum[] input);
    @nullable IntEnum[] RepeatNullableIntEnumArray(in @nullable IntEnum[] input);
    @nullable LongEnum[] RepeatNullableLongEnumArray(in @nullable LongEnum[] input);
    @nullable String[] RepeatNullableStringArray(in @nullable String[] input);
    @nullable IBinder[] RepeatNullableBinderArray(in @nullable IBinder[] input);
    @nullable IEmpty[] RepeatNullableInterfaceArray(in @nullable IEmpty[] input);

    // Nullable Arrays where each individual element can be nullable
    // (specifically for testing out parameters)
    @nullable String[] DoubleRepeatNullableStringArray(
        in @nullable String[] input, out @nullable String[] repeated);

    Foo repeatFoo(in Foo inFoo);
    void renameFoo(inout Foo foo, String name);
    void renameBar(inout Foo foo, String name);
    int getF(in Foo foo);

    GenericBar<int> repeatGenericBar(in GenericBar<int> bar);

    void RepeatExtendableParcelable(in ExtendableParcelable input, out ExtendableParcelable output);

    SimpleUnion RepeatSimpleUnion(in SimpleUnion u);

    IBinder getICompatTest();

    void RepeatExtendableParcelableWithoutExtension(in ExtendableParcelable input, out ExtendableParcelable output);

    IBinder getLegacyBinderTest();
}
