/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/unions/EnumUnion.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/unions/EnumUnion.aidl
 */
package android.aidl.tests.unions;
public final class EnumUnion implements android.os.Parcelable {
  // tags for union fields
  public final static int intEnum = 0;  // android.aidl.tests.IntEnum intEnum;
  public final static int longEnum = 1;  // android.aidl.tests.LongEnum longEnum;
  public final static int deprecatedField = 2;  // int deprecatedField;

  private int _tag;
  private Object _value;

  public EnumUnion() {
    int _value = android.aidl.tests.IntEnum.FOO;
    this._tag = intEnum;
    this._value = _value;
  }

  private EnumUnion(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
  }

  private EnumUnion(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // android.aidl.tests.IntEnum intEnum;

  public static EnumUnion intEnum(int _value) {
    return new EnumUnion(intEnum, _value);
  }

  public int getIntEnum() {
    _assertTag(intEnum);
    return (int) _value;
  }

  public void setIntEnum(int _value) {
    _set(intEnum, _value);
  }

  // android.aidl.tests.LongEnum longEnum;

  public static EnumUnion longEnum(long _value) {
    return new EnumUnion(longEnum, _value);
  }

  public long getLongEnum() {
    _assertTag(longEnum);
    return (long) _value;
  }

  public void setLongEnum(long _value) {
    _set(longEnum, _value);
  }

  // int deprecatedField;

  /** @deprecated do not use this */
  @Deprecated
  public static EnumUnion deprecatedField(int _value) {
    return new EnumUnion(deprecatedField, _value);
  }

  public int getDeprecatedField() {
    _assertTag(deprecatedField);
    return (int) _value;
  }

  public void setDeprecatedField(int _value) {
    _set(deprecatedField, _value);
  }

  public static final android.os.Parcelable.Creator<EnumUnion> CREATOR = new android.os.Parcelable.Creator<EnumUnion>() {
    @Override
    public EnumUnion createFromParcel(android.os.Parcel _aidl_source) {
      return new EnumUnion(_aidl_source);
    }
    @Override
    public EnumUnion[] newArray(int _aidl_size) {
      return new EnumUnion[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case intEnum:
      _aidl_parcel.writeInt(getIntEnum());
      break;
    case longEnum:
      _aidl_parcel.writeLong(getLongEnum());
      break;
    case deprecatedField:
      _aidl_parcel.writeInt(getDeprecatedField());
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case intEnum: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    case longEnum: {
      long _aidl_value;
      _aidl_value = _aidl_parcel.readLong();
      _set(_aidl_tag, _aidl_value);
      return; }
    case deprecatedField: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    }
    throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
  }

  @Override
  public int describeContents() {
    int _mask = 0;
    switch (getTag()) {
    }
    return _mask;
  }

  @Override
  public String toString() {
    switch (_tag) {
    case intEnum: return "EnumUnion.intEnum(" + (android.aidl.tests.IntEnum.$.toString(getIntEnum())) + ")";
    case longEnum: return "EnumUnion.longEnum(" + (getLongEnum()) + ")";
    case deprecatedField: return "EnumUnion.deprecatedField(" + (getDeprecatedField()) + ")";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof EnumUnion)) return false;
    EnumUnion that = (EnumUnion)other;
    if (_tag != that._tag) return false;
    if (!java.util.Objects.deepEquals(_value, that._value)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(_tag, _value).toArray());
  }

  private void _assertTag(int tag) {
    if (getTag() != tag) {
      throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
    }
  }

  private String _tagString(int _tag) {
    switch (_tag) {
    case intEnum: return "intEnum";
    case longEnum: return "longEnum";
    case deprecatedField: return "deprecatedField";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int intEnum = 0;
    public static final int longEnum = 1;
    /** @deprecated do not use this */
    @Deprecated
    public static final int deprecatedField = 2;
  }
}
