/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/immutable/Bar.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/immutable/Bar.aidl
 */
package android.aidl.tests.immutable;
public class Bar implements android.os.Parcelable
{
  public final java.lang.String s;
  public static final class Builder
  {
    private java.lang.String s = "bar";
    public Builder setS(java.lang.String s) {
      this.s = s;
      return this;
    }
    public android.aidl.tests.immutable.Bar build() {
      return new android.aidl.tests.immutable.Bar(s);
    }
  }
  public static final android.os.Parcelable.Creator<Bar> CREATOR = new android.os.Parcelable.Creator<Bar>() {
    @Override
    public Bar createFromParcel(android.os.Parcel _aidl_source) {
      return internalCreateFromParcel(_aidl_source);
    }
    @Override
    public Bar[] newArray(int _aidl_size) {
      return new Bar[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeString(s);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public Bar(java.lang.String s)
  {
    this.s = s;
  }
  private static Bar internalCreateFromParcel(android.os.Parcel _aidl_parcel)
  {
    Builder _aidl_parcelable_builder = new Builder();
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small"); _aidl_parcelable_builder.build();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      java.lang.String _aidl_temp_s;
      _aidl_temp_s = _aidl_parcel.readString();
      _aidl_parcelable_builder.setS(_aidl_temp_s);
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      return _aidl_parcelable_builder.build();
    }
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    return _mask;
  }
}
