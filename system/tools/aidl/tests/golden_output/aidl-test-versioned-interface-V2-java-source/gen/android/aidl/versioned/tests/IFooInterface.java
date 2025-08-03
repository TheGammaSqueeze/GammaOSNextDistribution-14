/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 2 --hash da8c4bc94ca7feff0e0a65563a466787698b5891 --rpc --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-java-source/gen/android/aidl/versioned/tests/IFooInterface.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-java-source/gen -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/2 system/tools/aidl/aidl_api/aidl-test-versioned-interface/2/android/aidl/versioned/tests/IFooInterface.aidl
 */
package android.aidl.versioned.tests;
public interface IFooInterface extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 2;
  public static final String HASH = "da8c4bc94ca7feff0e0a65563a466787698b5891";
  /** Default implementation for IFooInterface. */
  public static class Default implements android.aidl.versioned.tests.IFooInterface
  {
    @Override public void originalApi() throws android.os.RemoteException
    {
    }
    @Override public java.lang.String acceptUnionAndReturnString(android.aidl.versioned.tests.BazUnion u) throws android.os.RemoteException
    {
      return null;
    }
    @Override public int ignoreParcelablesAndRepeatInt(android.aidl.versioned.tests.Foo inFoo, android.aidl.versioned.tests.Foo inoutFoo, android.aidl.versioned.tests.Foo outFoo, int value) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int returnsLengthOfFooArray(android.aidl.versioned.tests.Foo[] foos) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public void newApi() throws android.os.RemoteException
    {
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
  public static abstract class Stub extends android.os.Binder implements android.aidl.versioned.tests.IFooInterface
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.versioned.tests.IFooInterface interface,
     * generating a proxy if needed.
     */
    public static android.aidl.versioned.tests.IFooInterface asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.versioned.tests.IFooInterface))) {
        return ((android.aidl.versioned.tests.IFooInterface)iin);
      }
      return new android.aidl.versioned.tests.IFooInterface.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
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
        case TRANSACTION_originalApi:
        {
          this.originalApi();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_acceptUnionAndReturnString:
        {
          android.aidl.versioned.tests.BazUnion _arg0;
          _arg0 = data.readTypedObject(android.aidl.versioned.tests.BazUnion.CREATOR);
          data.enforceNoDataAvail();
          java.lang.String _result = this.acceptUnionAndReturnString(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_ignoreParcelablesAndRepeatInt:
        {
          android.aidl.versioned.tests.Foo _arg0;
          _arg0 = data.readTypedObject(android.aidl.versioned.tests.Foo.CREATOR);
          android.aidl.versioned.tests.Foo _arg1;
          _arg1 = data.readTypedObject(android.aidl.versioned.tests.Foo.CREATOR);
          android.aidl.versioned.tests.Foo _arg2;
          _arg2 = new android.aidl.versioned.tests.Foo();
          int _arg3;
          _arg3 = data.readInt();
          data.enforceNoDataAvail();
          int _result = this.ignoreParcelablesAndRepeatInt(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          reply.writeInt(_result);
          reply.writeTypedObject(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedObject(_arg2, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_returnsLengthOfFooArray:
        {
          android.aidl.versioned.tests.Foo[] _arg0;
          _arg0 = data.createTypedArray(android.aidl.versioned.tests.Foo.CREATOR);
          data.enforceNoDataAvail();
          int _result = this.returnsLengthOfFooArray(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_newApi:
        {
          this.newApi();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.versioned.tests.IFooInterface
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
      @Override public void originalApi() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_originalApi, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method originalApi is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String acceptUnionAndReturnString(android.aidl.versioned.tests.BazUnion u) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(u, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_acceptUnionAndReturnString, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method acceptUnionAndReturnString is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int ignoreParcelablesAndRepeatInt(android.aidl.versioned.tests.Foo inFoo, android.aidl.versioned.tests.Foo inoutFoo, android.aidl.versioned.tests.Foo outFoo, int value) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(inFoo, 0);
          _data.writeTypedObject(inoutFoo, 0);
          _data.writeInt(value);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ignoreParcelablesAndRepeatInt, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method ignoreParcelablesAndRepeatInt is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readInt();
          if ((0!=_reply.readInt())) {
            inoutFoo.readFromParcel(_reply);
          }
          if ((0!=_reply.readInt())) {
            outFoo.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int returnsLengthOfFooArray(android.aidl.versioned.tests.Foo[] foos) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(foos, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_returnsLengthOfFooArray, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method returnsLengthOfFooArray is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void newApi() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_newApi, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method newApi is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
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
    static final int TRANSACTION_originalApi = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_acceptUnionAndReturnString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_ignoreParcelablesAndRepeatInt = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_returnsLengthOfFooArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_newApi = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android$aidl$versioned$tests$IFooInterface".replace('$', '.');
  public void originalApi() throws android.os.RemoteException;
  public java.lang.String acceptUnionAndReturnString(android.aidl.versioned.tests.BazUnion u) throws android.os.RemoteException;
  public int ignoreParcelablesAndRepeatInt(android.aidl.versioned.tests.Foo inFoo, android.aidl.versioned.tests.Foo inoutFoo, android.aidl.versioned.tests.Foo outFoo, int value) throws android.os.RemoteException;
  public int returnsLengthOfFooArray(android.aidl.versioned.tests.Foo[] foos) throws android.os.RemoteException;
  public void newApi() throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
}
