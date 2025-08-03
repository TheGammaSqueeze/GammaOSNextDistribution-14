/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen -t --min_sdk_version platform_apis --previous_api_dir=system/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1 --previous_hash 88311b9118fb6fe9eff4a2ca19121de0587f6d5f --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-java-source/gen/android/aidl/test/trunk/ITrunkStableTest.java.d -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-java-source/gen -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
package android.aidl.test.trunk;
public interface ITrunkStableTest extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = true ? 1 : 2;
  public static final String HASH = "88311b9118fb6fe9eff4a2ca19121de0587f6d5f";
  /** Default implementation for ITrunkStableTest. */
  public static class Default implements android.aidl.test.trunk.ITrunkStableTest
  {
    @Override public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte repeatEnum(byte input) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void callMyCallback(android.aidl.test.trunk.ITrunkStableTest.IMyCallback cb) throws android.os.RemoteException
    {
    }
    @Override public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public int getInterfaceVersion() {
      return 0;
    }
    @Override
    public String getInterfaceHash() {
      return "";
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.test.trunk.ITrunkStableTest
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.test.trunk.ITrunkStableTest interface,
     * generating a proxy if needed.
     */
    public static android.aidl.test.trunk.ITrunkStableTest asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.test.trunk.ITrunkStableTest))) {
        return ((android.aidl.test.trunk.ITrunkStableTest)iin);
      }
      return new android.aidl.test.trunk.ITrunkStableTest.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    /** @hide */
    public static java.lang.String getDefaultTransactionName(int transactionCode)
    {
      switch (transactionCode)
      {
        case TRANSACTION_repeatParcelable:
        {
          return "repeatParcelable";
        }
        case TRANSACTION_repeatEnum:
        {
          return "repeatEnum";
        }
        case TRANSACTION_repeatUnion:
        {
          return "repeatUnion";
        }
        case TRANSACTION_callMyCallback:
        {
          return "callMyCallback";
        }
        case TRANSACTION_repeatOtherParcelable:
        {
          return "repeatOtherParcelable";
        }
        case TRANSACTION_getInterfaceVersion:
        {
          return "getInterfaceVersion";
        }
        case TRANSACTION_getInterfaceHash:
        {
          return "getInterfaceHash";
        }
        default:
        {
          return null;
        }
      }
    }
    /** @hide */
    public java.lang.String getTransactionName(int transactionCode)
    {
      return this.getDefaultTransactionName(transactionCode);
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      else if (code == TRANSACTION_getInterfaceVersion) {
        reply.writeNoException();
        reply.writeInt(getInterfaceVersion());
        return true;
      }
      else if (code == TRANSACTION_getInterfaceHash) {
        reply.writeNoException();
        reply.writeString(getInterfaceHash());
        return true;
      }
      switch (code)
      {
        case TRANSACTION_repeatParcelable:
        {
          android.aidl.test.trunk.ITrunkStableTest.MyParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyParcelable.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.test.trunk.ITrunkStableTest.MyParcelable _result = this.repeatParcelable(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_repeatEnum:
        {
          byte _arg0;
          _arg0 = data.readByte();
          data.enforceNoDataAvail();
          byte _result = this.repeatEnum(_arg0);
          reply.writeNoException();
          reply.writeByte(_result);
          break;
        }
        case TRANSACTION_repeatUnion:
        {
          android.aidl.test.trunk.ITrunkStableTest.MyUnion _arg0;
          _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyUnion.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.test.trunk.ITrunkStableTest.MyUnion _result = this.repeatUnion(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_callMyCallback:
        {
          android.aidl.test.trunk.ITrunkStableTest.IMyCallback _arg0;
          _arg0 = android.aidl.test.trunk.ITrunkStableTest.IMyCallback.Stub.asInterface(data.readStrongBinder());
          data.enforceNoDataAvail();
          this.callMyCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_repeatOtherParcelable:
        {
          if (true) {
            throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
          }
          android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _result = this.repeatOtherParcelable(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.test.trunk.ITrunkStableTest
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      private int mCachedVersion = -1;
      private String mCachedHash = "-1";
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.test.trunk.ITrunkStableTest.MyParcelable _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatParcelable, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method repeatParcelable is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyParcelable.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte repeatEnum(byte input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByte(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatEnum, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method repeatEnum is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readByte();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.test.trunk.ITrunkStableTest.MyUnion _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatUnion, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method repeatUnion is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyUnion.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void callMyCallback(android.aidl.test.trunk.ITrunkStableTest.IMyCallback cb) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(cb);
          boolean _status = mRemote.transact(Stub.TRANSACTION_callMyCallback, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method callMyCallback is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException
      {
        if (true) {
          throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
        }
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatOtherParcelable, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override
      public int getInterfaceVersion() throws android.os.RemoteException {
        if (mCachedVersion == -1) {
          android.os.Parcel data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceVersion, data, reply, 0);
            reply.readException();
            mCachedVersion = reply.readInt();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedVersion;
      }
      @Override
      public synchronized String getInterfaceHash() throws android.os.RemoteException {
        if ("-1".equals(mCachedHash)) {
          android.os.Parcel data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceHash, data, reply, 0);
            reply.readException();
            mCachedHash = reply.readString();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedHash;
      }
    }
    static final int TRANSACTION_repeatParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_repeatEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_repeatUnion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_callMyCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_repeatOtherParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 16777214;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android$aidl$test$trunk$ITrunkStableTest".replace('$', '.');
  public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException;
  public byte repeatEnum(byte input) throws android.os.RemoteException;
  public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException;
  public void callMyCallback(android.aidl.test.trunk.ITrunkStableTest.IMyCallback cb) throws android.os.RemoteException;
  public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
  public static class MyParcelable implements android.os.Parcelable
  {
    public int a = 0;
    public int b = 0;
    // New in V2
    public int c = 0;
    public static final android.os.Parcelable.Creator<MyParcelable> CREATOR = new android.os.Parcelable.Creator<MyParcelable>() {
      @Override
      public MyParcelable createFromParcel(android.os.Parcel _aidl_source) {
        MyParcelable _aidl_out = new MyParcelable();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public MyParcelable[] newArray(int _aidl_size) {
        return new MyParcelable[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeInt(a);
      _aidl_parcel.writeInt(b);
      if (false) {;
        _aidl_parcel.writeInt(c);
      };
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
        a = _aidl_parcel.readInt();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        b = _aidl_parcel.readInt();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        if (false) {;
          c = _aidl_parcel.readInt();
        };
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
  }
  public static @interface MyEnum {
    public static final byte ZERO = 0;
    public static final byte ONE = 1;
    public static final byte TWO = 2;
    // New in V2
    public static final byte THREE = 3;
  }
  public static final class MyUnion implements android.os.Parcelable {
    // tags for union fields
    public final static int a = 0;  // int a;
    public final static int b = 1;  // int b;
    public final static int c = 2;  // int c;

    private int _tag;
    private Object _value;

    public MyUnion() {
      int _value = 0;
      this._tag = a;
      this._value = _value;
    }

    private MyUnion(android.os.Parcel _aidl_parcel) {
      readFromParcel(_aidl_parcel);
    }

    private MyUnion(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }

    public int getTag() {
      return _tag;
    }

    // int a;

    public static MyUnion a(int _value) {
      return new MyUnion(a, _value);
    }

    public int getA() {
      _assertTag(a);
      return (int) _value;
    }

    public void setA(int _value) {
      _set(a, _value);
    }

    // int b;

    public static MyUnion b(int _value) {
      return new MyUnion(b, _value);
    }

    public int getB() {
      _assertTag(b);
      return (int) _value;
    }

    public void setB(int _value) {
      _set(b, _value);
    }

    // int c;

    // New in V3
    public static MyUnion c(int _value) {
      return new MyUnion(c, _value);
    }

    public int getC() {
      _assertTag(c);
      return (int) _value;
    }

    public void setC(int _value) {
      _set(c, _value);
    }

    public static final android.os.Parcelable.Creator<MyUnion> CREATOR = new android.os.Parcelable.Creator<MyUnion>() {
      @Override
      public MyUnion createFromParcel(android.os.Parcel _aidl_source) {
        return new MyUnion(_aidl_source);
      }
      @Override
      public MyUnion[] newArray(int _aidl_size) {
        return new MyUnion[_aidl_size];
      }
    };

    @Override
    public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
      _aidl_parcel.writeInt(_tag);
      switch (_tag) {
      case a:
        _aidl_parcel.writeInt(getA());
        break;
      case b:
        _aidl_parcel.writeInt(getB());
        break;
      case c:
        if (true) throw new IllegalArgumentException("union: unknown tag: " + _tag);
        _aidl_parcel.writeInt(getC());
        break;
      }
    }

    public void readFromParcel(android.os.Parcel _aidl_parcel) {
      int _aidl_tag;
      _aidl_tag = _aidl_parcel.readInt();
      switch (_aidl_tag) {
      case a: {
        int _aidl_value;
        _aidl_value = _aidl_parcel.readInt();
        _set(_aidl_tag, _aidl_value);
        return; }
      case b: {
        int _aidl_value;
        _aidl_value = _aidl_parcel.readInt();
        _set(_aidl_tag, _aidl_value);
        return; }
      case c: {
        if (true) throw new IllegalArgumentException("union: unknown tag: " + _tag);
        int _aidl_value;
        _aidl_value = _aidl_parcel.readInt();
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
      case a: return "a";
      case b: return "b";
      case c: return "c";
      }
      throw new IllegalStateException("unknown field: " + _tag);
    }

    private void _set(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }
    public static @interface Tag {
      public static final int a = 0;
      public static final int b = 1;
      // New in V3
      public static final int c = 2;
    }
  }
  public interface IMyCallback extends android.os.IInterface
  {
    /**
     * The version of this interface that the caller is built against.
     * This might be different from what {@link #getInterfaceVersion()
     * getInterfaceVersion} returns as that is the version of the interface
     * that the remote object is implementing.
     */
    public static final int VERSION = true ? 1 : 2;
    public static final String HASH = "88311b9118fb6fe9eff4a2ca19121de0587f6d5f";
    /** Default implementation for IMyCallback. */
    public static class Default implements android.aidl.test.trunk.ITrunkStableTest.IMyCallback
    {
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException
      {
        return null;
      }
      @Override public byte repeatEnum(byte input) throws android.os.RemoteException
      {
        return 0;
      }
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException
      {
        return null;
      }
      @Override public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException
      {
        return null;
      }
      @Override
      public int getInterfaceVersion() {
        return 0;
      }
      @Override
      public String getInterfaceHash() {
        return "";
      }
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.test.trunk.ITrunkStableTest.IMyCallback
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.test.trunk.ITrunkStableTest.IMyCallback interface,
       * generating a proxy if needed.
       */
      public static android.aidl.test.trunk.ITrunkStableTest.IMyCallback asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.test.trunk.ITrunkStableTest.IMyCallback))) {
          return ((android.aidl.test.trunk.ITrunkStableTest.IMyCallback)iin);
        }
        return new android.aidl.test.trunk.ITrunkStableTest.IMyCallback.Stub.Proxy(obj);
      }
      @Override public android.os.IBinder asBinder()
      {
        return this;
      }
      /** @hide */
      public static java.lang.String getDefaultTransactionName(int transactionCode)
      {
        switch (transactionCode)
        {
          case TRANSACTION_repeatParcelable:
          {
            return "repeatParcelable";
          }
          case TRANSACTION_repeatEnum:
          {
            return "repeatEnum";
          }
          case TRANSACTION_repeatUnion:
          {
            return "repeatUnion";
          }
          case TRANSACTION_repeatOtherParcelable:
          {
            return "repeatOtherParcelable";
          }
          case TRANSACTION_getInterfaceVersion:
          {
            return "getInterfaceVersion";
          }
          case TRANSACTION_getInterfaceHash:
          {
            return "getInterfaceHash";
          }
          default:
          {
            return null;
          }
        }
      }
      /** @hide */
      public java.lang.String getTransactionName(int transactionCode)
      {
        return this.getDefaultTransactionName(transactionCode);
      }
      @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
      {
        java.lang.String descriptor = DESCRIPTOR;
        if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
          data.enforceInterface(descriptor);
        }
        if (code == INTERFACE_TRANSACTION) {
          reply.writeString(descriptor);
          return true;
        }
        else if (code == TRANSACTION_getInterfaceVersion) {
          reply.writeNoException();
          reply.writeInt(getInterfaceVersion());
          return true;
        }
        else if (code == TRANSACTION_getInterfaceHash) {
          reply.writeNoException();
          reply.writeString(getInterfaceHash());
          return true;
        }
        switch (code)
        {
          case TRANSACTION_repeatParcelable:
          {
            android.aidl.test.trunk.ITrunkStableTest.MyParcelable _arg0;
            _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyParcelable.CREATOR);
            data.enforceNoDataAvail();
            android.aidl.test.trunk.ITrunkStableTest.MyParcelable _result = this.repeatParcelable(_arg0);
            reply.writeNoException();
            reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            break;
          }
          case TRANSACTION_repeatEnum:
          {
            byte _arg0;
            _arg0 = data.readByte();
            data.enforceNoDataAvail();
            byte _result = this.repeatEnum(_arg0);
            reply.writeNoException();
            reply.writeByte(_result);
            break;
          }
          case TRANSACTION_repeatUnion:
          {
            android.aidl.test.trunk.ITrunkStableTest.MyUnion _arg0;
            _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyUnion.CREATOR);
            data.enforceNoDataAvail();
            android.aidl.test.trunk.ITrunkStableTest.MyUnion _result = this.repeatUnion(_arg0);
            reply.writeNoException();
            reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            break;
          }
          case TRANSACTION_repeatOtherParcelable:
          {
            if (true) {
              throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
            }
            android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _arg0;
            _arg0 = data.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable.CREATOR);
            data.enforceNoDataAvail();
            android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _result = this.repeatOtherParcelable(_arg0);
            reply.writeNoException();
            reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            break;
          }
          default:
          {
            return super.onTransact(code, data, reply, flags);
          }
        }
        return true;
      }
      private static class Proxy implements android.aidl.test.trunk.ITrunkStableTest.IMyCallback
      {
        private android.os.IBinder mRemote;
        Proxy(android.os.IBinder remote)
        {
          mRemote = remote;
        }
        private int mCachedVersion = -1;
        private String mCachedHash = "-1";
        @Override public android.os.IBinder asBinder()
        {
          return mRemote;
        }
        public java.lang.String getInterfaceDescriptor()
        {
          return DESCRIPTOR;
        }
        @Override public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.aidl.test.trunk.ITrunkStableTest.MyParcelable _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeTypedObject(input, 0);
            boolean _status = mRemote.transact(Stub.TRANSACTION_repeatParcelable, _data, _reply, 0);
            if (!_status) {
              throw new android.os.RemoteException("Method repeatParcelable is unimplemented.");
            }
            _reply.readException();
            _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyParcelable.CREATOR);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public byte repeatEnum(byte input) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          byte _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeByte(input);
            boolean _status = mRemote.transact(Stub.TRANSACTION_repeatEnum, _data, _reply, 0);
            if (!_status) {
              throw new android.os.RemoteException("Method repeatEnum is unimplemented.");
            }
            _reply.readException();
            _result = _reply.readByte();
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.aidl.test.trunk.ITrunkStableTest.MyUnion _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeTypedObject(input, 0);
            boolean _status = mRemote.transact(Stub.TRANSACTION_repeatUnion, _data, _reply, 0);
            if (!_status) {
              throw new android.os.RemoteException("Method repeatUnion is unimplemented.");
            }
            _reply.readException();
            _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyUnion.CREATOR);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException
        {
          if (true) {
            throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
          }
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeTypedObject(input, 0);
            boolean _status = mRemote.transact(Stub.TRANSACTION_repeatOtherParcelable, _data, _reply, 0);
            if (!_status) {
              throw new android.os.RemoteException("Method repeatOtherParcelable is unimplemented.");
            }
            _reply.readException();
            _result = _reply.readTypedObject(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable.CREATOR);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        @Override
        public int getInterfaceVersion() throws android.os.RemoteException {
          if (mCachedVersion == -1) {
            android.os.Parcel data = android.os.Parcel.obtain(asBinder());
            android.os.Parcel reply = android.os.Parcel.obtain();
            try {
              data.writeInterfaceToken(DESCRIPTOR);
              boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceVersion, data, reply, 0);
              reply.readException();
              mCachedVersion = reply.readInt();
            } finally {
              reply.recycle();
              data.recycle();
            }
          }
          return mCachedVersion;
        }
        @Override
        public synchronized String getInterfaceHash() throws android.os.RemoteException {
          if ("-1".equals(mCachedHash)) {
            android.os.Parcel data = android.os.Parcel.obtain(asBinder());
            android.os.Parcel reply = android.os.Parcel.obtain();
            try {
              data.writeInterfaceToken(DESCRIPTOR);
              boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceHash, data, reply, 0);
              reply.readException();
              mCachedHash = reply.readString();
            } finally {
              reply.recycle();
              data.recycle();
            }
          }
          return mCachedHash;
        }
      }
      static final int TRANSACTION_repeatParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
      static final int TRANSACTION_repeatEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
      static final int TRANSACTION_repeatUnion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
      static final int TRANSACTION_repeatOtherParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
      static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
      static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
      /** @hide */
      public int getMaxTransactionId()
      {
        return 16777214;
      }
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android$aidl$test$trunk$ITrunkStableTest$IMyCallback".replace('$', '.');
    public android.aidl.test.trunk.ITrunkStableTest.MyParcelable repeatParcelable(android.aidl.test.trunk.ITrunkStableTest.MyParcelable input) throws android.os.RemoteException;
    public byte repeatEnum(byte input) throws android.os.RemoteException;
    public android.aidl.test.trunk.ITrunkStableTest.MyUnion repeatUnion(android.aidl.test.trunk.ITrunkStableTest.MyUnion input) throws android.os.RemoteException;
    public android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable repeatOtherParcelable(android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable input) throws android.os.RemoteException;
    public int getInterfaceVersion() throws android.os.RemoteException;
    public String getInterfaceHash() throws android.os.RemoteException;
  }
  // New in V2
  public static class MyOtherParcelable implements android.os.Parcelable
  {
    public int a = 0;
    public int b = 0;
    public static final android.os.Parcelable.Creator<MyOtherParcelable> CREATOR = new android.os.Parcelable.Creator<MyOtherParcelable>() {
      @Override
      public MyOtherParcelable createFromParcel(android.os.Parcel _aidl_source) {
        MyOtherParcelable _aidl_out = new MyOtherParcelable();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public MyOtherParcelable[] newArray(int _aidl_size) {
        return new MyOtherParcelable[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeInt(a);
      _aidl_parcel.writeInt(b);
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
        a = _aidl_parcel.readInt();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        b = _aidl_parcel.readInt();
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
  }
}
