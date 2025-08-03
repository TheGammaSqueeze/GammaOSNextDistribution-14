/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/immutable/Foo.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/immutable/Foo.aidl
 */
package android.aidl.tests.immutable;
public class Foo implements android.os.Parcelable
{
  public final int a;
  public final android.aidl.tests.immutable.Bar b;
  public final java.util.List<android.aidl.tests.immutable.Bar> c;
  public final java.util.Map<java.lang.String,android.aidl.tests.immutable.Bar> d;
  public final android.aidl.tests.immutable.Bar[] e;
  public final android.aidl.tests.immutable.Union u;
  public static final class Builder
  {
    private int a = 10;
    public Builder setA(int a) {
      this.a = a;
      return this;
    }
    private android.aidl.tests.immutable.Bar b;
    public Builder setB(android.aidl.tests.immutable.Bar b) {
      this.b = b;
      return this;
    }
    private java.util.List<android.aidl.tests.immutable.Bar> c;
    public Builder setC(java.util.List<android.aidl.tests.immutable.Bar> c) {
      this.c = c;
      return this;
    }
    private java.util.Map<java.lang.String,android.aidl.tests.immutable.Bar> d;
    public Builder setD(java.util.Map<java.lang.String,android.aidl.tests.immutable.Bar> d) {
      this.d = d;
      return this;
    }
    private android.aidl.tests.immutable.Bar[] e;
    public Builder setE(android.aidl.tests.immutable.Bar[] e) {
      this.e = e;
      return this;
    }
    private android.aidl.tests.immutable.Union u;
    public Builder setU(android.aidl.tests.immutable.Union u) {
      this.u = u;
      return this;
    }
    public android.aidl.tests.immutable.Foo build() {
      return new android.aidl.tests.immutable.Foo(a, b, c, d, e, u);
    }
  }
  public static final android.os.Parcelable.Creator<Foo> CREATOR = new android.os.Parcelable.Creator<Foo>() {
    @Override
    public Foo createFromParcel(android.os.Parcel _aidl_source) {
      return internalCreateFromParcel(_aidl_source);
    }
    @Override
    public Foo[] newArray(int _aidl_size) {
      return new Foo[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeInt(a);
    _aidl_parcel.writeTypedObject(b, _aidl_flag);
    _aidl_parcel.writeTypedList(c, _aidl_flag);
    if (d == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(d.size());
      d.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeTypedObject(v, _aidl_flag);
      });
    }
    _aidl_parcel.writeTypedArray(e, _aidl_flag);
    _aidl_parcel.writeTypedObject(u, _aidl_flag);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public Foo(int a, android.aidl.tests.immutable.Bar b, java.util.List<android.aidl.tests.immutable.Bar> c, java.util.Map<java.lang.String,android.aidl.tests.immutable.Bar> d, android.aidl.tests.immutable.Bar[] e, android.aidl.tests.immutable.Union u)
  {
    this.a = a;
    this.b = b;
    this.c = c == null ? null : java.util.Collections.unmodifiableList(c);
    this.d = d == null ? null : java.util.Collections.unmodifiableMap(d);
    this.e = e;
    this.u = u;
  }
  private static Foo internalCreateFromParcel(android.os.Parcel _aidl_parcel)
  {
    Builder _aidl_parcelable_builder = new Builder();
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small"); _aidl_parcelable_builder.build();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      int _aidl_temp_a;
      _aidl_temp_a = _aidl_parcel.readInt();
      _aidl_parcelable_builder.setA(_aidl_temp_a);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      android.aidl.tests.immutable.Bar _aidl_temp_b;
      _aidl_temp_b = _aidl_parcel.readTypedObject(android.aidl.tests.immutable.Bar.CREATOR);
      _aidl_parcelable_builder.setB(_aidl_temp_b);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      java.util.List<android.aidl.tests.immutable.Bar> _aidl_temp_c;
      _aidl_temp_c = _aidl_parcel.createTypedArrayList(android.aidl.tests.immutable.Bar.CREATOR);
      _aidl_parcelable_builder.setC(_aidl_temp_c);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      java.util.Map<java.lang.String,android.aidl.tests.immutable.Bar> _aidl_temp_d;
      {
        int N = _aidl_parcel.readInt();
        _aidl_temp_d = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          android.aidl.tests.immutable.Bar v;
          v = _aidl_parcel.readTypedObject(android.aidl.tests.immutable.Bar.CREATOR);
          _aidl_temp_d.put(k, v);
        });
      }
      _aidl_parcelable_builder.setD(_aidl_temp_d);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      android.aidl.tests.immutable.Bar[] _aidl_temp_e;
      _aidl_temp_e = _aidl_parcel.createTypedArray(android.aidl.tests.immutable.Bar.CREATOR);
      _aidl_parcelable_builder.setE(_aidl_temp_e);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return _aidl_parcelable_builder.build();
      android.aidl.tests.immutable.Union _aidl_temp_u;
      _aidl_temp_u = _aidl_parcel.readTypedObject(android.aidl.tests.immutable.Union.CREATOR);
      _aidl_parcelable_builder.setU(_aidl_temp_u);
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
    _mask |= describeContents(b);
    _mask |= describeContents(c);
    _mask |= describeContents(d);
    _mask |= describeContents(e);
    _mask |= describeContents(u);
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
    if (_v instanceof java.util.Collection) {
      int _mask = 0;
      for (Object o : (java.util.Collection) _v) {
        _mask |= describeContents(o);
      }
      return _mask;
    }
    if (_v instanceof java.util.Map) {
      return describeContents(((java.util.Map) _v).values());
    }
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
}
