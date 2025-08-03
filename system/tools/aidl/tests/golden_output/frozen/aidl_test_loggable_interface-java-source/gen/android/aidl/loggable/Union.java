/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen/android/aidl/loggable/Union.java.d -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Union.aidl
 */
package android.aidl.loggable;
public final class Union implements android.os.Parcelable {
  // tags for union fields
  public final static int num = 0;  // int num;
  public final static int str = 1;  // String str;

  private int _tag;
  private Object _value;

  public Union() {
    int _value = 43;
    this._tag = num;
    this._value = _value;
  }

  private Union(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
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

  public void setNum(int _value) {
    _set(num, _value);
  }

  // String str;

  public static Union str(java.lang.String _value) {
    return new Union(str, _value);
  }

  public java.lang.String getStr() {
    _assertTag(str);
    return (java.lang.String) _value;
  }

  public void setStr(java.lang.String _value) {
    _set(str, _value);
  }

  public static final android.os.Parcelable.Creator<Union> CREATOR = new android.os.Parcelable.Creator<Union>() {
    @Override
    public Union createFromParcel(android.os.Parcel _aidl_source) {
      return new Union(_aidl_source);
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
      _aidl_parcel.writeString(getStr());
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case num: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    case str: {
      java.lang.String _aidl_value;
      _aidl_value = _aidl_parcel.readString();
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

  private void _assertTag(int tag) {
    if (getTag() != tag) {
      throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
    }
  }

  private String _tagString(int _tag) {
    switch (_tag) {
    case num: return "num";
    case str: return "str";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int num = 0;
    public static final int str = 1;
  }
}
