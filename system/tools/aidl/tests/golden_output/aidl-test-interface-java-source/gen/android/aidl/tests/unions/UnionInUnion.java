/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/unions/UnionInUnion.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/unions/UnionInUnion.aidl
 */
package android.aidl.tests.unions;
public final class UnionInUnion implements android.os.Parcelable {
  // tags for union fields
  public final static int first = 0;  // android.aidl.tests.unions.EnumUnion first;
  public final static int second = 1;  // int second;

  private int _tag;
  private Object _value;

  public UnionInUnion() {
    android.aidl.tests.unions.EnumUnion _value = null;
    this._tag = first;
    this._value = _value;
  }

  private UnionInUnion(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
  }

  private UnionInUnion(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // android.aidl.tests.unions.EnumUnion first;

  public static UnionInUnion first(android.aidl.tests.unions.EnumUnion _value) {
    return new UnionInUnion(first, _value);
  }

  public android.aidl.tests.unions.EnumUnion getFirst() {
    _assertTag(first);
    return (android.aidl.tests.unions.EnumUnion) _value;
  }

  public void setFirst(android.aidl.tests.unions.EnumUnion _value) {
    _set(first, _value);
  }

  // int second;

  public static UnionInUnion second(int _value) {
    return new UnionInUnion(second, _value);
  }

  public int getSecond() {
    _assertTag(second);
    return (int) _value;
  }

  public void setSecond(int _value) {
    _set(second, _value);
  }

  public static final android.os.Parcelable.Creator<UnionInUnion> CREATOR = new android.os.Parcelable.Creator<UnionInUnion>() {
    @Override
    public UnionInUnion createFromParcel(android.os.Parcel _aidl_source) {
      return new UnionInUnion(_aidl_source);
    }
    @Override
    public UnionInUnion[] newArray(int _aidl_size) {
      return new UnionInUnion[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case first:
      _aidl_parcel.writeTypedObject(getFirst(), _aidl_flag);
      break;
    case second:
      _aidl_parcel.writeInt(getSecond());
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case first: {
      android.aidl.tests.unions.EnumUnion _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.aidl.tests.unions.EnumUnion.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case second: {
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
    case first:
      _mask |= describeContents(getFirst());
      break;
    }
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }

  @Override
  public String toString() {
    switch (_tag) {
    case first: return "UnionInUnion.first(" + (java.util.Objects.toString(getFirst())) + ")";
    case second: return "UnionInUnion.second(" + (getSecond()) + ")";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof UnionInUnion)) return false;
    UnionInUnion that = (UnionInUnion)other;
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
    case first: return "first";
    case second: return "second";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int first = 0;
    public static final int second = 1;
  }
}
