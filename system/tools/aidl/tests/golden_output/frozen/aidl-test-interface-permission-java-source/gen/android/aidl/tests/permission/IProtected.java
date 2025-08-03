/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-permission-java-source/gen/android/aidl/tests/permission/IProtected.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-permission-java-source/gen -Iframeworks/base/core/java -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/permission/IProtected.aidl
 */
package android.aidl.tests.permission;
public interface IProtected extends android.os.IInterface
{
  /** Default implementation for IProtected. */
  public static class Default implements android.aidl.tests.permission.IProtected
  {
    @Override public void PermissionProtected() throws android.os.RemoteException
    {
    }
    @Override public void MultiplePermissionsAll() throws android.os.RemoteException
    {
    }
    @Override public void MultiplePermissionsAny() throws android.os.RemoteException
    {
    }
    @Override public void NonManifestPermission() throws android.os.RemoteException
    {
    }
    // Used by the integration tests to dynamically set permissions that are considered granted.
    @Override public void Grant(java.lang.String permission) throws android.os.RemoteException
    {
    }
    @Override public void Revoke(java.lang.String permission) throws android.os.RemoteException
    {
    }
    @Override public void RevokeAll() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.permission.IProtected
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
     * Cast an IBinder object into an android.aidl.tests.permission.IProtected interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.permission.IProtected asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.permission.IProtected))) {
        return ((android.aidl.tests.permission.IProtected)iin);
      }
      return new android.aidl.tests.permission.IProtected.Stub.Proxy(obj);
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
        case TRANSACTION_PermissionProtected:
        {
          return "PermissionProtected";
        }
        case TRANSACTION_MultiplePermissionsAll:
        {
          return "MultiplePermissionsAll";
        }
        case TRANSACTION_MultiplePermissionsAny:
        {
          return "MultiplePermissionsAny";
        }
        case TRANSACTION_NonManifestPermission:
        {
          return "NonManifestPermission";
        }
        case TRANSACTION_Grant:
        {
          return "Grant";
        }
        case TRANSACTION_Revoke:
        {
          return "Revoke";
        }
        case TRANSACTION_RevokeAll:
        {
          return "RevokeAll";
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
        case TRANSACTION_PermissionProtected:
        {
          this.PermissionProtected();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_MultiplePermissionsAll:
        {
          this.MultiplePermissionsAll();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_MultiplePermissionsAny:
        {
          this.MultiplePermissionsAny();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_NonManifestPermission:
        {
          this.NonManifestPermission();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_Grant:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          this.Grant(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_Revoke:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          this.Revoke(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_RevokeAll:
        {
          this.RevokeAll();
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
    private static class Proxy implements android.aidl.tests.permission.IProtected
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
      @Override public void PermissionProtected() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_PermissionProtected, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void MultiplePermissionsAll() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_MultiplePermissionsAll, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void MultiplePermissionsAny() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_MultiplePermissionsAny, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void NonManifestPermission() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_NonManifestPermission, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Used by the integration tests to dynamically set permissions that are considered granted.
      @Override public void Grant(java.lang.String permission) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(permission);
          boolean _status = mRemote.transact(Stub.TRANSACTION_Grant, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void Revoke(java.lang.String permission) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(permission);
          boolean _status = mRemote.transact(Stub.TRANSACTION_Revoke, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void RevokeAll() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RevokeAll, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_PermissionProtected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    /** Helper method to enforce permissions for PermissionProtected */
    protected void PermissionProtected_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermission(android.Manifest.permission.READ_PHONE_STATE, getCallingPid(), getCallingUid());
    }
    static final int TRANSACTION_MultiplePermissionsAll = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final String[] PERMISSIONS_MultiplePermissionsAll = {android.Manifest.permission.INTERNET, android.Manifest.permission.VIBRATE};
    /** Helper method to enforce permissions for MultiplePermissionsAll */
    protected void MultiplePermissionsAll_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermissionAllOf(PERMISSIONS_MultiplePermissionsAll, getCallingPid(), getCallingUid());
    }
    static final int TRANSACTION_MultiplePermissionsAny = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final String[] PERMISSIONS_MultiplePermissionsAny = {android.Manifest.permission.INTERNET, android.Manifest.permission.VIBRATE};
    /** Helper method to enforce permissions for MultiplePermissionsAny */
    protected void MultiplePermissionsAny_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermissionAnyOf(PERMISSIONS_MultiplePermissionsAny, getCallingPid(), getCallingUid());
    }
    static final int TRANSACTION_NonManifestPermission = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    /** Helper method to enforce permissions for NonManifestPermission */
    protected void NonManifestPermission_enforcePermission() throws SecurityException {
      mEnforcer.enforcePermission(android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, getCallingPid(), getCallingUid());
    }
    static final int TRANSACTION_Grant = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_Revoke = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_RevokeAll = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 6;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.permission.IProtected";
  @android.annotation.EnforcePermission(android.Manifest.permission.READ_PHONE_STATE)
  public void PermissionProtected() throws android.os.RemoteException;
  @android.annotation.EnforcePermission(allOf = {android.Manifest.permission.INTERNET, android.Manifest.permission.VIBRATE})
  public void MultiplePermissionsAll() throws android.os.RemoteException;
  @android.annotation.EnforcePermission(anyOf = {android.Manifest.permission.INTERNET, android.Manifest.permission.VIBRATE})
  public void MultiplePermissionsAny() throws android.os.RemoteException;
  @android.annotation.EnforcePermission(android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK)
  public void NonManifestPermission() throws android.os.RemoteException;
  // Used by the integration tests to dynamically set permissions that are considered granted.
  @android.annotation.RequiresNoPermission
  public void Grant(java.lang.String permission) throws android.os.RemoteException;
  @android.annotation.RequiresNoPermission
  public void Revoke(java.lang.String permission) throws android.os.RemoteException;
  @android.annotation.RequiresNoPermission
  public void RevokeAll() throws android.os.RemoteException;
}
