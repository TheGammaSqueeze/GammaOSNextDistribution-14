/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -t --min_sdk_version platform_apis -pout/soong/.intermediates/system/tools/aidl/aidl-test-interface_interface/preprocessed.aidl --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-java-source/gen/android/aidl/tests/ICppJavaTests.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-java-source/gen -Iframeworks/native/aidl/binder -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICppJavaTests.aidl
 */
package android.aidl.tests;
// Tests that are only supported by the C++/Java backends, not NDK/Rust
public interface ICppJavaTests extends android.os.IInterface
{
  /** Default implementation for ICppJavaTests. */
  public static class Default implements android.aidl.tests.ICppJavaTests
  {
    @Override public android.aidl.tests.BadParcelable RepeatBadParcelable(android.aidl.tests.BadParcelable input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> RepeatGenericParcelable(android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> input, android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> repeat) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.PersistableBundle RepeatPersistableBundle(android.os.PersistableBundle input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.PersistableBundle[] ReversePersistableBundles(android.os.PersistableBundle[] input, android.os.PersistableBundle[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.Union ReverseUnion(android.aidl.tests.Union input, android.aidl.tests.Union repeated) throws android.os.RemoteException
    {
      return null;
    }
    // Test that List<T> types work correctly.
    @Override public java.util.List<android.os.IBinder> ReverseNamedCallbackList(java.util.List<android.os.IBinder> input, java.util.List<android.os.IBinder> repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.io.FileDescriptor RepeatFileDescriptor(java.io.FileDescriptor read) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.io.FileDescriptor[] ReverseFileDescriptorArray(java.io.FileDescriptor[] input, java.io.FileDescriptor[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ICppJavaTests
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.ICppJavaTests interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.ICppJavaTests asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.ICppJavaTests))) {
        return ((android.aidl.tests.ICppJavaTests)iin);
      }
      return new android.aidl.tests.ICppJavaTests.Stub.Proxy(obj);
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
        case TRANSACTION_RepeatBadParcelable:
        {
          return "RepeatBadParcelable";
        }
        case TRANSACTION_RepeatGenericParcelable:
        {
          return "RepeatGenericParcelable";
        }
        case TRANSACTION_RepeatPersistableBundle:
        {
          return "RepeatPersistableBundle";
        }
        case TRANSACTION_ReversePersistableBundles:
        {
          return "ReversePersistableBundles";
        }
        case TRANSACTION_ReverseUnion:
        {
          return "ReverseUnion";
        }
        case TRANSACTION_ReverseNamedCallbackList:
        {
          return "ReverseNamedCallbackList";
        }
        case TRANSACTION_RepeatFileDescriptor:
        {
          return "RepeatFileDescriptor";
        }
        case TRANSACTION_ReverseFileDescriptorArray:
        {
          return "ReverseFileDescriptorArray";
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
        case TRANSACTION_RepeatBadParcelable:
        {
          android.aidl.tests.BadParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.BadParcelable.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.BadParcelable _result = this.RepeatBadParcelable(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_RepeatGenericParcelable:
        {
          android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.GenericStructuredParcelable.CREATOR);
          android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> _arg1;
          _arg1 = new android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer>();
          data.enforceNoDataAvail();
          android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> _result = this.RepeatGenericParcelable(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedObject(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_RepeatPersistableBundle:
        {
          android.os.PersistableBundle _arg0;
          _arg0 = data.readTypedObject(android.os.PersistableBundle.CREATOR);
          data.enforceNoDataAvail();
          android.os.PersistableBundle _result = this.RepeatPersistableBundle(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReversePersistableBundles:
        {
          android.os.PersistableBundle[] _arg0;
          _arg0 = data.createTypedArray(android.os.PersistableBundle.CREATOR);
          android.os.PersistableBundle[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new android.os.PersistableBundle[_arg1_length];
          }
          data.enforceNoDataAvail();
          android.os.PersistableBundle[] _result = this.ReversePersistableBundles(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseUnion:
        {
          android.aidl.tests.Union _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.Union.CREATOR);
          android.aidl.tests.Union _arg1;
          _arg1 = new android.aidl.tests.Union();
          data.enforceNoDataAvail();
          android.aidl.tests.Union _result = this.ReverseUnion(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedObject(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseNamedCallbackList:
        {
          java.util.List<android.os.IBinder> _arg0;
          _arg0 = data.createBinderArrayList();
          java.util.List<android.os.IBinder> _arg1;
          _arg1 = new java.util.ArrayList<android.os.IBinder>();
          data.enforceNoDataAvail();
          java.util.List<android.os.IBinder> _result = this.ReverseNamedCallbackList(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBinderList(_result);
          reply.writeBinderList(_arg1);
          break;
        }
        case TRANSACTION_RepeatFileDescriptor:
        {
          java.io.FileDescriptor _arg0;
          _arg0 = data.readRawFileDescriptor();
          data.enforceNoDataAvail();
          java.io.FileDescriptor _result = this.RepeatFileDescriptor(_arg0);
          reply.writeNoException();
          reply.writeRawFileDescriptor(_result);
          break;
        }
        case TRANSACTION_ReverseFileDescriptorArray:
        {
          java.io.FileDescriptor[] _arg0;
          _arg0 = data.createRawFileDescriptorArray();
          java.io.FileDescriptor[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new java.io.FileDescriptor[_arg1_length];
          }
          data.enforceNoDataAvail();
          java.io.FileDescriptor[] _result = this.ReverseFileDescriptorArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeRawFileDescriptorArray(_result);
          reply.writeRawFileDescriptorArray(_arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.tests.ICppJavaTests
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
      @Override public android.aidl.tests.BadParcelable RepeatBadParcelable(android.aidl.tests.BadParcelable input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.BadParcelable _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatBadParcelable, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.BadParcelable.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> RepeatGenericParcelable(android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> input, android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> repeat) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatGenericParcelable, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.GenericStructuredParcelable.CREATOR);
          if ((0!=_reply.readInt())) {
            repeat.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.PersistableBundle RepeatPersistableBundle(android.os.PersistableBundle input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.PersistableBundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatPersistableBundle, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.os.PersistableBundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.PersistableBundle[] ReversePersistableBundles(android.os.PersistableBundle[] input, android.os.PersistableBundle[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.PersistableBundle[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(input, 0);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReversePersistableBundles, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createTypedArray(android.os.PersistableBundle.CREATOR);
          _reply.readTypedArray(repeated, android.os.PersistableBundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.Union ReverseUnion(android.aidl.tests.Union input, android.aidl.tests.Union repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.Union _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseUnion, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.Union.CREATOR);
          if ((0!=_reply.readInt())) {
            repeated.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Test that List<T> types work correctly.
      @Override public java.util.List<android.os.IBinder> ReverseNamedCallbackList(java.util.List<android.os.IBinder> input, java.util.List<android.os.IBinder> repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<android.os.IBinder> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBinderList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseNamedCallbackList, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createBinderArrayList();
          _reply.readBinderList(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.io.FileDescriptor RepeatFileDescriptor(java.io.FileDescriptor read) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.io.FileDescriptor _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeRawFileDescriptor(read);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatFileDescriptor, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readRawFileDescriptor();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.io.FileDescriptor[] ReverseFileDescriptorArray(java.io.FileDescriptor[] input, java.io.FileDescriptor[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.io.FileDescriptor[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeRawFileDescriptorArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseFileDescriptorArray, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createRawFileDescriptorArray();
          _reply.readRawFileDescriptorArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_RepeatBadParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_RepeatGenericParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_RepeatPersistableBundle = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_ReversePersistableBundles = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_ReverseUnion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_ReverseNamedCallbackList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_RepeatFileDescriptor = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_ReverseFileDescriptorArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 7;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ICppJavaTests";
  public android.aidl.tests.BadParcelable RepeatBadParcelable(android.aidl.tests.BadParcelable input) throws android.os.RemoteException;
  public android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> RepeatGenericParcelable(android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> input, android.aidl.tests.GenericStructuredParcelable<Integer,android.aidl.tests.StructuredParcelable,Integer> repeat) throws android.os.RemoteException;
  public android.os.PersistableBundle RepeatPersistableBundle(android.os.PersistableBundle input) throws android.os.RemoteException;
  public android.os.PersistableBundle[] ReversePersistableBundles(android.os.PersistableBundle[] input, android.os.PersistableBundle[] repeated) throws android.os.RemoteException;
  public android.aidl.tests.Union ReverseUnion(android.aidl.tests.Union input, android.aidl.tests.Union repeated) throws android.os.RemoteException;
  // Test that List<T> types work correctly.
  public java.util.List<android.os.IBinder> ReverseNamedCallbackList(java.util.List<android.os.IBinder> input, java.util.List<android.os.IBinder> repeated) throws android.os.RemoteException;
  public java.io.FileDescriptor RepeatFileDescriptor(java.io.FileDescriptor read) throws android.os.RemoteException;
  public java.io.FileDescriptor[] ReverseFileDescriptorArray(java.io.FileDescriptor[] input, java.io.FileDescriptor[] repeated) throws android.os.RemoteException;
}
