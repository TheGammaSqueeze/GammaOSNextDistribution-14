/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/UnionWithFd.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/UnionWithFd.aidl
 */
package android.aidl.tests;
public final class UnionWithFd implements android.os.Parcelable {
  // tags for union fields
  public final static int num = 0;  // int num;
  public final static int pfd = 1;  // ParcelFileDescriptor pfd;

  private int _tag;
  private Object _value;

  public UnionWithFd() {
    int _value = 0;
    this._tag = num;
    this._value = _value;
  }

  private UnionWithFd(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
  }

  private UnionWithFd(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // int num;

  public static UnionWithFd num(int _value) {
    return new UnionWithFd(num, _value);
  }

  public int getNum() {
    _assertTag(num);
    return (int) _value;
  }

  public void setNum(int _value) {
    _set(num, _value);
  }

  // ParcelFileDescriptor pfd;

  public static UnionWithFd pfd(android.os.ParcelFileDescriptor _value) {
    return new UnionWithFd(pfd, _value);
  }

  public android.os.ParcelFileDescriptor getPfd() {
    _assertTag(pfd);
    return (android.os.ParcelFileDescriptor) _value;
  }

  public void setPfd(android.os.ParcelFileDescriptor _value) {
    _set(pfd, _value);
  }

  public static final android.os.Parcelable.Creator<UnionWithFd> CREATOR = new android.os.Parcelable.Creator<UnionWithFd>() {
    @Override
    public UnionWithFd createFromParcel(android.os.Parcel _aidl_source) {
      return new UnionWithFd(_aidl_source);
    }
    @Override
    public UnionWithFd[] newArray(int _aidl_size) {
      return new UnionWithFd[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case num:
      _aidl_parcel.writeInt(getNum());
      break;
    case pfd:
      _aidl_parcel.writeTypedObject(getPfd(), _aidl_flag);
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
    case pfd: {
      android.os.ParcelFileDescriptor _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    }
    throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
  }

  @Override
  public int describeContents() {
    int _mask = 0;
    switch (getTag()) {
    case pfd:
      _mask |= describeContents(getPfd());
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
    case pfd: return "pfd";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int num = 0;
    public static final int pfd = 1;
  }
}
