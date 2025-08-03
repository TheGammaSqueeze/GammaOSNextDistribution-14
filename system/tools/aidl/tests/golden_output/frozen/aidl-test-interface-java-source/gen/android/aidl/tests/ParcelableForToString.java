/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/ParcelableForToString.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ParcelableForToString.aidl
 */
package android.aidl.tests;
public class ParcelableForToString implements android.os.Parcelable
{
  public int intValue = 0;
  public int[] intArray;
  public long longValue = 0L;
  public long[] longArray;
  public double doubleValue = 0.000000;
  public double[] doubleArray;
  public float floatValue = 0.000000f;
  public float[] floatArray;
  public byte byteValue = 0;
  public byte[] byteArray;
  public boolean booleanValue = false;
  public boolean[] booleanArray;
  public java.lang.String stringValue;
  public java.lang.String[] stringArray;
  public java.util.List<java.lang.String> stringList;
  public android.aidl.tests.OtherParcelableForToString parcelableValue;
  public android.aidl.tests.OtherParcelableForToString[] parcelableArray;
  public int enumValue = android.aidl.tests.IntEnum.FOO;
  public int[] enumArray;
  public java.lang.String[] nullArray;
  public java.util.List<java.lang.String> nullList;
  public android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> parcelableGeneric;
  public android.aidl.tests.Union unionValue;
  public static final android.os.Parcelable.Creator<ParcelableForToString> CREATOR = new android.os.Parcelable.Creator<ParcelableForToString>() {
    @Override
    public ParcelableForToString createFromParcel(android.os.Parcel _aidl_source) {
      ParcelableForToString _aidl_out = new ParcelableForToString();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public ParcelableForToString[] newArray(int _aidl_size) {
      return new ParcelableForToString[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeInt(intValue);
    _aidl_parcel.writeIntArray(intArray);
    _aidl_parcel.writeLong(longValue);
    _aidl_parcel.writeLongArray(longArray);
    _aidl_parcel.writeDouble(doubleValue);
    _aidl_parcel.writeDoubleArray(doubleArray);
    _aidl_parcel.writeFloat(floatValue);
    _aidl_parcel.writeFloatArray(floatArray);
    _aidl_parcel.writeByte(byteValue);
    _aidl_parcel.writeByteArray(byteArray);
    _aidl_parcel.writeBoolean(booleanValue);
    _aidl_parcel.writeBooleanArray(booleanArray);
    _aidl_parcel.writeString(stringValue);
    _aidl_parcel.writeStringArray(stringArray);
    _aidl_parcel.writeStringList(stringList);
    _aidl_parcel.writeTypedObject(parcelableValue, _aidl_flag);
    _aidl_parcel.writeTypedArray(parcelableArray, _aidl_flag);
    _aidl_parcel.writeInt(enumValue);
    _aidl_parcel.writeIntArray(enumArray);
    _aidl_parcel.writeStringArray(nullArray);
    _aidl_parcel.writeStringList(nullList);
    _aidl_parcel.writeTypedObject(parcelableGeneric, _aidl_flag);
    _aidl_parcel.writeTypedObject(unionValue, _aidl_flag);
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
      intValue = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      intArray = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longValue = _aidl_parcel.readLong();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      longArray = _aidl_parcel.createLongArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleValue = _aidl_parcel.readDouble();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      doubleArray = _aidl_parcel.createDoubleArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatValue = _aidl_parcel.readFloat();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      floatArray = _aidl_parcel.createFloatArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteValue = _aidl_parcel.readByte();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      byteArray = _aidl_parcel.createByteArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      booleanValue = _aidl_parcel.readBoolean();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      booleanArray = _aidl_parcel.createBooleanArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringValue = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringArray = _aidl_parcel.createStringArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      stringList = _aidl_parcel.createStringArrayList();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableValue = _aidl_parcel.readTypedObject(android.aidl.tests.OtherParcelableForToString.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableArray = _aidl_parcel.createTypedArray(android.aidl.tests.OtherParcelableForToString.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      enumValue = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      enumArray = _aidl_parcel.createIntArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      nullArray = _aidl_parcel.createStringArray();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      nullList = _aidl_parcel.createStringArrayList();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      parcelableGeneric = _aidl_parcel.readTypedObject(android.aidl.tests.GenericStructuredParcelable.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      unionValue = _aidl_parcel.readTypedObject(android.aidl.tests.Union.CREATOR);
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
    _aidl_sj.add("intValue: " + (intValue));
    _aidl_sj.add("intArray: " + (java.util.Arrays.toString(intArray)));
    _aidl_sj.add("longValue: " + (longValue));
    _aidl_sj.add("longArray: " + (java.util.Arrays.toString(longArray)));
    _aidl_sj.add("doubleValue: " + (doubleValue));
    _aidl_sj.add("doubleArray: " + (java.util.Arrays.toString(doubleArray)));
    _aidl_sj.add("floatValue: " + (floatValue));
    _aidl_sj.add("floatArray: " + (java.util.Arrays.toString(floatArray)));
    _aidl_sj.add("byteValue: " + (byteValue));
    _aidl_sj.add("byteArray: " + (java.util.Arrays.toString(byteArray)));
    _aidl_sj.add("booleanValue: " + (booleanValue));
    _aidl_sj.add("booleanArray: " + (java.util.Arrays.toString(booleanArray)));
    _aidl_sj.add("stringValue: " + (java.util.Objects.toString(stringValue)));
    _aidl_sj.add("stringArray: " + (java.util.Arrays.toString(stringArray)));
    _aidl_sj.add("stringList: " + (java.util.Objects.toString(stringList)));
    _aidl_sj.add("parcelableValue: " + (java.util.Objects.toString(parcelableValue)));
    _aidl_sj.add("parcelableArray: " + (java.util.Arrays.toString(parcelableArray)));
    _aidl_sj.add("enumValue: " + (android.aidl.tests.IntEnum.$.toString(enumValue)));
    _aidl_sj.add("enumArray: " + (android.aidl.tests.IntEnum.$.arrayToString(enumArray)));
    _aidl_sj.add("nullArray: " + (java.util.Arrays.toString(nullArray)));
    _aidl_sj.add("nullList: " + (java.util.Objects.toString(nullList)));
    _aidl_sj.add("parcelableGeneric: " + (java.util.Objects.toString(parcelableGeneric)));
    _aidl_sj.add("unionValue: " + (java.util.Objects.toString(unionValue)));
    return "ParcelableForToString" + _aidl_sj.toString()  ;
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    _mask |= describeContents(parcelableValue);
    _mask |= describeContents(parcelableArray);
    _mask |= describeContents(parcelableGeneric);
    _mask |= describeContents(unionValue);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof Object[]) {
      int _mask = 0;
      for (Object o : (Object[]) _v) {
        _mask |= describeContents(o);
      }
      return _mask;
    }
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
}
