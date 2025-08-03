/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 3 --hash 70d76c61eb0c82288e924862c10b910d1b7d8cf8 --rpc --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-java-source/gen/android/aidl/versioned/tests/BazUnion.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-java-source/gen -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/3 system/tools/aidl/aidl_api/aidl-test-versioned-interface/3/android/aidl/versioned/tests/BazUnion.aidl
 */
package android.aidl.versioned.tests;
public final class BazUnion implements android.os.Parcelable {
  // tags for union fields
  public final static int intNum = 0;  // int intNum;
  public final static int longNum = 1;  // long longNum;

  private int _tag;
  private Object _value;

  public BazUnion() {
    int _value = 0;
    this._tag = intNum;
    this._value = _value;
  }

  private BazUnion(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
  }

  private BazUnion(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // int intNum;

  public static BazUnion intNum(int _value) {
    return new BazUnion(intNum, _value);
  }

  public int getIntNum() {
    _assertTag(intNum);
    return (int) _value;
  }

  public void setIntNum(int _value) {
    _set(intNum, _value);
  }

  // long longNum;

  public static BazUnion longNum(long _value) {
    return new BazUnion(longNum, _value);
  }

  public long getLongNum() {
    _assertTag(longNum);
    return (long) _value;
  }

  public void setLongNum(long _value) {
    _set(longNum, _value);
  }

  public static final android.os.Parcelable.Creator<BazUnion> CREATOR = new android.os.Parcelable.Creator<BazUnion>() {
    @Override
    public BazUnion createFromParcel(android.os.Parcel _aidl_source) {
      return new BazUnion(_aidl_source);
    }
    @Override
    public BazUnion[] newArray(int _aidl_size) {
      return new BazUnion[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case intNum:
      _aidl_parcel.writeInt(getIntNum());
      break;
    case longNum:
      _aidl_parcel.writeLong(getLongNum());
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case intNum: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    case longNum: {
      long _aidl_value;
      _aidl_value = _aidl_parcel.readLong();
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
    case intNum: return "intNum";
    case longNum: return "longNum";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int intNum = 0;
    public static final int longNum = 1;
  }
}
