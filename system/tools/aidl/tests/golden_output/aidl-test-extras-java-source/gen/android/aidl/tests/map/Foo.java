/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/map/Foo.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/map/Foo.aidl
 */
package android.aidl.tests.map;
public class Foo implements android.os.Parcelable
{
  public java.util.Map<java.lang.String,int[]> intEnumArrayMap;
  public java.util.Map<java.lang.String,int[]> intArrayMap;
  public java.util.Map<java.lang.String,android.aidl.tests.map.Bar> barMap;
  public java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> barArrayMap;
  public java.util.Map<java.lang.String,java.lang.String> stringMap;
  public java.util.Map<java.lang.String,java.lang.String[]> stringArrayMap;
  public java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> interfaceMap;
  public java.util.Map<java.lang.String,android.os.IBinder> ibinderMap;
  public static final android.os.Parcelable.Creator<Foo> CREATOR = new android.os.Parcelable.Creator<Foo>() {
    @Override
    public Foo createFromParcel(android.os.Parcel _aidl_source) {
      Foo _aidl_out = new Foo();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
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
    if (intEnumArrayMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(intEnumArrayMap.size());
      intEnumArrayMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeIntArray(v);
      });
    }
    if (intArrayMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(intArrayMap.size());
      intArrayMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeIntArray(v);
      });
    }
    if (barMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(barMap.size());
      barMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeTypedObject(v, _aidl_flag);
      });
    }
    if (barArrayMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(barArrayMap.size());
      barArrayMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeTypedArray(v, _aidl_flag);
      });
    }
    if (stringMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(stringMap.size());
      stringMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeString(v);
      });
    }
    if (stringArrayMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(stringArrayMap.size());
      stringArrayMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeStringArray(v);
      });
    }
    if (interfaceMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(interfaceMap.size());
      interfaceMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeStrongInterface(v);
      });
    }
    if (ibinderMap == null) {
      _aidl_parcel.writeInt(-1);
    } else {
      _aidl_parcel.writeInt(ibinderMap.size());
      ibinderMap.forEach((k, v) -> {
        _aidl_parcel.writeString(k);
        _aidl_parcel.writeStrongBinder(v);
      });
    }
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
      {
        int N = _aidl_parcel.readInt();
        intEnumArrayMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          int[] v;
          v = _aidl_parcel.createIntArray();
          intEnumArrayMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        intArrayMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          int[] v;
          v = _aidl_parcel.createIntArray();
          intArrayMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        barMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          android.aidl.tests.map.Bar v;
          v = _aidl_parcel.readTypedObject(android.aidl.tests.map.Bar.CREATOR);
          barMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        barArrayMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          android.aidl.tests.map.Bar[] v;
          v = _aidl_parcel.createTypedArray(android.aidl.tests.map.Bar.CREATOR);
          barArrayMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        stringMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          java.lang.String v;
          v = _aidl_parcel.readString();
          stringMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        stringArrayMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          java.lang.String[] v;
          v = _aidl_parcel.createStringArray();
          stringArrayMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        interfaceMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          android.aidl.tests.map.IEmpty v;
          v = android.aidl.tests.map.IEmpty.Stub.asInterface(_aidl_parcel.readStrongBinder());
          interfaceMap.put(k, v);
        });
      }
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      {
        int N = _aidl_parcel.readInt();
        ibinderMap = N < 0 ? null : new java.util.HashMap<>();
        java.util.stream.IntStream.range(0, N).forEach(i -> {
          String k = _aidl_parcel.readString();
          android.os.IBinder v;
          v = _aidl_parcel.readStrongBinder();
          ibinderMap.put(k, v);
        });
      }
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
    _mask |= describeContents(barMap);
    _mask |= describeContents(barArrayMap);
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
