/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-permission-java-source/gen/android/aidl/tests/permission/IProtectedInterface.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-permission-java-source/gen -Iframeworks/base/core/java -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/permission/IProtectedInterface.aidl
 */
package android.aidl.tests.permission;
public interface IProtectedInterface extends android.os.IInterface
{
  /** Default implementation for IProtectedInterface. */
  public static class Default implements android.aidl.tests.permission.IProtectedInterface
  {
    @Override public void Method1() throws android.os.RemoteException
    {
    }
    @Override public void Method2() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.permission.IProtectedInterface
  {
    private final android.os.PermissionEnforcer mEnforcer;
    /** Construct the stub using the Enforcer provided. */
    public Stub(android.os.PermissionEnforcer enforcer)
    {
      this.attachInterface(this, DESCRIPTOR);
      if (enforcer == null) {
        throw new IllegalArgumentException("enforcer cannot be null");
      }
      mEnforcer = enforcer;
    }
    @Deprecated
    /** Default constructor. */
    public Stub() {
      this(android.os.PermissionEnforcer.fromContext(
         android.app.ActivityThread.currentActivityThread().getSystemContext()));
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.permission.IProtectedInterface interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.permission.IProtectedInterface asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.permission.IProtectedInterface))) {
        return ((android.aidl.tests.permission.IProtectedInterface)iin);
      }
      return new android.aidl.tests.permission.IProtectedInterface.Stub.Proxy(obj);
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
        case TRANSACTION_Method1:
        {
          return "Method1";
        }
        case TRANSACTION_Method2:
        {
          return "Method2";
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
        case TRANSACTION_Method1:
        {
          this.Method1();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_Method2:
        {
          this.Method2();
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
    private static class Proxy implements android.aidl.tests.permission.IProtectedInterface
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
      @Override public void Method1() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_Method1, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void Method2() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_Method2, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_Method1 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    /** Helper method to enforce permissions for Method1 */
    protected void Method1_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermission(android.Manifest.permission.ACCESS_FINE_LOCATION, getCallingPid(), getCallingUid());
    }
    static final int TRANSACTION_Method2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    /** Helper method to enforce permissions for Method2 */
    protected void Method2_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermission(android.Manifest.permission.ACCESS_FINE_LOCATION, getCallingPid(), getCallingUid());
    }
    /** @hide */
    public int getMaxTransactionId()
    {
      return 1;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.permission.IProtectedInterface";
  @android.annotation.EnforcePermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
  public void Method1() throws android.os.RemoteException;
  @android.annotation.EnforcePermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
  public void Method2() throws android.os.RemoteException;
}
