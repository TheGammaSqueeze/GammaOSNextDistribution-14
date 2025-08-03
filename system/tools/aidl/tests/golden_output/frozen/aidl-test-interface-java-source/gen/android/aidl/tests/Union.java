/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/Union.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/Union.aidl
 */
package android.aidl.tests;
public final class Union implements android.os.Parcelable {
  // tags for union fields
  public final static int ns = 0;  // int[] ns;
  public final static int n = 1;  // int n;
  public final static int m = 2;  // int m;
  public final static int s = 3;  // String s;
  public final static int ibinder = 4;  // IBinder ibinder;
  public final static int ss = 5;  // List<String> ss;
  public final static int be = 6;  // android.aidl.tests.ByteEnum be;

  private int _tag;
  private Object _value;

  public Union() {
    int[] _value = {};
    this._tag = ns;
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

  // int[] ns;

  public static Union ns(int[] _value) {
    return new Union(ns, _value);
  }

  public int[] getNs() {
    _assertTag(ns);
    return (int[]) _value;
  }

  public void setNs(int[] _value) {
    _set(ns, _value);
  }

  // int n;

  public static Union n(int _value) {
    return new Union(n, _value);
  }

  public int getN() {
    _assertTag(n);
    return (int) _value;
  }

  public void setN(int _value) {
    _set(n, _value);
  }

  // int m;

  public static Union m(int _value) {
    return new Union(m, _value);
  }

  public int getM() {
    _assertTag(m);
    return (int) _value;
  }

  public void setM(int _value) {
    _set(m, _value);
  }

  // String s;

  public static Union s(java.lang.String _value) {
    return new Union(s, _value);
  }

  public java.lang.String getS() {
    _assertTag(s);
    return (java.lang.String) _value;
  }

  public void setS(java.lang.String _value) {
    _set(s, _value);
  }

  // IBinder ibinder;

  public static Union ibinder(android.os.IBinder _value) {
    return new Union(ibinder, _value);
  }

  public android.os.IBinder getIbinder() {
    _assertTag(ibinder);
    return (android.os.IBinder) _value;
  }

  public void setIbinder(android.os.IBinder _value) {
    _set(ibinder, _value);
  }

  // List<String> ss;

  public static Union ss(java.util.List<java.lang.String> _value) {
    return new Union(ss, _value);
  }

  @SuppressWarnings("unchecked")
  public java.util.List<java.lang.String> getSs() {
    _assertTag(ss);
    return (java.util.List<java.lang.String>) _value;
  }

  public void setSs(java.util.List<java.lang.String> _value) {
    _set(ss, _value);
  }

  // android.aidl.tests.ByteEnum be;

  public static Union be(byte _value) {
    return new Union(be, _value);
  }

  public byte getBe() {
    _assertTag(be);
    return (byte) _value;
  }

  public void setBe(byte _value) {
    _set(be, _value);
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
    case ns:
      _aidl_parcel.writeIntArray(getNs());
      break;
    case n:
      _aidl_parcel.writeInt(getN());
      break;
    case m:
      _aidl_parcel.writeInt(getM());
      break;
    case s:
      _aidl_parcel.writeString(getS());
      break;
    case ibinder:
      _aidl_parcel.writeStrongBinder(getIbinder());
      break;
    case ss:
      _aidl_parcel.writeStringList(getSs());
      break;
    case be:
      _aidl_parcel.writeByte(getBe());
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case ns: {
      int[] _aidl_value;
      _aidl_value = _aidl_parcel.createIntArray();
      _set(_aidl_tag, _aidl_value);
      return; }
    case n: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    case m: {
      int _aidl_value;
      _aidl_value = _aidl_parcel.readInt();
      _set(_aidl_tag, _aidl_value);
      return; }
    case s: {
      java.lang.String _aidl_value;
      _aidl_value = _aidl_parcel.readString();
      _set(_aidl_tag, _aidl_value);
      return; }
    case ibinder: {
      android.os.IBinder _aidl_value;
      _aidl_value = _aidl_parcel.readStrongBinder();
      _set(_aidl_tag, _aidl_value);
      return; }
    case ss: {
      java.util.List<java.lang.String> _aidl_value;
      _aidl_value = _aidl_parcel.createStringArrayList();
      _set(_aidl_tag, _aidl_value);
      return; }
    case be: {
      byte _aidl_value;
      _aidl_value = _aidl_parcel.readByte();
      _set(_aidl_tag, _aidl_value);
      return; }
    }
    throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
  }

  public static final String S1 = "a string constant in union";
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
    case ns: return "Union.ns(" + (java.util.Arrays.toString(getNs())) + ")";
    case n: return "Union.n(" + (getN()) + ")";
    case m: return "Union.m(" + (getM()) + ")";
    case s: return "Union.s(" + (java.util.Objects.toString(getS())) + ")";
    case ibinder: return "Union.ibinder(" + (java.util.Objects.toString(getIbinder())) + ")";
    case ss: return "Union.ss(" + (java.util.Objects.toString(getSs())) + ")";
    case be: return "Union.be(" + (getBe()) + ")";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof Union)) return false;
    Union that = (Union)other;
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
    case ns: return "ns";
    case n: return "n";
    case m: return "m";
    case s: return "s";
    case ibinder: return "ibinder";
    case ss: return "ss";
    case be: return "be";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int ns = 0;
    public static final int n = 1;
    public static final int m = 2;
    public static final int s = 3;
    public static final int ibinder = 4;
    public static final int ss = 5;
    public static final int be = 6;
  }
}
