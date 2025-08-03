/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/immutable/Union.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/immutable/Union.aidl
 */
package android.aidl.tests.immutable;
public final class Union implements android.os.Parcelable {
  // tags for union fields
  public final static int num = 0;  // int num;
  public final static int str = 1;  // String[] str;
  public final static int bar = 2;  // android.aidl.tests.immutable.Bar bar;

  private final int _tag;
  private final Object _value;

  public Union() {
    int _value = 0;
    this._tag = num;
    this._value = _value;
  }

  private Union(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // int num;

  public static Union num(int _value) {
    return new Union(num, _value);
  }

  public int getNum() {
    _assertTag(num);
    return (int) _value;
  }

  // String[] str;

  public static Union str(java.lang.String[] _value) {
    return new Union(str, _value);
  }

  public java.lang.String[] getStr() {
    _assertTag(str);
    return (java.lang.String[]) _value;
  }

  // android.aidl.tests.immutable.Bar bar;

  public static Union bar(android.aidl.tests.immutable.Bar _value) {
    return new Union(bar, _value);
  }

  public android.aidl.tests.immutable.Bar getBar() {
    _assertTag(bar);
    return (android.aidl.tests.immutable.Bar) _value;
  }

  public static final android.os.Parcelable.Creator<Union> CREATOR = new android.os.Parcelable.Creator<Union>() {
    @Override
    public Union createFromParcel(android.os.Parcel _aidl_source) {
      return internalCreateFromParcel(_aidl_source);
    }
    @Override
    public Union[] newArray(int _aidl_size) {
      return new Union[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case num:
      _aidl_parcel.writeInt(getNum());
      break;
    case str:
      _aidl_parcel.writeStringArray(getStr());
      break;
    case bar:
      _aidl_parcel.writeTypedObject(getBar(), _aidl_flag);
      break;
    }
  }

  private static Union internalCreateFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case num: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      return new Union(_aidl_tag, _aidl_value); }
    case str: {
      java.lang.String[] _aidl_value;
      _aidl_value = _aidl_parcel.createStringArray();
      return new Union(_aidl_tag, _aidl_value); }
    case bar: {
      android.aidl.tests.immutable.Bar _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.aidl.tests.immutable.Bar.CREATOR);
      return new Union(_aidl_tag, _aidl_value); }
    }
    throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
  }

  @Override
  public int describeContents() {
    int _mask = 0;
    switch (getTag()) {
    case bar:
      _mask |= describeContents(getBar());
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

  private void _assertTag(int tag) {
    if (getTag() != tag) {
      throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
    }
  }

  private String _tagString(int _tag) {
    switch (_tag) {
    case num: return "num";
    case str: return "str";
    case bar: return "bar";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }
  public static @interface Tag {
    public static final int num = 0;
    public static final int str = 1;
    public static final int bar = 2;
  }
}
