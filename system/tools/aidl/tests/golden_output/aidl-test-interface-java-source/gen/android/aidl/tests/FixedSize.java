/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/FixedSize.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/FixedSize.aidl
 */
package android.aidl.tests;
public class FixedSize implements android.os.Parcelable
{
  public static final android.os.Parcelable.Creator<FixedSize> CREATOR = new android.os.Parcelable.Creator<FixedSize>() {
    @Override
    public FixedSize createFromParcel(android.os.Parcel _aidl_source) {
      FixedSize _aidl_out = new FixedSize();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public FixedSize[] newArray(int _aidl_size) {
      return new FixedSize[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
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
    return _mask;
  }
  public static class FixedParcelable implements android.os.Parcelable
  {
    public boolean booleanValue = false;
    public byte byteValue = 0;
    public char charValue = '\0';
    public int intValue = 0;
    public long longValue = 0L;
    public float floatValue = 0.000000f;
    public double doubleValue = 0.000000;
    public long enumValue = android.aidl.tests.LongEnum.FOO;
    public android.aidl.tests.FixedSize.FixedUnion parcelableValue;
    public static final android.os.Parcelable.Creator<FixedParcelable> CREATOR = new android.os.Parcelable.Creator<FixedParcelable>() {
      @Override
      public FixedParcelable createFromParcel(android.os.Parcel _aidl_source) {
        FixedParcelable _aidl_out = new FixedParcelable();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public FixedParcelable[] newArray(int _aidl_size) {
        return new FixedParcelable[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeBoolean(booleanValue);
      _aidl_parcel.writeByte(byteValue);
      _aidl_parcel.writeInt(((int)charValue));
      _aidl_parcel.writeInt(intValue);
      _aidl_parcel.writeLong(longValue);
      _aidl_parcel.writeFloat(floatValue);
      _aidl_parcel.writeDouble(doubleValue);
      _aidl_parcel.writeLong(enumValue);
      _aidl_parcel.writeTypedObject(parcelableValue, _aidl_flag);
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
        booleanValue = _aidl_parcel.readBoolean();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        byteValue = _aidl_parcel.readByte();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        charValue = (char)_aidl_parcel.readInt();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        intValue = _aidl_parcel.readInt();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        longValue = _aidl_parcel.readLong();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        floatValue = _aidl_parcel.readFloat();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        doubleValue = _aidl_parcel.readDouble();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        enumValue = _aidl_parcel.readLong();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        parcelableValue = _aidl_parcel.readTypedObject(android.aidl.tests.FixedSize.FixedUnion.CREATOR);
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
      _mask |= describeContents(parcelableValue);
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
  public static final class FixedUnion implements android.os.Parcelable {
    // tags for union fields
    public final static int booleanValue = 0;  // boolean booleanValue;
    public final static int byteValue = 1;  // byte byteValue;
    public final static int charValue = 2;  // char charValue;
    public final static int intValue = 3;  // int intValue;
    public final static int longValue = 4;  // long longValue;
    public final static int floatValue = 5;  // float floatValue;
    public final static int doubleValue = 6;  // double doubleValue;
    public final static int enumValue = 7;  // android.aidl.tests.LongEnum enumValue;

    private int _tag;
    private Object _value;

    public FixedUnion() {
      boolean _value = false;
      this._tag = booleanValue;
      this._value = _value;
    }

    private FixedUnion(android.os.Parcel _aidl_parcel) {
      readFromParcel(_aidl_parcel);
    }

    private FixedUnion(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }

    public int getTag() {
      return _tag;
    }

    // boolean booleanValue;

    public static FixedUnion booleanValue(boolean _value) {
      return new FixedUnion(booleanValue, _value);
    }

    public boolean getBooleanValue() {
      _assertTag(booleanValue);
      return (boolean) _value;
    }

    public void setBooleanValue(boolean _value) {
      _set(booleanValue, _value);
    }

    // byte byteValue;

    public static FixedUnion byteValue(byte _value) {
      return new FixedUnion(byteValue, _value);
    }

    public byte getByteValue() {
      _assertTag(byteValue);
      return (byte) _value;
    }

    public void setByteValue(byte _value) {
      _set(byteValue, _value);
    }

    // char charValue;

    public static FixedUnion charValue(char _value) {
      return new FixedUnion(charValue, _value);
    }

    public char getCharValue() {
      _assertTag(charValue);
      return (char) _value;
    }

    public void setCharValue(char _value) {
      _set(charValue, _value);
    }

    // int intValue;

    public static FixedUnion intValue(int _value) {
      return new FixedUnion(intValue, _value);
    }

    public int getIntValue() {
      _assertTag(intValue);
      return (int) _value;
    }

    public void setIntValue(int _value) {
      _set(intValue, _value);
    }

    // long longValue;

    public static FixedUnion longValue(long _value) {
      return new FixedUnion(longValue, _value);
    }

    public long getLongValue() {
      _assertTag(longValue);
      return (long) _value;
    }

    public void setLongValue(long _value) {
      _set(longValue, _value);
    }

    // float floatValue;

    public static FixedUnion floatValue(float _value) {
      return new FixedUnion(floatValue, _value);
    }

    public float getFloatValue() {
      _assertTag(floatValue);
      return (float) _value;
    }

    public void setFloatValue(float _value) {
      _set(floatValue, _value);
    }

    // double doubleValue;

    public static FixedUnion doubleValue(double _value) {
      return new FixedUnion(doubleValue, _value);
    }

    public double getDoubleValue() {
      _assertTag(doubleValue);
      return (double) _value;
    }

    public void setDoubleValue(double _value) {
      _set(doubleValue, _value);
    }

    // android.aidl.tests.LongEnum enumValue;

    public static FixedUnion enumValue(long _value) {
      return new FixedUnion(enumValue, _value);
    }

    public long getEnumValue() {
      _assertTag(enumValue);
      return (long) _value;
    }

    public void setEnumValue(long _value) {
      _set(enumValue, _value);
    }

    public static final android.os.Parcelable.Creator<FixedUnion> CREATOR = new android.os.Parcelable.Creator<FixedUnion>() {
      @Override
      public FixedUnion createFromParcel(android.os.Parcel _aidl_source) {
        return new FixedUnion(_aidl_source);
      }
      @Override
      public FixedUnion[] newArray(int _aidl_size) {
        return new FixedUnion[_aidl_size];
      }
    };

    @Override
    public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
      _aidl_parcel.writeInt(_tag);
      switch (_tag) {
      case booleanValue:
        _aidl_parcel.writeBoolean(getBooleanValue());
        break;
      case byteValue:
        _aidl_parcel.writeByte(getByteValue());
        break;
      case charValue:
        _aidl_parcel.writeInt(((int)getCharValue()));
        break;
      case intValue:
        _aidl_parcel.writeInt(getIntValue());
        break;
      case longValue:
        _aidl_parcel.writeLong(getLongValue());
        break;
      case floatValue:
        _aidl_parcel.writeFloat(getFloatValue());
        break;
      case doubleValue:
        _aidl_parcel.writeDouble(getDoubleValue());
        break;
      case enumValue:
        _aidl_parcel.writeLong(getEnumValue());
        break;
      }
    }

    public void readFromParcel(android.os.Parcel _aidl_parcel) {
      int _aidl_tag;
      _aidl_tag = _aidl_parcel.readInt();
      switch (_aidl_tag) {
      case booleanValue: {
        boolean _aidl_value;
        _aidl_value = _aidl_parcel.readBoolean();
        _set(_aidl_tag, _aidl_value);
        return; }
      case byteValue: {
        byte _aidl_value;
        _aidl_value = _aidl_parcel.readByte();
        _set(_aidl_tag, _aidl_value);
        return; }
      case charValue: {
        char _aidl_value;
        _aidl_value = (char)_aidl_parcel.readInt();
        _set(_aidl_tag, _aidl_value);
        return; }
      case intValue: {
        int _aidl_value;
        _aidl_value = _aidl_parcel.readInt();
        _set(_aidl_tag, _aidl_value);
        return; }
      case longValue: {
        long _aidl_value;
        _aidl_value = _aidl_parcel.readLong();
        _set(_aidl_tag, _aidl_value);
        return; }
      case floatValue: {
        float _aidl_value;
        _aidl_value = _aidl_parcel.readFloat();
        _set(_aidl_tag, _aidl_value);
        return; }
      case doubleValue: {
        double _aidl_value;
        _aidl_value = _aidl_parcel.readDouble();
        _set(_aidl_tag, _aidl_value);
        return; }
      case enumValue: {
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
      case booleanValue: return "booleanValue";
      case byteValue: return "byteValue";
      case charValue: return "charValue";
      case intValue: return "intValue";
      case longValue: return "longValue";
      case floatValue: return "floatValue";
      case doubleValue: return "doubleValue";
      case enumValue: return "enumValue";
      }
      throw new IllegalStateException("unknown field: " + _tag);
    }

    private void _set(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }
    public static @interface Tag {
      public static final byte booleanValue = 0;
      public static final byte byteValue = 1;
      public static final byte charValue = 2;
      public static final byte intValue = 3;
      public static final byte longValue = 4;
      public static final byte floatValue = 5;
      public static final byte doubleValue = 6;
      public static final byte enumValue = 7;
    }
  }
}
