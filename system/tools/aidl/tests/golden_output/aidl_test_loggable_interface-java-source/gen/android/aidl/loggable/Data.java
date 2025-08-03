/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen/android/aidl/loggable/Data.java.d -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Data.aidl
 */
package android.aidl.loggable;
public class Data implements android.os.Parcelable
{
  public int num = 0;
  public java.lang.String str;
  public android.aidl.loggable.Union nestedUnion;
  public byte nestedEnum = android.aidl.loggable.Enum.FOO;
  public static final android.os.Parcelable.Creator<Data> CREATOR = new android.os.Parcelable.Creator<Data>() {
    @Override
    public Data createFromParcel(android.os.Parcel _aidl_source) {
      Data _aidl_out = new Data();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public Data[] newArray(int _aidl_size) {
      return new Data[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeInt(num);
    _aidl_parcel.writeString(str);
    _aidl_parcel.writeTypedObject(nestedUnion, _aidl_flag);
    _aidl_parcel.writeByte(nestedEnum);
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
      num = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      str = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      nestedUnion = _aidl_parcel.readTypedObject(android.aidl.loggable.Union.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      nestedEnum = _aidl_parcel.readByte();
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    _mask |= describeContents(nestedUnion);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
}
