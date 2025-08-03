/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/immutable/IBaz.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/immutable/IBaz.aidl
 */
package android.aidl.tests.immutable;
public interface IBaz extends android.os.IInterface
{
  /** Default implementation for IBaz. */
  public static class Default implements android.aidl.tests.immutable.IBaz
  {
    @Override public android.aidl.tests.immutable.Foo RepeatFoo(android.aidl.tests.immutable.Foo a) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.immutable.IBaz
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.immutable.IBaz interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.immutable.IBaz asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.immutable.IBaz))) {
        return ((android.aidl.tests.immutable.IBaz)iin);
      }
      return new android.aidl.tests.immutable.IBaz.Stub.Proxy(obj);
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
        case TRANSACTION_RepeatFoo:
        {
          return "RepeatFoo";
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
        case TRANSACTION_RepeatFoo:
        {
          android.aidl.tests.immutable.Foo _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.immutable.Foo.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.immutable.Foo _result = this.RepeatFoo(_arg0);
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
    private static class Proxy implements android.aidl.tests.immutable.IBaz
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
      @Override public android.aidl.tests.immutable.Foo RepeatFoo(android.aidl.tests.immutable.Foo a) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.immutable.Foo _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(a, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatFoo, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.immutable.Foo.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_RepeatFoo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 0;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.immutable.IBaz";
  public android.aidl.tests.immutable.Foo RepeatFoo(android.aidl.tests.immutable.Foo a) throws android.os.RemoteException;
}
