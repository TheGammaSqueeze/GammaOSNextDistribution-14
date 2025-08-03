/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/StructuredParcelable.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/StructuredParcelable.aidl
 */
package android.aidl.tests;
public class StructuredParcelable implements android.os.Parcelable
{
  public int[] shouldContainThreeFs;
  public int f = 0;
  public java.lang.String shouldBeJerry;
  public byte shouldBeByteBar;
  public int shouldBeIntBar;
  public long shouldBeLongBar;
  public byte[] shouldContainTwoByteFoos;
  public int[] shouldContainTwoIntFoos;
  public long[] shouldContainTwoLongFoos;
  public java.lang.String stringDefaultsToFoo = "foo";
  public byte byteDefaultsToFour = 4;
  public int intDefaultsToFive = 5;
  public long longDefaultsToNegativeSeven = -7L;
  public boolean booleanDefaultsToTrue = true;
  public char charDefaultsToC = 'C';
  public float floatDefaultsToPi = 3.140000f;
  public double doubleWithDefault = -314000000000000000.000000;
  public int[] arrayDefaultsTo123 = {1, 2, 3};
  public int[] arrayDefaultsToEmpty = {};
  public boolean boolDefault = false;
  public byte byteDefault = 0;
  public int intDefault = 0;
  public long longDefault = 0L;
  public float floatDefault = 0.000000f;
  public double doubleDefault = 0.000000;
  // parse checks only
  public double checkDoubleFromFloat = 3.140000;
  public java.lang.String[] checkStringArray1 = {"a", "b"};
  public java.lang.String[] checkStringArray2 = {"a", "b"};
  // Add test to verify corner cases
  public int int32_min = -2147483648;
  public int int32_max = 2147483647;
  public long int64_max = 9223372036854775807L;
  public int hexInt32_neg_1 = -1;
  public android.os.IBinder ibinder;
  public android.aidl.tests.StructuredParcelable.Empty empty;
  // Constant expressions that evaluate to 1
  public byte[] int8_1 = {1, 1, 1, 1, 1};
  public int[] int32_1 = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
  public long[] int64_1 = {1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L};
  public int hexInt32_pos_1 = 1;
  public int hexInt64_pos_1 = 1;
  public int const_exprs_1;
  public int const_exprs_2;
  public int const_exprs_3;
  public int const_exprs_4;
  public int const_exprs_5;
  public int const_exprs_6;
  public int const_exprs_7;
  public int const_exprs_8;
  public int const_exprs_9;
  public int const_exprs_10;
  // String expressions
  public java.lang.String addString1 = "hello world!";
  public java.lang.String addString2 = "The quick brown fox jumps over the lazy dog.";
  public int shouldSetBit0AndBit2 = 0;
  public android.aidl.tests.Union u;
  public android.aidl.tests.Union shouldBeConstS1;
  public int defaultWithFoo = android.aidl.tests.IntEnum.FOO;
  public static final android.os.Parcelable.Creator<StructuredParcelable> CREATOR = new android.os.Parcelable.Creator<StructuredParcelable>() {
    @Override
    public StructuredParcelable createFromParcel(android.os.Parcel _aidl_source) {
      StructuredParcelable _aidl_out = new StructuredParcelable();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public StructuredParcelable[] newArray(int _aidl_size) {
      return new StructuredParcelable[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeIntArray(shouldContainThreeFs);
    _aidl_parcel.writeInt(f);
    _aidl_parcel.writeString(shouldBeJerry);
    _aidl_parcel.writeByte(shouldBeByteBar);
    _aidl_parcel.writeInt(shouldBeIntBar);
    _aidl_parcel.writeLong(shouldBeLongBar);
    _aidl_parcel.writeByteArray(shouldContainTwoByteFoos);
    _aidl_parcel.writeIntArray(shouldContainTwoIntFoos);
    _aidl_parcel.writeLongArray(shouldContainTwoLongFoos);
    _aidl_parcel.writeString(stringDefaultsToFoo);
    _aidl_parcel.writeByte(byteDefaultsToFour);
    _aidl_parcel.writeInt(intDefaultsToFive);
    _aidl_parcel.writeLong(longDefaultsToNegativeSeven);
    _aidl_parcel.writeBoolean(booleanDefaultsToTrue);
    _aidl_parcel.writeInt(((int)charDefaultsToC));
    _aidl_parcel.writeFloat(floatDefaultsToPi);
    _aidl_parcel.writeDouble(doubleWithDefault);
    _aidl_parcel.writeIntArray(arrayDefaultsTo123);
    _aidl_parcel.writeIntArray(arrayDefaultsToEmpty);
    _aidl_parcel.writeBoolean(boolDefault);
    _aidl_parcel.writeByte(byteDefault);
    _aidl_parcel.writeInt(intDefault);
    _aidl_parcel.writeLong(longDefault);
    _aidl_parcel.writeFloat(floatDefault);
    _aidl_parcel.writeDouble(doubleDefault);
    _aidl_parcel.writeDouble(checkDoubleFromFloat);
    _aidl_parcel.writeStringArray(checkStringArray1);
    _aidl_parcel.writeStringArray(checkStringArray2);
    _aidl_parcel.writeInt(int32_min);
    _aidl_parcel.writeInt(int32_max);
    _aidl_parcel.writeLong(int64_max);
    _aidl_parcel.writeInt(hexInt32_neg_1);
    _aidl_parcel.writeStrongBinder(ibinder);
    _aidl_parcel.writeTypedObject(empty, _aidl_flag);
    _aidl_parcel.writeByteArray(int8_1);
    _aidl_parcel.writeIntArray(int32_1);
    _aidl_parcel.writeLongArray(int64_1);
    _aidl_parcel.writeInt(hexInt32_pos_1);
    _aidl_parcel.writeInt(hexInt64_pos_1);
    _aidl_parcel.writeInt(const_exprs_1);
    _aidl_parcel.writeInt(const_exprs_2);
    _aidl_parcel.writeInt(const_exprs_3);
    _aidl_parcel.writeInt(const_exprs_4);
    _aidl_parcel.writeInt(const_exprs_5);
    _aidl_parcel.writeInt(const_exprs_6);
    _aidl_parcel.writeInt(const_exprs_7);
    _aidl_parcel.writeInt(const_exprs_8);
    _aidl_parcel.writeInt(const_exprs_9);
    _aidl_parcel.writeInt(const_exprs_10);
    _aidl_parcel.writeString(addString1);
    _aidl_parcel.writeString(addString2);
    _aidl_parcel.writeInt(shouldSetBit0AndBit2);
    _aidl_parcel.writeTypedObject(u, _aidl_flag);
    _aidl_parcel.writeTypedObject(shouldBeConstS1, _aidl_flag);
    _aidl_parcel.writeInt(defaultWithFoo);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public final void readFromParcel(android.os.Parcel _aidl_parcel)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldContainThreeFs = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      f = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldBeJerry = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldBeByteBar = _aidl_parcel.readByte();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldBeIntBar = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldBeLongBar = _aidl_parcel.readLong();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldContainTwoByteFoos = _aidl_parcel.createByteArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldContainTwoIntFoos = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldContainTwoLongFoos = _aidl_parcel.createLongArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringDefaultsToFoo = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteDefaultsToFour = _aidl_parcel.readByte();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intDefaultsToFive = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longDefaultsToNegativeSeven = _aidl_parcel.readLong();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      booleanDefaultsToTrue = _aidl_parcel.readBoolean();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      charDefaultsToC = (char)_aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatDefaultsToPi = _aidl_parcel.readFloat();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleWithDefault = _aidl_parcel.readDouble();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      arrayDefaultsTo123 = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      arrayDefaultsToEmpty = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      boolDefault = _aidl_parcel.readBoolean();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteDefault = _aidl_parcel.readByte();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intDefault = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longDefault = _aidl_parcel.readLong();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatDefault = _aidl_parcel.readFloat();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleDefault = _aidl_parcel.readDouble();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      checkDoubleFromFloat = _aidl_parcel.readDouble();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      checkStringArray1 = _aidl_parcel.createStringArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      checkStringArray2 = _aidl_parcel.createStringArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int32_min = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int32_max = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int64_max = _aidl_parcel.readLong();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      hexInt32_neg_1 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      ibinder = _aidl_parcel.readStrongBinder();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      empty = _aidl_parcel.readTypedObject(android.aidl.tests.StructuredParcelable.Empty.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int8_1 = _aidl_parcel.createByteArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int32_1 = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      int64_1 = _aidl_parcel.createLongArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      hexInt32_pos_1 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      hexInt64_pos_1 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_1 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_2 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_3 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_4 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_5 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_6 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_7 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_8 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_9 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      const_exprs_10 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      addString1 = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      addString2 = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldSetBit0AndBit2 = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      u = _aidl_parcel.readTypedObject(android.aidl.tests.Union.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      shouldBeConstS1 = _aidl_parcel.readTypedObject(android.aidl.tests.Union.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      defaultWithFoo = _aidl_parcel.readInt();
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  public static final int BIT0 = 1;
  public static final int BIT1 = 2;
  public static final int BIT2 = 4;
  @Override
  public String toString() {
    java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
    _aidl_sj.add("shouldContainThreeFs: " + (java.util.Arrays.toString(shouldContainThreeFs)));
    _aidl_sj.add("f: " + (f));
    _aidl_sj.add("shouldBeJerry: " + (java.util.Objects.toString(shouldBeJerry)));
    _aidl_sj.add("shouldBeByteBar: " + (shouldBeByteBar));
    _aidl_sj.add("shouldBeIntBar: " + (android.aidl.tests.IntEnum.$.toString(shouldBeIntBar)));
    _aidl_sj.add("shouldBeLongBar: " + (shouldBeLongBar));
    _aidl_sj.add("shouldContainTwoByteFoos: " + (java.util.Arrays.toString(shouldContainTwoByteFoos)));
    _aidl_sj.add("shouldContainTwoIntFoos: " + (android.aidl.tests.IntEnum.$.arrayToString(shouldContainTwoIntFoos)));
    _aidl_sj.add("shouldContainTwoLongFoos: " + (java.util.Arrays.toString(shouldContainTwoLongFoos)));
    _aidl_sj.add("stringDefaultsToFoo: " + (java.util.Objects.toString(stringDefaultsToFoo)));
    _aidl_sj.add("byteDefaultsToFour: " + (byteDefaultsToFour));
    _aidl_sj.add("intDefaultsToFive: " + (intDefaultsToFive));
    _aidl_sj.add("longDefaultsToNegativeSeven: " + (longDefaultsToNegativeSeven));
    _aidl_sj.add("booleanDefaultsToTrue: " + (booleanDefaultsToTrue));
    _aidl_sj.add("charDefaultsToC: " + (charDefaultsToC));
    _aidl_sj.add("floatDefaultsToPi: " + (floatDefaultsToPi));
    _aidl_sj.add("doubleWithDefault: " + (doubleWithDefault));
    _aidl_sj.add("arrayDefaultsTo123: " + (java.util.Arrays.toString(arrayDefaultsTo123)));
    _aidl_sj.add("arrayDefaultsToEmpty: " + (java.util.Arrays.toString(arrayDefaultsToEmpty)));
    _aidl_sj.add("boolDefault: " + (boolDefault));
    _aidl_sj.add("byteDefault: " + (byteDefault));
    _aidl_sj.add("intDefault: " + (intDefault));
    _aidl_sj.add("longDefault: " + (longDefault));
    _aidl_sj.add("floatDefault: " + (floatDefault));
    _aidl_sj.add("doubleDefault: " + (doubleDefault));
    _aidl_sj.add("checkDoubleFromFloat: " + (checkDoubleFromFloat));
    _aidl_sj.add("checkStringArray1: " + (java.util.Arrays.toString(checkStringArray1)));
    _aidl_sj.add("checkStringArray2: " + (java.util.Arrays.toString(checkStringArray2)));
    _aidl_sj.add("int32_min: " + (int32_min));
    _aidl_sj.add("int32_max: " + (int32_max));
    _aidl_sj.add("int64_max: " + (int64_max));
    _aidl_sj.add("hexInt32_neg_1: " + (hexInt32_neg_1));
    _aidl_sj.add("ibinder: " + (java.util.Objects.toString(ibinder)));
    _aidl_sj.add("empty: " + (java.util.Objects.toString(empty)));
    _aidl_sj.add("int8_1: " + (java.util.Arrays.toString(int8_1)));
    _aidl_sj.add("int32_1: " + (java.util.Arrays.toString(int32_1)));
    _aidl_sj.add("int64_1: " + (java.util.Arrays.toString(int64_1)));
    _aidl_sj.add("hexInt32_pos_1: " + (hexInt32_pos_1));
    _aidl_sj.add("hexInt64_pos_1: " + (hexInt64_pos_1));
    _aidl_sj.add("const_exprs_1: " + (const_exprs_1));
    _aidl_sj.add("const_exprs_2: " + (const_exprs_2));
    _aidl_sj.add("const_exprs_3: " + (const_exprs_3));
    _aidl_sj.add("const_exprs_4: " + (const_exprs_4));
    _aidl_sj.add("const_exprs_5: " + (const_exprs_5));
    _aidl_sj.add("const_exprs_6: " + (const_exprs_6));
    _aidl_sj.add("const_exprs_7: " + (const_exprs_7));
    _aidl_sj.add("const_exprs_8: " + (const_exprs_8));
    _aidl_sj.add("const_exprs_9: " + (const_exprs_9));
    _aidl_sj.add("const_exprs_10: " + (const_exprs_10));
    _aidl_sj.add("addString1: " + (java.util.Objects.toString(addString1)));
    _aidl_sj.add("addString2: " + (java.util.Objects.toString(addString2)));
    _aidl_sj.add("shouldSetBit0AndBit2: " + (shouldSetBit0AndBit2));
    _aidl_sj.add("u: " + (java.util.Objects.toString(u)));
    _aidl_sj.add("shouldBeConstS1: " + (java.util.Objects.toString(shouldBeConstS1)));
    _aidl_sj.add("defaultWithFoo: " + (android.aidl.tests.IntEnum.$.toString(defaultWithFoo)));
    return "StructuredParcelable" + _aidl_sj.toString()  ;
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof StructuredParcelable)) return false;
    StructuredParcelable that = (StructuredParcelable)other;
    if (!java.util.Objects.deepEquals(shouldContainThreeFs, that.shouldContainThreeFs)) return false;
    if (!java.util.Objects.deepEquals(f, that.f)) return false;
    if (!java.util.Objects.deepEquals(shouldBeJerry, that.shouldBeJerry)) return false;
    if (!java.util.Objects.deepEquals(shouldBeByteBar, that.shouldBeByteBar)) return false;
    if (!java.util.Objects.deepEquals(shouldBeIntBar, that.shouldBeIntBar)) return false;
    if (!java.util.Objects.deepEquals(shouldBeLongBar, that.shouldBeLongBar)) return false;
    if (!java.util.Objects.deepEquals(shouldContainTwoByteFoos, that.shouldContainTwoByteFoos)) return false;
    if (!java.util.Objects.deepEquals(shouldContainTwoIntFoos, that.shouldContainTwoIntFoos)) return false;
    if (!java.util.Objects.deepEquals(shouldContainTwoLongFoos, that.shouldContainTwoLongFoos)) return false;
    if (!java.util.Objects.deepEquals(stringDefaultsToFoo, that.stringDefaultsToFoo)) return false;
    if (!java.util.Objects.deepEquals(byteDefaultsToFour, that.byteDefaultsToFour)) return false;
    if (!java.util.Objects.deepEquals(intDefaultsToFive, that.intDefaultsToFive)) return false;
    if (!java.util.Objects.deepEquals(longDefaultsToNegativeSeven, that.longDefaultsToNegativeSeven)) return false;
    if (!java.util.Objects.deepEquals(booleanDefaultsToTrue, that.booleanDefaultsToTrue)) return false;
    if (!java.util.Objects.deepEquals(charDefaultsToC, that.charDefaultsToC)) return false;
    if (!java.util.Objects.deepEquals(floatDefaultsToPi, that.floatDefaultsToPi)) return false;
    if (!java.util.Objects.deepEquals(doubleWithDefault, that.doubleWithDefault)) return false;
    if (!java.util.Objects.deepEquals(arrayDefaultsTo123, that.arrayDefaultsTo123)) return false;
    if (!java.util.Objects.deepEquals(arrayDefaultsToEmpty, that.arrayDefaultsToEmpty)) return false;
    if (!java.util.Objects.deepEquals(boolDefault, that.boolDefault)) return false;
    if (!java.util.Objects.deepEquals(byteDefault, that.byteDefault)) return false;
    if (!java.util.Objects.deepEquals(intDefault, that.intDefault)) return false;
    if (!java.util.Objects.deepEquals(longDefault, that.longDefault)) return false;
    if (!java.util.Objects.deepEquals(floatDefault, that.floatDefault)) return false;
    if (!java.util.Objects.deepEquals(doubleDefault, that.doubleDefault)) return false;
    if (!java.util.Objects.deepEquals(checkDoubleFromFloat, that.checkDoubleFromFloat)) return false;
    if (!java.util.Objects.deepEquals(checkStringArray1, that.checkStringArray1)) return false;
    if (!java.util.Objects.deepEquals(checkStringArray2, that.checkStringArray2)) return false;
    if (!java.util.Objects.deepEquals(int32_min, that.int32_min)) return false;
    if (!java.util.Objects.deepEquals(int32_max, that.int32_max)) return false;
    if (!java.util.Objects.deepEquals(int64_max, that.int64_max)) return false;
    if (!java.util.Objects.deepEquals(hexInt32_neg_1, that.hexInt32_neg_1)) return false;
    if (!java.util.Objects.deepEquals(ibinder, that.ibinder)) return false;
    if (!java.util.Objects.deepEquals(empty, that.empty)) return false;
    if (!java.util.Objects.deepEquals(int8_1, that.int8_1)) return false;
    if (!java.util.Objects.deepEquals(int32_1, that.int32_1)) return false;
    if (!java.util.Objects.deepEquals(int64_1, that.int64_1)) return false;
    if (!java.util.Objects.deepEquals(hexInt32_pos_1, that.hexInt32_pos_1)) return false;
    if (!java.util.Objects.deepEquals(hexInt64_pos_1, that.hexInt64_pos_1)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_1, that.const_exprs_1)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_2, that.const_exprs_2)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_3, that.const_exprs_3)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_4, that.const_exprs_4)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_5, that.const_exprs_5)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_6, that.const_exprs_6)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_7, that.const_exprs_7)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_8, that.const_exprs_8)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_9, that.const_exprs_9)) return false;
    if (!java.util.Objects.deepEquals(const_exprs_10, that.const_exprs_10)) return false;
    if (!java.util.Objects.deepEquals(addString1, that.addString1)) return false;
    if (!java.util.Objects.deepEquals(addString2, that.addString2)) return false;
    if (!java.util.Objects.deepEquals(shouldSetBit0AndBit2, that.shouldSetBit0AndBit2)) return false;
    if (!java.util.Objects.deepEquals(u, that.u)) return false;
    if (!java.util.Objects.deepEquals(shouldBeConstS1, that.shouldBeConstS1)) return false;
    if (!java.util.Objects.deepEquals(defaultWithFoo, that.defaultWithFoo)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(shouldContainThreeFs, f, shouldBeJerry, shouldBeByteBar, shouldBeIntBar, shouldBeLongBar, shouldContainTwoByteFoos, shouldContainTwoIntFoos, shouldContainTwoLongFoos, stringDefaultsToFoo, byteDefaultsToFour, intDefaultsToFive, longDefaultsToNegativeSeven, booleanDefaultsToTrue, charDefaultsToC, floatDefaultsToPi, doubleWithDefault, arrayDefaultsTo123, arrayDefaultsToEmpty, boolDefault, byteDefault, intDefault, longDefault, floatDefault, doubleDefault, checkDoubleFromFloat, checkStringArray1, checkStringArray2, int32_min, int32_max, int64_max, hexInt32_neg_1, ibinder, empty, int8_1, int32_1, int64_1, hexInt32_pos_1, hexInt64_pos_1, const_exprs_1, const_exprs_2, const_exprs_3, const_exprs_4, const_exprs_5, const_exprs_6, const_exprs_7, const_exprs_8, const_exprs_9, const_exprs_10, addString1, addString2, shouldSetBit0AndBit2, u, shouldBeConstS1, defaultWithFoo).toArray());
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    _mask |= describeContents(empty);
    _mask |= describeContents(u);
    _mask |= describeContents(shouldBeConstS1);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
  // Make sure we can send an empty parcelable
  public static class Empty implements android.os.Parcelable
  {
    public static final android.os.Parcelable.Creator<Empty> CREATOR = new android.os.Parcelable.Creator<Empty>() {
      @Override
      public Empty createFromParcel(android.os.Parcel _aidl_source) {
        Empty _aidl_out = new Empty();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public Empty[] newArray(int _aidl_size) {
        return new Empty[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      int _aidl_end_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.setDataPosition(_aidl_start_pos);
      _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
      _aidl_parcel.setDataPosition(_aidl_end_pos);
    }
    public final void readFromParcel(android.os.Parcel _aidl_parcel)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      int _aidl_parcelable_size = _aidl_parcel.readInt();
      try {
        if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      } finally {
        if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
          throw new android.os.BadParcelableException("Overflow in the size of parcelable");
        }
        _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      }
    }
    @Override
    public String toString() {
      java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
      return "Empty" + _aidl_sj.toString()  ;
    }
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null) return false;
      if (!(other instanceof Empty)) return false;
      Empty that = (Empty)other;
      return true;
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.deepHashCode(java.util.Arrays.asList().toArray());
    }
    @Override
    public int describeContents() {
      int _mask = 0;
      return _mask;
    }
  }
}
