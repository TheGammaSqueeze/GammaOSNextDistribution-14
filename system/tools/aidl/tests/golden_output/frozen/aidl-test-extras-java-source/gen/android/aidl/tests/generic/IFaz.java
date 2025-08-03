/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/generic/IFaz.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/generic/IFaz.aidl
 */
package android.aidl.tests.generic;
public interface IFaz extends android.os.IInterface
{
  /** Default implementation for IFaz. */
  public static class Default implements android.aidl.tests.generic.IFaz
  {
    @Override public android.aidl.tests.generic.Pair<Integer,java.lang.String> getPair() throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.generic.Pair<android.aidl.tests.generic.Baz,android.aidl.tests.generic.Baz> getPair2() throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.generic.Pair<Integer,Integer> getPair3() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.generic.IFaz
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.generic.IFaz interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.generic.IFaz asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.generic.IFaz))) {
        return ((android.aidl.tests.generic.IFaz)iin);
      }
      return new android.aidl.tests.generic.IFaz.Stub.Proxy(obj);
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
        case TRANSACTION_getPair:
        {
          return "getPair";
        }
        case TRANSACTION_getPair2:
        {
          return "getPair2";
        }
        case TRANSACTION_getPair3:
        {
          return "getPair3";
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
      switch (code)
      {
        case TRANSACTION_getPair:
        {
          android.aidl.tests.generic.Pair<Integer,java.lang.String> _result = this.getPair();
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getPair2:
        {
          android.aidl.tests.generic.Pair<android.aidl.tests.generic.Baz,android.aidl.tests.generic.Baz> _result = this.getPair2();
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getPair3:
        {
          android.aidl.tests.generic.Pair<Integer,Integer> _result = this.getPair3();
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
    private static class Proxy implements android.aidl.tests.generic.IFaz
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public android.aidl.tests.generic.Pair<Integer,java.lang.String> getPair() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.generic.Pair<Integer,java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPair, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.generic.Pair.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.generic.Pair<android.aidl.tests.generic.Baz,android.aidl.tests.generic.Baz> getPair2() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.generic.Pair<android.aidl.tests.generic.Baz,android.aidl.tests.generic.Baz> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPair2, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.generic.Pair.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.generic.Pair<Integer,Integer> getPair3() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.generic.Pair<Integer,Integer> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPair3, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.generic.Pair.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_getPair = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_getPair2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getPair3 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 2;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.generic.IFaz";
  public android.aidl.tests.generic.Pair<Integer,java.lang.String> getPair() throws android.os.RemoteException;
  public android.aidl.tests.generic.Pair<android.aidl.tests.generic.Baz,android.aidl.tests.generic.Baz> getPair2() throws android.os.RemoteException;
  public android.aidl.tests.generic.Pair<Integer,Integer> getPair3() throws android.os.RemoteException;
}
